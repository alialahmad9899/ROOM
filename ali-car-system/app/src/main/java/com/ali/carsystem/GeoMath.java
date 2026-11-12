package com.ali.carsystem;

public final class GeoMath {
    private static final double EARTH = 6371008.8;
    private GeoMath() {}
    public static double distanceMeters(double lat1,double lon1,double lat2,double lon2) {
        double p1=Math.toRadians(lat1), p2=Math.toRadians(lat2);
        double dp=Math.toRadians(lat2-lat1), dl=Math.toRadians(lon2-lon1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 2*EARTH*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }
    public static double speedKmh(double distanceMeters,long elapsedMillis) {
        if (elapsedMillis <= 0 || distanceMeters < 0) return 0;
        double v=distanceMeters/(elapsedMillis/1000d)*3.6d;
        return (!Double.isNaN(v) && !Double.isInfinite(v)) ? Math.max(0,Math.min(350,v)) : 0;
    }
    public static boolean isUsableFix(long fixTime,long now,long maxAgeMs,float accuracyMeters) {
        return fixTime > 0 && now >= fixTime && now-fixTime <= maxAgeMs &&
                (accuracyMeters <= 0 || accuracyMeters <= 100f);
    }
}
