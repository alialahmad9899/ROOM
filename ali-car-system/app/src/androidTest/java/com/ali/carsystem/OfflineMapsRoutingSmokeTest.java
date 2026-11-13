package com.ali.carsystem;

import android.content.Context;
import android.location.Location;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Exercises the packaged Syria map, local POI index and real BRouter road engine.
 * The route test deliberately uses two fixed Syrian city coordinates so it can
 * run with network access disabled and does not depend on emulator GPS hardware.
 */
@RunWith(AndroidJUnit4.class)
public final class OfflineMapsRoutingSmokeTest {

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private void preparePackage() throws Exception {
        OfflineMapsManager.ensureBundledAssets(context());
        assertNotNull("Syria vector map must be installed", OfflineMapsManager.getMapFile(context()));
        assertNotNull("Syria POI database must be installed", OfflineMapsManager.getPlacesDatabase(context()));
        assertTrue("All BRouter road tiles and profile files must be installed",
                OfflineMapsManager.hasOfflineRouting(context()));
    }

    @Test
    public void bundledSyriaMapPlacesAndRoadTilesArePresent() throws Exception {
        preparePackage();

        File map = OfflineMapsManager.getMapFile(context());
        File places = OfflineMapsManager.getPlacesDatabase(context());
        assertTrue(map.length() > 50L * 1024L * 1024L);
        assertTrue(places.length() > 1024L * 1024L);

        List<OfflinePlacesDb.Place> homs = OfflinePlacesDb.search(context(), "حمص", 8);
        assertFalse("Offline database must find Homs without a network geocoder", homs.isEmpty());

        List<OfflinePlacesDb.Place> pharmacies = OfflinePlacesDb.search(context(), "صيدلية", 8);
        assertFalse("Offline POI categories must find pharmacies", pharmacies.isEmpty());
    }

    @Test(timeout = 90000)
    public void brouterCalculatesDrivingRouteFromHomsToDamascusOffline() throws Exception {
        preparePackage();

        Location start = new Location("test-offline-gps");
        start.setLatitude(34.7308d);
        start.setLongitude(36.7094d);
        start.setAccuracy(8f);
        start.setTime(System.currentTimeMillis());

        GeoPoint damascus = new GeoPoint(33.5138d, 36.2765d);
        JSONObject response = OfflineRoutingEngine.requestRoutes(context(), start, damascus);

        assertNotNull("BRouter must return a local result without network access", response);
        assertEquals("Ok", response.optString("code"));
        assertEquals("offline-brouter", response.optString("_ali_provider"));

        JSONArray routes = response.optJSONArray("routes");
        assertNotNull(routes);
        assertTrue("A driving route must be returned", routes.length() > 0);

        JSONObject route = routes.getJSONObject(0);
        assertTrue("Route distance must be positive", route.optDouble("distance", 0d) > 0d);
        JSONArray legs = route.optJSONArray("legs");
        assertNotNull(legs);
        assertTrue(legs.length() > 0);

        JSONArray steps = legs.getJSONObject(0).optJSONArray("steps");
        assertNotNull("Offline route must include turn-by-turn instructions", steps);
        assertTrue("Offline route must include at least one maneuver", steps.length() > 0);
    }
}
