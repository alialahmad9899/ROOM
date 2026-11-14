package com.ali.carsystem;

/** Defines which trip statistics reset while preserving the lifetime odometer. */
final class TripResetPolicy {
    static final class State {
        final long tripMeters, odometerMeters;
        final float maxSpeedKmh;
        State(long tripMeters,long odometerMeters,float maxSpeedKmh) {
            this.tripMeters=tripMeters;
            this.odometerMeters=odometerMeters;
            this.maxSpeedKmh=maxSpeedKmh;
        }
    }
    private TripResetPolicy() { }
    static State afterReset(long odometerMeters) {
        return new State(0L,odometerMeters,0f);
    }
}
