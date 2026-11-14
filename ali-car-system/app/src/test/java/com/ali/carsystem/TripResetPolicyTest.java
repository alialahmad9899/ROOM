package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TripResetPolicyTest {
    @Test public void resetClearsTripAndMaximumSpeedButKeepsLifetimeOdometer() {
        TripResetPolicy.State state=TripResetPolicy.afterReset(123456L);
        assertEquals(0L,state.tripMeters);
        assertEquals(123456L,state.odometerMeters);
        assertEquals(0f,state.maxSpeedKmh,0.001f);
    }
    @Test public void resetPreservesAZeroOdometer() {
        TripResetPolicy.State state=TripResetPolicy.afterReset(0L);
        assertEquals(0L,state.tripMeters);
        assertEquals(0L,state.odometerMeters);
        assertEquals(0f,state.maxSpeedKmh,0.001f);
    }
}
