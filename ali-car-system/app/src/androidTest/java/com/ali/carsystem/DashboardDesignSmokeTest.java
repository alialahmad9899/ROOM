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
            assertNotNull(content.findViewWithTag("dashboard_speed_gauge"));
            assertNotNull(content.findViewWithTag("dashboard_analog_clock"));
            View dock=content.findViewWithTag("dashboard_bottom_dock");
            assertNotNull(dock);
            assertEquals(5,((ViewGroup)dock).getChildCount());
            androidx.viewpager.widget.ViewPager pager=
                    (androidx.viewpager.widget.ViewPager)content.findViewWithTag("dashboard_view_pager");
            assertNotNull(pager);
            assertEquals(2,pager.getAdapter().getCount());
            pager.setCurrentItem(1,false);
            assertNotNull(content.findViewWithTag("dashboard_secondary_page"));
            assertNotNull(content.findViewWithTag("dashboard_secondary_shortcuts"));
            pager.setCurrentItem(0,false);
        });
    }
}
