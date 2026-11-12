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
}
