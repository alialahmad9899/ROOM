package com.ali.carsystem;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity implements LocationListener {
    public static final String EXTRA_BOOT_GREETING = "ali.boot.greeting";
    public static final String EXTRA_SHOW_WELCOME = "ali.show.welcome";

    private static final int BG = 0xFF080D13;
    private static final int PANEL = 0xFF111C27;
    private static final int TILE = 0xFF172633;
    private static final int WHITE = 0xFFF2F6F9;
    private static final int MUTED = 0xFF91A4B2;
    private static final int ACCENT = 0xFF42D9D0;
    private static final int GOLD = 0xFFFFC56E;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private WifiManager wifiManager;
    private SpeedView speedView;
    private TextView gpsText;
    private TextView wifiText;
    private TextView btText;
    private TextView batteryText;
    private TextView clockText;
    private TextView dateText;
    private FrameLayout frame;
    private View welcomeOverlay;
    private TextToSpeech tts;
    private long lastGps = 0L;
    private float speed = 0f;
    private boolean gpsRegistered = false;
    private View ambientOverlay;
    private long lastBatteryRefresh = 0L;
    private final Runnable ambientRunnable = new Runnable() { public void run() { showAmbientClock(); } };

    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            updateClockAndBattery();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        applyImmersiveMode();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        buildDashboard();
        showWelcome();
        Intent launch = getIntent();
        if (launch != null && launch.getBooleanExtra(EXTRA_BOOT_GREETING, false)) {
            playBootGreeting();
        }
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }
    @Override public void onUserInteraction() {
        super.onUserInteraction();
        removeAmbientClock();
        scheduleAmbientClock();
    }
    private void scheduleAmbientClock() {
        handler.removeCallbacks(ambientRunnable);
        handler.postDelayed(ambientRunnable, 90000L);
    }
    private void showAmbientClock() {
        if (isFinishing() || frame == null || ambientOverlay != null) return;
        TextView clock = label("", 58, 0xFF77808A, true);
        clock.setGravity(Gravity.CENTER);
        clock.setBackgroundColor(0xFF020304);
        clock.setAlpha(0.82f);
        ambientOverlay = clock;
        frame.addView(clock, new FrameLayout.LayoutParams(-1, -1));
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (ambientOverlay != clock) return;
                clock.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(new Date()));
                clock.postDelayed(this, 15000L);
            }
        };
        clock.setTag(ticker);
        ticker.run();
    }
    private void removeAmbientClock() {
        if (ambientOverlay == null) return;
        Object ticker = ambientOverlay.getTag();
        if (ticker instanceof Runnable) handler.removeCallbacks((Runnable) ticker);
        if (ambientOverlay.getParent() instanceof FrameLayout)
            ((FrameLayout) ambientOverlay.getParent()).removeView(ambientOverlay);
        ambientOverlay = null;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientBox box(int color, int radius) {
        return new GradientBox(color, dp(radius));
    }

    private void buildDashboard() {
        frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(8));
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        header.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView logo = label("ALI CAR", 26, WHITE, true);
        logo.setLetterSpacing(0.13f);
        TextView sub = label("SMART DRIVING  •  P01Y", 9, ACCENT, true);
        brand.addView(logo, new LinearLayout.LayoutParams(-1, dp(34)));
        brand.addView(sub, new LinearLayout.LayoutParams(-1, dp(18)));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(54), 1f));

        LinearLayout clock = new LinearLayout(this);
        clock.setGravity(Gravity.CENTER);
        clock.setOrientation(LinearLayout.VERTICAL);
        clockText = label("--:--", 25, WHITE, true);
        clockText.setGravity(Gravity.CENTER);
        dateText = label("", 10, MUTED, false);
        dateText.setGravity(Gravity.CENTER);
        clock.addView(clockText, new LinearLayout.LayoutParams(-1, dp(31)));
        clock.addView(dateText, new LinearLayout.LayoutParams(-1, dp(18)));
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(dp(118), dp(54));
        clockParams.leftMargin = dp(12);
        header.addView(clock, clockParams);

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setOrientation(LinearLayout.VERTICAL);
        wifiText = label("Wi-Fi", 10, MUTED, true);
        btText = label("Bluetooth", 10, MUTED, true);
        batteryText = label("BAT --%", 10, GOLD, true);
        status.addView(wifiText, new LinearLayout.LayoutParams(-2, dp(15)));
        status.addView(btText, new LinearLayout.LayoutParams(-2, dp(15)));
        status.addView(batteryText, new LinearLayout.LayoutParams(-2, dp(15)));
        batteryText.setOnClickListener(v -> openBatterySettings());
        header.addView(status);
        TextView quickSettings = label("⚙", 23, GOLD, true);
        quickSettings.setGravity(Gravity.CENTER);
        quickSettings.setBackground(box(TILE, 18));
        quickSettings.setOnClickListener(v -> openIntent(
            new Intent(Settings.ACTION_SETTINGS), "إعدادات الجهاز غير متاحة"));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        settingsParams.leftMargin = dp(8);
        header.addView(quickSettings, settingsParams);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(60)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout gaugePanel = new LinearLayout(this);
        gaugePanel.setOrientation(LinearLayout.VERTICAL);
        gaugePanel.setGravity(Gravity.CENTER);
        gaugePanel.setPadding(dp(8), dp(4), dp(8), dp(4));
        gaugePanel.setBackground(box(PANEL, 24));
        speedView = new SpeedView(this);
        gaugePanel.addView(speedView, new LinearLayout.LayoutParams(-1, 0, 1f));
        gpsText = label("بانتظار إشارة GPS", 12, MUTED, false);
        gpsText.setGravity(Gravity.CENTER);
        gaugePanel.addView(gpsText, new LinearLayout.LayoutParams(-1, dp(26)));
        LinearLayout.LayoutParams gaugeParams = new LinearLayout.LayoutParams(0, -1, 0.94f);
        gaugeParams.rightMargin = dp(12);
        body.addView(gaugePanel, gaugeParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("لوحة القيادة", 18, WHITE, true);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        controls.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));

        tileRow(controls,
            new Tile("⌖", "الملاحة", this::openNavigation),
            new Tile("▶", "يوتيوب لايت", () -> openActivity(YouTubeLiteActivity.class)));
        tileRow(controls,
            new Tile("♫", "الموسيقى", () -> openActivity(MusicActivity.class)),
            new Tile("⚙", "إعدادات الجهاز", () -> openIntent(new Intent(Settings.ACTION_SETTINGS), "إعدادات الجهاز غير متاحة")));
        tileRow(controls,
            new Tile("◉", "تشغيل Wi-Fi", this::toggleWifi),
            new Tile("ᛒ", "Bluetooth", this::toggleBluetooth));


        LinearLayout brightnessRow = new LinearLayout(this);
        brightnessRow.setGravity(Gravity.CENTER_VERTICAL);
        brightnessRow.setPadding(dp(10), 0, dp(10), 0);
        brightnessRow.setBackground(box(TILE, 14));
        TextView brightnessLabel = label("سطوع", 12, ACCENT, true);
        brightnessRow.addView(brightnessLabel, new LinearLayout.LayoutParams(dp(44), -1));
        SeekBar brightness = new SeekBar(this);
        brightness.setMax(255);
        brightness.setProgress(readBrightness());
        if (Build.VERSION.SDK_INT >= 21) {
            brightness.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            brightness.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        }
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) applyBrightness(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        brightnessRow.addView(brightness, new LinearLayout.LayoutParams(0, -1, 1f));
        controls.addView(brightnessRow, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(0, -1, 1.06f);
        body.addView(controls, controlsParams);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = label("نظام السيارة الذكي  •  GPS", 10, MUTED, false);
        TextView credit = label("تم تطوير الواجهة بواسطة : ali ahmad", 11, ACCENT, true);
        credit.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        footer.addView(left, new LinearLayout.LayoutParams(0, dp(25), 1f));
        footer.addView(credit);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(27)));

        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        refreshStatus();
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    private void tileRow(LinearLayout parent, Tile first, Tile second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        rowParams.bottomMargin = dp(7);
        parent.addView(row, rowParams);
        addTile(row, first);
        addTile(row, second);
    }

    private void addTile(LinearLayout row, Tile tile) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(box(TILE, 17));
        card.setClickable(true);
        card.setFocusable(true);
        int iconColor = tile.title.contains("الملاحة") ? 0xFF4DB6FF
            : tile.title.contains("يوتيوب") ? 0xFFFF5D68
            : tile.title.contains("الموسيقى") ? 0xFFC58BFF
            : tile.title.contains("Wi-Fi") ? 0xFF40D9C8
            : tile.title.contains("Bluetooth") ? 0xFF579BFF : 0xFFFFB65C;
        TextView icon = label(tile.icon, 32, iconColor, true);
        icon.setGravity(Gravity.CENTER);
        TextView title = label(tile.title, 14, WHITE, true);
        title.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(-1, dp(36)));
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(27)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        row.addView(card, params);
        card.setOnClickListener(v -> tile.action.run());
    }

    private void showWelcome() {
        if (frame == null) return;
        if (welcomeOverlay != null) frame.removeView(welcomeOverlay);

        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(BG);
        TextView brand = label("ALI CAR", 42, WHITE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.19f);
        TextView line = label("WELCOME TO YOUR DRIVE", 13, ACCENT, true);
        line.setGravity(Gravity.CENTER);
        TextView arabic = label("رحلتك تبدأ من هنا", 17, MUTED, false);
        arabic.setGravity(Gravity.CENTER);
        splash.addView(brand, new LinearLayout.LayoutParams(-1, dp(58)));
        splash.addView(line, new LinearLayout.LayoutParams(-1, dp(30)));
        splash.addView(arabic, new LinearLayout.LayoutParams(-1, dp(36)));
        frame.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        welcomeOverlay = splash;
        splash.setAlpha(0f);
        splash.setScaleX(0.96f);
        splash.setScaleY(0.96f);
        splash.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420L)
            .withEndAction(() -> splash.postDelayed(() ->
                splash.animate().alpha(0f).setDuration(420L).withEndAction(() -> {
                    if (frame != null && splash.getParent() == frame) frame.removeView(splash);
                    if (welcomeOverlay == splash) welcomeOverlay = null;
                }).start(), 900L)).start();
    }

    private void openNavigation() {
        openActivity(NavigationActivity.class);
    }

    private void openActivity(Class<?> activity) {
        try {
            startActivity(new Intent(this, activity));
        } catch (RuntimeException error) {
            toast("تعذّر فتح الشاشة المطلوبة");
        }
    }

    private void openIntent(Intent intent, String errorMessage) {
        try {
            if (intent.resolveActivity(getPackageManager()) == null) {
                toast(errorMessage);
                return;
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            toast(errorMessage);
        }
    }

    private void toggleWifi() {
        if (wifiManager == null) {
            toast("خدمة Wi-Fi غير متاحة");
            return;
        }
        boolean target = !wifiManager.isWifiEnabled();
        boolean accepted;
        try {
            accepted = wifiManager.setWifiEnabled(target);
        } catch (SecurityException error) {
            accepted = false;
        }
        toast(accepted ? (target ? "جارٍ تشغيل Wi-Fi" : "جارٍ إيقاف Wi-Fi")
            : "تعذّر تغيير حالة Wi-Fi");
        handler.postDelayed(this::refreshStatus, 1200L);
    }

    private void toggleBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            toast("Bluetooth غير مدعوم في هذا الجهاز");
            return;
        }
        boolean target = !adapter.isEnabled();
        boolean accepted;
        try {
            accepted = target ? adapter.enable() : adapter.disable();
        } catch (SecurityException error) {
            accepted = false;
        }
        toast(accepted ? (target ? "جارٍ تشغيل Bluetooth" : "جارٍ إيقاف Bluetooth")
            : "تعذّر تغيير حالة Bluetooth");
        handler.postDelayed(this::refreshStatus, 1500L);
    }

    private void refreshStatus() {
        try {
            if (wifiText != null && wifiManager != null) {
                wifiText.setText(wifiManager.isWifiEnabled() ? "Wi-Fi  •  يعمل" : "Wi-Fi  •  متوقف");
                wifiText.setTextColor(wifiManager.isWifiEnabled() ? ACCENT : MUTED);
            }
        } catch (RuntimeException ignored) { }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (btText != null) {
                boolean enabled = adapter != null && adapter.isEnabled();
                btText.setText(enabled ? "Bluetooth  •  يعمل" : "Bluetooth  •  متوقف");
                btText.setTextColor(enabled ? ACCENT : MUTED);
            }
        } catch (RuntimeException ignored) { }
        updateClockAndBattery();
    }

    private void updateClockAndBattery() {
        if (speedView != null && gpsText != null
            && (lastGps == 0L || SystemClock.elapsedRealtime() - lastGps > 6000L)) {
            speedView.setGps(false);
            gpsText.setText("بانتظار إشارة GPS");
        }
        if (clockText != null) {
            Date now = new Date();
            clockText.setText(new SimpleDateFormat("hh:mm a", Locale.US).format(now));
            dateText.setText(new SimpleDateFormat("EEE  •  dd MMM", Locale.US).format(now));
        }
        if (batteryText != null && SystemClock.elapsedRealtime() - lastBatteryRefresh >= 30000L) {
            lastBatteryRefresh = SystemClock.elapsedRealtime();
            try {
                Intent battery = registerReceiver(null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery != null) {
                    int level = battery.getIntExtra("level", -1);
                    int scale = battery.getIntExtra("scale", 100);
                    if (level >= 0 && scale > 0) {
                        int percent = Math.round(level * 100f / scale);
                        batteryText.setText("BAT  " + percent + "%");
                    }
                }
            } catch (RuntimeException ignored) { }
        }
    }

    private void openBatterySettings() {
        Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        openIntent(intent, "إعدادات البطارية غير متاحة");
    }

    private int readBrightness() {
        try {
            return Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 150);
        } catch (RuntimeException error) {
            return 150;
        }
    }

    private void applyBrightness(int value) {
        int safeValue = Math.max(15, Math.min(255, value));
        try {
            if (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this)) {
                Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, safeValue);
            } else {
                toast("اسمح للتطبيق بتغيير سطوع النظام من الإعدادات");
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            }
        } catch (RuntimeException ignored) {
            // The window-level fallback still makes the brightness control useful.
        }
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = safeValue / 255f;
        getWindow().setAttributes(params);
    }

    private void playBootGreeting() {
        try {
            android.media.ToneGenerator chime =
                new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 65);
            chime.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 220);
            handler.postDelayed(() -> {
                chime.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 280);
                handler.postDelayed(chime::release, 360L);
            }, 260L);
        } catch (RuntimeException ignored) { }
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int language = tts.setLanguage(new Locale("ar"));
                    if (language != TextToSpeech.LANG_MISSING_DATA
                        && language != TextToSpeech.LANG_NOT_SUPPORTED) {
                        handler.postDelayed(() -> {
                            if (tts != null) {
                                tts.speak("أهلاً وسهلاً بك سيد علي، بسم الله نبدأ",
                                    TextToSpeech.QUEUE_FLUSH, null);
                            }
                        }, 900L);
                    }
                }
            });
        } catch (RuntimeException ignored) { }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        scheduleAmbientClock();
        startGps();
        refreshStatus();
        handler.removeCallbacks(clockTicker);
        handler.post(clockTicker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(clockTicker);
        handler.removeCallbacks(ambientRunnable);
        removeAmbientClock();
        stopGps();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (RuntimeException ignored) { }
            tts = null;
        }
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_SHOW_WELCOME, false)) showWelcome();
        if (intent != null && intent.getBooleanExtra(EXTRA_BOOT_GREETING, false)) playBootGreeting();
        refreshStatus();
    }

    private void startGps() {
        if (locationManager == null) return;
        try {
            if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsText.setText("فعّل GPS لعرض السرعة");
                speedView.setGps(false);
                return;
            }
            if (!gpsRegistered) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
                gpsRegistered = true;
            }
        } catch (RuntimeException ignored) { }
    }

    private void stopGps() {
        if (locationManager != null && gpsRegistered) {
            try { locationManager.removeUpdates(this); } catch (RuntimeException ignored) { }
        }
        gpsRegistered = false;
        lastGps = 0L;
        speed = 0f;
        if (speedView != null) {
            speedView.setSpeed(0f);
            speedView.setGps(false);
        }
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null || speedView == null) return;
        if (location.hasAccuracy() && location.getAccuracy() > 35f) return;
        float nextSpeed = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        if (Float.isNaN(nextSpeed) || Float.isInfinite(nextSpeed) || nextSpeed < 0f) return;
        if (speed > 18f && Math.abs(nextSpeed - speed) > 85f) return;
        lastGps = SystemClock.elapsedRealtime();
        speed = speed == 0f ? nextSpeed : speed * 0.72f + nextSpeed * 0.28f;
        speedView.setSpeed(speed);
        speedView.setGps(true);
        gpsText.setText("إشارة GPS متصلة");
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider) && speedView != null) {
            speedView.setGps(false);
            gpsText.setText("تم إيقاف GPS");
        }
    }

    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) startGps();
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private static final class Tile {
        final String icon;
        final String title;
        final Runnable action;
        Tile(String icon, String title, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
    }

    private final class SpeedView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private float value = 0f;
        private boolean gps = false;

        SpeedView(Activity activity) { super(activity); }

        void setSpeed(float next) {
            value = Math.max(0f, Math.min(220f, next));
            invalidate();
        }

        void setGps(boolean connected) {
            gps = connected;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() * 0.49f;
            float radius = Math.min(getWidth() * 0.42f, getHeight() * 0.44f);
            arc.set(cx - radius, cy - radius, cx + radius, cy + radius);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(9));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFF2B3B47);
            canvas.drawArc(arc, 135f, 270f, false, paint);
            paint.setColor(gps ? ACCENT : 0xFF637783);
            canvas.drawArc(arc, 135f, (Math.min(value, 220f) / 220f) * 270f, false, paint);

            for (int number = 0; number <= 220; number += 10) {
                double angle = Math.toRadians(135 + number / 220f * 270f);
                float outer = radius * 0.87f;
                float inner = radius * (number % 20 == 0 ? 0.74f : 0.80f);
                paint.setStrokeWidth(number % 20 == 0 ? dp(2) : dp(1));
                paint.setColor(number % 20 == 0 ? WHITE : MUTED);
                canvas.drawLine(
                    cx + (float) Math.cos(angle) * outer,
                    cy + (float) Math.sin(angle) * outer,
                    cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    paint
                );
                if (number % 40 == 0) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(dp(10));
                    paint.setColor(MUTED);
                    canvas.drawText("" + number,
                        cx + (float) Math.cos(angle) * radius * 0.62f,
                        cy + (float) Math.sin(angle) * radius * 0.62f + dp(4),
                        paint);
                    paint.setStyle(Paint.Style.STROKE);
                }
            }

            double needleAngle = Math.toRadians(135 + Math.min(value, 220f) / 220f * 270f);
            paint.setColor(GOLD);
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(cx, cy,
                cx + (float) Math.cos(needleAngle) * radius * 0.58f,
                cy + (float) Math.sin(needleAngle) * radius * 0.58f, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, dp(6), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(WHITE);
            paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            paint.setTextSize(Math.min(dp(52), radius * 0.40f));
            canvas.drawText(gps ? "" + Math.round(value) : "--",
                cx, cy + radius * 0.34f, paint);
            paint.setTextSize(dp(13));
            paint.setColor(MUTED);
            canvas.drawText("km/h", cx, cy + radius * 0.52f, paint);
            paint.setTextSize(dp(11));
            paint.setColor(ACCENT);
            canvas.drawText("ALI  •  GPS SPEED", cx, cy - radius * 0.57f, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
    }

    private static final class GradientBox extends android.graphics.drawable.GradientDrawable {
        GradientBox(int color, float radius) {
            setColor(color);
            setCornerRadius(radius);
            setStroke(1, 0xFF263B49);
        }
    }
}
