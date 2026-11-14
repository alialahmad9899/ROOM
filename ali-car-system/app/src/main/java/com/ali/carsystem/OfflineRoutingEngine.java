package com.ali.carsystem;

import android.content.Context;
import android.app.ActivityManager;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Offline car-routing bridge to the BRouter engine bundled by the build workflow.
 * Reflection keeps normal IDE builds possible even before the optional engine JAR
 * has been generated; no network connection is used by this class.
 */
final class OfflineRoutingEngine {
    private static final String TAG = "ALI-CAR-Routing";
    private static final ReentrantLock ROUTING_LOCK = new ReentrantLock();
    private static final AtomicLong REQUEST_IDS = new AtomicLong();

    private OfflineRoutingEngine() { }

    static JSONObject requestRoutes(Context context, Location start, GeoPoint end) throws Exception {
        if (context == null || start == null || end == null)
            throw new IOException("نقاط المسار غير مكتملة");
        if (!OfflineMapsManager.hasOfflineRouting(context)) return null;

        boolean acquired;
        try {
            acquired = ROUTING_LOCK.tryLock(1L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("تم إيقاف انتظار محرك التوجيه", interrupted);
        }
        if (!acquired) throw new IOException("محرك التوجيه مشغول بطلب سابق؛ أعد المحاولة بعد لحظة");

        long startedAt = SystemClock.elapsedRealtime();
        long requestId = REQUEST_IDS.incrementAndGet();
        File rawTrack = null;
        try {
            File base = OfflineMapsManager.getBrouterRoot(context);
            File segments = new File(base, "segments4");
            File profiles = new File(base, "profiles2");
            File modes = new File(base, "modes");
            if (!modes.exists() && !modes.mkdirs())
                throw new IOException("تعذر إنشاء مجلد ملفات BRouter المؤقتة");

            // BRouter uses process-wide directory properties; the lock above serializes
            // every call and the raw-track file is unique to this request.
            System.setProperty("profileBaseDir", profiles.getAbsolutePath());
            System.setProperty("segmentBaseDir", segments.getAbsolutePath());

            Class<?> collectorClass = Class.forName("btools.router.RoutingParamCollector");
            Class<?> contextClass = Class.forName("btools.router.RoutingContext");
            Class<?> engineClass = Class.forName("btools.router.RoutingEngine");
            Class<?> jsonFormatClass = Class.forName("btools.router.FormatJson");

            Object collector = collectorClass.getDeclaredConstructor().newInstance();
            String lonLats = String.format(Locale.US, "%.7f,%.7f;%.7f,%.7f",
                    start.getLongitude(), start.getLatitude(), end.getLongitude(), end.getLatitude());
            @SuppressWarnings("unchecked")
            List<Object> waypoints = (List<Object>) collectorClass
                    .getMethod("getWayPointList", String.class).invoke(collector, lonLats);
            if (waypoints == null || waypoints.size() < 2)
                throw new IOException("تعذر تجهيز نقاط الانطلاق والوجهة");

            Object routingContext = contextClass.getDeclaredConstructor().newInstance();
            contextClass.getField("localFunction").set(routingContext, "car-fast");
            int memoryMb = 32;
            try {
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (manager != null) memoryMb = Math.max(32, Math.min(64, manager.getMemoryClass() / 4));
                contextClass.getField("memoryclass").setInt(routingContext, memoryMb);
            } catch (Exception ignored) { }
            rawTrack = new File(modes, "car-fast_rawtrack-" + requestId + ".dat");
            try {
                contextClass.getField("rawTrackPath").set(routingContext, rawTrack.getAbsolutePath());
            } catch (Exception ignored) { }

            Map<String, String> params = new HashMap<>();
            params.put("outputFormat", "json");
            params.put("turnInstructionMode", "1");
            Method setParams = collectorClass.getMethod("setParams", contextClass, List.class, Map.class);
            setParams.invoke(collector, routingContext, waypoints, params);

            Constructor<?> engineConstructor = engineClass.getConstructor(
                    String.class, String.class, File.class, List.class, contextClass);
            Object engine = engineConstructor.newInstance(null, null, segments, waypoints, routingContext);
            engineClass.getMethod("doRun", long.class).invoke(engine, 45000L);

            String error = (String) engineClass.getMethod("getErrorMessage").invoke(engine);
            if (error != null && !error.trim().isEmpty()) throw new IOException("BRouter: " + error);
            Object track = engineClass.getMethod("getFoundTrack").invoke(engine);
            if (track == null) throw new IOException("محرك التوجيه المحلي لم يُرجع مساراً");

            Constructor<?> formatterConstructor = jsonFormatClass.getConstructor(contextClass);
            Object formatter = formatterConstructor.newInstance(routingContext);
            String geoJson = (String) jsonFormatClass.getMethod("format", track.getClass()).invoke(formatter, track);
            JSONObject normalized = normalize(geoJson);
            JSONArray routes = normalized.optJSONArray("routes");
            int pointCount = 0;
            if (routes != null && routes.length() > 0) {
                JSONObject geometry = routes.getJSONObject(0).optJSONObject("geometry");
                JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
                pointCount = coordinates == null ? 0 : coordinates.length();
            }
            Log.i(TAG, "route#" + requestId + " elapsedMs="
                    + (SystemClock.elapsedRealtime() - startedAt)
                    + " points=" + pointCount + " memoryMb=" + memoryMb);
            return normalized;
        } catch (ClassNotFoundException missing) {
            Log.e(TAG, "BRouter classes missing", missing);
            throw new IOException("محرك التوجيه المحلي غير مضمّن في هذا الإصدار؛ أعد تثبيت حزمة ALI CAR مع BRouter", missing);
        } catch (IOException error) {
            Log.e(TAG, "route#" + requestId + " failed after "
                    + (SystemClock.elapsedRealtime() - startedAt) + "ms: " + error.getMessage(), error);
            throw error;
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            Log.e(TAG, "route#" + requestId + " failed after "
                    + (SystemClock.elapsedRealtime() - startedAt) + "ms", cause);
            throw new IOException("فشل حساب المسار المحلي: " + message, cause);
        } finally {
            if (rawTrack != null && rawTrack.exists()) {
                try { rawTrack.delete(); } catch (Exception ignored) { }
            }
            ROUTING_LOCK.unlock();
        }
    }

    private static JSONObject normalize(String raw) throws Exception {
        JSONObject geoJson = new JSONObject(raw);
        JSONArray features = geoJson.optJSONArray("features");
        if (features == null || features.length() == 0) throw new IOException("BRouter لم يُنتج هندسة للمسار");
        JSONObject feature = features.optJSONObject(0);
        JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (coordinates == null || coordinates.length() < 2) throw new IOException("المسار المحلي لا يحتوي نقاطاً كافية");
        JSONObject properties = feature.optJSONObject("properties");
        double distance = number(properties == null ? null : properties.optString("track-length", "0"));
        double duration = number(properties == null ? null : properties.optString("total-time", "0"));

        JSONArray steps = buildManeuverSteps(properties, coordinates);
        JSONObject route = new JSONObject();
        route.put("geometry", geometry);
        route.put("distance", Math.max(0d, distance));
        route.put("duration", Math.max(0d, duration));
        JSONObject leg = new JSONObject();
        leg.put("steps", steps);
        route.put("legs", new JSONArray().put(leg));

        JSONObject result = new JSONObject();
        result.put("code", "Ok");
        result.put("_ali_provider", "offline-brouter");
        result.put("routes", new JSONArray().put(route));
        return result;
    }

    private static JSONArray buildManeuverSteps(JSONObject properties, JSONArray coordinates) throws Exception {
        JSONArray steps = new JSONArray();
        JSONArray hints = properties == null ? null : properties.optJSONArray("voicehints");
        if (hints != null) {
            for (int i = 0; i < hints.length(); i++) {
                JSONArray hint = hints.optJSONArray(i);
                if (hint == null || hint.length() < 5) continue;
                int pointIndex = hint.optInt(0, -1);
                int command = hint.optInt(1, 1);
                int exit = hint.optInt(2, 0);
                double distance = hint.optDouble(3, 0d);
                if (pointIndex < 0 || pointIndex >= coordinates.length()) continue;
                JSONArray point = coordinates.optJSONArray(pointIndex);
                if (point == null || point.length() < 2) continue;

                String modifier = command == 2 || command == 3 || command == 4 || command == 8 || command == 10
                        ? "left"
                        : command == 5 || command == 6 || command == 7 || command == 9 || command == 11
                        ? "right"
                        : command == 10 || command == 11 || command == 15 ? "uturn" : "straight";
                if (command == 10 || command == 11 || command == 15) modifier = "uturn";
                String type = command == 13 || command == 14 ? "roundabout" : command == 1 || command == 16 ? "continue" : "turn";

                JSONObject maneuver = new JSONObject();
                maneuver.put("type", type);
                maneuver.put("modifier", modifier);
                maneuver.put("location", new JSONArray().put(point.optDouble(0)).put(point.optDouble(1)));
                if (exit > 0) maneuver.put("exit", exit);

                JSONObject step = new JSONObject();
                step.put("maneuver", maneuver);
                step.put("distance", Math.max(0d, distance));
                step.put("name", "");
                steps.put(step);
            }
        }

        if (coordinates.length() > 0) {
            JSONArray last = coordinates.optJSONArray(coordinates.length() - 1);
            if (last != null && last.length() >= 2) {
                JSONObject arrival = new JSONObject();
                arrival.put("type", "arrive");
                arrival.put("modifier", "straight");
                arrival.put("location", new JSONArray().put(last.optDouble(0)).put(last.optDouble(1)));
                steps.put(new JSONObject().put("maneuver", arrival).put("distance", 0d).put("name", ""));
            }
        }
        return steps;
    }

    private static double number(String value) {
        if (value == null) return 0d;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isNaN(parsed) || Double.isInfinite(parsed) ? 0d : parsed;
        } catch (Exception ignored) { return 0d; }
    }
}
