package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LocationFixPolicyTest {
    @Test public void acceptsRecentAccurateFix() {
        assertTrue(LocationFixPolicy.isUsable(33.5,36.2,true,8f,
                9_000_000_000L,10_000_000_000L,1_000L,2_000L,15_000L,100f));
    }
    @Test public void rejectsCachedFixOlderThanFifteenSeconds() {
        assertFalse(LocationFixPolicy.isUsable(33.5,36.2,true,8f,
                1_000_000_000L,20_000_000_000L,1_000L,20_000L,15_000L,100f));
    }
    @Test public void rejectsFutureDatedAndOutOfOrderFixes() {
        assertFalse(LocationFixPolicy.isFresh(11_000_000_000L,1_000L,
                10_000_000_000L,2_000L,15_000L));
        assertFalse(LocationFixPolicy.isNewer(9_000_000_000L,2_000L,
                10_000_000_000L,1_000L));
    }
    @Test public void rejectsInvalidCoordinatesAndInaccurateFixes() {
        assertFalse(LocationFixPolicy.isUsable(91,36,true,8f,9_000_000_000L,
                10_000_000_000L,1_000L,2_000L,15_000L,100f));
        assertFalse(LocationFixPolicy.isUsable(33.5,36.2,true,250f,9_000_000_000L,
                10_000_000_000L,1_000L,2_000L,15_000L,100f));
        assertFalse(LocationFixPolicy.isUsable(Double.NaN,36.2,true,8f,9_000_000_000L,
                10_000_000_000L,1_000L,2_000L,15_000L,100f));
    }
    @Test public void usesWallClockFallbackOnlyWhenMonotonicTimestampMissing() {
        assertTrue(LocationFixPolicy.isFresh(0L,5_000L,0L,10_000L,5_000L));
        assertFalse(LocationFixPolicy.isFresh(0L,11_000L,0L,10_000L,5_000L));
    }
    @Test public void rejectsImpossibleSpeedValues() {
        assertEquals(36f,LocationFixPolicy.speedKmh(10f),0.01f);
        assertEquals(0f,LocationFixPolicy.speedKmh(Float.NaN),0f);
        assertEquals(0f,LocationFixPolicy.speedKmh(90f),0f);
    }
}
