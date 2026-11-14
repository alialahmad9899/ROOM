package com.ali.carsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class BackgroundNavigationServiceSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity =
            new ActivityTestRule<>(MainActivity.class);

    @Test public void serviceLoadsPersistedRouteFromFileAndAcknowledgesReady() throws Exception {
        Context context = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getTargetContext();
        File directory = new File(context.getFilesDir(), "navigation");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File route = new File(directory, "service-smoke-route.json");
        String json = "{\"points\":[[33.5000,36.2700],[33.5010,36.2700],[33.5020,36.2700]],"
                + "\"maneuvers\":[{\"lat\":33.5010,\"lon\":36.2700,\"text\":\"انعطف يميناً\"}],"
                + "\"destination_name\":\"وجهة اختبار\",\"destination_lat\":33.5020,"
                + "\"destination_lon\":36.2700,\"duration_seconds\":120}";
        try (FileOutputStream output = new FileOutputStream(route, false)) {
            output.write(json.getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        }

        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean active = new AtomicBoolean(false);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (!NavigationGuidanceService.ACTION_STATE.equals(intent.getAction())) return;
                if (intent.getBooleanExtra("ready", false)) {
                    active.set(intent.getBooleanExtra("active", false));
                    ready.countDown();
                }
            }
        };
        context.registerReceiver(receiver,
                new IntentFilter(NavigationGuidanceService.ACTION_STATE));
        try {
            Intent start = new Intent(context, NavigationGuidanceService.class);
            start.setAction(NavigationGuidanceService.ACTION_START);
            start.putExtra(NavigationGuidanceService.EXTRA_ROUTE_FILE, route.getAbsolutePath());
            start.putExtra(NavigationGuidanceService.EXTRA_DEST_NAME, "وجهة اختبار");
            start.putExtra(NavigationGuidanceService.EXTRA_DEST_LAT, 33.5020);
            start.putExtra(NavigationGuidanceService.EXTRA_DEST_LON, 36.2700);
            context.startService(start);

            assertTrue("Service did not acknowledge a parsed route within 15 seconds",
                    ready.await(15, TimeUnit.SECONDS));
            assertTrue("Service must acknowledge active state only after route parsing", active.get());
            assertTrue("Persisted route must remain available while navigation is active",
                    NavigationGuidanceService.isActive(context));
        } finally {
            context.unregisterReceiver(receiver);
            Intent stop = new Intent(context, NavigationGuidanceService.class);
            stop.setAction(NavigationGuidanceService.ACTION_STOP);
            context.startService(stop);
        }
    }
}
