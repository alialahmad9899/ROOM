package com.ali.carsystem;

import android.view.View;
import android.view.ViewGroup;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class LaunchSmokeTest {
    @Rule
    public ActivityTestRule<MainActivity> activity =
            new ActivityTestRule<>(MainActivity.class);

    @Test public void dashboardActivityStartsAndCreatesItsContentTree() {
        MainActivity main = activity.getActivity();
        assertNotNull("MainActivity must be created", main);
        assertFalse("MainActivity must remain alive after launch", main.isFinishing());
        View content = main.findViewById(android.R.id.content);
        assertNotNull("Activity content root must exist", content);
        assertTrue("Dashboard must install a root view", content instanceof ViewGroup);
        assertTrue("Dashboard content must contain its ViewPager",
                ((ViewGroup) content).getChildCount() > 0);
        View hud = content.findViewWithTag("dashboard_navigation_hud");
        assertNotNull("Floating guidance HUD must be layered over the dashboard wallpaper", hud);
        assertNotNull("HUD must render a vector turn arrow",
                content.findViewWithTag("navigation_hud_arrow"));
        assertNotNull("HUD must expose a numeric turn distance",
                content.findViewWithTag("navigation_hud_distance"));
        assertNotNull("HUD must expose the next maneuver instruction",
                content.findViewWithTag("navigation_hud_instruction"));
        assertEquals("HUD remains hidden when no route is active", View.GONE, hud.getVisibility());
    }
}
