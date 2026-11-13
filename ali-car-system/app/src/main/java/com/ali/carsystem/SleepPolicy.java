package com.ali.carsystem;

/** Pure decision rules shared by automatic sleep and its unit tests. */
public final class SleepPolicy {
    private SleepPolicy() { }

    public static boolean mayEnterAutomaticSleep(boolean charging,
                                                 boolean musicPlaying,
                                                 boolean navigationActive) {
        return !charging && !musicPlaying && !navigationActive;
    }

    public static boolean mayDisableConnectivity(boolean charging,
                                                 boolean musicPlaying,
                                                 boolean navigationActive) {
        return !charging && !musicPlaying && !navigationActive;
    }
}
