package com.ali.carsystem;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

public final class NavigationActivity extends Activity implements LocationListener {
    private MapView map;
    private EditText query;
    private TextView status, steps;
    private LocationManager locationManager;
    private Location current;
    private AsyncTask<String, Void, Result> task;
    private Marker destinationMarker, currentMarker;
    private Polyline routeLine;
    private boolean firstFix = true;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void immersive() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        immersive();
        Configuration.getInstance().setUserAgentValue(NetworkCompat.USER_AGENT);
        try { HttpsURLConnection.setDefaultSSLSocketFactory(LegacyTls.socketFactory(getApplicationContext())); }
        catch (Exception ignored) { }
        Configuration.getInstance().setCacheMapTileCount((short) 4);
        Configuration.getInstance().setTileDownloadThreads((short) 1);
        Configuration.getInstance().setTileFileSystemThreads((short) 1);
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(8L * 1024L * 1024L);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(4L * 1024L * 1024L);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.setBackgroundColor(0xFF06101B);

        TextView title = text("الملاحة", 21, 0xFFFFFFFF, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        query = new EditText(this);
        query.setSingleLine(true);
        query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xFF9BB3C4);
        query.setHint("مدينة أو محافظة أو شارع");
        query.setTextSize(15);
        query.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        query.setTextDirection(View.TEXT_DIRECTION_RTL);
        searchRow.addView(query, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button go = new Button(this);
        go.setText("ابحث وارسم الطريق");
        searchRow.addView(go, new LinearLayout.LayoutParams(dp(152), dp(46)));
        root.addView(searchRow);

        status = text("ابحث عن وجهتك لعرضها على الخريطة.", 12, 0xFFFFD88A, false);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));

        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setUseDataConnection(true);
        map.setFlingEnabled(true);
        map.getController().setZoom(5.0);
        map.getController().setCenter(new GeoPoint(34.8, 38.9));
        root.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView details = new ScrollView(this);
        steps = text("سيتم عرض تعليمات الطريق هنا بعد تحديد الوجهة.", 12, Color.WHITE, false);
        steps.setPadding(dp(8), dp(5), dp(8), dp(5));
        details.addView(steps);
        root.addView(details, new LinearLayout.LayoutParams(-1, dp(76)));

        Button back = new Button(this);
        back.setText("العودة إلى الواجهة الرئيسية");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(40)));

        setContentView(root);
        go.setOnClickListener(v -> search());
        query.setOnEditorActionListener((v, action, event) -> {
            search();
            return true;
        });
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(android.graphics.Typeface.create("sans-serif",
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        return view;
    }

    private void loadLastKnownLocation() {
        try {
            if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return;
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            current = gps == null ? network : network == null ? gps
                    : (gps.getTime() >= network.getTime() ? gps : network);
        } catch (Exception ignored) { }
    }

    private void search() {
        String value = query.getText().toString().trim();
        if (value.length() < 2) {
            query.setError("اكتب اسم مدينة أو منطقة");
            return;
        }
        if (task != null) task.cancel(true);
        loadLastKnownLocation();
        status.setText("جارٍ البحث عن " + value + "...");
        steps.setText("يتم الآن تحديد الوجهة وتجهيز المسار.");
        task = new AsyncTask<String, Void, Result>() {
            @Override protected Result doInBackground(String... values) {
                return findDestination(values[0], current);
            }
            @Override protected void onPostExecute(Result result) {
                if (!isFinishing()) displayResult(result);
            }
        }.execute(value);
    }

    private Result findDestination(String searchText, Location origin) {
        try {
            String encoded = URLEncoder.encode(searchText, "UTF-8");
            String address = "https://nominatim.openstreetmap.org/search?q=" + encoded
                    + "&format=jsonv2&limit=5&addressdetails=1&accept-language=ar,en";
            JSONArray results = new JSONArray(request(address));
            if (results.length() == 0) return Result.error("لم يتم العثور على هذه الوجهة. جرّب اسماً آخر.");

            JSONObject selected = results.getJSONObject(0);
            double highest = -1;
            for (int i = 0; i < results.length(); i++) {
                JSONObject candidate = results.getJSONObject(i);
                double score = candidate.optDouble("importance", 0);
                String type = candidate.optString("type", "");
                if ("city".equals(type) || "town".equals(type)
                        || "state".equals(type) || "administrative".equals(type)
                        || "road".equals(type) || "residential".equals(type)) score += 1;
                if (score > highest) {
                    highest = score;
                    selected = candidate;
                }
            }

            String name = selected.optString("display_name", searchText);
            GeoPoint destination = new GeoPoint(selected.getDouble("lat"), selected.getDouble("lon"));
            if (origin == null || (System.currentTimeMillis() - origin.getTime()) > 120000L) {
                return new Result(null, name, destination, null, null,
                        "تم تحديد الوجهة. للحصول على مسار من موقعك، فعّل نظام تحديد الموقع وانتظر إشارة حديثة.", 0, 0);
            }

            String routeUrl = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline&steps=true",
                    origin.getLongitude(), origin.getLatitude(),
                    destination.getLongitude(), destination.getLatitude());
            JSONObject routeResponse = new JSONObject(request(routeUrl));
            JSONArray routes = routeResponse.optJSONArray("routes");
            if (!"Ok".equals(routeResponse.optString("code")) || routes == null || routes.length() == 0) {
                return new Result(null, name, destination, null, null,
                        "تم تحديد الوجهة، لكن خدمة المسارات لم تجد طريق قيادة متاحاً.", 0, 0);
            }

            JSONObject route = routes.getJSONObject(0);
            ArrayList<GeoPoint> points = decodePolyline(route.optString("geometry", ""));
            StringBuilder instructions = new StringBuilder("إرشادات الطريق:\n");
            JSONArray legs = route.optJSONArray("legs");
            if (legs != null && legs.length() > 0) {
                JSONArray maneuvers = legs.getJSONObject(0).optJSONArray("steps");
                if (maneuvers != null) {
                    for (int i = 0; i < maneuvers.length() && i < 35; i++) {
                        instructions.append(maneuverText(maneuvers.getJSONObject(i))).append('\n');
                    }
                }
            }
            return new Result(null, name, destination,
                    new GeoPoint(origin.getLatitude(), origin.getLongitude()),
                    points, instructions.toString(),
                    route.optDouble("distance") / 1000.0,
                    route.optDouble("duration") / 60.0);
        } catch (Exception error) {
            return Result.error(NetworkCompat.arabicError(error));
        }
    }

    private void displayResult(Result result) {
        if (result.error != null) {
            status.setText(result.error);
            steps.setText("تحقق من اتصال الإنترنت واسم الوجهة ثم أعد المحاولة.");
            return;
        }
        if (destinationMarker != null) map.getOverlays().remove(destinationMarker);
        destinationMarker = new Marker(map);
        destinationMarker.setPosition(result.destination);
        destinationMarker.setTitle(result.name);
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(destinationMarker);

        if (result.start == null) {
            map.setMapOrientation(0);
            map.getController().setCenter(result.destination);
            map.getController().setZoom(12.0);
            status.setText("الوجهة: " + result.name);
            steps.setText(result.steps);
            map.invalidate();
            return;
        }

        if (routeLine != null) map.getOverlays().remove(routeLine);
        routeLine = new Polyline(map);
        routeLine.setPoints(result.points);
        routeLine.setColor(0xFF00D7FF);
        routeLine.setWidth(dp(7));
        map.getOverlays().add(routeLine);

        ensureCarMarker();
        currentMarker.setPosition(result.start);
        GeoPoint center = new GeoPoint(
                (result.start.getLatitude() + result.destination.getLatitude()) / 2.0,
                (result.start.getLongitude() + result.destination.getLongitude()) / 2.0);
        map.getController().animateTo(center);
        map.zoomToBoundingBox(routeLine.getBounds(), true, dp(30));

        status.setText(String.format(Locale.US, "المسافة %.1f كم  •  الوقت التقريبي %.0f دقيقة",
                result.kilometers, result.minutes));
        steps.setText("الوجهة: " + result.name + "\n" + result.steps);
        map.invalidate();
    }

    private void ensureCarMarker() {
        if (currentMarker != null) return;
        currentMarker = new Marker(map);
        currentMarker.setTitle("موقعك الحالي");
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        currentMarker.setIcon(new BitmapDrawable(getResources(), createCarArrow()));
        map.getOverlays().add(currentMarker);
    }

    private Bitmap createCarArrow() {
        int size = dp(48);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF00BFFF);
        canvas.drawCircle(size / 2f, size / 2f, size * 0.46f, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        canvas.drawCircle(size / 2f, size / 2f, size * 0.46f, paint);
        paint.setStyle(Paint.Style.FILL);
        Path arrow = new Path();
        arrow.moveTo(size * 0.5f, size * 0.12f);
        arrow.lineTo(size * 0.78f, size * 0.78f);
        arrow.lineTo(size * 0.5f, size * 0.64f);
        arrow.lineTo(size * 0.22f, size * 0.78f);
        arrow.close();
        paint.setColor(Color.WHITE);
        canvas.drawPath(arrow, paint);
        return bitmap;
    }

    private String request(String address) throws Exception {
        HttpURLConnection connection = NetworkCompat.open(this, address);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        try {
            int code = connection.getResponseCode();
            if (code == 403) throw new java.io.IOException("رفض خادم البحث الطلب. حاول بعد قليل.");
            if (code < 200 || code >= 300) throw new java.io.IOException("تعذر الوصول إلى خدمة البحث.");
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    if (response.length() > 1600000)
                        throw new java.io.IOException("استجابة خدمة البحث أكبر من الحجم المسموح.");
                }
                return response.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private ArrayList<GeoPoint> decodePolyline(String encoded) {
        ArrayList<GeoPoint> points = new ArrayList<>();
        int index = 0, latitude = 0, longitude = 0;
        while (index < encoded.length()) {
            int[] latPart = decodeValue(encoded, index);
            latitude += latPart[0];
            index = latPart[1];
            int[] lonPart = decodeValue(encoded, index);
            longitude += lonPart[0];
            index = lonPart[1];
            points.add(new GeoPoint(latitude / 1E5, longitude / 1E5));
            if (points.size() > 20000) break;
        }
        return points;
    }

    private int[] decodeValue(String source, int start) {
        int result = 0, shift = 0, index = start, value;
        do {
            if (index >= source.length()) throw new IllegalArgumentException("مسار غير مكتمل");
            value = source.charAt(index++) - 63;
            result |= (value & 31) << shift;
            shift += 5;
        } while (value >= 32);
        return new int[]{(result & 1) != 0 ? ~(result >> 1) : (result >> 1), index};
    }

    private String maneuverText(JSONObject step) {
        JSONObject maneuver = step.optJSONObject("maneuver");
        String type = maneuver == null ? "" : maneuver.optString("type", "");
        String modifier = maneuver == null ? "" : maneuver.optString("modifier", "");
        String road = step.optString("name", "");
        String direction = modifier.contains("left") ? "اليسار"
                : modifier.contains("right") ? "اليمين" : "إلى الأمام";
        String action = "arrive".equals(type) ? "الوصول إلى الوجهة"
                : "roundabout".equals(type) ? "ادخل الدوار"
                : "تابع " + direction;
        return "• " + action + (road.length() > 0 ? " باتجاه " + road : "")
                + " بعد " + Math.round(step.optDouble("distance")) + " متر";
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        if (map != null) map.onResume();
        if (locationManager != null
                && checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 2f, this); }
            catch (Exception ignored) { }
        }
    }

    @Override protected void onPause() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (task != null) task.cancel(true);
        if (map != null) {
            map.onDetach();
            map = null;
        }
        super.onDestroy();
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null) return;
        current = location;
        if (!location.hasAccuracy() || location.getAccuracy() <= 40f) {
            ensureCarMarker();
            currentMarker.setPosition(new GeoPoint(location.getLatitude(), location.getLongitude()));
            if (location.hasBearing() && location.hasSpeed() && location.getSpeed() > 1.5f) {
                float bearing = location.getBearing();
                map.setMapOrientation(-bearing);
                currentMarker.setRotation(0f);
            }
            if (firstFix || routeLine != null) {
                map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
                firstFix = false;
            }
            map.invalidate();
        }
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider))
            status.setText("نظام تحديد الموقع متوقف. فعّله لرسم مسار من موقعك.");
    }
    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) status.setText("تم تفعيل نظام تحديد الموقع.");
    }
    @Override public void onStatusChanged(String provider, int state, Bundle extras) { }

    private static final class Result {
        final String error, name, steps;
        final GeoPoint destination, start;
        final ArrayList<GeoPoint> points;
        final double kilometers, minutes;
        Result(String error, String name, GeoPoint destination, GeoPoint start,
               ArrayList<GeoPoint> points, String steps, double kilometers, double minutes) {
            this.error = error;
            this.name = name;
            this.destination = destination;
            this.start = start;
            this.points = points;
            this.steps = steps;
            this.kilometers = kilometers;
            this.minutes = minutes;
        }
        static Result error(String value) {
            return new Result(value, "", null, null, null, "", 0, 0);
        }
    }
}
