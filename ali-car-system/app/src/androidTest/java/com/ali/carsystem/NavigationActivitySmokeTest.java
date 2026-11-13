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
public final class NavigationActivitySmokeTest {
    @Rule public ActivityTestRule<NavigationActivity> activity=new ActivityTestRule<>(NavigationActivity.class);

    @Test public void navigationScreenStartsWithoutARecentGpsFix(){
        NavigationActivity screen=activity.getActivity();
        assertNotNull(screen);
        assertFalse(screen.isFinishing());
        View content=screen.findViewById(android.R.id.content);
        assertTrue(content instanceof ViewGroup);
        assertTrue(((ViewGroup)content).getChildCount()>0);
        assertNotNull(content.findViewWithTag("navigation_free_mode"));
        assertNotNull(content.findViewWithTag("navigation_guided_mode"));
        assertNotNull(content.findViewWithTag("navigation_route_choices"));
        assertNotNull(content.findViewWithTag("navigation_start_background"));
        assertNotNull(content.findViewWithTag("navigation_stop_background"));
    }

    @Test public void canSwitchBetweenFreeMapAndGuidedDrivingWithoutGps(){
        NavigationActivity screen=activity.getActivity();
        View content=screen.findViewById(android.R.id.content);
        View free=content.findViewWithTag("navigation_free_mode");
        View guided=content.findViewWithTag("navigation_guided_mode");
        View maneuver=content.findViewWithTag("navigation_maneuver");
        assertNotNull(free);assertNotNull(guided);assertNotNull(maneuver);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->free.performClick());
        assertEquals(View.GONE,maneuver.getVisibility());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->guided.performClick());
        assertEquals(View.VISIBLE,maneuver.getVisibility());
    }
}
