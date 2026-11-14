package com.ali.carsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Floating cockpit HUD for live, structured offline turn-by-turn guidance. */
public final class NavigationBannerView extends LinearLayout {
    private final NavigationArrowView arrow;
    private final TextView distance;
    private final TextView instruction;
    private final TextView details;
    private boolean receiverRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!NavigationGuidanceService.ACTION_STATE.equals(intent.getAction())) return;
            render(intent.getStringExtra("guidance"),
                    intent.getBooleanExtra("active", false),
                    intent.getFloatExtra("accuracy_meters", -1f),
                    intent.getFloatExtra(NavigationGuidanceService.EXTRA_DISTANCE_TO_TURN_METERS, -1f),
                    intent.getFloatExtra(NavigationGuidanceService.EXTRA_REMAINING_ROUTE_METERS, -1f),
                    intent.getLongExtra(NavigationGuidanceService.EXTRA_ETA_MINUTES, -1L),
                    intent.getStringExtra(NavigationGuidanceService.EXTRA_MANEUVER_TEXT),
                    intent.getFloatExtra(NavigationGuidanceService.EXTRA_SPEED_KMH, -1f));
        }
    };

    public NavigationBannerView(Context context) { this(context, null); }

    public NavigationBannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        int pad = dp(12);
        setPadding(pad, dp(8), pad, dp(8));
        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE9112B3D, 0xE9071826, 0xD90B2131});
        glass.setCornerRadius(dp(58));
        glass.setStroke(dp(1), 0x9969DDF4);
        setBackground(glass);
        setElevation(dp(12));
        setMinimumHeight(dp(112));
        setTag("dashboard_navigation_hud");
        setClickable(false);
        setFocusable(false);

        arrow = new NavigationArrowView(context);
        arrow.setTag("navigation_hud_arrow");
        LayoutParams arrowParams = new LayoutParams(dp(82), dp(82));
        arrowParams.rightMargin = dp(10);
        addView(arrow, arrowParams);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        addView(column, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        TextView kicker = new TextView(context);
        kicker.setText("الانعطاف التالي");
        kicker.setTextColor(0xFF83DFF3);
        kicker.setTextSize(10);
        kicker.setTypeface(Typeface.DEFAULT_BOLD);
        kicker.setGravity(Gravity.RIGHT);
        column.addView(kicker, new LayoutParams(LayoutParams.MATCH_PARENT, dp(17)));

        distance = new TextView(context);
        distance.setTag("navigation_hud_distance");
        distance.setText("—");
        distance.setTextColor(0xFFFFD77A);
        distance.setTextSize(34);
        distance.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        distance.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        distance.setIncludeFontPadding(false);
        distance.setShadowLayer(dp(7), 0, 0, 0x8841DFFF);
        column.addView(distance, new LayoutParams(LayoutParams.MATCH_PARENT, dp(43)));

        instruction = new TextView(context);
        instruction.setTag("navigation_hud_instruction");
        instruction.setText("بانتظار بدء المسار");
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(13);
        instruction.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        instruction.setGravity(Gravity.RIGHT);
        instruction.setSingleLine(true);
        instruction.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(instruction, new LayoutParams(LayoutParams.MATCH_PARENT, dp(22)));

        details = new TextView(context);
        details.setTag("navigation_hud_details");
        details.setText("ملاحة محلية دون إنترنت");
        details.setTextColor(0xFF9CBACB);
        details.setTextSize(9);
        details.setGravity(Gravity.RIGHT);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(details, new LayoutParams(LayoutParams.MATCH_PARENT, dp(17)));

        setVisibility(GONE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refreshSavedState() {
        SharedPreferences state = getContext().getSharedPreferences(
                "ali_car_navigation", Context.MODE_PRIVATE);
        render(state.getString("latest_state", ""),
                state.getBoolean("active", false), -1f,
                state.getFloat(NavigationGuidanceService.EXTRA_DISTANCE_TO_TURN_METERS, -1f),
                state.getFloat(NavigationGuidanceService.EXTRA_REMAINING_ROUTE_METERS, -1f),
                state.getLong(NavigationGuidanceService.EXTRA_ETA_MINUTES, -1L),
                state.getString(NavigationGuidanceService.EXTRA_MANEUVER_TEXT, ""),
                state.getFloat(NavigationGuidanceService.EXTRA_SPEED_KMH, -1f));
    }

    private void render(String state, boolean active, float accuracy, float distanceMeters,
                        float remainingMeters, long etaMinutes, String maneuver, float speedKmh) {
        if (!active) {
            setVisibility(GONE);
            return;
        }
        String message = state == null ? "" : state.trim();
        String turn = maneuver == null ? "" : maneuver.trim();
        arrow.setDirection(turn);
        if (distanceMeters >= 0f && !Float.isNaN(distanceMeters) && !Float.isInfinite(distanceMeters)) {
            distance.setText(formatDistance(distanceMeters));
            instruction.setText(turn.isEmpty() ? "تابع الطريق" : turn);
        } else if (message.contains("وصلت")) {
            distance.setText("وصلت");
            instruction.setText("أنت عند وجهتك");
        } else {
            distance.setText("—");
            instruction.setText(message.contains("GPS") || message.contains("موقع")
                    ? "بانتظار إشارة GPS دقيقة" : "الملاحة تعمل");
        }

        List<String> info = new ArrayList<>();
        if (remainingMeters >= 0f && !Float.isNaN(remainingMeters) && !Float.isInfinite(remainingMeters))
            info.add("متبقي " + formatDistance(remainingMeters));
        if (etaMinutes >= 0L) info.add("وصول " + etaMinutes + " د");
        if (speedKmh > 1f && !Float.isNaN(speedKmh) && !Float.isInfinite(speedKmh))
            info.add("سرعة " + Math.round(speedKmh) + " كم/س");
        if (accuracy > 0f && !Float.isNaN(accuracy) && !Float.isInfinite(accuracy))
            info.add("GPS ±" + Math.round(accuracy) + " م");
        if (info.isEmpty()) {
            info.add(message.contains("GPS") || message.contains("الموقع")
                    ? "المسار محفوظ • ننتظر موقعاً صالحاً"
                    : "ملاحة محلية دون إنترنت");
        }
        details.setText(TextUtils.join("  •  ", info));
        setContentDescription((turn.isEmpty() ? "توجيه الملاحة" : turn)
                + "، " + distance.getText() + "، " + details.getText());
        setVisibility(VISIBLE);
    }

    private String formatDistance(float meters) {
        if (meters >= 1000f) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            return new DecimalFormat("0.0", symbols).format(meters / 1000f) + " كم";
        }
        return Math.max(0, Math.round(meters)) + " م";
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

    private static final class NavigationArrowView extends View {
        private static final int STRAIGHT = 0, LEFT = 1, RIGHT = 2, ROUNDABOUT = 3, UTURN = 4;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int direction = STRAIGHT;

        NavigationArrowView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setDirection(String text) {
            String value = text == null ? "" : text;
            int next = value.contains("يسار") ? LEFT
                    : value.contains("يمين") ? RIGHT
                    : value.contains("دوار") ? ROUNDABOUT
                    : value.contains("استدر") ? UTURN : STRAIGHT;
            if (direction != next) { direction = next; invalidate(); }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.45f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF0B2B40);
            paint.setShadowLayer(dp(7), 0, dp(3), 0xAA000000);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(0xFF56DDF6);
            canvas.drawCircle(cx, cy, radius - dp(2), paint);
            paint.setColor(0x6656DDF6);
            canvas.drawCircle(cx, cy, radius - dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(6));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFF8BEFFF);
            Path path = new Path();
            Path head = new Path();
            float s = radius * 0.76f;
            if (direction == LEFT) {
                path.moveTo(cx + s * .55f, cy + s);
                path.lineTo(cx + s * .55f, cy + s * .05f);
                path.quadTo(cx + s * .55f, cy - s * .36f, cx - s * .28f, cy - s * .36f);
                path.lineTo(cx - s * .55f, cy - s * .36f);
                head.moveTo(cx - s * .55f, cy - s * .36f);
                head.lineTo(cx - s * .16f, cy - s * .67f);
                head.moveTo(cx - s * .55f, cy - s * .36f);
                head.lineTo(cx - s * .16f, cy - s * .05f);
            } else if (direction == RIGHT) {
                path.moveTo(cx - s * .55f, cy + s);
                path.lineTo(cx - s * .55f, cy + s * .05f);
                path.quadTo(cx - s * .55f, cy - s * .36f, cx + s * .28f, cy - s * .36f);
                path.lineTo(cx + s * .55f, cy - s * .36f);
                head.moveTo(cx + s * .55f, cy - s * .36f);
                head.lineTo(cx + s * .16f, cy - s * .67f);
                head.moveTo(cx + s * .55f, cy - s * .36f);
                head.lineTo(cx + s * .16f, cy - s * .05f);
            } else if (direction == ROUNDABOUT) {
                canvas.drawArc(cx - s * .48f, cy - s * .48f, cx + s * .48f,
                        cy + s * .48f, -85f, 285f, false, paint);
                head.moveTo(cx + s * .42f, cy - s * .31f);
                head.lineTo(cx + s * .49f, cy - s * .02f);
                head.lineTo(cx + s * .20f, cy - s * .08f);
                canvas.drawPath(head, paint);
                return;
            } else if (direction == UTURN) {
                path.moveTo(cx + s * .55f, cy + s);
                path.lineTo(cx + s * .55f, cy - s * .10f);
                path.cubicTo(cx + s * .55f, cy - s * .88f, cx - s * .55f,
                        cy - s * .88f, cx - s * .55f, cy - s * .10f);
                head.moveTo(cx - s * .55f, cy - s * .10f);
                head.lineTo(cx - s * .85f, cy - s * .40f);
                head.moveTo(cx - s * .55f, cy - s * .10f);
                head.lineTo(cx - s * .25f, cy - s * .40f);
            } else {
                path.moveTo(cx, cy + s);
                path.lineTo(cx, cy - s * .43f);
                head.moveTo(cx, cy - s * .43f);
                head.lineTo(cx - s * .38f, cy - s * .04f);
                head.moveTo(cx, cy - s * .43f);
                head.lineTo(cx + s * .38f, cy - s * .04f);
            }
            canvas.drawPath(path, paint);
            canvas.drawPath(head, paint);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}