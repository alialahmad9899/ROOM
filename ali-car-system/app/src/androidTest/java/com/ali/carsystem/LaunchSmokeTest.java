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

    private int dp(MainActivity main,int value){
        return (int)(value*main.getResources().getDisplayMetrics().density+.5f);
    }

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
        assertNotNull(content.findViewWithTag("dashboard_cockpit_body"));
        assertNotNull(content.findViewWithTag("dashboard_bottom_dock"));
        assertNotNull(content.findViewWithTag("dashboard_secondary_swipe_hint"));
        assertNotNull(content.findViewWithTag("dashboard_digital_clock"));
        androidx.viewpager.widget.ViewPager pager=
                (androidx.viewpager.widget.ViewPager)content.findViewWithTag("dashboard_view_pager");
        assertNotNull("Dashboard page container is installed",pager);
        assertEquals("Dashboard has a second swipe page",2,pager.getAdapter().getCount());
        assertNotNull("Floating guidance HUD must be layered over the dashboard wallpaper", hud);
        assertNotNull("HUD must render a vector turn arrow",
                content.findViewWithTag("navigation_hud_arrow"));
        assertNotNull("HUD must expose a numeric turn distance",
                content.findViewWithTag("navigation_hud_distance"));
        assertNotNull("HUD must expose the next maneuver instruction",
                content.findViewWithTag("navigation_hud_instruction"));
        View mediaBar=content.findViewWithTag("dashboard_media_bar");
        assertNotNull("Left media panel must be the stable HUD anchor",mediaBar);
        assertTrue("HUD is a collapsible dashboard row",hud.getParent() instanceof android.widget.LinearLayout);
        assertTrue("HUD uses compact row dimensions",hud.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams);
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
        View trip=content.findViewWithTag("dashboard_trip_top");
        assertNotNull("Trip summary must remain above navigation guidance",trip);
        assertSame("Trip, navigation and music share the top information column",
                trip.getParent(),hud.getParent());
        assertSame(trip.getParent(),mediaBar.getParent());
        android.widget.LinearLayout top=(android.widget.LinearLayout)trip.getParent();
        assertTrue("Trip summary must precede navigation guidance",
                top.indexOfChild(trip)<top.indexOfChild(hud));
        assertTrue("Navigation guidance must precede the compact music strip",
                top.indexOfChild(hud)<top.indexOfChild(mediaBar));

        int[] hudPosition=new int[2],tripPosition=new int[2],mediaPosition=new int[2],clockPosition=new int[2];
        hud.getLocationOnScreen(hudPosition);trip.getLocationOnScreen(tripPosition);
        mediaBar.getLocationOnScreen(mediaPosition);
        View clock=content.findViewWithTag("dashboard_analog_clock");
        clock.getLocationOnScreen(clockPosition);
        assertTrue("Active navigation HUD must appear below the trip summary",
                hudPosition[1]>=tripPosition[1]+trip.getHeight());
        assertTrue("Compact music strip must appear below navigation guidance",
                mediaPosition[1]>=hudPosition[1]+hud.getHeight());
        boolean hudCoversClock=hudPosition[0]<clockPosition[0]+clock.getWidth()
                &&hudPosition[0]+hud.getWidth()>clockPosition[0]
                &&hudPosition[1]<clockPosition[1]+clock.getHeight()
                &&hudPosition[1]+hud.getHeight()>clockPosition[1];
        assertFalse("Navigation HUD must not overlap the clock panel",hudCoversClock);
        assertTrue("Compact HUD must fit in its reserved row",hud.getHeight()<=dp(main,90));
        TextView distance = (TextView) content.findViewWithTag("navigation_hud_distance");
        assertEquals("100 م", distance.getText().toString());
        TextView instruction = (TextView) content.findViewWithTag("navigation_hud_instruction");
        assertEquals("انعطف يميناً", instruction.getText().toString());
        TextView details = (TextView) content.findViewWithTag("navigation_hud_details");
        assertTrue("Route details include remaining distance",
                details.getText().toString().contains("متبقي 5.2 كم"));
        assertTrue("Route details include live speed",
                details.getText().toString().contains("سرعة 62 كم/س"));

        // Stop the real foreground service rather than injecting a synthetic inactive
        // broadcast. A still-running service may legitimately republish its active state.
        Intent stopService = new Intent(main, NavigationGuidanceService.class);
        stopService.setAction(NavigationGuidanceService.ACTION_STOP);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> main.startService(stopService));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(250L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse("Stopped service must clear its persisted active route",
                NavigationGuidanceService.isActive(main));
        assertEquals("Stopping guidance must hide the HUD", View.GONE, hud.getVisibility());
    }
}
