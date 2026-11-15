package com.ali.carsystem;

import android.location.Location;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class NavigationLocationRegressionTest {
    @Rule public ActivityTestRule<NavigationActivity> activity =
            new ActivityTestRule<>(NavigationActivity.class);

    private Location fix(String provider,long ageMs,float accuracy,double lat,double lon) {
        Location location=new Location(provider);
        location.setLatitude(lat);
        location.setLongitude(lon);
        location.setAccuracy(accuracy);
        location.setTime(System.currentTimeMillis()-ageMs);
        location.setElapsedRealtimeNanos(Math.max(1L,
                SystemClock.elapsedRealtimeNanos()-ageMs*1000000L));
        return location;
    }

    @Test public void staleAndOutOfOrderFusedFixesCannotReplaceLiveGpsPosition() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NavigationActivity screen=activity.getActivity();
            Location live=fix("gps",0L,8f,33.5138,36.2765);
            assertTrue("A fresh accurate GPS fix must be accepted",
                    screen.acceptsLocationForTest(live));
            screen.onLocationChanged(live);
            assertTrue("A valid live GPS fix must create the blue current-position marker",
                    screen.hasCurrentLocationMarkerForTest());

            Location stale=fix("fused",5*60*1000L,5f,33.5200,36.2800);
            assertFalse("A cached five-minute-old Fused fix must be rejected",
                    screen.acceptsLocationForTest(stale));

            Location outOfOrder=fix("gps",15000L,6f,33.5100,36.2700);
            assertFalse("An older callback must not move the marker backwards",
                    screen.acceptsLocationForTest(outOfOrder));

            Location inaccurate=fix("network",0L,1500f,33.5138,36.2765);
            assertFalse("An unusably inaccurate fix must not become the car position",
                    screen.acceptsLocationForTest(inaccurate));

            Location newer=fix("gps",0L,12f,33.5142,36.2770);
            assertTrue("A newer accurate GPS fix must continue to be accepted",
                    screen.acceptsLocationForTest(newer));
        });
    }
}
