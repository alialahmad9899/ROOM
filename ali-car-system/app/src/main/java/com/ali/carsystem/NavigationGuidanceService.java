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
import android.os.Handler;
import android.os.Process;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public static final String EXTRA_ROUTE_FILE = "route_file";
    public static final String EXTRA_DEST_NAME = "destination_name";
    public static final String EXTRA_DEST_LAT = "destination_lat";
    public static final String EXTRA_DEST_LON = "destination_lon";

    private static final String PREFS = "ali_car_navigation";
    private static final String KEY_ROUTE = "active_route_json";
    private static final String KEY_ROUTE_FILE = "active_route_file";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_LATEST_STATE = "latest_state";
    private static final int NOTIFICATION_ID = 7391;
    private static final float MAX_GUIDANCE_ACCURACY_M = 50f;
    private static final long MAX_FIX_AGE_MS = 6000L;

    private final List<GeoPoint> routePoints = new ArrayList<>();
    private final List<Maneuver> maneuvers = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ali-car-navigation-io");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private RouteProgressEngine progressEngine;
    private RouteProgressEngine.Progress latestProgress;
    private boolean routeLoaded, gpsRegistered, networkRegistered, criteriaRegistered;
    private int routeLoadGeneration;
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
        SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = state.getString(KEY_ROUTE_FILE, "");
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            tts = new TextToSpeech(this, this);
        } catch (Exception error) {
            tts = null;
            ttsReady = false;
            arabicAvailable = false;
            AppDiagnostics.record(this, "Initialize Arabic navigation TTS", error);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            clearRoute();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground("جارٍ تجهيز المسار المحلي للملاحة...");
        String routeFilePath = intent == null ? null : intent.getStringExtra(EXTRA_ROUTE_FILE);
        if (routeFilePath == null || routeFilePath.trim().isEmpty()) {
            routeFilePath = prefs.getString(KEY_ROUTE_FILE, "");
        }
        if (routeFilePath == null || routeFilePath.trim().isEmpty()
                || !new File(routeFilePath).isFile()) {
            publishState("لا يوجد ملف مسار محفوظ صالح للاستعادة", false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_START.equals(intent.getAction())) {
            String name = intent.getStringExtra(EXTRA_DEST_NAME);
            if (name != null && !name.trim().isEmpty()) destinationName = name;
            destinationLat = intent.getDoubleExtra(EXTRA_DEST_LAT, destinationLat);
            destinationLon = intent.getDoubleExtra(EXTRA_DEST_LON, destinationLon);
        }

        final String selectedFile = routeFilePath;
        final int generation = ++routeLoadGeneration;
        prefs.edit().putString(KEY_ROUTE_FILE, selectedFile).putBoolean(KEY_ACTIVE, false).apply();
        routeExecutor.execute(() -> {
            try {
                StringBuilder json = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(selectedFile), "UTF-8"))) {
                    char[] buffer = new char[8192];
                    int count;
                    while ((count = reader.read(buffer)) != -1) json.append(buffer, 0, count);
                }
                if (json.length() == 0) throw new IllegalArgumentException("ملف المسار فارغ");
                parsePayload(new JSONObject(json.toString()));
                mainHandler.post(() -> {
                    if (generation != routeLoadGeneration) return;
                    routeLoaded = true;
                    prefs.edit().putString("destination_name", destinationName)
                            .putFloat("destination_lat", (float) destinationLat)
                            .putFloat("destination_lon", (float) destinationLon).apply();
                    startGps();
                });
            } catch (Exception error) {
                AppDiagnostics.record(this, "Navigation route file load/parse", error);
                final String detail = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                mainHandler.post(() -> {
                    if (generation != routeLoadGeneration) return;
                    publishState("تعذر تحميل المسار المحفوظ: " + detail, false);
                    clearRoute();
                    stopForeground(true);
                    stopSelf();
                });
            }
        });
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
        List<RouteProgressEngine.Point> matcherPoints = new ArrayList<>(routePoints.size());
        for (GeoPoint point : routePoints) {
            matcherPoints.add(new RouteProgressEngine.Point(point.getLatitude(), point.getLongitude()));
        }
        List<RouteProgressEngine.Turn> matcherTurns = new ArrayList<>(maneuvers.size());
        for (Maneuver turn : maneuvers) {
            matcherTurns.add(new RouteProgressEngine.Turn(nearestRouteIndex(turn.point), turn.text));
        }
        progressEngine = new RouteProgressEngine(matcherPoints, matcherTurns);
        latestProgress = null;
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

    private int nearestRouteIndex(GeoPoint point) {
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < routePoints.size(); i++) {
            GeoPoint candidate = routePoints.get(i);
            double distance = GeoMath.distanceMeters(point.getLatitude(), point.getLongitude(),
                    candidate.getLatitude(), candidate.getLongitude());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void startGps() {
        if (locationManager == null || !routeLoaded) {
            publishState("محرك الموقع غير جاهز بعد؛ المسار محفوظ", true);
            return;
        }
        boolean fine = checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            publishState("إذن الموقع غير ممنوح؛ فعّله لمتابعة الملاحة", true);
            return;
        }

        try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        gpsRegistered = false;
        networkRegistered = false;
        criteriaRegistered = false;
        boolean gpsEnabled = false, networkEnabled = false;
        try { gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER); }
        catch (Exception ignored) { }
        try { networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
        catch (Exception ignored) { }

        if (fine && gpsEnabled) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, this, Looper.getMainLooper());
                gpsRegistered = true;
            } catch (Exception ignored) { gpsRegistered = false; }
        }
        if (coarse && networkEnabled) {
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        2500L, 0f, this, Looper.getMainLooper());
                networkRegistered = true;
            } catch (Exception ignored) { networkRegistered = false; }
        }
        try {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(fine ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);
            criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
            criteria.setAltitudeRequired(false);
            criteria.setBearingRequired(false);
            criteria.setSpeedRequired(false);
            criteria.setCostAllowed(true);
            String best = locationManager.getBestProvider(criteria, true);
            if (best != null) {
                locationManager.requestLocationUpdates(2000L, 0f, criteria, this, Looper.getMainLooper());
                criteriaRegistered = true;
            }
        } catch (Exception ignored) { criteriaRegistered = false; }

        Location bestFix = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                if (!locationManager.getAllProviders().contains(provider)) continue;
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate == null) continue;
                long age = candidate.getElapsedRealtimeNanos() > 0
                        ? Math.max(0L, (android.os.SystemClock.elapsedRealtimeNanos()
                        - candidate.getElapsedRealtimeNanos()) / 1000000L)
                        : Math.max(0L, System.currentTimeMillis() - candidate.getTime());
                if (age > 300000L) continue;
                if (bestFix == null || !bestFix.hasAccuracy()
                        || (candidate.hasAccuracy() && candidate.getAccuracy() < bestFix.getAccuracy())
                        || candidate.getTime() > bestFix.getTime()) {
                    bestFix = candidate;
                }
            } catch (SecurityException ignored) { }
            catch (Exception ignored) { }
        }
        if (bestFix != null) onLocationChanged(bestFix);
        if (gpsRegistered || networkRegistered || criteriaRegistered) {
            publishState("الملاحة الخلفية جاهزة • GPS "
                    + (gpsRegistered ? "مسجّل" : "بانتظار GPS")
                    + " • الشبكة " + (networkRegistered ? "متاحة" : "غير متاحة"), true);
        } else {
            publishState("الخدمة تعمل والمسار محفوظ • فعّل الموقع للحصول على التوجيه", true);
        }
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null || progressEngine == null || !routeLoaded || rerouting) return;
        lastAccuracy = location.hasAccuracy() ? location.getAccuracy() : -1f;
        long ageMs = location.getElapsedRealtimeNanos() > 0
                ? Math.max(0L, (android.os.SystemClock.elapsedRealtimeNanos()
                - location.getElapsedRealtimeNanos()) / 1000000L)
                : Math.max(0L, System.currentTimeMillis() - location.getTime());
        if (ageMs > MAX_FIX_AGE_MS || lastAccuracy <= 0f
                || lastAccuracy > MAX_GUIDANCE_ACCURACY_M) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastPoorGpsNotice > 15000L) {
                lastPoorGpsNotice = now;
                publishState("الموقع التقريبي متاح، ننتظر GPS أدق قبل إعطاء تعليمات انعطاف", true);
            }
            return;
        }

        float bearing = location.hasBearing() ? location.getBearing() : Float.NaN;
        float speed = location.hasSpeed() ? location.getSpeed() : 0f;
        latestProgress = progressEngine.update(location.getLatitude(), location.getLongitude(),
                lastAccuracy, bearing, speed);
        if (latestProgress == null || !latestProgress.onRoute) {
            double distance = latestProgress == null ? Double.MAX_VALUE
                    : latestProgress.distanceFromRouteMeters;
            if (distance > 90d) {
                offRouteFixes++;
                if (offRouteSince == 0L) offRouteSince = android.os.SystemClock.elapsedRealtime();
                long now = android.os.SystemClock.elapsedRealtime();
                if (offRouteFixes >= 3 && now - offRouteSince >= 10000L
                        && now - lastRerouteAt > 20000L && !rerouting) {
                    lastRerouteAt = now;
                    recalculateRoute(location);
                } else if (now - lastPoorGpsNotice > 8000L) {
                    lastPoorGpsNotice = now;
                    publishState("قد تكون خارج المسار • جارٍ التحقق من الموقع", true);
                }
            }
            return;
        }

        offRouteFixes = 0;
        offRouteSince = 0L;
        currentProgressMeters = Math.max(currentProgressMeters, latestProgress.progressMeters);
        nextManeuverIndex = latestProgress.nextTurnIndex < 0
                ? maneuvers.size() : latestProgress.nextTurnIndex;
        updateNextManeuver();
        publishProgress();
    }

    private void updateNextManeuver() {
        if (latestProgress == null || !latestProgress.onRoute) return;
        if (latestProgress.nextTurnIndex < 0 || latestProgress.nextTurnIndex >= maneuvers.size()) {
            nextManeuverIndex = maneuvers.size();
            publishState("تابع حتى الوصول إلى " + destinationName, true);
            return;
        }
        nextManeuverIndex = latestProgress.nextTurnIndex;
        Maneuver next = maneuvers.get(nextManeuverIndex);
        double distance = Math.max(0d, latestProgress.remainingToTurnMeters);
        double remaining = Math.max(0d, latestProgress.remainingRouteMeters);
        long etaMinutes = routeSeconds > 0d && totalMeters > 0d
                ? Math.round((routeSeconds * (remaining / totalMeters)) / 60d) : -1L;
        String label = "بعد " + Math.round(distance) + " متر، " + next.text;
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
        try {
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
        } catch (Exception error) {
            ttsReady = false;
            arabicAvailable = false;
            AppDiagnostics.record(this, "Configure Arabic navigation TTS", error);
            publishState("تعذر تهيئة الصوت العربي؛ التعليمات النصية مستمرة", true);
        }
    }

    private void recalculateRoute(Location start) {
        if (rerouting || !routeLoaded) return;
        rerouting = true;
        final Location origin = new Location(start);
        final double lat = destinationLat, lon = destinationLon;
        new AsyncTask<Void, Void, JSONObject>() {
            @Override protected JSONObject doInBackground(Void... ignored) {
                try {
                    JSONObject result = OfflineRoutingEngine.requestRoutes(getApplicationContext(), origin,
                            new GeoPoint(lat, lon));
                    JSONArray routes = result == null ? null : result.optJSONArray("routes");
                    if (routes == null || routes.length() == 0)
                        throw new IOException("لم يُرجع محرك BRouter مساراً جديداً");
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

                    // Prepare and validate the replacement off the main thread.
                    parsePayload(payload);
                    String activePath = prefs.getString(KEY_ROUTE_FILE, "");
                    if (activePath == null || activePath.isEmpty())
                        throw new IOException("ملف المسار النشط غير محفوظ");
                    File target = new File(activePath);
                    File temporary = new File(activePath + ".tmp");
                    File backup = new File(activePath + ".bak");
                    try (java.io.FileOutputStream output = new java.io.FileOutputStream(temporary, false)) {
                        output.write(payload.toString().getBytes("UTF-8"));
                        output.flush();
                        output.getFD().sync();
                    }
                    if (backup.exists()) backup.delete();
                    if (target.exists() && !target.renameTo(backup))
                        throw new IOException("تعذر حفظ نسخة المسار السابقة");
                    if (!temporary.renameTo(target)) {
                        if (backup.exists()) backup.renameTo(target);
                        throw new IOException("تعذر تثبيت المسار الجديد");
                    }
                    if (backup.exists()) backup.delete();
                    return payload;
                } catch (Exception error) {
                    AppDiagnostics.record(NavigationGuidanceService.this, "Offline reroute update", error);
                    // Restore the last known-good route in memory if the replacement failed.
                    try {
                        String oldPath = prefs.getString(KEY_ROUTE_FILE, "");
                        if (oldPath != null && !oldPath.isEmpty()) {
                            StringBuilder oldJson = new StringBuilder();
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                    new FileInputStream(oldPath), "UTF-8"))) {
                                char[] buffer = new char[8192];
                                int count;
                                while ((count = reader.read(buffer)) != -1) oldJson.append(buffer, 0, count);
                            }
                            if (oldJson.length() > 0) parsePayload(new JSONObject(oldJson.toString()));
                        }
                    } catch (Exception restoreError) {
                        android.util.Log.e("ALI-CAR-Navigation", "Could not restore previous route", restoreError);
                    }
                    JSONObject failure = new JSONObject();
                    try { failure.put("_ali_error", error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage()); }
                    catch (Exception ignoredError) { }
                    return failure;
                }
            }

            @Override protected void onPostExecute(JSONObject payload) {
                rerouting = false;
                if (payload == null || payload.has("_ali_error")) {
                    publishState("تعذر إعادة حساب المسار محلياً: "
                            + (payload == null ? "نتيجة فارغة" : payload.optString("_ali_error")), true);
                    return;
                }
                currentProgressMeters = 0d;
                latestProgress = null;
                offRouteFixes = 0;
                offRouteSince = 0L;
                lastAnnouncedStage = -1;
                prefs.edit().putString("destination_name", destinationName)
                        .putFloat("destination_lat", (float) destinationLat)
                        .putFloat("destination_lon", (float) destinationLon).apply();
                publishState("أُعيد حساب المسار محلياً بعد الانحراف", true);
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
        if (latestProgress != null && latestProgress.onRoute
                && nextManeuverIndex < maneuvers.size()) {
            Maneuver m = maneuvers.get(nextManeuverIndex);
            double distance = Math.max(0d, latestProgress.remainingToTurnMeters);
            double remaining = Math.max(0d, latestProgress.remainingRouteMeters);
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
        prefs.edit().putBoolean(KEY_ACTIVE, active && routeLoaded)
                .putString(KEY_LATEST_STATE, latestState).apply();
        Intent update = new Intent(ACTION_STATE);
        update.setPackage(getPackageName());
        update.putExtra("guidance", latestState);
        update.putExtra("active", active && routeLoaded);
        update.putExtra("ready", routeLoaded);
        update.putExtra("accuracy_meters", lastAccuracy);
        sendBroadcast(update);
        if (active) ensureForeground(latestState);
    }

    private void clearRoute() {
        routeLoadGeneration++;
        routeLoaded = false;
        gpsRegistered = false;
        networkRegistered = false;
        criteriaRegistered = false;
        routePoints.clear();
        maneuvers.clear();
        progressEngine = null;
        latestProgress = null;
        String oldPath = prefs.getString(KEY_ROUTE_FILE, "");
        prefs.edit().remove(KEY_ROUTE).remove(KEY_ROUTE_FILE)
                .remove("destination_name").remove("destination_lat").remove("destination_lon")
                .putBoolean(KEY_ACTIVE, false).apply();
        if (oldPath != null && !oldPath.isEmpty()) {
            try { new File(oldPath).delete(); } catch (Exception ignored) { }
        }
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
        mainHandler.removeCallbacksAndMessages(null);
        routeExecutor.shutdownNow();
        if (foregroundStarted) stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}