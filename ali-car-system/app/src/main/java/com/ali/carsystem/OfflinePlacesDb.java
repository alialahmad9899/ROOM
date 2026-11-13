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
        String label() { String translated=translateCategory(category);return translated.isEmpty()||name.equals(translated)?name:name + " • " + translated; }
        private static String translateCategory(String value) {
            if (value == null) return "";
            String[] pair = value.split(":", 2);
            if (pair.length != 2) return value;
            String key = pair[0], tag = pair[1];
            if ("shop".equals(key)) return "متجر";
            if ("place".equals(key)) {
                if ("city".equals(tag)) return "مدينة";
                if ("town".equals(tag)) return "بلدة";
                if ("village".equals(tag)) return "قرية";
                if ("hamlet".equals(tag)) return "ضيعة";
            }
            if ("restaurant".equals(tag)) return "مطعم";
            if ("fast_food".equals(tag)) return "وجبات سريعة";
            if ("cafe".equals(tag)) return "مقهى";
            if ("pharmacy".equals(tag)) return "صيدلية";
            if ("hospital".equals(tag)) return "مشفى";
            if ("clinic".equals(tag)) return "عيادة";
            if ("doctors".equals(tag)) return "طبيب";
            if ("bank".equals(tag)) return "مصرف";
            if ("atm".equals(tag)) return "صراف آلي";
            if ("fuel".equals(tag)) return "محطة وقود";
            if ("school".equals(tag)) return "مدرسة";
            if ("university".equals(tag)) return "جامعة";
            if ("hotel".equals(tag)) return "فندق";
            if ("parking".equals(tag)) return "موقف سيارات";
            if ("park".equals(tag)) return "حديقة";
            return tag.replace('_', ' ');
        }
    }

    private OfflinePlacesDb() { }

    private static String categoryPattern(String query) {
        if (containsAny(query, "مطاعم", "مطعم", "restaurants", "restaurant")) return "%:restaurant";
        if (containsAny(query, "وجبات سريعة", "fast food")) return "%:fast_food";
        if (containsAny(query, "مقهى", "مقاهي", "كافيه", "coffee", "cafe")) return "%:cafe";
        if (containsAny(query, "صيدلية", "صيدليات", "pharmacy")) return "%:pharmacy";
        if (containsAny(query, "مشفى", "مشفى", "مستشفى", "مستشفيات", "hospital")) return "%:hospital";
        if (containsAny(query, "عيادة", "عيادات", "clinic")) return "%:clinic";
        if (containsAny(query, "طبيب", "اطباء", "دكتور", "doctors")) return "%:doctors";
        if (containsAny(query, "مصرف", "بنك", "banks", "bank")) return "%:bank";
        if (containsAny(query, "صراف", "atm")) return "%:atm";
        if (containsAny(query, "محطة وقود", "بنزين", "محطات الوقود", "fuel")) return "%:fuel";
        if (containsAny(query, "مدرسة", "مدارس", "school")) return "%:school";
        if (containsAny(query, "جامعة", "جامعات", "university")) return "%:university";
        if (containsAny(query, "روضة", "رياض اطفال", "kindergarten")) return "%:kindergarten";
        if (containsAny(query, "شرطة", "مخفر", "police")) return "%:police";
        if (containsAny(query, "بريد", "مكتب بريد", "post office")) return "%:post_office";
        if (containsAny(query, "فندق", "فنادق", "hotel", "hotels")) return "%:hotel";
        if (containsAny(query, "مواقف", "موقف سيارات", "parking")) return "%:parking";
        if (containsAny(query, "حديقة", "حدائق", "park")) return "leisure:park";
        if (containsAny(query, "محلات", "متاجر", "اسواق", "محلات تجارية", "shops", "shopping")) return "shop:%";
        if (containsAny(query, "سوبرماركت", "سوبر ماركت", "بقالة", "supermarket")) return "shop:supermarket";
        if (containsAny(query, "مخبز", "مخابز", "bakery")) return "shop:bakery";
        if (containsAny(query, "ملحمة", "ملاحم", "butcher")) return "shop:butcher";
        if (containsAny(query, "غسيل سيارات", "car wash")) return "%:car_wash";
        return "__NO_CATEGORY_MATCH__";
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    static List<Place> search(Context context, String query, int limit) {
        List<Place> result = new ArrayList<>();
        File file = OfflineMapsManager.getPlacesDatabase(context);
        if (file == null || !file.isFile() || query == null || query.trim().length() < 2) return result;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            String pattern = "%" + query.trim().replace("%", "\\%").replace("_", "\\_") + "%";
            String categoryPattern = categoryPattern(query.trim().toLowerCase(java.util.Locale.ROOT));
            android.content.SharedPreferences prefs=context.getSharedPreferences("ali_car_v3",Context.MODE_PRIVATE);
            boolean hasLocation=prefs.contains("last_latitude")&&prefs.contains("last_longitude");
            float nearLat=prefs.getFloat("last_latitude",0f),nearLon=prefs.getFloat("last_longitude",0f);
            float longitudeScale=(float)Math.cos(Math.toRadians(nearLat));
            String sql="SELECT name, category, lat, lon FROM places "
                    + "WHERE name LIKE ? ESCAPE '\\' COLLATE NOCASE "
                    + "OR name_ar LIKE ? ESCAPE '\\' COLLATE NOCASE "
                    + "OR category LIKE ? COLLATE NOCASE "
                    + "ORDER BY CASE WHEN name = ? OR name_ar = ? THEN 0 ELSE 1 END";
            ArrayList<String> args=new ArrayList<>();
            args.add(pattern);args.add(pattern);args.add(categoryPattern);args.add(query.trim());args.add(query.trim());
            if(hasLocation){
                sql+=", ((lat-?)*(lat-?) + ((lon-?)*(lon-?)*?)) ASC";
                args.add(String.valueOf(nearLat));args.add(String.valueOf(nearLat));
                args.add(String.valueOf(nearLon));args.add(String.valueOf(nearLon));args.add(String.valueOf(longitudeScale*longitudeScale));
            }
            sql+=", name LIMIT ?";
            args.add(String.valueOf(Math.max(1,Math.min(limit,20))));
            cursor=db.rawQuery(sql,args.toArray(new String[0]));
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
