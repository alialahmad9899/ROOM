package com.ali.carsystem;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLException;

import org.osmdroid.util.GeoPoint;

/**
 * Network isolation for car routing. Never lets one public demo endpoint
 * become a single point of failure. Responses are normalized to OSRM JSON.
 */
final class RouteNetworkClient {
    static final String OSRM_BASE = "https://router.project-osrm.org";
    static final String FOSSGIS_BASE = "https://routing.openstreetmap.de/routed-car";
    static final String VALHALLA_URL = "https://valhalla1.openstreetmap.de/route";
    private static final String PREFS = "routing";
    private static final String LAST_ENGINE = "last_successful_engine";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 7500;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final long TOTAL_BUDGET_MS = 32000L;

    private RouteNetworkClient() {}

    static JSONObject requestRoutes(Context context, Location start, GeoPoint end) throws Exception {
        if (context == null || start == null || end == null) {
            throw new IOException("بيانات نقطة البداية أو الوجهة غير مكتملة");
        }
        if (!Double.isFinite(start.getLatitude()) || !Double.isFinite(start.getLongitude())
                || !Double.isFinite(end.getLatitude()) || !Double.isFinite(end.getLongitude())
                || Math.abs(start.getLatitude()) > 90 || Math.abs(end.getLatitude()) > 90
                || Math.abs(start.getLongitude()) > 180 || Math.abs(end.getLongitude()) > 180) {
            throw new IOException("إحداثيات البداية أو الوجهة خارج النطاق الجغرافي الصحيح");
        }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String preferred = prefs.getString(LAST_ENGINE, "osrm");
        List<String> engines = orderedEngines(preferred);
        List<String> failures = new ArrayList<>();
        long deadline = SystemClock.elapsedRealtime() + TOTAL_BUDGET_MS;

        for (String engine : engines) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                failures.add("انتهت مهلة المحاولة الإجمالية");
                break;
            }
            try {
                JSONObject response;
                if ("valhalla".equals(engine)) {
                    response = requestValhalla(context, start, end);
                } else {
                    String base = "fossgis".equals(engine) ? FOSSGIS_BASE : OSRM_BASE;
                    response = requestOsrm(context, base, start, end);
                }
                JSONArray routes = response.optJSONArray("routes");
                if (!"Ok".equalsIgnoreCase(response.optString("code", ""))
                        || routes == null || routes.length() == 0) {
                    String detail = response.optString("message",
                            response.optString("status_message",
                                    response.optString("code", "لم يرجع الخادم أي مسار")));
                    failures.add(engine + ": " + compact(detail));
                    continue;
                }
                response.put("_ali_provider", engine);
                prefs.edit().putString(LAST_ENGINE, engine).apply();
                return response;
            } catch (Exception error) {
                failures.add(engine + ": " + describe(error));
            }
        }

        throw new IOException("فشلت جميع محركات المسارات المتاحة. " + joinFailures(failures));
    }

    static List<String> orderedEngines(String preferred) {
        List<String> all = Arrays.asList("osrm", "fossgis", "valhalla");
        List<String> ordered = new ArrayList<>();
        if (all.contains(preferred)) ordered.add(preferred);
        for (String engine : all) if (!ordered.contains(engine)) ordered.add(engine);
        return ordered;
    }

    static String buildOsrmUrl(String base, double startLat, double startLon,
                               double endLat, double endLon) {
        String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return String.format(Locale.US,
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=3",
                root, startLon, startLat, endLon, endLat);
    }

    static String buildValhallaRequest(double startLat, double startLon,
                                       double endLat, double endLon) {
        return String.format(Locale.US,
                "{\"locations\":[{\"lat\":%.7f,\"lon\":%.7f},{\"lat\":%.7f,\"lon\":%.7f}],"
                        + "\"costing\":\"auto\",\"units\":\"kilometers\","
                        + "\"format\":\"osrm\",\"shape_format\":\"geojson\","
                        + "\"directions_type\":\"instructions\",\"alternates\":3}",
                startLat, startLon, endLat, endLon);
    }

    private static JSONObject requestOsrm(Context context, String base,
                                          Location start, GeoPoint end) throws Exception {
        String url = buildOsrmUrl(base, start.getLatitude(), start.getLongitude(),
                end.getLatitude(), end.getLongitude());
        HttpURLConnection connection = LegacyTls.open(context, url, "GET",
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        try {
            return readJson(connection, null);
        } finally {
            connection.disconnect();
        }
    }

    private static JSONObject requestValhalla(Context context, Location start,
                                              GeoPoint end) throws Exception {
        HttpURLConnection connection = LegacyTls.open(context, VALHALLA_URL, "POST",
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("X-Client-Id", "com.ali.carsystem/3.5.0");
        byte[] body = buildValhallaRequest(start.getLatitude(), start.getLongitude(),
                end.getLatitude(), end.getLongitude()).getBytes("UTF-8");
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
                output.flush();
            }
            return readJson(connection, "Valhalla");
        } finally {
            connection.disconnect();
        }
    }

    private static JSONObject readJson(HttpURLConnection connection, String provider)
            throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String body = readBounded(stream);
        if (status < 200 || status >= 300) {
            String message = provider == null ? "" : provider + " ";
            message += "HTTP " + status;
            if (status == 429) message += " (تم تجاوز حد الطلبات)";
            else if (status == 403) message += " (رفض الخادم الطلب)";
            else if (status >= 500) message += " (عطل مؤقت في الخادم)";
            if (!body.isEmpty()) message += ": " + compact(body);
            throw new IOException(message);
        }
        if (body.isEmpty()) throw new IOException("استجابة الخادم فارغة (HTTP " + status + ")");
        try {
            return new JSONObject(body);
        } catch (JSONException invalid) {
            throw new IOException("استجابة المسارات ليست JSON صالحاً: " + compact(body), invalid);
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count, total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("استجابة المسار تجاوزت حد 8 ميغابايت");
                }
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private static String describe(Exception error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof SocketTimeoutException) return "انتهت مهلة الاتصال";
        if (cause instanceof UnknownHostException) return "تعذر حل اسم الخادم DNS";
        if (cause instanceof SSLException || cause instanceof CertificateException) {
            return "فشل التحقق من TLS/SSL: " + compact(cause.getMessage());
        }
        return compact(error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage());
    }

    private static String compact(String value) {
        if (value == null) return "خطأ غير محدد";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 220 ? clean.substring(0, 220) + "…" : clean;
    }

    private static String joinFailures(List<String> errors) {
        StringBuilder result = new StringBuilder();
        for (String error : errors) {
            if (result.length() > 0) result.append(" | ");
            result.append(error);
        }
        return result.toString();
    }
}
