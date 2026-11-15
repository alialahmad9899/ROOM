package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class OffRouteReroutePolicyTest {
    @Test public void reroutesOnlyAfterSustainedAccurateOffRouteFixes() {
        OffRouteReroutePolicy policy=new OffRouteReroutePolicy();
        assertFalse(policy.shouldReroute(false,8f,1000L,false));
        assertFalse(policy.shouldReroute(false,8f,2000L,false));
        assertFalse(policy.shouldReroute(false,8f,3000L,false));
        assertFalse(policy.shouldReroute(false,8f,4000L,false));
        assertTrue(policy.shouldReroute(false,8f,6000L,false));
    }

    @Test public void ignoresPoorAccuracyAndInFlightRequests() {
        OffRouteReroutePolicy policy=new OffRouteReroutePolicy();
        assertFalse(policy.shouldReroute(false,90f,1000L,false));
        assertFalse(policy.shouldReroute(false,8f,2000L,true));
    }

    @Test public void resetsWhenVehicleReturnsToRoute() {
        OffRouteReroutePolicy policy=new OffRouteReroutePolicy();
        policy.shouldReroute(false,8f,1000L,false);
        policy.shouldReroute(false,8f,2000L,false);
        assertFalse(policy.shouldReroute(true,8f,3000L,false));
        assertFalse(policy.shouldReroute(false,8f,4000L,false));
        assertFalse(policy.shouldReroute(false,8f,5000L,false));
        assertFalse(policy.shouldReroute(false,8f,6000L,false));
        assertFalse(policy.shouldReroute(false,8f,7000L,false));
        assertTrue(policy.shouldReroute(false,8f,10000L,false));
    }

    @Test public void appliesThirtySecondRerouteCooldown() {
        OffRouteReroutePolicy policy=new OffRouteReroutePolicy();
        for(long t:new long[]{1000L,2500L,4000L,5500L})policy.shouldReroute(false,8f,t,false);
        assertTrue(policy.shouldReroute(false,8f,7000L,false));
        for(long t:new long[]{8000L,9500L,11000L,12500L,14000L}) {
            assertFalse(policy.shouldReroute(false,8f,t,false));
        }
        for(long t:new long[]{38000L,39500L,41000L,42500L})policy.shouldReroute(false,8f,t,false);
        assertTrue(policy.shouldReroute(false,8f,44000L,false));
    }
}
