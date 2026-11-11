package com.ali.carsystem;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public final class NavigationActivity extends Activity implements LocationListener {
    private static final int BG = 0xFF080D13;
    private static final int PANEL = 0xFF111C27;
    private static final int WHITE = 0xFFF2F6F9;
    private static final int MUTED = 0xFF91A4B2;
    private static final int ACCENT = 0xFF42D9D0;

    private MapView map;
    private EditText destinationInput;
    private TextView status;
    private TextView instructions;
    private LocationManager locationManager;
    private Location currentLocation;
    private AsyncTask<String, Void, NavigationResult> activeTask;
    private long lastGeocodeRequest = 0L;
    private Marker currentMarker;
    private Marker destinationMarker;
    private Polyline routeLine;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        configureMapCache();
        buildScreen();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        loadLastKnownLocation();
    }

    private void configureMapCache() {
        try {
            Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
            );
            File base = new File(getFilesDir(), "osmdroid");
            File tileCache = new File(base, "tiles");
            if (!tileCache.exists()) tileCache.mkdirs();
            Configuration.getInstance().setOsmdroidBasePath(base);
            Configuration.getInstance().setOsmdroidTileCache(tileCache);
            Configuration.getInstance().setUserAgentValue(
                "ALI-Car-System/1.1 (com.ali.carsystem)"
            );
        } catch (RuntimeException ignored) {
            // The map can still attempt to use osmdroid's default cache.
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(10), dp(8), dp(10), dp(7));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ALI CAR  •  الملاحة", 18, WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1f));
        TextView back = text("رجوع", 13, ACCENT, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(12), 0, dp(12), 0);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(65), dp(38)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        destinationInput = new EditText(this);
        destinationInput.setSingleLine(true);
        destinationInput.setTextColor(WHITE);
        destinationInput.setHintTextColor(MUTED);
        destinationInput.setTextSize(14);
        destinationInput.setHint("اكتب اسم المكان أو العنوان");
        destinationInput.setPadding(dp(10), 0, dp(10), 0);
        destinationInput.setBackgroundColor(PANEL);
        searchRow.addView(destinationInput, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button search = button("ابحث وارسم المسار");
        search.setOnClickListener(v -> searchDestination());
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(dp(160), dp(44));
        searchButtonParams.leftMargin = dp(7);
        searchRow.addView(search, searchButtonParams);
        root.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(48)));

        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(13.0);
        map.setBackgroundColor(0xFF101820);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        mapParams.topMargin = dp(6);
        mapParams.bottomMargin = dp(6);
        root.addView(map, mapParams);

        status = text("أدخل وجهتك ثم اضغط بحث. يلزم اتصال بالإنترنت.", 12, ACCENT, false);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(25)));

        ScrollView instructionScroll = new ScrollView(this);
        instructionScroll.setFillViewport(false);
        instructions = text(
            "بيانات الخريطة © OpenStreetMap contributors",
            12, MUTED, false
        );
        instructions.setPadding(dp(10), dp(5), dp(10), dp(5));
        instructions.setBackgroundColor(PANEL);
        instructionScroll.addView(instructions);
        root.addView(instructionScroll, new LinearLayout.LayoutParams(-1, dp(74)));

        TextView attribution = text("© OpenStreetMap contributors  •  التوجيه التجريبي عبر OSRM",
            9, MUTED, false);
        attribution.setGravity(Gravity.CENTER);
        root.addView(attribution, new LinearLayout.LayoutParams(-1, dp(16)));

        setContentView(root);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.create(
            "sans-serif", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL
        ));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12);
        button.setTextColor(0xFF071014);
        button.setAllCaps(false);
        button.setBackgroundColor(ACCENT);
        return button;
    }

    private void loadLastKnownLocation() {
        if (locationManager == null) return;
        if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            status.setText("يلزم السماح بالوصول إلى الموقع لبدء التوجيه");
            return;
        }
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            currentLocation = newerLocation(gps, network);
            if (currentLocation != null) updateCurrentMarker();
        } catch (RuntimeException ignored) { }
    }

    private Location newerLocation(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.getTime() >= second.getTime() ? first : second;
    }

    @Override protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (locationManager != null
            && checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000L, 5f, this
                );
            } catch (RuntimeException ignored) { }
        }
    }

    @Override protected void onPause() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (RuntimeException ignored) { }
        }
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (activeTask != null) activeTask.cancel(true);
        if (map != null) map.onDetach();
        super.onDestroy();
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null) return;
        currentLocation = location;
        updateCurrentMarker();
        status.setText(String.format(Locale.US,
            "موقع GPS: %.5f, %.5f", location.getLatitude(), location.getLongitude()));
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            status.setText("GPS متوقف؛ فعّله للحصول على موقعك");
        }
    }

    @Override public void onProviderEnabled(String provider) { }

    @Override public void onStatusChanged(String provider, int state, Bundle extras) { }

    private void updateCurrentMarker() {
        if (map == null || currentLocation == null) return;
        GeoPoint point = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
        if (currentMarker == null) {
            currentMarker = new Marker(map);
            currentMarker.setTitle("موقعك الحالي");
            map.getOverlays().add(currentMarker);
        }
        currentMarker.setPosition(point);
        map.getController().animateTo(point);
        map.invalidate();
    }

    private void searchDestination() {
        String query = destinationInput.getText().toString().trim();
        if (query.length() < 2) {
            destinationInput.setError("اكتب اسم وجهة أو عنواناً صحيحاً");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastGeocodeRequest < 1100L) {
            status.setText("انتظر لحظة قبل إرسال بحث جديد");
            return;
        }
        lastGeocodeRequest = now;
        loadLastKnownLocation();
        if (currentLocation == null) {
            status.setText("لم يصل GPS بعد. انتظر تحديد موقعك ثم أعد المحاولة.");
            return;
        }
        if (activeTask != null) activeTask.cancel(true);
        status.setText("جارٍ البحث عن الوجهة وحساب المسار...");
        instructions.setText("يرجى الانتظار حتى يكتمل البحث.");
        activeTask = new RouteTask(this, currentLocation).execute(query);
    }

    private void showRoute(NavigationResult result) {
        if (isFinishing()) return;
        if (result.error != null) {
            status.setText(result.error);
            instructions.setText("تحقق من الإنترنت واسم الوجهة وإشارة GPS.");
            return;
        }

        if (routeLine != null) map.getOverlays().remove(routeLine);
        if (destinationMarker != null) map.getOverlays().remove(destinationMarker);

        routeLine = new Polyline(map);
        routeLine.setColor(0xFF00BEB5);
        routeLine.setWidth(dp(5));
        routeLine.setPoints(result.geometry);
        map.getOverlays().add(routeLine);

        destinationMarker = new Marker(map);
        destinationMarker.setPosition(result.destination);
        destinationMarker.setTitle(result.destinationName);
        destinationMarker.setSnippet("الوجهة المحددة");
        map.getOverlays().add(destinationMarker);

        if (currentMarker == null) {
            currentMarker = new Marker(map);
            currentMarker.setTitle("موقعك الحالي");
            map.getOverlays().add(currentMarker);
        }
        currentMarker.setPosition(result.start);
        map.getController().animateTo(new GeoPoint(
            (result.start.getLatitude() + result.destination.getLatitude()) / 2.0,
            (result.start.getLongitude() + result.destination.getLongitude()) / 2.0
        ));
        map.getController().setZoom(12.5);
        map.invalidate();

        status.setText(String.format(Locale.US,
            "المسافة %.1f كم  •  الوقت التقديري %d دقيقة",
            result.distanceKm, result.durationMinutes));
        instructions.setText(result.stepsText
            + "\n\n© OpenStreetMap contributors  •  Route: OSRM");
    }

    private static String readResponse(HttpURLConnection connection) throws Exception {
        InputStream input = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line);
            if (output.length() > 2_000_000) throw new IllegalStateException("Response too large");
        }
        reader.close();
        return output.toString();
    }

    private static JSONObject getJson(String address, String userAgent) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "ar,en;q=0.8");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("الخدمة أعادت HTTP " + code);
            }
            return new JSONObject(readResponse(connection));
        } finally {
            connection.disconnect();
        }
    }

    private static JSONArray getJsonArray(String address, String userAgent) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("الخدمة أعادت HTTP " + code);
            }
            return new JSONArray(readResponse(connection));
        } finally {
            connection.disconnect();
        }
    }

    private static ArrayList<GeoPoint> decodePolyline(String encoded) {
        ArrayList<GeoPoint> points = new ArrayList<>();
        int index = 0;
        int latitude = 0;
        int longitude = 0;
        while (index < encoded.length()) {
            int[] lat = decodeValue(encoded, index);
            latitude += lat[0];
            index = lat[1];
            int[] lon = decodeValue(encoded, index);
            longitude += lon[0];
            index = lon[1];
            points.add(new GeoPoint(latitude / 1E5, longitude / 1E5));
        }
        return points;
    }

    private static int[] decodeValue(String encoded, int start) {
        int result = 0;
        int shift = 0;
        int index = start;
        int value;
        do {
            if (index >= encoded.length()) throw new IllegalArgumentException("Invalid polyline");
            value = encoded.charAt(index++) - 63;
            result |= (value & 0x1f) << shift;
            shift += 5;
        } while (value >= 0x20);
        int decoded = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{decoded, index};
    }

    private static String maneuverText(JSONObject step) {
        JSONObject maneuver = step.optJSONObject("maneuver");
        String type = maneuver == null ? "" : maneuver.optString("type", "");
        String modifier = maneuver == null ? "" : maneuver.optString("modifier", "");
        String road = step.optString("name", "");
        double meters = step.optDouble("distance", 0);
        String direction;
        if ("left".equals(modifier) || "sharp left".equals(modifier)) direction = "يسار";
        else if ("right".equals(modifier) || "sharp right".equals(modifier)) direction = "يمين";
        else if ("slight left".equals(modifier)) direction = "ميل يسار";
        else if ("slight right".equals(modifier)) direction = "ميل يمين";
        else if ("uturn".equals(modifier)) direction = "انعطاف للخلف";
        else direction = "تابع مباشرة";

        String action;
        if ("depart".equals(type)) action = "انطلق";
        else if ("arrive".equals(type)) action = "وصلت إلى وجهتك";
        else if ("roundabout".equals(type) || "rotary".equals(type)) action = "ادخل الدوار";
        else if ("merge".equals(type)) action = "اندماج";
        else if ("fork".equals(type)) action = "عند المفترق";
        else if ("end of road".equals(type)) action = "نهاية الطريق، اتجه";
        else if ("new name".equals(type) || "continue".equals(type)) action = "استمر";
        else action = "انعطف";

        String distance = meters >= 1000
            ? String.format(Locale.US, "%.1f كم", meters / 1000.0)
            : String.format(Locale.US, "%.0f م", meters);
        String text = action;
        if (!"depart".equals(type) && !"arrive".equals(type)) text += " " + direction;
        if (!road.isEmpty()) text += " نحو " + road;
        return "• " + text + " بعد " + distance;
    }

    private static final class NavigationResult {
        final String error;
        final String destinationName;
        final GeoPoint start;
        final GeoPoint destination;
        final ArrayList<GeoPoint> geometry;
        final String stepsText;
        final double distanceKm;
        final int durationMinutes;

        NavigationResult(String error, String name, GeoPoint start, GeoPoint destination,
                         ArrayList<GeoPoint> geometry, String steps, double distanceKm,
                         int durationMinutes) {
            this.error = error;
            this.destinationName = name;
            this.start = start;
            this.destination = destination;
            this.geometry = geometry;
            this.stepsText = steps;
            this.distanceKm = distanceKm;
            this.durationMinutes = durationMinutes;
        }
    }

    private static final class RouteTask extends AsyncTask<String, Void, NavigationResult> {
        private final NavigationActivity activity;
        private final Location startLocation;

        RouteTask(NavigationActivity activity, Location start) {
            this.activity = activity;
            this.startLocation = new Location(start);
        }

        @Override protected NavigationResult doInBackground(String... queries) {
            try {
                String query = URLEncoder.encode(queries[0], "UTF-8");
                String agent = "ALI-Car-System/1.1 (com.ali.carsystem)";
                String normalizedQuery = queries[0].trim().toLowerCase(Locale.US);
                android.content.SharedPreferences cache = activity.getSharedPreferences(
                    "nominatim_cache", Context.MODE_PRIVATE);
                String cachedQuery = cache.getString("query", "");
                long cachedAt = cache.getLong("cached_at", 0L);
                boolean cacheHit = normalizedQuery.equals(cachedQuery)
                    && System.currentTimeMillis() - cachedAt < 7L * 24L * 60L * 60L * 1000L
                    && cache.contains("latitude") && cache.contains("longitude");
                double destinationLat;
                double destinationLon;
                String name;
                if (cacheHit) {
                    destinationLat = Double.longBitsToDouble(cache.getLong("latitude", 0L));
                    destinationLon = Double.longBitsToDouble(cache.getLong("longitude", 0L));
                    name = cache.getString("display_name", queries[0]);
                } else {
                    String searchUrl = "https://nominatim.openstreetmap.org/search?q="
                        + query + "&format=jsonv2&limit=1&accept-language=ar";
                    JSONArray matches = getJsonArray(searchUrl, agent);
                    if (matches.length() == 0) {
                        return new NavigationResult("لم يتم العثور على الوجهة", "", null,
                            null, null, "", 0, 0);
                    }
                    JSONObject match = matches.getJSONObject(0);
                    destinationLat = Double.parseDouble(match.getString("lat"));
                    destinationLon = Double.parseDouble(match.getString("lon"));
                    name = match.optString("display_name", queries[0]);
                    cache.edit()
                        .putString("query", normalizedQuery)
                        .putLong("latitude", Double.doubleToRawLongBits(destinationLat))
                        .putLong("longitude", Double.doubleToRawLongBits(destinationLon))
                        .putString("display_name", name)
                        .putLong("cached_at", System.currentTimeMillis())
                        .apply();
                }
                GeoPoint destination = new GeoPoint(destinationLat, destinationLon);
                GeoPoint start = new GeoPoint(
                    startLocation.getLatitude(), startLocation.getLongitude());

                String routeUrl = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f"
                        + "?overview=full&geometries=polyline&steps=true",
                    start.getLongitude(), start.getLatitude(),
                    destination.getLongitude(), destination.getLatitude());
                JSONObject response = getJson(routeUrl, agent);
                if (!"Ok".equals(response.optString("code"))) {
                    return new NavigationResult("تعذّر حساب المسار لهذه الوجهة", "", null,
                        null, null, "", 0, 0);
                }
                JSONArray routes = response.optJSONArray("routes");
                if (routes == null || routes.length() == 0) {
                    return new NavigationResult("لا يوجد مسار قيادة متاح", "", null,
                        null, null, "", 0, 0);
                }
                JSONObject route = routes.getJSONObject(0);
                ArrayList<GeoPoint> geometry = decodePolyline(route.getString("geometry"));
                StringBuilder stepsText = new StringBuilder("تعليمات المسار:\n");
                JSONArray legs = route.optJSONArray("legs");
                if (legs != null && legs.length() > 0) {
                    JSONArray steps = legs.getJSONObject(0).optJSONArray("steps");
                    if (steps != null) {
                        for (int i = 0; i < steps.length(); i++) {
                            stepsText.append(maneuverText(steps.getJSONObject(i))).append('\n');
                        }
                    }
                }
                double distanceKm = route.optDouble("distance", 0) / 1000.0;
                int durationMinutes = (int) Math.ceil(route.optDouble("duration", 0) / 60.0);
                return new NavigationResult(null, name, start, destination,
                    geometry, stepsText.toString(), distanceKm, durationMinutes);
            } catch (Exception error) {
                String message = error.getMessage();
                return new NavigationResult(
                    message == null ? "تعذّر الاتصال بخدمة الملاحة" : "الملاحة: " + message,
                    "", null, null, null, "", 0, 0);
            }
        }

        @Override protected void onPostExecute(NavigationResult result) {
            if (activity != null && !activity.isFinishing()) activity.showRoute(result);
        }
    }
}
