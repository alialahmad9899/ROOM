package com.ali.carsystem;

/** Debounces off-route GPS noise and applies a cooldown before requesting a new route. */
public final class OffRouteReroutePolicy {
    private static final int REQUIRED_FIXES=4;
    private static final long REQUIRED_DURATION_MS=5000L;
    private static final long COOLDOWN_MS=30000L;
    private int offRouteFixes;
    private long offRouteSinceMs;
    private long lastRerouteAtMs=Long.MIN_VALUE;

    public boolean shouldReroute(boolean onRoute,float accuracyMeters,long nowMs,
                                 boolean routeRequestInFlight) {
        if(onRoute){offRouteFixes=0;offRouteSinceMs=0L;return false;}
        if(routeRequestInFlight||Float.isNaN(accuracyMeters)||Float.isInfinite(accuracyMeters)
                ||accuracyMeters<=0f||accuracyMeters>60f)return false;
        if(lastRerouteAtMs!=Long.MIN_VALUE&&nowMs-lastRerouteAtMs<COOLDOWN_MS)return false;
        if(offRouteSinceMs==0L)offRouteSinceMs=nowMs;
        offRouteFixes++;
        if(offRouteFixes<REQUIRED_FIXES||nowMs-offRouteSinceMs<REQUIRED_DURATION_MS)return false;
        lastRerouteAtMs=nowMs;
        offRouteFixes=0;offRouteSinceMs=0L;
        return true;
    }
}
