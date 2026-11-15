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

    @Test public void dashboardMatchesReferenceMediaRoadClockAndDockLayout() {
        MainActivity main=activity.getActivity();
        ViewGroup content=(ViewGroup)main.findViewById(android.R.id.content);
        View media=content.findViewWithTag("dashboard_media_bar");
        View digital=content.findViewWithTag("dashboard_digital_clock");
        View analog=content.findViewWithTag("dashboard_analog_clock");
        View hud=content.findViewWithTag("dashboard_navigation_hud");
        View dock=content.findViewWithTag("dashboard_bottom_dock");
        assertNotNull("Left radio/media panel must be present",media);
        assertNotNull("Right digital clock must be present",digital);
        assertNotNull("Right analog clock must be present",analog);
        assertNotNull("Compact navigation HUD must be present",hud);
        assertNotNull("Five-button bottom dock must be present",dock);
        assertEquals(5,((ViewGroup)dock).getChildCount());
        int[] mediaPosition=new int[2],clockPosition=new int[2];
        media.getLocationOnScreen(mediaPosition);
        analog.getLocationOnScreen(clockPosition);
        assertTrue("Media panel must be left of the clock panel",
                mediaPosition[0]<clockPosition[0]);
        assertEquals("Analog clock keeps the reference-sized dial",
                (int)(174*main.getResources().getDisplayMetrics().density+.5f),
                analog.getLayoutParams().width);
        assertEquals("HUD stays hidden until an active route is received",View.GONE,hud.getVisibility());
        assertTrue("HUD is a compact row beneath the media panel",
                hud.getParent() instanceof android.widget.LinearLayout);
        androidx.viewpager.widget.ViewPager pager=
                (androidx.viewpager.widget.ViewPager)content.findViewWithTag("dashboard_view_pager");
        assertNotNull(pager);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> pager.setCurrentItem(1,false));
        assertNotNull("Speedometer is retained on the secondary drive-centre page",
                content.findViewWithTag("dashboard_speed_gauge"));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> pager.setCurrentItem(0,false));
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
