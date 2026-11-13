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
public final class MusicActivitySmokeTest {
    @Rule public ActivityTestRule<MusicActivity> activity=new ActivityTestRule<>(MusicActivity.class);
    @Test public void musicScreenStartsAndInstallsItsLayout(){
        MusicActivity screen=activity.getActivity();
        assertNotNull(screen);
        assertFalse(screen.isFinishing());
        View content=screen.findViewById(android.R.id.content);
        assertTrue(content instanceof ViewGroup);
        assertTrue(((ViewGroup)content).getChildCount()>0);
    }
}
