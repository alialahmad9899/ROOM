package com.ali.carsystem;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Keeps an explicitly-started route alive after NavigationActivity is closed.
 * Spoken guidance is Arabic-only and is suppressed while the GPS fix is weak
 * or the current position cannot be matched to the selected road geometry.
 */
public final class NavigationService extends Service implements LocationListener, TextToSpeech.OnInitListener {
    public static final String ACTION_START = "com.ali.carsystem.navigation.START";
    public static final String ACTION_STOP = "com.ali.carsystem.navigation.STOP";
    public static final String ACTION_STATE = "com.ali.carsystem.navigation.STATE";
    public static final String EXTRA_ROUTE_JSON = "route_json";

    private static final String PREFS = "ali_car_navigation";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ROUTE = "route_json";
    private static final String KEY_STATE = "last_state";
    private static final int NOTIFICATION_ID = 2304;
    private static final long GOOD_FIX_MAX_AGE_MS = 15000L;
    private static final float VOICE_MAX_ACCURACY_METERS = 30f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private AudioManager audioManager;
    private TextToSpeech tts;
    private PowerManager.WakeLock navigationWakeLock;
    private RouteProgressEngine progressEngine;
    private List<RouteProgressEngine.Turn> turns = new ArrayList<>();
    private String destination = "";
    private String routeJson = "";
    private double totalDistanceMeters;
    private double totalDurationMinutes;
    private double startProgressMeters;
    private boolean arabicVoiceReady;
    private boolean locationRegistered;
    private boolean routeActive;
    private boolean arrived;
    private int currentTurnIndex = -1;
    private int spokenStage;
    private int offRouteFixes;
    private long lastNotificationAt;
    private long lastAudioUtteranceId;
    private String statusText = "بانتظار إشارة GPS";

    private final AudioManager.OnAudioFocusChangeListener audioFocusListener =
            focusChange -> { /* Navigation speech is short; the next spoken cue requests focus again. */ };

    @Override public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        tts = new TextToSpeech(getApplicationContext(), this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopNavigation();
            return START_NOT_STICKY;
        }

        String suppliedRoute = intent == null ? null : intent.getStringExtra(EXTRA_ROUTE_JSON);
        if (suppliedRoute != null && !suppliedRoute.trim().isEmpty()) {
            routeJson = suppliedRoute;
            getPreferences().edit().putString(KEY_ROUTE, routeJson).apply();
        } else if (routeJson.isEmpty()) {
            routeJson = getPreferences().getString(KEY_ROUTE, "");
        }

