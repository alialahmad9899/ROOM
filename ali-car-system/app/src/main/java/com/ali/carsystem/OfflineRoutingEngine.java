package com.ali.carsystem;

import android.content.Context;
import android.location.Location;

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

/**
 * Offline car-routing bridge to the BRouter engine bundled by the build workflow.
 * Reflection keeps normal IDE builds possible even before the optional engine JAR
 * has been generated; no network connection is used by this class.
 */
final class OfflineRoutingEngine {
    private OfflineRoutingEngine() { }

    static JSONObject requestRoutes(Context context, Location start, GeoPoint end) throws Exception {
        if (context == null || start == null || end == null) throw new IOException("نقاط المسار غير مكتملة");
        if (!OfflineMapsManager.hasOfflineRouting(context)) return null;

        File base = OfflineMapsManager.getBrouterRoot(context);
        File segments = new File(base, "segments4");
        File profiles = new File(base, "profiles2");
        File modes = new File(base, "modes");
        if (!modes.exists()) modes.mkdirs();

        // BRouter looks up the profile by name under profileBaseDir.
        System.setProperty("profileBaseDir", profiles.getAbsolutePath());
        System.setProperty("segmentBaseDir", segments.getAbsolutePath());

        try {
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
            if (waypoints == null || waypoints.size() < 2) throw new IOException("تعذر تجهيز نقاط الانطلاق والوجهة");

            Object routingContext = contextClass.getDeclaredConstructor().newInstance();
            Field localFunction = contextClass.getField("localFunction");
            localFunction.set(routingContext, "car-fast");
            try { contextClass.getField("memoryclass").setInt(routingContext, 32); } catch (Exception ignored) { }
            try { contextClass.getField("rawTrackPath").set(routingContext, new File(modes, "car-fast_rawtrack.dat").getAbsolutePath()); } catch (Exception ignored) { }

            Map<String, String> params = new HashMap<>();
            params.put("outputFormat", "json");
            params.put("turnInstructionMode", "1");
            params.put("maxRunningTime", "45");
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
            return normalize(geoJson);
        } catch (ClassNotFoundException missing) {
            throw new IOException("محرك التوجيه المحلي غير مضمّن في هذا الإصدار؛ أعد تثبيت حزمة ALI CAR مع BRouter", missing);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            throw new IOException("فشل حساب المسار المحلي: " + message, cause);
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

        JSONObject route = new JSONObject();
        route.put("geometry", geometry);
        route.put("distance", Math.max(0d, distance));
        route.put("duration", Math.max(0d, duration));
        JSONObject leg = new JSONObject();
        leg.put("steps", new JSONArray());
        route.put("legs", new JSONArray().put(leg));

        JSONObject result = new JSONObject();
        result.put("code", "Ok");
        result.put("_ali_provider", "offline-brouter");
        result.put("routes", new JSONArray().put(route));
        return result;
    }

    private static double number(String value) {
        if (value == null) return 0d;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isNaN(parsed) || Double.isInfinite(parsed) ? 0d : parsed;
        } catch (Exception ignored) { return 0d; }
    }
}
