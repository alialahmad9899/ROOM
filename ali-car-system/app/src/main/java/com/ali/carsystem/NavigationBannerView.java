package com.ali.carsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Compact persistent navigation strip shared by the dashboard and media screen.
 * It renders only the last state published by the real foreground navigation service.
 */
public final class NavigationBannerView extends LinearLayout {
    private final TextView arrow;
    private final TextView instruction;
    private final TextView details;
    private boolean receiverRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!NavigationGuidanceService.ACTION_STATE.equals(intent.getAction())) return;
            render(intent.getStringExtra("guidance"),
                    intent.getBooleanExtra("active", false),
                    intent.getFloatExtra("accuracy_meters", -1f));
        }
    };

    public NavigationBannerView(Context context) {
        this(context, null);
    }

    public NavigationBannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        setPadding(pad, dp(6), pad, dp(6));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xEE103247, 0xEE071724});
        background.setCornerRadius(dp(13));
        background.setStroke(dp(1), 0x9952D9F3);
        setBackground(background);

        arrow = new TextView(context);
        arrow.setText("↱");
        arrow.setTextColor(0xFF70E5FF);
        arrow.setTextSize(29);
        arrow.setTypeface(Typeface.DEFAULT_BOLD);
        arrow.setGravity(Gravity.CENTER);
        addView(arrow, new LayoutParams(dp(42), LayoutParams.MATCH_PARENT));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams columnParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        addView(textColumn, columnParams);

        instruction = new TextView(context);
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(15);
        instruction.setTypeface(Typeface.DEFAULT_BOLD);
        instruction.setSingleLine(true);
        instruction.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textColumn.addView(instruction, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(25)));

        details = new TextView(context);
        details.setTextColor(0xFF9DC9DA);
        details.setTextSize(10);
        details.setSingleLine(true);
        details.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textColumn.addView(details, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(18)));
        setTag("navigation_guidance_banner");
        setVisibility(GONE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refreshSavedState() {
        SharedPreferences state = getContext().getSharedPreferences(
                "ali_car_navigation", Context.MODE_PRIVATE);
        render(state.getString("latest_state", ""),
                state.getBoolean("active", false), -1f);
    }

    private void render(String message, boolean active, float accuracy) {
        if (!active || message == null || message.trim().isEmpty()) {
            setVisibility(GONE);
            return;
        }
        String value = message.trim();
        String heading = value;
        int separator = value.indexOf(" • ");
        if (separator >= 0) heading = value.substring(0, separator);
        instruction.setText(heading);
        String extra = separator >= 0 ? value.substring(separator + 3) : "ملاحة محلية دون إنترنت";
        if (accuracy > 0f) extra = extra + " • GPS ±" + Math.round(accuracy) + " م";
        details.setText(extra);
        String lower = heading;
        if (lower.contains("يسار")) arrow.setText("↰");
        else if (lower.contains("يمين")) arrow.setText("↱");
        else if (lower.contains("دوار")) arrow.setText("⟳");
        else arrow.setText("↑");
        setVisibility(VISIBLE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!receiverRegistered) {
            try {
                getContext().registerReceiver(stateReceiver,
                        new IntentFilter(NavigationGuidanceService.ACTION_STATE));
                receiverRegistered = true;
            } catch (Exception ignored) { }
        }
        refreshSavedState();
    }

    @Override protected void onDetachedFromWindow() {
        if (receiverRegistered) {
            try { getContext().unregisterReceiver(stateReceiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        super.onDetachedFromWindow();
    }
}
