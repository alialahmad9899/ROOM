package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public class SleepPolicyTest {
    @Test public void manualSleepIsNotGatedByAutomaticPolicy() {
        // Manual sleep is an explicit UI action and intentionally does not call this policy.
        assertTrue(SleepPolicy.mayEnterAutomaticSleep(false, false, false));
    }

    @Test public void automaticSleepIsBlockedWhileCharging() {
        assertFalse(SleepPolicy.mayEnterAutomaticSleep(true, false, false));
    }

    @Test public void automaticSleepIsBlockedWhileMusicPlays() {
        assertFalse(SleepPolicy.mayEnterAutomaticSleep(false, true, false));
    }

    @Test public void automaticSleepIsBlockedDuringNavigation() {
        assertFalse(SleepPolicy.mayEnterAutomaticSleep(false, false, true));
    }

    @Test public void connectivityIsPreservedForActiveMediaOrNavigation() {
        assertFalse(SleepPolicy.mayDisableConnectivity(false, true, false));
        assertFalse(SleepPolicy.mayDisableConnectivity(false, false, true));
        assertFalse(SleepPolicy.mayDisableConnectivity(true, false, false));
    }

    @Test public void connectivityCanBeDisabledOnBatteryWhenIdle() {
        assertTrue(SleepPolicy.mayDisableConnectivity(false, false, false));
    }
}
