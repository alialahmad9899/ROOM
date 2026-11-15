package com.ali.carsystem;

/** Shared, clock-safe validation for GNSS/network location fixes. */
final class LocationFixPolicy {
    private LocationFixPolicy() { }

    static boolean isUsable(double latitude, double longitude,
            boolean hasAccuracy, float accuracyMeters,
            long fixElapsedNanos, long nowElapsedNanos,
            long fixWallTimeMs, long nowWallTimeMs,
            long maxAgeMs, float maxAccuracyMeters) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)
                || latitude < -90d || latitude > 90d
                || longitude < -180d || longitude > 180d) return false;
        if (!hasAccuracy || (Float.isNaN(accuracyMeters) || Float.isInfinite(accuracyMeters))
                || accuracyMeters <= 0f || accuracyMeters > maxAccuracyMeters) return false;
        return isFresh(fixElapsedNanos, fixWallTimeMs,
                nowElapsedNanos, nowWallTimeMs, maxAgeMs);
    }

    static boolean isFresh(long fixElapsedNanos, long fixWallTimeMs,
            long nowElapsedNanos, long nowWallTimeMs, long maxAgeMs) {
        if (maxAgeMs < 0L) return false;
        if (fixElapsedNanos > 0L) {
            if (nowElapsedNanos < fixElapsedNanos) return false;
            return nowElapsedNanos - fixElapsedNanos <= maxAgeMs * 1000000L;
        }
        return fixWallTimeMs > 0L && nowWallTimeMs >= fixWallTimeMs
                && nowWallTimeMs - fixWallTimeMs <= maxAgeMs;
    }

    static boolean isNewer(long candidateElapsedNanos, long candidateWallTimeMs,
            long acceptedElapsedNanos, long acceptedWallTimeMs) {
        if (acceptedElapsedNanos <= 0L && acceptedWallTimeMs <= 0L) return true;
        if (candidateElapsedNanos > 0L && acceptedElapsedNanos > 0L)
            return candidateElapsedNanos > acceptedElapsedNanos;
        return candidateWallTimeMs > 0L && candidateWallTimeMs > acceptedWallTimeMs;
    }

    static float speedKmh(float metersPerSecond) {
        if ((Float.isNaN(metersPerSecond) || Float.isInfinite(metersPerSecond)) || metersPerSecond < 0f
                || metersPerSecond > 70f) return 0f;
        return metersPerSecond * 3.6f;
    }
}
