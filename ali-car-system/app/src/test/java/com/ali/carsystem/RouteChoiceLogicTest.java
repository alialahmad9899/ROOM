package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RouteChoiceLogicTest {
    @Test public void selectsShortestRoute() {
        assertEquals(1, RouteChoiceLogic.shortestIndex(new double[]{8.1, 5.4, 6.2}));
    }
    @Test public void selectsFastestRoute() {
        assertEquals(2, RouteChoiceLogic.fastestIndex(new double[]{22, 18, 12}));
    }
    @Test public void selectsRouteWithFewestTurns() {
        assertEquals(1, RouteChoiceLogic.fewestTurnsIndex(new int[]{14, 5, 9}));
    }
    @Test public void selectsLongestAlternative() {
        assertEquals(0, RouteChoiceLogic.longestIndex(new double[]{14.2, 9.7, 12.3}));
    }
    @Test public void handlesMissingAlternatives() {
        assertEquals(-1, RouteChoiceLogic.shortestIndex(new double[0]));
        assertEquals(-1, RouteChoiceLogic.fewestTurnsIndex(null));
    }
    @Test public void ignoresInvalidMetrics() {
        assertEquals(2, RouteChoiceLogic.shortestIndex(new double[]{Double.NaN, -1, 4.5}));
    }
}