        if (!configureRoute(routeJson)) {
            routeActive = false;
            statusText = "تعذر استعادة المسار؛ افتح الخريطة وحدد الوجهة مجدداً";
            getPreferences().edit().putBoolean(KEY_ACTIVE, false).putString(KEY_STATE, statusText).apply();
            broadcastState(false, null, 0, 0, 0, statusText);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        routeActive = true;
        statusText = "الملاحة نشطة • بانتظار تثبيت موقع GPS";
        getPreferences().edit().putBoolean(KEY_ACTIVE, true).putString(KEY_STATE, statusText).apply();
        startForeground(NOTIFICATION_ID, buildNotification(statusText));
        acquireNavigationWakeLock();
        startGpsUpdates();
        if (suppliedRoute != null) {
            speak("بدأ التوجيه إلى " + destination);
        }
        broadcastState(true, null, 0, totalDistanceMeters, 0, statusText);
        return START_STICKY;
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean configureRoute(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return false;
            JSONObject root = new JSONObject(json);
            destination = root.optString("destination", "الوجهة المحددة");
            totalDistanceMeters = root.optDouble("distance_m", 0);
            totalDurationMinutes = root.optDouble("duration_min", 0);
            JSONArray pointsJson = root.optJSONArray("points");
            if (pointsJson == null || pointsJson.length() < 2 || pointsJson.length() > 30000) return false;

            ArrayList<RouteProgressEngine.Point> points = new ArrayList<>();
            for (int i = 0; i < pointsJson.length(); i++) {
                JSONArray pair = pointsJson.optJSONArray(i);
                if (pair == null || pair.length() < 2) return false;
                double lat = pair.getDouble(0);
                double lon = pair.getDouble(1);
                if (Double.isNaN(lat) || Double.isNaN(lon)
                        || Math.abs(lat) > 90 || Math.abs(lon) > 180) return false;
                points.add(new RouteProgressEngine.Point(lat, lon));
            }

            turns = new ArrayList<>();
            JSONArray turnsJson = root.optJSONArray("maneuvers");
            if (turnsJson != null) {
                for (int i = 0; i < turnsJson.length() && i < 300; i++) {
                    JSONObject item = turnsJson.optJSONObject(i);
                    if (item == null) continue;
                    int index = item.optInt("route_index", -1);
                    String text = item.optString("instruction", "");
                    if (index >= 0 && index < points.size() && !text.trim().isEmpty()) {
                        turns.add(new RouteProgressEngine.Turn(index, text));
                    }
                }
            }
            if (turns.isEmpty() || !turns.get(turns.size() - 1).instruction.contains("وصلت")) {
                turns.add(new RouteProgressEngine.Turn(points.size() - 1, "وصلت إلى وجهتك"));
            }

            progressEngine = new RouteProgressEngine(points, turns);
            if (totalDistanceMeters <= 0) totalDistanceMeters = progressEngine.getTotalMeters();
            currentTurnIndex = -1;
            spokenStage = 0;
            offRouteFixes = 0;
            arrived = false;
            routeActive = true;
            startProgressMeters = 0;
            statusText = "جارٍ تحديد موقع السيارة";
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private void startGpsUpdates() {
        if (locationManager == null) {
            statusText = "خدمة الموقع غير متاحة على هذا الجهاز";
            publishStatus();
            return;
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                statusText = "فعّل GPS لبدء التوجيه الصوتي الدقيق";
                publishStatus();
                return;
            }
            statusText = "الملاحة نشطة • بانتظار تثبيت موقع GPS";
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this);
            locationRegistered = true;
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) onLocationChanged(last);
            publishStatus();
        } catch (SecurityException error) {
            statusText = "إذن الموقع غير متاح؛ لن يصدر التطبيق تعليمات صوتية";
            publishStatus();
        } catch (Exception error) {
            statusText = "تعذر تشغيل GPS؛ تحقق من إعدادات الموقع";
            publishStatus();
        }
    }

    private void acquireNavigationWakeLock() {
        try {
            PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
            if (manager != null && (navigationWakeLock == null || !navigationWakeLock.isHeld())) {
                navigationWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "com.ali.carsystem:NavigationGuidance");
                navigationWakeLock.setReferenceCounted(false);
                navigationWakeLock.acquire();
            }
        } catch (Exception ignored) {
            navigationWakeLock = null;
        }
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null || progressEngine == null || arrived) return;
        long age = Math.max(0, System.currentTimeMillis() - location.getTime());
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 999f;
        if (age > GOOD_FIX_MAX_AGE_MS) {
            statusText = "موقع GPS قديم؛ ننتظر قراءة جديدة";
            publishStatus();
            return;
        }

        RouteProgressEngine.Progress progress =
                progressEngine.update(location.getLatitude(), location.getLongitude(), accuracy);

        if (!progress.onRoute) {
            offRouteFixes++;
            statusText = "لم نتمكن من مطابقة موقع GPS مع الطريق؛ تم تعليق التعليمات الصوتية";
            publishStatus();
            if (offRouteFixes >= 3 && arabicVoiceReady) {
                speak("تعذر مطابقة موقع السيارة مع المسار. أعد فحص الطريق عند التوقف الآمن.");
                offRouteFixes = 0;
            }
            return;
        }
        offRouteFixes = 0;

        if (accuracy > VOICE_MAX_ACCURACY_METERS) {
            statusText = "دقة GPS ضعيفة ±" + Math.round(accuracy) + " متر؛ التعليمات الصوتية متوقفة مؤقتاً";
            publishProgress(progress, accuracy, statusText);
            return;
        }

        if (progress.nextTurnIndex < 0 || progress.nextTurnIndex >= turns.size()) {
            arrived = true;
            statusText = "وصلت إلى وجهتك";
            speak("وصلت إلى وجهتك");
            publishProgress(progress, accuracy, statusText);
            stopLocationUpdates();
            getPreferences().edit().putBoolean(KEY_ACTIVE, false).putString(KEY_STATE, statusText).apply();
            mainHandler.postDelayed(this::finishArrivedSession, 12000L);
            return;
        }

        if (progress.nextTurnIndex != currentTurnIndex) {
            currentTurnIndex = progress.nextTurnIndex;
            spokenStage = 0;
        }

        RouteProgressEngine.Turn next = turns.get(currentTurnIndex);
        double metres = progress.remainingToTurnMeters;
        String instruction = next.instruction;
        String display = metres >= 1000
                ? String.format(Locale.US, "بعد %.1f كم، %s", metres / 1000.0, instruction)
                : "بعد " + Math.round(metres) + " متر، " + instruction;

        if (instruction.contains("وصلت") && progress.remainingRouteMeters <= 35.0) {
            arrived = true;
            statusText = "وصلت إلى وجهتك";
            speak("وصلت إلى وجهتك");
            publishProgress(progress, accuracy, statusText);
            stopLocationUpdates();
            getPreferences().edit().putBoolean(KEY_ACTIVE, false).putString(KEY_STATE, statusText).apply();
            mainHandler.postDelayed(this::finishArrivedSession, 12000L);
            return;
        }

        // Speak only the most urgent stage reached by this GPS fix. This avoids
        // replacing one phrase with another if GPS jumps over several thresholds.
        if (metres <= 35.0 && spokenStage < 3) {
            speak("الآن، " + instruction);
            spokenStage = 3;
        } else if (metres <= 150.0 && spokenStage < 2) {
            speak("استعد، بعد " + spokenDistance(metres) + "، " + instruction);
            spokenStage = 2;
        } else if (metres <= 500.0 && spokenStage < 1) {
            speak("بعد " + spokenDistance(metres) + "، " + instruction);
            spokenStage = 1;
        }

        statusText = display;
        publishProgress(progress, accuracy, statusText);
    }

    private String spokenDistance(double metres) {
        long rounded = Math.max(0, Math.round(metres / 10.0) * 10L);
        String number = String.valueOf(rounded);
        StringBuilder arabic = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char value = number.charAt(i);
            arabic.append(value >= '0' && value <= '9' ? (char) ('٠' + value - '0') : value);
        }
        return arabic + " متر";
    }

    private void publishProgress(RouteProgressEngine.Progress progress, float accuracy, String message) {
        double traveled = Math.max(0, progress.progressMeters - startProgressMeters);
        double remaining = progress.remainingRouteMeters;
        double eta = totalDistanceMeters > 1.0
                ? totalDurationMinutes * remaining / totalDistanceMeters : totalDurationMinutes;
        broadcastState(true, progress, traveled, remaining, eta, message);
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotificationAt >= 4000L) {
            lastNotificationAt = now;
            updateNotification(message);
        }
    }

    private void publishStatus() {
        broadcastState(routeActive, null, 0,
                routeActive ? totalDistanceMeters : 0,
                routeActive ? totalDurationMinutes : 0, statusText);
        if (routeActive) updateNotification(statusText);
    }

    private void broadcastState(boolean active, RouteProgressEngine.Progress progress,
                                double traveledMeters, double remainingMeters, double etaMinutes,
                                String message) {
        double turnMeters = progress == null ? 0 : progress.remainingToTurnMeters;
        String instruction = "";
        if (progress != null && progress.nextTurnIndex >= 0 && progress.nextTurnIndex < turns.size()) {
            instruction = turns.get(progress.nextTurnIndex).instruction;
        }
        Intent update = new Intent(ACTION_STATE);
        update.setPackage(getPackageName());
        update.putExtra("active", active);
        update.putExtra("destination", destination);
        update.putExtra("instruction", instruction);
        update.putExtra("turn_distance_m", turnMeters);
        update.putExtra("remaining_km", Math.max(0, remainingMeters) / 1000.0);
        update.putExtra("traveled_km", Math.max(0, traveledMeters) / 1000.0);
        update.putExtra("eta_minutes", Math.max(0, etaMinutes));
        update.putExtra("message", message == null ? "" : message);
        update.putExtra("voice_ready", arabicVoiceReady);
        update.putExtra("arrived", arrived);
        sendBroadcast(update);

        getPreferences().edit()
                .putBoolean(KEY_ACTIVE, active && !arrived)
                .putString(KEY_STATE, message == null ? "" : message)
                .putString("destination", destination)
                .putString("instruction", instruction)
                .putFloat("turn_distance_m", (float) turnMeters)
                .putFloat("remaining_km", (float) (Math.max(0, remainingMeters) / 1000.0))
                .putFloat("traveled_km", (float) (Math.max(0, traveledMeters) / 1000.0))
                .putFloat("eta_minutes", (float) Math.max(0, etaMinutes))
                .putBoolean("voice_ready", arabicVoiceReady)
                .putBoolean("arrived", arrived)
                .apply();
    }

    private Notification buildNotification(String content) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 23041, open, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stop = new Intent(this, NavigationService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 23042, stop, PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("ALI CAR • الملاحة")
                .setContentText(content == null ? "المسار نشط" : content)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_media_pause, "إيقاف المسار", stopPending)
                .build();
    }

    private void updateNotification(String message) {
        try {
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(message));
        } catch (Exception ignored) { }
    }

    private void speak(String message) {
        if (tts == null || !arabicVoiceReady || message == null || message.trim().isEmpty()) return;
        try {
            if (audioManager != null) {
                audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                    "ali-nav-" + (++lastAudioUtteranceId));
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, parameters);
        } catch (Exception ignored) {
            if (audioManager != null) audioManager.abandonAudioFocus(audioFocusListener);
        }
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            arabicVoiceReady = false;
            statusText = "محرك النطق غير متاح؛ التعليمات ستظهر كتابةً فقط";
            publishStatus();
            return;
        }
        Locale arabic = new Locale("ar");
        int language = tts.isLanguageAvailable(arabic);
        if (language >= TextToSpeech.LANG_AVAILABLE) {
            int result = tts.setLanguage(arabic);
            arabicVoiceReady = result >= TextToSpeech.LANG_AVAILABLE;
        } else {
            arabicVoiceReady = false;
        }
        statusText = arabicVoiceReady
                ? "التوجيه الصوتي العربي جاهز"
                : "الصوت العربي غير مثبت؛ افتح إعدادات تحويل النص إلى كلام وثبّت العربية";
        publishStatus();
    }

    private void finishArrivedSession() {
        stopForeground(true);
        stopSelf();
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationRegistered) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        locationRegistered = false;
    }

    private void stopNavigation() {
        routeActive = false;
        getPreferences().edit().putBoolean(KEY_ACTIVE, false).putString(KEY_STATE, "تم إيقاف الملاحة").remove(KEY_ROUTE).apply();
        statusText = "تم إيقاف الملاحة";
        broadcastState(false, null, 0, 0, 0, statusText);
        stopLocationUpdates();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            statusText = "تم إيقاف GPS؛ التوجيه الصوتي متوقف";
            publishStatus();
        }
    }

    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            statusText = "تم تفعيل GPS؛ جارٍ استئناف التوجيه";
            startGpsUpdates();
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Keep an explicitly active foreground navigation session alive when the task is dismissed.
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        stopLocationUpdates();
        if (navigationWakeLock != null && navigationWakeLock.isHeld()) {
            try { navigationWakeLock.release(); } catch (Exception ignored) { }
        }
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { }
        }
        if (audioManager != null) {
            try { audioManager.abandonAudioFocus(audioFocusListener); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
