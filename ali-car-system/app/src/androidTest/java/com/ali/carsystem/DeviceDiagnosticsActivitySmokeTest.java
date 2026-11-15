package com.ali.carsystem;

import android.content.ClipboardManager;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DeviceDiagnosticsActivitySmokeTest {
    @Rule public ActivityTestRule<DeviceDiagnosticsActivity> activity=
            new ActivityTestRule<>(DeviceDiagnosticsActivity.class);

    @Test public void reportsCoreDeviceServicesAndCanRefreshAndCopy() {
        DeviceDiagnosticsActivity screen=activity.getActivity();
        assertNotNull(screen.findViewById(android.R.id.content).findViewWithTag("device_diagnostics_title"));
        for(String tag:new String[]{"device_diagnostics_android","device_diagnostics_gps",
                "device_diagnostics_bluetooth","device_diagnostics_network","device_diagnostics_audio",
                "device_diagnostics_memory","device_diagnostics_storage","device_diagnostics_battery",
                "device_diagnostics_offline_maps","device_diagnostics_browser"}) {
            assertNotNull("Missing diagnostics row: "+tag,
                    screen.findViewById(android.R.id.content).findViewWithTag(tag));
        }
        View refresh=screen.findViewById(android.R.id.content).findViewWithTag("device_diagnostics_refresh");
        View copy=screen.findViewById(android.R.id.content).findViewWithTag("device_diagnostics_copy");
        assertNotNull(refresh);assertNotNull(copy);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(refresh::performClick);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(copy::performClick);
        ClipboardManager clipboard=(ClipboardManager)screen.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        assertNotNull("Diagnostics report must be copied to clipboard",clipboard.getPrimaryClip());
    }
}
