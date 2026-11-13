package com.ali.carsystem;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;

/**
 * Offline-first foreground turn guidance. The selected route is persisted as JSON,
 * so route tracking can survive activity removal and service recreation.
 */
public final class NavigationGuidanceService extends Service implements LocationListener,
        TextToSpeech.OnInitListener, AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_START = "com.ali.carsystem.NAVIGATION_START";
    public static final String ACTION_STOP = "com.ali.carsystem.NAVIGATION_STOP";
    public static final String ACTION_STATE = "com.ali.carsystem.NAVIGATION_STATE";
    public static final String EXTRA_ROUTE_JSON = "route_json";
    public static final String EXTRA_DEST_NAME = "destination_name";
    public static final String EXTRA_DEST_LAT = "destination_lat";
    public static final String EXTRA_DEST_LON = "destination_lon";

    private static final String PREFS = "ali_car_navigation";
    private static final String KEY_ROUTE = "active_route_json";
    private static final int NOTIFICATION_ID = 7391;
    private static final float MAX_GUIDANCE_ACCURACY_M = 50f;
    private static final long MAX_FIX_AGE_MS = 6000L;

    private final List<GeoPoint> routePoints = new ArrayList<>();
    private final List<Maneuver> maneuvers = new ArrayList<>();
    private SharedPreferences prefs;
    private LocationManager locationManager;
    private TextToSpeech tts;
    private AudioManager audioManager;
    private boolean ttsReady, arabicAvailable, foregroundStarted, rerouting;
    private String destinationName = "الوجهة";
    private double destinationLat, destinationLon, totalMeters, routeSeconds;
    private double currentProgressMeters;
    private long offRouteSince;
    private int offRouteFixes, nextManeuverIndex, lastAnnouncedStage = -1;
    private long lastPoorGpsNotice, lastStateNotice, lastRerouteAt;
    private float lastAccuracy = -1f;
    private String latestState = "الملاحة الخلفية جاهزة";

    private static final class Maneuver {
        final GeoPoint point;
        final String text;
        double distanceAlong;
        Maneuver(GeoPoint p, String t) { point = p; text = t; }
    }

    public static boolean isActive(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ROUTE, "").length() > 0;
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        tts = new TextToSpeech(this, this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            clearRoute();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String routeJson = intent == null ? null : intent.getStringExtra(EXTRA_ROUTE_JSON);
        if (routeJson != null && !routeJson.trim().isEmpty()) {
            prefs.edit().putString(KEY_ROUTE, routeJson).apply();
        } else {
            routeJson = prefs.getString(KEY_ROUTE, "");
        }
        if (routeJson == null || routeJson.trim().isEmpty()) {
            publishState("لا يوجد مسار محفوظ لاستعادته", false);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            parsePayload(new JSONObject(routeJson));
        } catch (Exception error) {
            publishState("تعذر استعادة المسار المحفوظ؛ أعد حسابه من الخريطة", false);
            clearRoute();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_START.equals(intent.getAction())) {
            destinationName = intent.getStringExtra(EXTRA_DEST_NAME) == null
                    ? destinationName : intent.getStringExtra(EXTRA_DEST_NAME);
            destinationLat = intent.getDoubleExtra(EXTRA_DEST_LAT, destinationLat);
            destinationLon = intent.getDoubleExtra(EXTRA_DEST_LON, destinationLon);
        }
        prefs.edit().putString("destination_name", destinationName)
                .putFloat("destination_lat", (float) destinationLat)
                .putFloat("destination_lon", (float) destinationLon).apply();

        ensureForeground("المسار محفوظ • بانتظار إشارة GPS");
        startGps();
        return START_STICKY;
    }

    private void parsePayload(JSONObject payload) throws Exception {
        JSONArray points = payload.optJSONArray("points");
        JSONArray turns = payload.optJSONArray("maneuvers");
        if (points == null || points.length() < 2) throw new IllegalArgumentException("هندسة المسار ناقصة");
        routePoints.clear();
        maneuvers.clear();
        totalMeters = 0d;
        GeoPoint previous = null;
        for (int i = 0; i < points.length(); i++) {
            JSONArray pair = points.optJSONArray(i);
            if (pair == null || pair.length() < 2) continue;
            double lat = pair.optDouble(0, Double.NaN);
            double lon = pair.optDouble(1, Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
            GeoPoint point = new GeoPoint(lat, lon);
            if (previous != null) totalMeters += GeoMath.distanceMeters(
                    previous.getLatitude(), previous.getLongitude(), lat, lon);
            routePoints.add(point);
            previous = point;
        }
        if (routePoints.size() < 2 || totalMeters < 20d) throw new IllegalArgumentException("المسار غير صالح");
        routeSeconds = Math.max(0d, payload.optDouble("duration_seconds", 0d));
        destinationName = payload.optString("destination_name", destinationName);
        destinationLat = payload.optDouble("destination_lat", destinationLat);
        destinationLon = payload.optDouble("destination_lon", destinationLon);

        if (turns != null) {
            for (int i = 0; i < turns.length(); i++) {
                JSONObject turn = turns.optJSONObject(i);
                if (turn == null) continue;
                double lat = turn.optDouble("lat", Double.NaN);
                double lon = turn.optDouble("lon", Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                Maneuver m = new Maneuver(new GeoPoint(lat, lon), turn.optString("text", "تابع الطريق"));
                m.distanceAlong = nearestRouteDistance(m.point);
                maneuvers.add(m);
            }
        }
        Collections.sort(maneuvers, (a, b) -> Double.compare(a.distanceAlong, b.distanceAlong));
        currentProgressMeters = 0d;
        nextManeuverIndex = 0;
        lastAnnouncedStage = -1;
    }

    private double nearestRouteDistance(GeoPoint point) {
        double cumulative = 0d, bestDistance = Double.MAX_VALUE, bestAlong = 0d;
        for (int i = 0; i < routePoints.size(); i++) {
            GeoPoint p = routePoints.get(i);
            double d = GeoMath.distanceMeters(point.getLatitude(), point.getLongitude(),
                    p.getLatitude(), p.getLongitude());
            if (d < bestDistance) { bestDistance = d; bestAlong = cumulative; }
            if (i + 1 < routePoints.size()) {
                GeoPoint q = routePoints.get(i + 1);
                cumulative += GeoMath.distanceMeters(p.getLatitude(), p.getLongitude(),
                        q.getLatitude(), q.getLongitude());
            }
        }
        return bestAlong;
    }

    private void startGps() {
        if (locationManager == null) {
            publishState("خدمة GPS غير متاحة في الجهاز", true);
            return;
        }
        if (checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                != PackageManager.PERMISSION_GRANTED) {
            publishState("إذن GPS الدقيق غير ممنوح؛ فعّله لاستمرار الملاحة", true);
            return;
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                publishState("GPS متوقف؛ فعّل الموقع لمتابعة المسار", true);
                return;
            }
            locationManager.removeUpdates(this);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f,
                    this, Looper.getMainLooper());
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) onLocationChanged(last);
            publishState("الملاحة الخلفية نشطة • توجيه محلي دون إنترنت", true);
        } catch (SecurityException error) {
            publishState("رفض النظام إذن الوصول إلى GPS", true);
        } catch (Exception error) {
            publishState("تعذر تشغيل GPS: " + error.getClass().getSimpleName(), true);
        }
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null) return;
        lastAccuracy = location.hasAccuracy() ? location.getAccuracy() : -1f;
        long ageMs = location.getElapsedRealtimeNanos() > 0
                ? Math.max(0L, (android.os.SystemClock.elapsedRealtimeNanos()
                - location.getElapsedRealtimeNanos()) / 1000000L)
                : Math.max(0L, System.currentTimeMillis() - location.getTime());
        if (lastAccuracy <= 0 || lastAccuracy > MAX_GUIDANCE_ACCURACY_M || ageMs > MAX_FIX_AGE_MS) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastPoorGpsNotice > 15000L) {
                lastPoorGpsNotice = now;
                publishState("إشارة GPS ضعيفة أو قديمة؛ تحقق من الطريق ولا تعتمد على التوجيه حالياً", true);
            }
            return;
        }

        Match match = matchRoute(location);
        currentProgressMeters = Math.max(currentProgressMeters, match.progressMeters);
        if (match.distanceMeters > 120d) {
            offRouteFixes++;
            if (offRouteSince == 0d) offRouteSince = android.os.SystemClock.elapsedRealtime();
            long now = android.os.SystemClock.elapsedRealtime();
            if (offRouteFixes >= 2 && now - offRouteSince >= 10000L
                    && now - lastRerouteAt > 20000L && !rerouting) {
                lastRerouteAt = now;
                recalculateRoute(location);
            }
        } else {
            offRouteFixes = 0;
            offRouteSince = 0d;
        }
        updateNextManeuver();
        publishProgress();
    }

    private static final class Match {
        final double distanceMeters, progressMeters;
        Match(double d, double p) { distanceMeters = d; progressMeters = p; }
    }

    private Match matchRoute(Location location) {
        double bestDistance = Double.MAX_VALUE, bestProgress = currentProgressMeters;
        double cumulative = 0d;
        double latScale = 110540d;
        double cos = Math.cos(Math.toRadians(location.getLatitude()));
        for (int i = 0; i + 1 < routePoints.size(); i++) {
            GeoPoint a = routePoints.get(i), b = routePoints.get(i + 1);
            double ax = (a.getLongitude() - location.getLongitude()) * 111320d * cos;
            double ay = (a.getLatitude() - location.getLatitude()) * latScale;
            double bx = (b.getLongitude() - location.getLongitude()) * 111320d * cos;
            double by = (b.getLatitude() - location.getLatitude()) * latScale;
            double dx = bx - ax, dy = by - ay;
            double denom = dx * dx + dy * dy;
            double t = denom <= 0d ? 0d : Math.max(0d, Math.min(1d, -(ax * dx + ay * dy) / denom));
            double px = ax + t * dx, py = ay + t * dy;
            double distance = Math.sqrt(px * px + py * py);
            double segment = GeoMath.distanceMeters(a.getLatitude(), a.getLongitude(),
                    b.getLatitude(), b.getLongitude());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestProgress = cumulative + segment * t;
            }
            cumulative += segment;
        }
        return new Match(bestDistance, bestProgress);
    }

    private void updateNextManeuver() {
        while (nextManeuverIndex < maneuvers.size()
                && maneuvers.get(nextManeuverIndex).distanceAlong < currentProgressMeters - 25d) {
            nextManeuverIndex++;
            lastAnnouncedStage = -1;
        }
        if (nextManeuverIndex >= maneuvers.size()) {
            publishState("تابع حتى الوصول إلى " + destinationName, true);
            return;
        }
        Maneuver next = maneuvers.get(nextManeuverIndex);
        double distance = Math.max(0d, next.distanceAlong - currentProgressMeters);
        String label = "بعد " + Math.round(distance) + " متر، " + next.text;
        double remaining = Math.max(0d, totalMeters - currentProgressMeters);
        long etaMinutes = routeSeconds > 0d && totalMeters > 0d
                ? Math.round((routeSeconds * (remaining / totalMeters)) / 60d) : -1L;
        publishState(label + " • متبقٍ " + distanceLabel(remaining)
                + (etaMinutes >= 0 ? " • وصول خلال " + etaMinutes + " دقيقة" : ""), true);

        int stage = distance <= 30d ? 3 : distance <= 100d ? 2 : distance <= 300d ? 1
                : distance <= 800d ? 0 : -1;
        if (stage >= 0 && stage > lastAnnouncedStage) {
            lastAnnouncedStage = stage;
            String spoken = stage == 3 ? "الآن، " + next.text
                    : "بعد " + Math.max(50, Math.round((float) distance / 50f) * 50) + " متر، " + next.text;
            speakArabic(spoken);
        }
    }

    private String distanceLabel(double meters) {
        return meters >= 1000d ? String.format(Locale.US, "%.1f كيلومتر", meters / 1000d)
                : Math.round(meters) + " متر";
    }

    private void speakArabic(String message) {
        if (tts == null || !ttsReady || !arabicAvailable
                || !prefs.getBoolean("voice_enabled", true)) {
            if (!arabicAvailable) publishState("بيانات الصوت العربي غير متاحة؛ التوجيه النصي مستمر دون صوت", true);
            return;
        }
        try {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ali-nav-" + nextManeuverIndex + "-" + lastAnnouncedStage);
            params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME,
                    String.valueOf(Math.max(0.1f, Math.min(1f, prefs.getFloat("voice_volume", 0.85f)))));
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, params);
        } catch (Exception error) {
            if (audioManager != null) audioManager.abandonAudioFocus(this);
        }
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            arabicAvailable = false;
            publishState("محرك النطق غير جاهز؛ لا يوجد تحويل صوتي إلى الإنجليزية كبديل", true);
            return;
        }
        int language = tts.isLanguageAvailable(new Locale("ar", "SA"));
        arabicAvailable = language >= TextToSpeech.LANG_AVAILABLE
                && tts.setLanguage(new Locale("ar", "SA")) >= TextToSpeech.LANG_AVAILABLE;
        ttsReady = true;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) {
                if (audioManager != null) audioManager.abandonAudioFocus(NavigationGuidanceService.this);
            }
            @Override public void onError(String utteranceId) {
                if (audioManager != null) audioManager.abandonAudioFocus(NavigationGuidanceService.this);
            }
        });
        if (!arabicAvailable) publishState("لا توجد بيانات نطق عربية مثبتة؛ تظهر التعليمات بالعربية على الشاشة فقط", true);
    }

    private void recalculateRoute(Location start) {
        rerouting = true;
        final Location origin = new Location(start);
        final double lat = destinationLat, lon = destinationLon;
        new AsyncTask<Void, Void, JSONObject>() {
            @Override protected JSONObject doInBackground(Void... ignored) {
                try {
                    return OfflineRoutingEngine.requestRoutes(getApplicationContext(), origin,
                            new GeoPoint(lat, lon));
                } catch (Exception error) { return null; }
            }
            @Override protected void onPostExecute(JSONObject result) {
                rerouting = false;
                try {
                    JSONArray routes = result == null ? null : result.optJSONArray("routes");
                    if (routes == null || routes.length() == 0) {
                        publishState("خرجت عن المسار وتعذر إعادة الحساب محلياً؛ توقف بمكان آمن وافحص الخريطة", true);
                        return;
                    }
                    JSONObject route = routes.getJSONObject(0);
                    JSONObject geometry = route.getJSONObject("geometry");
                    JSONArray coordinates = geometry.getJSONArray("coordinates");
                    JSONArray points = new JSONArray();
                    for (int i = 0; i < coordinates.length(); i++) {
                        JSONArray xy = coordinates.getJSONArray(i);
                        points.put(new JSONArray().put(xy.getDouble(1)).put(xy.getDouble(0)));
                    }
                    JSONArray turnList = new JSONArray();
                    JSONArray legs = route.optJSONArray("legs");
                    if (legs != null) for (int li = 0; li < legs.length(); li++) {
                        JSONArray steps = legs.getJSONObject(li).optJSONArray("steps");
                        if (steps == null) continue;
                        for (int si = 0; si < steps.length(); si++) {
                            JSONObject maneuver = steps.getJSONObject(si).optJSONObject("maneuver");
                            if (maneuver == null) continue;
                            JSONArray loc = maneuver.optJSONArray("location");
                            if (loc == null || loc.length() < 2) continue;
                            turnList.put(new JSONObject().put("lat", loc.getDouble(1))
                                    .put("lon", loc.getDouble(0))
                                    .put("text", arabicInstruction(maneuver)));
                        }
                    }
                    JSONObject payload = new JSONObject();
                    payload.put("points", points);
                    payload.put("maneuvers", turnList);
                    payload.put("destination_name", destinationName);
                    payload.put("destination_lat", destinationLat);
                    payload.put("destination_lon", destinationLon);
                    payload.put("duration_seconds", route.optDouble("duration", 0d));
                    parsePayload(payload);
                    prefs.edit().putString(KEY_ROUTE, payload.toString()).apply();
                    publishState("أُعيد حساب المسار محلياً بعد الانحراف", true);
                } catch (Exception error) {
                    publishState("تعذر تحديث المسار المحلي؛ تحقق من الطريق يدوياً", true);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private String arabicInstruction(JSONObject maneuver) {
        String type = maneuver.optString("type", "");
        String mod = maneuver.optString("modifier", "");
        int exit = maneuver.optInt("exit", 0);
        if ("arrive".equals(type)) return "وصلت إلى الوجهة";
        if ("roundabout".equals(type) || "rotary".equals(type))
            return exit > 0 ? "ادخل الدوار وخذ المخرج رقم " + exit : "ادخل الدوار";
        if (mod.contains("left")) return "انعطف يساراً";
        if (mod.contains("right")) return "انعطف يميناً";
        if (mod.contains("uturn")) return "استدر للخلف";
        return "تابع السير إلى الأمام";
    }

    private void ensureForeground(String text) {
        Notification notification = buildNotification(text);
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification);
            foregroundStarted = true;
        } else {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, NavigationActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, NavigationGuidanceService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, NOTIFICATION_ID + 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("ALI CAR • الملاحة")
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopPending)
                .build();
    }

    private void publishProgress() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastStateNotice < 900L) return;
        lastStateNotice = now;
        if (nextManeuverIndex < maneuvers.size()) {
            Maneuver m = maneuvers.get(nextManeuverIndex);
            double distance = Math.max(0d, m.distanceAlong - currentProgressMeters);
            double remaining = Math.max(0d, totalMeters - currentProgressMeters);
            long eta = routeSeconds > 0d && totalMeters > 0d
                    ? Math.round(routeSeconds * remaining / totalMeters / 60d) : -1L;
            String message = "بعد " + Math.round(distance) + " متر، " + m.text
                    + " • قطعت " + distanceLabel(currentProgressMeters) + " • متبقٍ " + distanceLabel(remaining)
                    + (eta >= 0 ? " • " + eta + " دقيقة" : "");
            latestState = message;
            ensureForeground(message);
            publishState(message, true);
        }
    }

    private void publishState(String message, boolean active) {
        latestState = message == null ? "" : message;
        Intent update = new Intent(ACTION_STATE);
        update.setPackage(getPackageName());
        update.putExtra("guidance", latestState);
        update.putExtra("active", active);
        update.putExtra("accuracy_meters", lastAccuracy);
        sendBroadcast(update);
        if (active) ensureForeground(latestState);
    }

    private void clearRoute() {
        prefs.edit().remove(KEY_ROUTE).remove("destination_name")
                .remove("destination_lat").remove("destination_lon").apply();
        try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Exception ignored) { }
        if (tts != null) tts.stop();
        if (audioManager != null) audioManager.abandonAudioFocus(this);
        publishState("تم إيقاف الملاحة الخلفية", false);
    }

    @Override public void onProviderEnabled(String provider) { startGps(); }
    @Override public void onProviderDisabled(String provider) {
        publishState("GPS متوقف؛ لا يمكن متابعة المسار حتى إعادة تفعيله", true);
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onAudioFocusChange(int focusChange) { }

    @Override public void onDestroy() {
        try { if (locationManager != null) locationManager.removeUpdates(this); } catch (Exception ignored) { }
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { } }
        if (audioManager != null) audioManager.abandonAudioFocus(this);
        if (foregroundStarted) stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}