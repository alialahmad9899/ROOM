package com.ali.carsystem;

/** Per-track size policy for the requested offline music library. */
public final class MusicFilePolicy {
    public static final long MAX_TRACK_BYTES = 4_000_000L;

    private MusicFilePolicy() {}

    public static boolean isAllowed(long bytes) {
        return bytes > 0L && bytes <= MAX_TRACK_BYTES;
    }
}
