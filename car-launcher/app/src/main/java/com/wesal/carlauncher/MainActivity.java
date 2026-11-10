package com.wesal.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity implements LocationListener {
    private static final int BG = 0xFF0B1118;
    private static final int PANEL = 0xFF131F2A;
    private static final int TILE = 0xFF1B2B38;
    private static final int WHITE = 0xFFF4F7FA;
    private static final int MUTED = 0xFF93A6B5;
    private static final int ACCENT = 0xFF39D6C5;
    private static final int AMBER = 0xFFFFC36B;

    private SpeedometerView speedometer;
    private TextView gpsStatus;
    private LocationManager locationManager;
    private long lastGpsUpdate = 0L;
    private float displayedSpeed = 0f;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable gpsWatchdog = new Runnable() {
        @Override public void run() {
            boolean fresh = lastGpsUpdate > 0L
                    && SystemClock.elapsedRealtime() - lastGpsUpdate <= 6000L;
            speedometer.setGpsAvailable(fresh);
            if (!fresh && locationManager != null
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                setGpsLabel("بانتظار إشارة GPS", false);
            }
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        buildDashboard();
    }

    private void buildDashboard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = new TextView(this);
        brand.setText("WESAL  /  DRIVE");
        brand.setTextColor(WHITE);
        brand.setTextSize(18);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView tagline = new TextView(this);
        tagline.setText("رفيقك على الطريق");
        tagline.setTextColor(MUTED);
        tagline.setTextSize(13);
        tagline.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(tagline, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout gaugePanel = new LinearLayout(this);
        gaugePanel.setOrientation(LinearLayout.VERTICAL);
        gaugePanel.setGravity(Gravity.CENTER);
        gaugePanel.setPadding(dp(8), dp(4), dp(8), dp(4));
        gaugePanel.setBackground(panelBackground());
        speedometer = new SpeedometerView(this);
        gaugePanel.addView(speedometer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        gpsStatus = new TextView(this);
        gpsStatus.setText("جارٍ فحص GPS...");
        gpsStatus.setTextColor(MUTED);
        gpsStatus.setTextSize(12);
        gpsStatus.setGravity(Gravity.CENTER);
        gpsStatus.setPadding(0, dp(2), 0, dp(5));
        gaugePanel.addView(gpsStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(26)));

        LinearLayout.LayoutParams gaugeParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.92f);
        gaugeParams.rightMargin = dp(10);
        content.addView(gaugePanel, gaugeParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(4), dp(2), dp(2), dp(2));

        TextView controlsTitle = new TextView(this);
        controlsTitle.setText("اختصارات السيارة");
        controlsTitle.setTextColor(WHITE);
        controlsTitle.setTextSize(17);
        controlsTitle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        controlsTitle.setPadding(0, 0, dp(5), dp(6));
        controls.addView(controlsTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        addTileRow(controls,
                new Tile("⌖", "الملاحة", () -> openNavigation()),
                new Tile("♫", "الموسيقى", () -> openMusic()));
        addTileRow(controls,
                new Tile("◉", "راديو إنترنت", () -> openMusic()),
                new Tile("☎", "الهاتف", () -> openIntent(
                        new Intent(Intent.ACTION_DIAL), "تطبيق الهاتف غير متوفر")));
        addTileRow(controls,
                new Tile("ᛒ", "البلوتوث", () -> openIntent(
                        new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                        "إعدادات البلوتوث غير متاحة")),
                new Tile("⚙", "الإعدادات", () -> openIntent(
                        new Intent(Settings.ACTION_SETTINGS),
                        "إعدادات الجهاز غير متاحة")));

        content.addView(controls, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.08f));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView footer = new TextView(this);
        footer.setText("WESAL DRIVE  •  واجهة خفيفة بدون خدمات تعمل بالخلفية");
        footer.setTextColor(0xFF657887);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(5), 0, 0);
        root.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));

        setContentView(root);
    }

    private void addTileRow(LinearLayout parent, Tile first, Tile second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rowParams.bottomMargin = dp(8);
        parent.addView(row, rowParams);

        addTile(row, first);
        if (second != null) addTile(row, second);
        else {
            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private void addTile(LinearLayout row, Tile tile) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(tileBackground());

        TextView symbol = new TextView(this);
        symbol.setText(tile.icon);
        symbol.setTextColor(ACCENT);
        symbol.setTextSize(29);
        symbol.setGravity(Gravity.CENTER);
        symbol.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(symbol, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(39)));

        TextView label = new TextView(this);
        label.setText(tile.label);
        label.setTextColor(WHITE);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        row.addView(card, params);
        card.setOnClickListener(v -> tile.action.run());
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(PANEL);
        drawable.setCornerRadius(dp(22));
        drawable.setStroke(dp(1), 0xFF243744);
        return drawable;
    }

    private RippleDrawable tileBackground() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(TILE);
        shape.setCornerRadius(dp(18));
        shape.setStroke(dp(1), 0xFF2A3D4B);
        return new RippleDrawable(ColorStateList.valueOf(0x3354D9CE), shape, null);
    }

    private void openNavigation() {
        Intent googleNav = new Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=&mode=d"));
        if (googleNav.resolveActivity(getPackageManager()) != null) {
            openIntent(googleNav, "تطبيق الملاحة غير مثبت");
        } else {
            openIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")),
                    "لم يتم العثور على تطبيق خرائط");
        }
    }

    private void openMusic() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_MUSIC);
        openIntent(intent, "لم يتم العثور على تطبيق موسيقى أو راديو");
    }

    private void openIntent(Intent intent, String missingMessage) {
        try {
            if (intent.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, missingMessage, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, missingMessage, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGpsUpdates();
    }

    @Override
    protected void onPause() {
        stopGpsUpdates();
        super.onPause();
    }

    private void startGpsUpdates() {
        handler.removeCallbacks(gpsWatchdog);
        handler.post(gpsWatchdog);
        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            setGpsLabel("صلاحية الموقع غير مفعّلة", false);
            return;
        }
        try {
            if (locationManager == null
                    || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                setGpsLabel("فعّل GPS لعرض السرعة", false);
                speedometer.setGpsAvailable(false);
                return;
            }
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 1.0f, this);
            setGpsLabel("GPS مفعّل • بانتظار الإشارة", false);
        } catch (SecurityException | IllegalArgumentException error) {
            setGpsLabel("تعذّر الوصول إلى GPS", false);
        }
    }

    private void stopGpsUpdates() {
        handler.removeCallbacks(gpsWatchdog);
        lastGpsUpdate = 0L;
        speedometer.setGpsAvailable(false);
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
                // Location permission may have been revoked while the activity was paused.
            }
        }
    }

    private void setGpsLabel(String label, boolean connected) {
        if (gpsStatus == null) return;
        gpsStatus.setText((connected ? "●  " : "○  ") + label);
        gpsStatus.setTextColor(connected ? ACCENT : MUTED);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        lastGpsUpdate = SystemClock.elapsedRealtime();
        float next = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        if (next < 0f || Float.isNaN(next) || Float.isInfinite(next)) next = 0f;
        if (next > 200f) next = 200f;
        displayedSpeed = displayedSpeed == 0f ? next : (displayedSpeed * 0.65f) + (next * 0.35f);
        speedometer.setSpeed(displayedSpeed);
        speedometer.setGpsAvailable(true);
        setGpsLabel("إشارة GPS متصلة", true);
    }

    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) startGpsUpdates();
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            lastGpsUpdate = 0L;
            speedometer.setGpsAvailable(false);
            setGpsLabel("تم إيقاف GPS", false);
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Tile {
        final String icon;
        final String label;
        final Runnable action;
        Tile(String icon, String label, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
        }
    }

    private final class SpeedometerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcBounds = new RectF();
        private float speed = 0f;
        private boolean gpsAvailable = false;

        SpeedometerView(Activity context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setSpeed(float value) {
            speed = Math.max(0f, Math.min(200f, value));
            invalidate();
        }

        void setGpsAvailable(boolean value) {
            gpsAvailable = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.49f;
            float radius = Math.min(getWidth() * 0.43f, getHeight() * 0.43f);
            if (radius < dp(60)) radius = Math.min(getWidth(), getHeight()) * 0.42f;
            arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(9));
            paint.setColor(0xFF2C3C48);
            canvas.drawArc(arcBounds, 135f, 270f, false, paint);

            float activeSweep = Math.min(270f, (speed / 200f) * 270f);
            paint.setColor(gpsAvailable ? ACCENT : 0xFF607381);
            canvas.drawArc(arcBounds, 135f, activeSweep, false, paint);

            for (int value = 0; value <= 200; value += 10) {
                float angle = (float) Math.toRadians(135f + (value / 200f) * 270f);
                boolean major = value % 20 == 0;
                float outer = radius * 0.86f;
                float inner = radius * (major ? 0.72f : 0.78f);
                float x1 = cx + (float) Math.cos(angle) * outer;
                float y1 = cy + (float) Math.sin(angle) * outer;
                float x2 = cx + (float) Math.cos(angle) * inner;
                float y2 = cy + (float) Math.sin(angle) * inner;
                paint.setStrokeWidth(major ? dp(2) : dp(1));
                paint.setColor(major ? WHITE : MUTED);
                canvas.drawLine(x1, y1, x2, y2, paint);
                if (major) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(MUTED);
                    paint.setTextSize(dp(10));
                    paint.setTextAlign(Paint.Align.CENTER);
                    float tx = cx + (float) Math.cos(angle) * radius * 0.62f;
                    float ty = cy + (float) Math.sin(angle) * radius * 0.62f + dp(4);
                    canvas.drawText(String.valueOf(value), tx, ty, paint);
                    paint.setStyle(Paint.Style.STROKE);
                }
            }

            float needleAngle = (float) Math.toRadians(135f + (Math.min(speed, 200f) / 200f) * 270f);
            float needleLength = radius * 0.59f;
            paint.setColor(AMBER);
            paint.setStrokeWidth(dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(needleAngle) * needleLength,
                    cy + (float) Math.sin(needleAngle) * needleLength, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(AMBER);
            canvas.drawCircle(cx, cy, dp(7), paint);

            paint.setColor(WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            paint.setTextSize(Math.min(dp(54), radius * 0.40f));
            String speedText = gpsAvailable ? String.valueOf(Math.round(speed)) : "--";
            canvas.drawText(speedText, cx, cy + radius * 0.37f, paint);

            paint.setColor(MUTED);
            paint.setTextSize(dp(13));
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            canvas.drawText("km/h", cx, cy + radius * 0.55f, paint);

            paint.setColor(ACCENT);
            paint.setTextSize(dp(11));
            canvas.drawText("GPS SPEED", cx, cy - radius * 0.57f, paint);
        }
    }
}
