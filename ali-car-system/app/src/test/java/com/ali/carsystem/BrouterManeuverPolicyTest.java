package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrouterManeuverPolicyTest {
    @Test public void mapsAllLeftRightAndKeepCommands() {
        for (int code : new int[]{2,3,4,17}) assertEquals("left",BrouterManeuverPolicy.modifier(code));
        for (int code : new int[]{5,6,7,18}) assertEquals("right",BrouterManeuverPolicy.modifier(code));
        assertEquals("keep-left",BrouterManeuverPolicy.modifier(8));
        assertEquals("keep-right",BrouterManeuverPolicy.modifier(9));
        assertEquals("exit",BrouterManeuverPolicy.type(17));
        assertEquals("exit",BrouterManeuverPolicy.type(18));
    }
    @Test public void mapsAllUTurnAndRoundaboutCommands() {
        for (int code : new int[]{10,11,15}) assertEquals("uturn",BrouterManeuverPolicy.modifier(code));
        assertEquals("roundabout",BrouterManeuverPolicy.type(13));
        assertEquals("roundabout",BrouterManeuverPolicy.type(14));
    }
    @Test public void filtersOffRouteAndPreservesContinueAndBeeline() {
        assertFalse(BrouterManeuverPolicy.isNavigable(12));
        assertTrue(BrouterManeuverPolicy.isNavigable(16));
        assertEquals("continue",BrouterManeuverPolicy.type(1));
        assertEquals("continue",BrouterManeuverPolicy.type(16));
    }
    @Test public void doesNotTreatOffRouteAsATurn() {
        assertEquals("straight",BrouterManeuverPolicy.modifier(12));
    }
}
