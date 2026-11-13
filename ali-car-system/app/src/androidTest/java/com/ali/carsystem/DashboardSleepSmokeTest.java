package com.ali.carsystem;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup;
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
        ViewGroup content = (ViewGroup) activity.getActivity().findViewById(android.R.id.content);
        View speed = content.findViewWithTag("dashboard_speed_gauge");
        View clock = content.findViewWithTag("dashboard_analog_clock");
        assertNotNull(speed);
        assertNotNull(clock);
        assertEquals(speed.getLayoutParams().width, clock.getLayoutParams().width);
        assertEquals(speed.getLayoutParams().height, clock.getLayoutParams().height);
    }

    @Test public void sleepModeDimsScreenUntilExplicitWakeButtonIsPressed() {
        MainActivity main = activity.getActivity();
        ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
        View sleep = content.findViewWithTag("dashboard_sleep_toggle");
        assertNotNull(sleep);
        final float oldBrightness = main.getWindow().getAttributes().screenBrightness;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(sleep::performClick);
        View overlay = content.findViewWithTag("ambient_sleep_overlay");
        assertNotNull(overlay);
        assertTrue("Sleep overlay must dim the app window",
                main.getWindow().getAttributes().screenBrightness <= 0.05f);
        assertNotNull(((ViewGroup) overlay).findViewWithTag("ambient_sleep_time"));
        View wake = ((ViewGroup) overlay).findViewWithTag("ambient_wake_button");
        assertNotNull(wake);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(wake::performClick);
        assertNull(content.findViewWithTag("ambient_sleep_overlay"));
        assertEquals("Sleep mode must restore the previous brightness", oldBrightness,
                main.getWindow().getAttributes().screenBrightness, 0.001f);
    }
}
