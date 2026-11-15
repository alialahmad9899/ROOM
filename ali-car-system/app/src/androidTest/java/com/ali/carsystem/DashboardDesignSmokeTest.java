package com.ali.carsystem;

import android.view.View;
import android.view.ViewGroup;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DashboardDesignSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity=
            new ActivityTestRule<>(MainActivity.class);

    @Test public void referenceCockpitAndSwipePageAreInstalled(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MainActivity main=activity.getActivity();
            View content=main.findViewById(android.R.id.content);
            assertTrue(content instanceof ViewGroup);
            assertNotNull(content.findViewWithTag("dashboard_cockpit_body"));
            assertNotNull(content.findViewWithTag("dashboard_media_bar"));
            assertNotNull(content.findViewWithTag("dashboard_trip_top"));
            assertNotNull(content.findViewWithTag("dashboard_speed_gauge"));
            assertNotNull(content.findViewWithTag("dashboard_four_way_swipe_surface"));
            assertNotNull(content.findViewWithTag("dashboard_analog_clock"));
            assertNotNull(content.findViewWithTag("dashboard_digital_clock"));
            View dock=content.findViewWithTag("dashboard_bottom_dock");
            assertNotNull(dock);
            assertEquals(5,((ViewGroup)dock).getChildCount());
            androidx.viewpager.widget.ViewPager pager=
                    (androidx.viewpager.widget.ViewPager)content.findViewWithTag("dashboard_view_pager");
            assertNotNull(pager);
            assertEquals(2,pager.getAdapter().getCount());
            View speed=content.findViewWithTag("dashboard_speed_gauge");
            View clock=content.findViewWithTag("dashboard_analog_clock");
            assertNotNull(speed);assertNotNull(clock);
            assertNull("Speedometer must not be wrapped in a card",
                    ((android.widget.LinearLayout)speed.getParent()).getBackground());
            assertNull("Clock must not be wrapped in a card",
                    ((android.widget.LinearLayout)clock.getParent()).getBackground());
            assertNotNull("YouTube Lite dock entry is present",content.findViewWithTag("dashboard_bottom_dock"));
            pager.setCurrentItem(1,false);
            assertNotNull(content.findViewWithTag("dashboard_secondary_page"));
            assertNotNull(content.findViewWithTag("dashboard_secondary_shortcuts"));
            assertNotNull("Trip and odometer shortcuts remain on the secondary drive-centre page",
                    content.findViewWithTag("dashboard_secondary_shortcuts"));
            pager.setCurrentItem(0,false);
        });
    }
}
