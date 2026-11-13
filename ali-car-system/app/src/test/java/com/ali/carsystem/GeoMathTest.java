package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeoMathTest {
    @Test public void damascusToDamascusIsZero() {
        assertEquals(0,GeoMath.distanceMeters(33.5138,36.2765,33.5138,36.2765),0.001);
    }
    @Test public void oneDegreeLatitudeIsAbout111Km() {
        assertEquals(111195,GeoMath.distanceMeters(0,0,1,0),250);
    }
    @Test public void calculatesSpeed() {
        assertEquals(36,GeoMath.speedKmh(100,10000),0.0001);
    }
    @Test public void rejectsBadElapsedTime() {
        assertEquals(0,GeoMath.speedKmh(100,0),0.0001);
    }
    @Test public void rejectsStaleGps() {
        assertFalse(GeoMath.isUsableFix(1000,200000,120000,5));
    }
    @Test public void acceptsMonotonicFreshFix() {
        assertTrue(GeoMath.isFreshElapsedFix(5_000_000_000L,6_000_000_000L,2_000));
    }
    @Test public void rejectsFutureMonotonicFix() {
        assertFalse(GeoMath.isFreshElapsedFix(7_000_000_000L,6_000_000_000L,2_000));
    }
    @Test public void rejectsFixBeyondNavigationAccuracyCeiling() {
        assertFalse(GeoMath.isAccuracyUsable(350f,250f));
        assertTrue(GeoMath.isAccuracyUsable(75f,250f));
    }
}
