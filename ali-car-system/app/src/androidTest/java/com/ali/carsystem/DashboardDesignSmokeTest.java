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
            View trip=content.findViewWithTag("dashboard_trip_top");
            View route=content.findViewWithTag("dashboard_navigation_hud");
            View music=content.findViewWithTag("dashboard_media_bar");
            assertNotNull(trip);
            assertNotNull(content.findViewWithTag("dashboard_speed_gauge"));
            assertNotNull("The live trip-distance dial is present",content.findViewWithTag("dashboard_trip_gauge"));
            assertNotNull("The theme-aware cockpit background is present",content.findViewWithTag("dashboard_thematic_background"));
            assertNotNull(content.findViewWithTag("dashboard_four_way_swipe_surface"));
            assertSame("Trip, route and music must share the top information column",trip.getParent(),route.getParent());
            assertSame(trip.getParent(),music.getParent());
            android.widget.LinearLayout top=(android.widget.LinearLayout)trip.getParent();
            assertTrue("Trip summary must be above route guidance",top.indexOfChild(trip)<top.indexOfChild(route));
            assertTrue("Route guidance must be above the music strip",top.indexOfChild(route)<top.indexOfChild(music));
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
            assertNotNull("YouTube mobile dock entry is present",content.findViewWithTag("dashboard_bottom_dock"));
            assertTrue("Settings icon is a real image resource",
                    content.findViewWithTag("dashboard_settings_button") instanceof android.widget.ImageView);
            assertTrue("Sleep icon is a real image resource",
                    content.findViewWithTag("dashboard_sleep_toggle") instanceof android.widget.ImageView);
            assertTrue("Previous media control uses a vector image",
                    content.findViewWithTag("dashboard_media_previous") instanceof android.widget.ImageView);
            assertTrue("Next media control uses a vector image",
                    content.findViewWithTag("dashboard_media_next") instanceof android.widget.ImageView);
            pager.setCurrentItem(1,false);
            assertNotNull(content.findViewWithTag("dashboard_secondary_page"));
            assertNotNull(content.findViewWithTag("dashboard_secondary_shortcuts"));
            assertNotNull("Trip and odometer shortcuts remain on the secondary drive-centre page",
                    content.findViewWithTag("dashboard_secondary_shortcuts"));
            pager.setCurrentItem(0,false);
        });
    }
}
