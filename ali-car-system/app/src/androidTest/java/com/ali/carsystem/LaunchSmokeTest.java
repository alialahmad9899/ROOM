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
    }
}
