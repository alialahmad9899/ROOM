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
public final class WelcomeStartupSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity =
            new ActivityTestRule<>(MainActivity.class);

    @Test public void firstBootWelcomeIsStaticAndDefersHeavyStartupWork() throws Exception {
        MainActivity main = activity.getActivity();
        Thread.sleep(500L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
        View welcome = content.findViewWithTag("welcome_splash_overlay");
        assertNotNull("First launch must show the welcome screen", welcome);
        assertEquals("Welcome must use the safe software renderer",
                View.LAYER_TYPE_SOFTWARE, welcome.getLayerType());
        assertFalse("GPS service must wait until welcome closes",
                main.isGpsStartedForTest());
        assertFalse("Text-to-speech must wait until welcome closes",
                main.isTtsInitializationAttemptedForTest());

        Thread.sleep(3200L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertNull("Welcome must close automatically",
                content.findViewWithTag("welcome_splash_overlay"));
        assertTrue("GPS must start after welcome closes",
                main.isGpsStartedForTest());
        assertTrue("Text-to-speech must initialize after welcome closes",
                main.isTtsInitializationAttemptedForTest());
    }
}
