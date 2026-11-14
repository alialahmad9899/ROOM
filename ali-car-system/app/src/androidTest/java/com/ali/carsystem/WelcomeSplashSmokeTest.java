package com.ali.carsystem;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class WelcomeSplashSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity =
            new ActivityTestRule<>(MainActivity.class);

    @Test public void welcomeUsesSoftwareRenderingAndKeepsScreenAwakeUntilDismissed() {
        MainActivity main = activity.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            main.showWelcomeForTest();
            main.applySleepPolicyForTest();
        });

        ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
        View splash = content.findViewWithTag("welcome_splash_overlay");
        assertNotNull("Welcome overlay must be visible during startup", splash);
        assertEquals("Legacy Intel graphics use the software canvas path",
                View.LAYER_TYPE_SOFTWARE, splash.getLayerType());
        assertTrue("Welcome overlay must hold the display awake",
                main.isWelcomeScreenLockActiveForTest());
        assertTrue("FLAG_KEEP_SCREEN_ON must be set while welcome is visible",
                (main.getWindow().getAttributes().flags &
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(main::dismissWelcomeForTest);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(650L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertFalse("Welcome lock must be released after the fade-out",
                main.isWelcomeScreenLockActiveForTest());
        assertNull("Welcome overlay must be removed after dismissal",
                content.findViewWithTag("welcome_splash_overlay"));
    }
}
