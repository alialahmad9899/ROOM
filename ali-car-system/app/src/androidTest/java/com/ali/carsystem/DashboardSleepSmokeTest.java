package com.ali.carsystem;

import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DashboardSleepSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity =
            new ActivityTestRule<>(MainActivity.class);

    @Test public void dashboardHasEqualLargeAnalogClockAndSpeedDial() {
        View content = activity.getActivity().findViewById(android.R.id.content);
        View speed = content.findViewWithTag("dashboard_speed_gauge");
        View clock = content.findViewWithTag("dashboard_analog_clock");
        assertNotNull(speed);
        assertNotNull(clock);
        assertEquals(speed.getLayoutParams().width, clock.getLayoutParams().width);
        assertEquals(speed.getLayoutParams().height, clock.getLayoutParams().height);
    }

    @Test public void sleepModeDimsScreenUntilExplicitWakeButtonIsPressed() {
        MainActivity main = activity.getActivity();
        View content = main.findViewById(android.R.id.content);
        View sleep = content.findViewWithTag("dashboard_sleep_toggle");
        assertNotNull(sleep);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(sleep::performClick);
        View overlay = content.findViewWithTag("ambient_sleep_overlay");
        assertNotNull(overlay);
        assertNotNull(overlay.findViewWithTag("ambient_sleep_time"));
        View wake = overlay.findViewWithTag("ambient_wake_button");
        assertNotNull(wake);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(wake::performClick);
        assertNull(content.findViewWithTag("ambient_sleep_overlay"));
    }
}
