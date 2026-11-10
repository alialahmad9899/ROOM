package com.wesal.carlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final int background = 0xFF101820;
    private final int surface = 0xFF1D2A35;
    private final int text = 0xFFF4F7FA;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        buildDashboard();
    }

    private void buildDashboard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(14));
        root.setBackgroundColor(background);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("وصال | قيادة أسهل");
        title.setTextColor(text);
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, dp(3));
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("واجهة بسيطة وخفيفة لجهاز السيارة");
        subtitle.setTextColor(0xFFB8C7D3);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle, matchWrap());

        addButton(root, "🧭   الخرائط والملاحة", () -> openMap());
        addButton(root, "♫   الموسيقى", () -> openCategory(Intent.CATEGORY_APP_MUSIC, "لم يتم العثور على تطبيق موسيقى"));
        addButton(root, "☎   الهاتف", () -> openIntent(new Intent(Intent.ACTION_DIAL), "لا يوجد تطبيق هاتف متاح"));
        addButton(root, "ᛒ   البلوتوث", () -> openIntent(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), "إعدادات البلوتوث غير متاحة"));
        addButton(root, "⚙   إعدادات الجهاز", () -> openIntent(new Intent(android.provider.Settings.ACTION_SETTINGS), "الإعدادات غير متاحة"));

        TextView footer = new TextView(this);
        footer.setText("إصدار تجريبي خفيف • لا يعمل بالخلفية");
        footer.setTextColor(0xFF8293A1);
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, 0);
        root.addView(footer, matchWrap());
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface));
        button.setMinHeight(dp(58));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(62));
        params.bottomMargin = dp(9);
        root.addView(button, params);
        button.setOnClickListener(v -> action.run());
    }

    private void openMap() {
        Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
        openIntent(map, "لا يوجد تطبيق خرائط يدعم الملاحة على الجهاز");
    }

    private void openCategory(String category, String missingMessage) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        openIntent(intent, missingMessage);
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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
