package com.ali.carsystem;

import android.app.Activity;
import android.app.Instrumentation;
import android.media.AudioManager;
import android.widget.SeekBar;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.action.ViewActions.click;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DashboardDockNavigationSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity =
            new ActivityTestRule<>(MainActivity.class);

    private void waitForWelcomeToClose(MainActivity main) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        long start = android.os.SystemClock.elapsedRealtime();
        long deadline = start + 9000L;
        boolean welcomeWasSeen = false;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync();
            ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
            boolean visible = content.findViewWithTag("welcome_splash_overlay") != null;
            if (visible) welcomeWasSeen = true;
            if (welcomeWasSeen && !visible) return;
            // showWelcome is posted shortly after onCreate; do not mistake its
            // scheduled-but-not-yet-visible interval for a completed dismissal.
            if (!welcomeWasSeen && android.os.SystemClock.elapsedRealtime() - start > 1200L) {
                fail("Welcome overlay was never displayed");
            }
            android.os.SystemClock.sleep(100L);
        }
        fail("Welcome overlay did not close before testing dashboard actions");
    }

    private void clickDockIconAndExpectActivity(int index, Class<? extends Activity> target) {
        MainActivity main = activity.getActivity();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
        View icon = content.findViewWithTag("dashboard_dock_icon_" + index);
        assertNotNull("Dock icon " + index + " must exist", icon);
        assertTrue("Dock icon " + index + " must be clickable", icon.isClickable());

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(target.getName(), null, false);
        instrumentation.runOnMainSync(() -> assertTrue(
                "Dock icon " + index + " click must be dispatched", icon.performClick()));
        Activity opened = instrumentation.waitForMonitorWithTimeout(monitor, 10000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("Dock icon " + index + " must open " + target.getSimpleName(), opened);
        instrumentation.runOnMainSync(opened::finish);
        instrumentation.waitForIdleSync();
    }

    @Test public void navigationMusicAndYoutubeDockIconsLaunchTheirRealActivities() {
        MainActivity main = activity.getActivity();
        waitForWelcomeToClose(main);
        clickDockIconAndExpectActivity(0, NavigationActivity.class);
        clickDockIconAndExpectActivity(2, MusicActivity.class);
        clickDockIconAndExpectActivity(4, YouTubeSearchActivity.class);
    }

    @Test public void appsTileShowsTheAppsMenuAndItsYoutubeItemUsesTheInternalPlayer() {
        MainActivity main = activity.getActivity();
        waitForWelcomeToClose(main);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
        View appsIcon = content.findViewWithTag("dashboard_dock_icon_3");
        assertNotNull("Apps dock icon must exist", appsIcon);
        assertTrue(appsIcon.isClickable());
        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(YouTubeSearchActivity.class.getName(), null, false);
        instrumentation.runOnMainSync(() -> appsIcon.performClick());
        onView(withText("تطبيقات السيارة")).check(matches(isDisplayed()));
        onView(withText("YouTube داخل ALI CAR")).perform(click());
        Activity opened = instrumentation.waitForMonitorWithTimeout(monitor, 10000L);
        instrumentation.removeMonitor(monitor);
        assertNotNull("Apps-menu YouTube item must open the internal search screen", opened);
        instrumentation.runOnMainSync(opened::finish);
        instrumentation.waitForIdleSync();
    }

    @Test public void volumeControlsArePresentAndInteractive() {
        MainActivity main = activity.getActivity();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        waitForWelcomeToClose(main);
        ViewGroup content = (ViewGroup) main.findViewById(android.R.id.content);
        SeekBar seek = (SeekBar) content.findViewWithTag("dashboard_volume_seekbar");
        View down = content.findViewWithTag("dashboard_volume_down");
        View up = content.findViewWithTag("dashboard_volume_up");
        assertNotNull("Music volume seek bar must be visible on dashboard", seek);
        assertNotNull("Volume down control must be present", down);
        assertNotNull("Volume up control must be present", up);
        assertTrue(seek.isEnabled());
        assertTrue(down.isClickable());
        assertTrue(up.isClickable());

        AudioManager audio = (AudioManager) main.getSystemService(MainActivity.AUDIO_SERVICE);
        int original = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int expected;
        if (original > 0) {
            expected = original - 1;
            instrumentation.runOnMainSync(down::performClick);
            assertEquals("Volume-down button must change the media stream", expected,
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            instrumentation.runOnMainSync(up::performClick);
        } else {
            expected = Math.min(seek.getMax(), original + 1);
            instrumentation.runOnMainSync(up::performClick);
            assertEquals("Volume-up button must change the media stream", expected,
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            instrumentation.runOnMainSync(down::performClick);
        }
        assertEquals("Volume buttons must restore the original media level", original,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC));
    }
}
