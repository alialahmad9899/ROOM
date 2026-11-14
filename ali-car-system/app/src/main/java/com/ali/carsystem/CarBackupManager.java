package com.ali.carsystem;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Export/restore only ALI CAR's own local preferences; large map files are never copied. */
final class CarBackupManager {
    private static final String FORMAT = "ALI_CAR_LOCAL_BACKUP_V1";
    private static final int MAX_BACKUP_CHARS = 2 * 1024 * 1024;
    private static final String[] PREF_FILES = {
            "ali_car_v3", "ali_car_audio", "ali_car_navigation",
            "ali_car_navigation_ui", "osmdroid"
    };
    private CarBackupManager() { }

    static String exportJson(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("created_at", System.currentTimeMillis());
        JSONObject files = new JSONObject();
        for (String fileName : PREF_FILES) {
            SharedPreferences prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("playing".equals(key) || "resume_enabled".equals(key)) continue;
                JSONObject typed = new JSONObject();
                if (value instanceof Boolean) {
                    typed.put("type", "boolean"); typed.put("value", value);
                } else if (value instanceof Integer) {
                    typed.put("type", "int"); typed.put("value", value);
                } else if (value instanceof Long) {
                    typed.put("type", "long"); typed.put("value", value);
                } else if (value instanceof Float) {
                    typed.put("type", "float"); typed.put("value", ((Float) value).doubleValue());
                } else if (value instanceof String) {
                    typed.put("type", "string"); typed.put("value", value);
                } else if (value instanceof Set) {
                    JSONArray array = new JSONArray();
                    for (Object item : (Set<?>) value) if (item instanceof String) array.put(item);
                    typed.put("type", "string_set"); typed.put("value", array);
                } else {
                    continue;
                }
                values.put(key, typed);
            }
            files.put(fileName, values);
        }
        root.put("preferences", files);
        String output = root.toString();
        if (output.length() > MAX_BACKUP_CHARS) throw new IllegalStateException("حجم النسخة الاحتياطية أكبر من المسموح");
        return output;
    }

    static int restoreJson(Context context, String input) throws Exception {
        if (input == null || input.trim().isEmpty() || input.length() > MAX_BACKUP_CHARS)
            throw new IllegalArgumentException("ملف النسخة الاحتياطية فارغ أو حجمه غير صالح");
        JSONObject root = new JSONObject(input);
        if (!FORMAT.equals(root.optString("format")))
            throw new IllegalArgumentException("صيغة النسخة الاحتياطية غير معروفة");
        JSONObject files = root.optJSONObject("preferences");
        if (files == null) throw new IllegalArgumentException("ملف النسخة لا يحتوي على الإعدادات");

        LinkedHashMap<String, LinkedHashMap<String, Object>> validated = new LinkedHashMap<>();
        for (String fileName : PREF_FILES) {
            JSONObject values = files.optJSONObject(fileName);
            if (values == null) continue;
            LinkedHashMap<String, Object> output = new LinkedHashMap<>();
            JSONArray names = values.names();
            if (names == null) { validated.put(fileName, output); continue; }
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i);
                JSONObject typed = values.optJSONObject(key);
                if (typed == null) throw new IllegalArgumentException("قيمة إعداد غير صالحة: " + key);
                String type = typed.optString("type");
                Object value = typed.opt("value");
                if ("boolean".equals(type) && value instanceof Boolean) output.put(key, value);
                else if ("int".equals(type) && value instanceof Number) output.put(key, ((Number) value).intValue());
                else if ("long".equals(type) && value instanceof Number) output.put(key, ((Number) value).longValue());
                else if ("float".equals(type) && value instanceof Number) output.put(key, ((Number) value).floatValue());
                else if ("string".equals(type) && value instanceof String) output.put(key, value);
                else if ("string_set".equals(type) && value instanceof JSONArray) {
                    LinkedHashSet<String> set = new LinkedHashSet<>();
                    JSONArray array = (JSONArray) value;
                    for (int j = 0; j < array.length(); j++) {
                        Object item = array.opt(j);
                        if (!(item instanceof String)) throw new IllegalArgumentException("قائمة إعدادات غير صالحة");
                        set.add((String) item);
                    }
                    output.put(key, set);
                } else throw new IllegalArgumentException("نوع إعداد غير مدعوم: " + key);
            }
            validated.put(fileName, output);
        }
        if (validated.isEmpty()) throw new IllegalArgumentException("لم يتم العثور على إعدادات ALI CAR");

        // Validation finishes before the first preference file is changed.
        for (Map.Entry<String, LinkedHashMap<String, Object>> file : validated.entrySet()) {
            SharedPreferences.Editor editor = context.getSharedPreferences(
                    file.getKey(), Context.MODE_PRIVATE).edit().clear();
            for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
                else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
                else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
                else if (value instanceof Set) {
                    @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value;
                    editor.putStringSet(entry.getKey(), set);
                }
            }
            if ("ali_car_audio".equals(file.getKey())) {
                editor.putBoolean("playing", false);
                editor.putBoolean("resume_enabled", false);
            }
            editor.apply();
        }
        return validated.size();
    }
}
