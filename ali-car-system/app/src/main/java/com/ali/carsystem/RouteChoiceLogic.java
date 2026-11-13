package com.ali.carsystem;

/** Deterministic ranking for the route alternatives actually returned by the routing service. */
public final class RouteChoiceLogic {
    private RouteChoiceLogic() {}

    public static int shortestIndex(double[] distancesKm) { return minIndex(distancesKm); }
    public static int fastestIndex(double[] durationsMinutes) { return minIndex(durationsMinutes); }
    public static int longestIndex(double[] distancesKm) { return maxIndex(distancesKm); }

    public static int fewestTurnsIndex(int[] turns) {
        if (turns == null || turns.length == 0) return -1;
        int best = -1;
        for (int i = 0; i < turns.length; i++) {
            if (turns[i] < 0) continue;
            if (best < 0 || turns[i] < turns[best]) best = i;
        }
        return best;
    }

    private static int minIndex(double[] values) {
        if (values == null || values.length == 0) return -1;
        int best = -1;
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) continue;
            if (best < 0 || value < values[best]) best = i;
        }
        return best;
    }

    private static int maxIndex(double[] values) {
        if (values == null || values.length == 0) return -1;
        int best = -1;
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) continue;
            if (best < 0 || value > values[best]) best = i;
        }
        return best;
    }
}
