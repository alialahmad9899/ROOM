package com.ali.carsystem;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class OfflinePlacesDb {
    static final class Place {
        final String name;
        final String category;
        final GeoPoint point;
        Place(String n, String c, double lat, double lon) {
            name = n; category = c; point = new GeoPoint(lat, lon);
        }
        String label() { return category == null || category.isEmpty() ? name : name + " • " + category; }
    }

    private OfflinePlacesDb() { }

    static List<Place> search(Context context, String query, int limit) {
        List<Place> result = new ArrayList<>();
        File file = OfflineMapsManager.getPlacesDatabase(context);
        if (file == null || !file.isFile() || query == null || query.trim().length() < 2) return result;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            String pattern = "%" + query.trim().replace("%", "\\%").replace("_", "\\_") + "%";
            cursor = db.rawQuery(
                    "SELECT name, category, lat, lon FROM places "
                    + "WHERE name LIKE ? ESCAPE '\\' COLLATE NOCASE "
                    + "OR name_ar LIKE ? ESCAPE '\\' COLLATE NOCASE "
                    + "ORDER BY CASE WHEN name = ? OR name_ar = ? THEN 0 ELSE 1 END, name LIMIT ?",
                    new String[]{pattern, pattern, query.trim(), query.trim(), String.valueOf(Math.max(1, Math.min(limit, 20)))});
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String category = cursor.getString(1);
                double lat = cursor.getDouble(2);
                double lon = cursor.getDouble(3);
                if (!Double.isNaN(lat) && !Double.isNaN(lon)
                        && Math.abs(lat) <= 90d && Math.abs(lon) <= 180d)
                    result.add(new Place(name == null ? query : name, category, lat, lon));
            }
        } catch (Exception ignored) {
            result.clear();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return result;
    }
}
