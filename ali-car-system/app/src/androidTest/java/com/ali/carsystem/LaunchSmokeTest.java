package com.ali.carsystem;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
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

        Intent active = new Intent(NavigationGuidanceService.ACTION_STATE);
        active.setPackage(main.getPackageName());
        active.putExtra("guidance", "بعد 100 متر انعطف يميناً");
        active.putExtra("active", true);
        active.putExtra("accuracy_meters", 4f);
        active.putExtra(NavigationGuidanceService.EXTRA_DISTANCE_TO_TURN_METERS, 100f);
        active.putExtra(NavigationGuidanceService.EXTRA_REMAINING_ROUTE_METERS, 5200f);
        active.putExtra(NavigationGuidanceService.EXTRA_ETA_MINUTES, 8L);
        active.putExtra(NavigationGuidanceService.EXTRA_MANEUVER_TEXT, "انعطف يميناً");
        active.putExtra(NavigationGuidanceService.EXTRA_SPEED_KMH, 62f);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> main.sendBroadcast(active));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals("Active route must reveal the floating HUD", View.VISIBLE, hud.getVisibility());
        TextView distance = content.findViewWithTag("navigation_hud_distance");
        assertEquals("100 م", distance.getText().toString());
        TextView instruction = content.findViewWithTag("navigation_hud_instruction");
        assertEquals("انعطف يميناً", instruction.getText().toString());
        TextView details = content.findViewWithTag("navigation_hud_details");
        assertTrue("Route details include remaining distance",
                details.getText().toString().contains("متبقي 5.2 كم"));
        assertTrue("Route details include live speed",
                details.getText().toString().contains("سرعة 62 كم/س"));

        Intent stopped = new Intent(NavigationGuidanceService.ACTION_STATE);
        stopped.setPackage(main.getPackageName());
        stopped.putExtra("active", false);
        main.sendBroadcast(stopped);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("Stopping guidance must hide the HUD", View.GONE, hud.getVisibility());
    }
}
