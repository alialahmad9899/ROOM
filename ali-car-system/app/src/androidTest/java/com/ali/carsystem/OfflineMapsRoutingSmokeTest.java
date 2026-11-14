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

    @Test(timeout = 180000)
    public void brouterCalculatesSeveralSyrianDrivingRoutesOffline() throws Exception {
        preparePackage();
        double[][] cases = {
                {34.7308d, 36.7094d, 33.5138d, 36.2765d}, // Homs -> Damascus
                {33.5138d, 36.2765d, 32.6180d, 36.1020d}, // Damascus -> Daraa
                {34.7308d, 36.7094d, 34.8950d, 35.8866d}, // Homs -> Tartus
                {34.7308d, 36.7094d, 36.2021d, 37.1343d}  // Homs -> Aleppo
        };
        for (int index = 0; index < cases.length; index++) {
            Location start = new Location("test-offline-gps-" + index);
            start.setLatitude(cases[index][0]);
            start.setLongitude(cases[index][1]);
            start.setAccuracy(8f);
            start.setTime(System.currentTimeMillis());
            GeoPoint destination = new GeoPoint(cases[index][2], cases[index][3]);
            JSONObject response = OfflineRoutingEngine.requestRoutes(context(), start, destination);
            assertNotNull("BRouter returned null for route case " + index, response);
            assertEquals("Ok for route case " + index, "Ok", response.optString("code"));
            assertEquals("offline-brouter", response.optString("_ali_provider"));
            JSONArray routes = response.optJSONArray("routes");
            assertNotNull("Routes array missing for case " + index, routes);
            assertTrue("No route for case " + index, routes.length() > 0);
            JSONObject route = routes.getJSONObject(0);
            assertTrue("Non-positive distance for case " + index,
                    route.optDouble("distance", 0d) > 0d);
            JSONArray legs = route.optJSONArray("legs");
            assertNotNull("Legs missing for case " + index, legs);
            assertTrue("No legs for case " + index, legs.length() > 0);
            JSONArray steps = legs.getJSONObject(0).optJSONArray("steps");
            assertNotNull("Maneuvers missing for case " + index, steps);
            assertTrue("No turn-by-turn steps for case " + index, steps.length() > 0);
        }
    }
}
