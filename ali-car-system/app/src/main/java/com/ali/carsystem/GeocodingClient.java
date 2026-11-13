package com.ali.carsystem;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Online place search fallback. Offline SQLite search is handled before this client. */
final class GeocodingClient {
    static final class Candidate {
        final double latitude;
        final double longitude;
        final String name;
        final String provider;
        Candidate(double lat, double lon, String title, String source) {
            latitude = lat; longitude = lon; name = title; provider = source;
        }
    }

    private GeocodingClient() { }

    static List<Candidate> search(Context context, String query) throws Exception {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) throw new IOException("أدخل اسم مدينة أو مكان");
        List<String> failures = new ArrayList<>();
        try {
            List<Candidate> found = searchNominatim(context, q);
            if (!found.isEmpty()) return found;
            failures.add("Nominatim: لم يعثر على نتائج");
        } catch (Exception error) {
            failures.add("Nominatim: " + compact(error.getMessage()));
        }
        try {
            List<Candidate> found = searchPhoton(context, q);
            if (!found.isEmpty()) return found;
            failures.add("Photon: لم يعثر على نتائج");
        } catch (Exception error) {
            failures.add("Photon: " + compact(error.getMessage()));
        }
        throw new IOException("لم ينجح البحث عبر الخادمين. " + join(failures));
    }

    private static List<Candidate> searchNominatim(Context context, String query) throws Exception {
        String url = "https://nominatim.openstreetmap.org/search?q="
                + URLEncoder.encode(query, "UTF-8")
                + "&format=jsonv2&limit=8&addressdetails=1&accept-language=ar,en";
        JSONArray array = new JSONArray(read(context, url));
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            double lat = row.optDouble("lat", Double.NaN);
            double lon = row.optDouble("lon", Double.NaN);
            if (!valid(lat, lon)) continue;
            result.add(new Candidate(lat, lon, row.optString("display_name", query), "Nominatim"));
        }
        return result;
    }

    private static List<Candidate> searchPhoton(Context context, String query) throws Exception {
        String url = "https://photon.komoot.io/api/?q="
                + URLEncoder.encode(query, "UTF-8") + "&limit=8&lang=ar";
        JSONObject root = new JSONObject(read(context, url));
        JSONArray features = root.optJSONArray("features");
        List<Candidate> result = new ArrayList<>();
        if (features == null) return result;
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            if (feature == null) continue;
            JSONObject geometry = feature.optJSONObject("geometry");
            JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
            if (coordinates == null || coordinates.length() < 2) continue;
            double lon = coordinates.optDouble(0, Double.NaN);
            double lat = coordinates.optDouble(1, Double.NaN);
            if (!valid(lat, lon)) continue;
            JSONObject properties = feature.optJSONObject("properties");
            result.add(new Candidate(lat, lon, photonName(properties, query), "Photon"));
        }
        return result;
    }

    private static String photonName(JSONObject p, String fallback) {
        if (p == null) return fallback;
        String name = p.optString("name", "");
        String city = p.optString("city", "");
        String state = p.optString("state", "");
        String country = p.optString("country", "");
        StringBuilder text = new StringBuilder();
        if (!name.isEmpty()) text.append(name);
        if (!city.isEmpty() && !city.equals(name)) append(text, city);
        if (!state.isEmpty() && !state.equals(city)) append(text, state);
        if (!country.isEmpty()) append(text, country);
        return text.length() == 0 ? fallback : text.toString();
    }

    private static void append(StringBuilder b, String value) {
        if (b.length() > 0) b.append(" • ");
        b.append(value);
    }

    private static boolean valid(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isInfinite(lat) && !Double.isNaN(lon)
                && !Double.isInfinite(lon) && Math.abs(lat) <= 90d && Math.abs(lon) <= 180d;
    }

    private static String read(Context context, String url) throws Exception {
        HttpURLConnection connection = LegacyTls.open(context, url, "GET", 6500, 8500);
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readLimited(stream);
            if (status < 200 || status >= 300) {
                String label = status == 429 ? "تجاوز حد طلبات البحث (429)"
                        : status == 403 ? "رفض خادم البحث الطلب (403)"
                        : "HTTP " + status;
                throw new IOException(label + (body.isEmpty() ? "" : ": " + compact(body)));
            }
            if (body.isEmpty()) throw new IOException("رد خادم البحث فارغ");
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > 2 * 1024 * 1024) throw new IOException("رد البحث أكبر من الحد المسموح");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private static String compact(String value) {
        if (value == null || value.trim().isEmpty()) return "تعذر الاتصال";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 200 ? clean.substring(0, 200) + "…" : clean;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(" | ");
            out.append(value);
        }
        return out.toString();
    }
}
