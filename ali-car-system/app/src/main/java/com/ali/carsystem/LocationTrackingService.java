package com.ali.carsystem;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import java.util.Locale;

/**
 * Keeps the GPS trip/odometer tracker alive while dashboard, music or navigation
 * activities are switched. The service never needs internet for GPS distance.
 */
public final class LocationTrackingService extends Service implements LocationListener {
    public static final String ACTION_UPDATE = "com.ali.carsystem.GPS_TRACK_UPDATE";
    public static final String ACTION_RESET_TRIP = "com.ali.carsystem.RESET_TRIP";
    public static final String EXTRA_SPEED_KMH = "speed_kmh";
    public static final String EXTRA_TRIP_METERS = "trip_meters";
    public static final String EXTRA_ODOMETER_METERS = "odometer_meters";
    public static final String EXTRA_MAX_SPEED_KMH = "max_speed_kmh";
    public static final String EXTRA_ACCURACY_METERS = "accuracy_meters";

    private static final int NOTIFICATION_ID = 7315;
    private static final String PREFS = "ali_car_v3";
    private static final double MAX_CAR_SPEED_MPS = 70.0d;
    private static final float MAX_DISTANCE_ACCURACY_M = 45.0f;

    private LocationManager locationManager;
    private Location previousAccepted;
    private SharedPreferences prefs;
    private long tripMeters;
    private long odometerMeters;
    private float speedKmh;
    private float maxSpeedKmh;
    private float accuracyMeters = -1f;
    private long lastNotificationAt;
    private boolean gpsRegistered;
    private boolean networkRegistered;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        migrateLegacyMeters();
        tripMeters = prefs.getLong("trip_meters", 0L);
        odometerMeters = prefs.getLong("odometer_meters", 0L);
        maxSpeedKmh = prefs.getFloat("max_speed", 0f);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        startForeground(NOTIFICATION_ID, buildNotification("جارٍ تشغيل تتبع GPS"));
        startLocationUpdates();
    }

    private void migrateLegacyMeters() {
        if (!prefs.contains("trip_meters")) {
            long old = Math.max(0L, Math.round(prefs.getFloat("trip_km", 0f) * 1000d));
            prefs.edit().putLong("trip_meters", old).apply();
        }
        if (!prefs.contains("odometer_meters")) {
            long old = Math.max(0L, Math.round(prefs.getFloat("odometer_km", 0f) * 1000d));
            prefs.edit().putLong("odometer_meters", old).apply();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RESET_TRIP.equals(intent.getAction())) {
            tripMeters = 0L;
            prefs.edit().putLong("trip_meters", 0L).putFloat("trip_km", 0f).apply();
            publishUpdate();
        }
        startLocationUpdates();
        return START_STICKY;
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 7315, open, PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("ALI CAR • GPS")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void startLocationUpdates() {
        if (locationManager == null) return;
        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            publishStatus("إذن الموقع غير متاح");
            return;
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
                gpsRegistered = true;
            }
        } catch (Exception ignored) { gpsRegistered = false; }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this, Looper.getMainLooper());
                networkRegistered = true;
            }
        } catch (Exception ignored) { networkRegistered = false; }

        Location gps = lastKnown(LocationManager.GPS_PROVIDER);
        Location network = lastKnown(LocationManager.NETWORK_PROVIDER);
        Location best = chooseBetter(gps, network);
        if (best != null) onLocationChanged(best);
        publishStatus((gpsRegistered || networkRegistered) ? "تتبع GPS نشط" : "بانتظار إشارة GPS");
    }

    private Location lastKnown(String provider) {
        try {
            if (locationManager != null && locationManager.getAllProviders().contains(provider))
                return locationManager.getLastKnownLocation(provider);
        } catch (SecurityException ignored) { }
        return null;
    }

    private Location chooseBetter(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        if (!a.hasAccuracy()) return b;
        if (!b.hasAccuracy()) return a;
        return b.getAccuracy() < a.getAccuracy() ? b : a;
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null) return;
        long now = location.getElapsedRealtimeNanos();
        long oldTime = previousAccepted == null ? 0L : previousAccepted.getElapsedRealtimeNanos();
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 999f;
        accuracyMeters = accuracy;

        float reportedSpeed = location.hasSpeed() ? Math.max(0f, location.getSpeed() * 3.6f) : 0f;
        if (reportedSpeed > 250f) reportedSpeed = 0f;

        boolean acceptedMovement = false;
        if (previousAccepted != null && now > oldTime) {
            long dtMs = (now - oldTime) / 1000000L;
            double distance = location.distanceTo(previousAccepted);
            float oldAccuracy = previousAccepted.hasAccuracy() ? previousAccepted.getAccuracy() : 999f;
            double measuredSpeed = dtMs > 0 ? distance / (dtMs / 1000d) : Double.POSITIVE_INFINITY;
            double noiseFloor = Math.max(4.0d, (accuracy + oldAccuracy) * 0.40d);
            boolean fixesGood = accuracy <= MAX_DISTANCE_ACCURACY_M && oldAccuracy <= MAX_DISTANCE_ACCURACY_M;
            boolean elapsedValid = dtMs >= 700L && dtMs <= 30000L;
            boolean movementValid = distance >= noiseFloor && distance <= (dtMs / 1000d) * MAX_CAR_SPEED_MPS + 30d;
            boolean speedValid = !Double.isNaN(measuredSpeed) && !Double.isInfinite(measuredSpeed)
                    && measuredSpeed <= MAX_CAR_SPEED_MPS;
            if (fixesGood && elapsedValid && movementValid && speedValid) {
                long delta = Math.max(0L, Math.round(distance));
                tripMeters += delta;
                odometerMeters += delta;
                prefs.edit()
                        .putLong("trip_meters", tripMeters)
                        .putLong("odometer_meters", odometerMeters)
                        .putFloat("trip_km", tripMeters / 1000f)
                        .putFloat("odometer_km", odometerMeters / 1000f)
                        .apply();
                acceptedMovement = true;
                if (reportedSpeed <= 0f) reportedSpeed = (float) (measuredSpeed * 3.6d);
            }
            // If GPS has been unusable for too long, re-anchor rather than accumulating
            // a stale distance that could be mistaken for driving.
            if (dtMs > 30000L && accuracy <= 100f) previousAccepted = new Location(location);
        } else if (accuracy <= 100f) {
            previousAccepted = new Location(location);
        }

        // Keep the last good anchor across small noisy fixes, so sub-threshold
        // movement accumulates instead of being discarded every second.
        if (acceptedMovement && accuracy <= 100f) previousAccepted = new Location(location);
        if (reportedSpeed > 250f) reportedSpeed = 0f;
        speedKmh = reportedSpeed;
        maxSpeedKmh = Math.max(maxSpeedKmh, speedKmh);
        prefs.edit().putFloat("max_speed", maxSpeedKmh).apply();
        publishUpdate();

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastNotificationAt > 5000L) {
            lastNotificationAt = nowMs;
            String text = String.format(Locale.US, "%.0f km/h • %.1f km", speedKmh, odometerMeters / 1000d);
            try { startForeground(NOTIFICATION_ID, buildNotification(text)); } catch (Exception ignored) { }
        }
    }

    private void publishStatus(String text) {
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra("status", text);
        sendBroadcast(update);
    }

    private void publishUpdate() {
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra(EXTRA_SPEED_KMH, speedKmh);
        update.putExtra(EXTRA_TRIP_METERS, tripMeters);
        update.putExtra(EXTRA_ODOMETER_METERS, odometerMeters);
        update.putExtra(EXTRA_MAX_SPEED_KMH, maxSpeedKmh);
        update.putExtra(EXTRA_ACCURACY_METERS, accuracyMeters);
        sendBroadcast(update);
    }

    @Override public void onProviderEnabled(String provider) { startLocationUpdates(); }
    @Override public void onProviderDisabled(String provider) { publishStatus("إشارة الموقع متوقفة"); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override public void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        gpsRegistered = false;
        networkRegistered = false;
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
