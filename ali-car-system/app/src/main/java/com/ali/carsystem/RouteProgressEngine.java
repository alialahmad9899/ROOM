package com.ali.carsystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight route matcher. All progress and turn distances are measured along
 * the routed geometry, never as a straight-line distance to a turn.
 */
public final class RouteProgressEngine {
    private static final double METERS_PER_DEGREE = 111319.49079327358;
    private static final double EARTH_RADIUS_METERS = 6371008.8;

    public static final class Point {
        public final double latitude;
        public final double longitude;

        public Point(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final class Turn {
        public final int routeIndex;
        public final String instruction;

        public Turn(int routeIndex, String instruction) {
            this.routeIndex = routeIndex;
            this.instruction = instruction == null ? "" : instruction;
        }
    }

    public static final class Progress {
        public final boolean onRoute;
        public final double progressMeters;
        public final double remainingRouteMeters;
        public final double remainingToTurnMeters;
        public final double distanceFromRouteMeters;
        public final int nextTurnIndex;

        private Progress(boolean onRoute, double progressMeters, double remainingRouteMeters,
                         double remainingToTurnMeters, double distanceFromRouteMeters,
                         int nextTurnIndex) {
            this.onRoute = onRoute;
            this.progressMeters = progressMeters;
            this.remainingRouteMeters = remainingRouteMeters;
            this.remainingToTurnMeters = remainingToTurnMeters;
            this.distanceFromRouteMeters = distanceFromRouteMeters;
            this.nextTurnIndex = nextTurnIndex;
        }
    }

    private static final class Candidate {
        final int segmentIndex;
        final double fraction;
        final double progressMeters;
        final double distanceMeters;

        Candidate(int segmentIndex, double fraction, double progressMeters, double distanceMeters) {
            this.segmentIndex = segmentIndex;
            this.fraction = fraction;
            this.progressMeters = progressMeters;
            this.distanceMeters = distanceMeters;
        }
    }

    private final List<Point> points;
    private final List<Turn> turns;
    private final double[] cumulativeMeters;
    private final double totalMeters;
    private int lastSegmentIndex;
    private double lastProgressMeters;
    private boolean hasFix;

    public RouteProgressEngine(List<Point> routePoints, List<Turn> routeTurns) {
        if (routePoints == null || routePoints.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two points");
        }
        points = Collections.unmodifiableList(new ArrayList<>(routePoints));
        turns = routeTurns == null ? Collections.<Turn>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(routeTurns));
        cumulativeMeters = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulativeMeters[i] = cumulativeMeters[i - 1] + distanceMeters(points.get(i - 1), points.get(i));
        }
        totalMeters = cumulativeMeters[cumulativeMeters.length - 1];
        lastSegmentIndex = 0;
        lastProgressMeters = 0;
    }

    public double getTotalMeters() {
        return totalMeters;
    }

    public Progress update(double latitude, double longitude, float horizontalAccuracyMeters) {
        if (!isFinite(latitude) || !isFinite(longitude)) {
            return offRoute(Double.POSITIVE_INFINITY);
        }

        int last = points.size() - 2;
        int from = hasFix ? Math.max(0, lastSegmentIndex - 3) : 0;
        int to = hasFix ? Math.min(last, lastSegmentIndex + 300) : last;
        Candidate best = findNearest(latitude, longitude, from, to, hasFix ? lastProgressMeters - 20.0 : 0.0);

        // A GPS fix can jump several route vertices between updates. Expand the
        // search once, but still reject backwards route progress.
        if (best == null || best.distanceMeters > allowedSnapDistance(horizontalAccuracyMeters)) {
            int expandedFrom = hasFix ? Math.max(0, lastSegmentIndex - 2) : 0;
            int expandedTo = last;
            Candidate expanded = findNearest(latitude, longitude, expandedFrom, expandedTo,
                    hasFix ? lastProgressMeters - 20.0 : 0.0);
            if (expanded != null && (best == null || expanded.distanceMeters < best.distanceMeters)) {
                best = expanded;
            }
        }

        if (best == null) return offRoute(Double.POSITIVE_INFINITY);
        if (best.distanceMeters > allowedSnapDistance(horizontalAccuracyMeters)) {
            return offRoute(best.distanceMeters);
        }

        double progress = Math.max(lastProgressMeters, best.progressMeters);
        lastProgressMeters = Math.min(totalMeters, progress);
        lastSegmentIndex = best.segmentIndex;
        hasFix = true;

        int nextTurn = -1;
        double toTurn = 0;
        for (int i = 0; i < turns.size(); i++) {
            int routeIndex = clamp(turns.get(i).routeIndex, 0, points.size() - 1);
            double turnDistance = cumulativeMeters[routeIndex];
            if (turnDistance >= lastProgressMeters - 12.0) {
                nextTurn = i;
                toTurn = Math.max(0, turnDistance - lastProgressMeters);
                break;
            }
        }

        return new Progress(true, lastProgressMeters,
                Math.max(0, totalMeters - lastProgressMeters), toTurn,
                best.distanceMeters, nextTurn);
    }

    private Candidate findNearest(double latitude, double longitude, int from, int to,
                                  double minimumProgressMeters) {
        Candidate best = null;
        for (int i = Math.max(0, from); i <= Math.min(points.size() - 2, to); i++) {
            Point a = points.get(i);
            Point b = points.get(i + 1);
            double meanLat = Math.toRadians((latitude + a.latitude + b.latitude) / 3.0);
            double metersPerLon = METERS_PER_DEGREE * Math.max(0.01, Math.cos(meanLat));
            double bx = (b.longitude - a.longitude) * metersPerLon;
            double by = (b.latitude - a.latitude) * METERS_PER_DEGREE;
            double px = (longitude - a.longitude) * metersPerLon;
            double py = (latitude - a.latitude) * METERS_PER_DEGREE;
            double lengthSquared = bx * bx + by * by;
            if (lengthSquared < 0.0001) continue;
            double fraction = clamp((px * bx + py * by) / lengthSquared, 0.0, 1.0);
            double dx = px - fraction * bx;
            double dy = py - fraction * by;
            double perpendicular = Math.sqrt(dx * dx + dy * dy);
            double segmentLength = cumulativeMeters[i + 1] - cumulativeMeters[i];
            double progress = cumulativeMeters[i] + segmentLength * fraction;
            if (progress < minimumProgressMeters) continue;
            if (best == null || perpendicular < best.distanceMeters) {
                best = new Candidate(i, fraction, progress, perpendicular);
            }
        }
        return best;
    }

    private Progress offRoute(double distanceMeters) {
        return new Progress(false, lastProgressMeters, Math.max(0, totalMeters - lastProgressMeters),
                0, distanceMeters, -1);
    }

    private double allowedSnapDistance(float accuracy) {
        if (accuracy <= 0 || Float.isNaN(accuracy) || Float.isInfinite(accuracy)) return 45.0;
        // Prefer suppressing guidance over matching an imprecise fix to a nearby road.
        return Math.max(25.0, Math.min(45.0, accuracy * 1.8));
    }

    private static double distanceMeters(Point a, Point b) {
        double lat1 = Math.toRadians(a.latitude);
        double lat2 = Math.toRadians(b.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude - a.longitude);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLon = Math.sin(dLon / 2.0);
        double value = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(value)));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
