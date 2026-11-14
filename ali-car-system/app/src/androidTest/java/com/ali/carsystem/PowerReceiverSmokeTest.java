package com.ali.carsystem;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class PowerReceiverSmokeTest {
    @Rule public ActivityTestRule<MusicActivity> activity =
            new ActivityTestRule<>(MusicActivity.class);

    @Test public void pluggingInChargerDoesNotReplaceMusicActivityWithLauncher() {
        Context context = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getTargetContext();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        assertNotNull(manager);
        String before = manager.getRunningTasks(1).get(0).topActivity.getClassName();
        assertEquals(MusicActivity.class.getName(), before);

        new PowerReceiver().onReceive(context,
                new Intent(Intent.ACTION_POWER_CONNECTED));

        String after = manager.getRunningTasks(1).get(0).topActivity.getClassName();
        assertEquals("Charging must not bring MainActivity over the current screen", before, after);
        assertEquals(MusicActivity.class.getName(), after);
    }
}
