package com.ali.carsystem;

import android.view.View;
import android.view.ViewGroup;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
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
        assertNotNull(content.findViewWithTag("navigation_free_mode"));
        assertNotNull(content.findViewWithTag("navigation_guided_mode"));
        assertNotNull(content.findViewWithTag("navigation_route_choices"));
        assertNotNull(content.findViewWithTag("navigation_start_background"));
        assertNotNull(content.findViewWithTag("navigation_stop_background"));
        View mapView=content.findViewWithTag("navigation_offline_map");
        assertNotNull("Offline vector map view",mapView);
        assertTrue("MapView must use the explicit offline placeholder or the MapsForge provider",
                mapView instanceof org.osmdroid.views.MapView);
        assertFalse("Do not create osmdroid's default MapTileProviderBasic/MapTileApproximater threads",
                ((org.osmdroid.views.MapView)mapView).getTileProvider()
                        instanceof org.osmdroid.tileprovider.MapTileProviderBasic);
        assertNotNull("Save selected destination",content.findViewWithTag("navigation_save_destination"));
        assertNotNull("Saved and recent destinations",content.findViewWithTag("navigation_saved_places"));
        assertNotNull("Save/reopen parking position",content.findViewWithTag("navigation_parking"));
        assertNotNull("Automatic day/night map control",content.findViewWithTag("navigation_night_mode"));
        assertNotNull("Map night overlay",content.findViewWithTag("navigation_night_overlay"));
    }

    @Test(timeout = 240000)
    public void bundledOfflineMapsFinishInstallingAndActivateTheTileProvider() {
        NavigationActivity screen=activity.getActivity();
        long deadline=SystemClock.elapsedRealtime()+180000L;
        boolean[] state=new boolean[4];
        String[] status={""};
        while(SystemClock.elapsedRealtime()<deadline){
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                state[0]=screen.areOfflineAssetsReadyForTest();
                state[1]=screen.isOfflineTileProviderReadyForTest();
                state[2]=screen.isOfflineTileGeneratedForTest();
                state[3]=screen.mapCameraInsideOfflineCoverageForTest();
                status[0]=screen.offlineStatusForTest();
            });
            if(state[0]&&state[1]&&state[2]&&state[3])break;
            SystemClock.sleep(500L);
        }
        assertTrue("Syria map, POI database and BRouter files must be installed within 180 seconds. Status: "+status[0],
                state[0]);
        assertTrue("MapsForge tile provider must be created after the offline package is installed. Status: "+status[0],
                state[1]);
        assertTrue("The visible map status must show a ready Syria map. Status: "+status[0],
                status[0].contains("الخريطة: جاهزة"));
        assertTrue("The visible map status must show the POI database as ready. Status: "+status[0],
                status[0].contains("الأماكن: جاهزة"));
        assertTrue("The visible map status must show offline routing as ready. Status: "+status[0],
                status[0].contains("توجيه دون إنترنت: جاهز"));
        assertTrue("Damascus must lie inside the installed Syria vector map bounds: "+screen.offlineCoverageForTest(),
                screen.isCoordinateInsideOfflineCoverage(33.5138d,36.2765d));
        assertTrue("The actual MapsForge provider must be installed on the visible MapView",
                screen.isOfflineTileProviderReadyForTest());
        assertTrue("MapsForge must generate a real vector tile around Damascus. Status: "+status[0]
                +" Bounds: "+screen.offlineCoverageForTest(),
                screen.isOfflineTileGeneratedForTest());
        assertTrue("The default map camera must open inside available offline map coverage",
                screen.mapCameraInsideOfflineCoverageForTest());
        assertTrue("The visible status must only say ready after MapsForge generated an actual tile",
                status[0].contains("عرض الخريطة: جاهز • تم توليد بلاطة فعلية"));
    }

    @Test public void canSwitchBetweenFreeMapAndGuidedDrivingWithoutGps(){
        NavigationActivity screen=activity.getActivity();
        View content=screen.findViewById(android.R.id.content);
        View free=content.findViewWithTag("navigation_free_mode");
        View guided=content.findViewWithTag("navigation_guided_mode");
        View maneuver=content.findViewWithTag("navigation_maneuver");
        assertNotNull(free);assertNotNull(guided);assertNotNull(maneuver);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->free.performClick());
        assertEquals(View.GONE,maneuver.getVisibility());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->guided.performClick());
        assertEquals(View.VISIBLE,maneuver.getVisibility());
    }
}
