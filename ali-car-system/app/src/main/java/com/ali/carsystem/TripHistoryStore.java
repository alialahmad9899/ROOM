package com.ali.carsystem;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small, private trip history; keeps only the last 30 completed journeys. */
final class TripHistoryStore {
    private static final String PREFS = "ali_car_v3";
    private static final String KEY = "trip_history_json";
    private static final int MAX_ENTRIES = 30;
    private TripHistoryStore() { }

    static synchronized boolean record(Context context, long distanceMeters,
                                       float maxSpeedKmh, long startedAt, long finishedAt) {
        if (context == null || distanceMeters < 100L) return false;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray history;
        try { history = new JSONArray(preferences.getString(KEY, "[]")); }
        catch (Exception ignored) { history = new JSONArray(); }
        try {
            long safeFinish = Math.max(1L, finishedAt);
            long safeStart = startedAt > 0L ? Math.min(startedAt, safeFinish) : safeFinish;
            JSONObject entry = new JSONObject();
            entry.put("started_at", safeStart);
            entry.put("finished_at", safeFinish);
            entry.put("distance_meters", distanceMeters);
            entry.put("duration_ms", Math.max(0L, safeFinish - safeStart));
            entry.put("max_speed_kmh", Math.max(0f, maxSpeedKmh));
            JSONArray updated = new JSONArray();
            updated.put(entry);
            for (int i = 0; i < history.length() && updated.length() < MAX_ENTRIES; i++) {
                JSONObject old = history.optJSONObject(i);
                if (old != null) updated.put(old);
            }
            preferences.edit().putString(KEY, updated.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    static JSONArray read(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
        } catch (Exception ignored) { return new JSONArray(); }
    }

    static String format(Context context) {
        JSONArray entries = read(context);
        if (entries.length() == 0) return "لا توجد رحلات محفوظة بعد. ستظهر الرحلة هنا عند تصفير عداد الرحلة.";
        StringBuilder text = new StringBuilder();
        java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("yyyy/MM/dd  HH:mm", java.util.Locale.getDefault());
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) continue;
            long when = item.optLong("finished_at", 0L);
            double km = item.optLong("distance_meters", 0L) / 1000d;
            long duration = item.optLong("duration_ms", 0L);
            long minutes = duration / 60000L;
            text.append(i + 1).append(") ")
                    .append(when > 0L ? date.format(new java.util.Date(when)) : "تاريخ غير معروف")
                    .append("\nالمسافة: ").append(String.format(java.util.Locale.US, "%.1f كم", km))
                    .append("  •  المدة: ").append(minutes).append(" دقيقة")
                    .append("  •  أقصى سرعة: ").append(Math.round(item.optDouble("max_speed_kmh", 0d))).append(" كم/س");
            if (i < entries.length() - 1) text.append("\n\n");
        }
        return text.toString();
    }
}
