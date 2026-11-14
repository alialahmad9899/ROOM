package com.ali.carsystem;

import android.view.View;
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
        int[] clockPosition=new int[2],speedPosition=new int[2];
        clock.getLocationOnScreen(clockPosition);speed.getLocationOnScreen(speedPosition);
        assertTrue("Analog clock must be physically left of the speedometer",
                clockPosition[0] < speedPosition[0]);
        assertNotNull("Dashboard must host persistent navigation guidance",
                content.findViewWithTag("navigation_guidance_banner"));
    }

    @Test public void sleepPolicySeparatesManualAndAutomaticSleep() {
        assertTrue("Manual sleep must always be available", MainActivity.shouldAllowManualSleep());
        assertFalse("Charging blocks automatic sleep",
                MainActivity.shouldAllowAutomaticSleep(true, false, false));
        assertFalse("Music blocks automatic sleep",
                MainActivity.shouldAllowAutomaticSleep(false, true, false));
        assertFalse("Active navigation blocks automatic sleep",
                MainActivity.shouldAllowAutomaticSleep(false, false, true));
        assertTrue("Automatic sleep is allowed only when all blockers are absent",
                MainActivity.shouldAllowAutomaticSleep(false, false, false));
    }

    @Test public void sleepModeDimsScreenUntilExplicitWakeButtonIsPressed() {
        MainActivity main = activity.getActivity();
        ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
        View sleep = content.findViewWithTag("dashboard_sleep_toggle");
        assertNotNull(sleep);
        final float oldBrightness = main.getWindow().getAttributes().screenBrightness;
        // The emulator may report itself as externally powered. Isolate this visual
        // test from hardware power state; the interlock is covered above.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(main::showAmbientClockForTest);
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
