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
public final class NavigationActivitySmokeTest {
    @Rule public ActivityTestRule<NavigationActivity> activity=new ActivityTestRule<>(NavigationActivity.class);
    @Test public void navigationScreenStartsWithoutARecentGpsFix(){
        NavigationActivity screen=activity.getActivity();
        assertNotNull(screen);
        assertFalse(screen.isFinishing());
        View content=screen.findViewById(android.R.id.content);
        assertTrue(content instanceof ViewGroup);
        assertTrue(((ViewGroup)content).getChildCount()>0);
    }
}
