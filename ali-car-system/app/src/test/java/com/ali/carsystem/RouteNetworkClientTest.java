package com.ali.carsystem;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public final class RouteNetworkClientTest {
    @Test public void triesIndependentProvidersAndRemembersPreferredFirst() {
        assertEquals(Arrays.asList("fossgis", "osrm", "valhalla"),
                RouteNetworkClient.orderedEngines("fossgis"));
        assertEquals(Arrays.asList("osrm", "fossgis", "valhalla"),
                RouteNetworkClient.orderedEngines("unknown"));
    }

    @Test public void buildsOsrmCoordinateOrderAndRequestsAlternatives() {
        String url = RouteNetworkClient.buildOsrmUrl(
                RouteNetworkClient.OSRM_BASE, 52.520008, 13.404954, 52.516275, 13.377704);
        assertTrue(url.startsWith("https://router.project-osrm.org/route/v1/driving/"));
        assertTrue(url.contains("13.404954,52.520008;13.377704,52.516275"));
        assertTrue(url.endsWith("?overview=full&geometries=geojson&steps=true&alternatives=3"));
    }

    @Test public void supportsIndependentFossgisOsrmEndpoint() {
        String url = RouteNetworkClient.buildOsrmUrl(
                RouteNetworkClient.FOSSGIS_BASE, 52.520008, 13.404954, 52.516275, 13.377704);
        assertTrue(url.startsWith("https://routing.openstreetmap.de/routed-car/route/v1/driving/"));
    }

    @Test public void buildsValhallaOsrmCompatibleAlternativeRequest() {
        String body = RouteNetworkClient.buildValhallaRequest(
                52.520008, 13.404954, 52.516275, 13.377704);
        assertTrue(body.contains("\"costing\":\"auto\""));
        assertTrue(body.contains("\"format\":\"osrm\""));
        assertTrue(body.contains("\"shape_format\":\"geojson\""));
        assertTrue(body.contains("\"alternates\":3"));
    }
}
