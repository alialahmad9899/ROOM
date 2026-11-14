package com.ali.carsystem;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class RouteProgressEngineTest {
    private RouteProgressEngine engine() {
        return new RouteProgressEngine(
                Arrays.asList(
                        new RouteProgressEngine.Point(0.0, 0.0000),
                        new RouteProgressEngine.Point(0.0, 0.0010),
                        new RouteProgressEngine.Point(0.0, 0.0020),
                        new RouteProgressEngine.Point(0.0, 0.0030)),
                Arrays.asList(
                        new RouteProgressEngine.Turn(2, "انعطف يميناً"),
                        new RouteProgressEngine.Turn(3, "وصلت إلى الوجهة")));
    }

    @Test public void distanceToTurnUsesRoadGeometry() {
        RouteProgressEngine engine = engine();
        RouteProgressEngine.Progress progress = engine.update(0.0, 0.0015, 5f);
        assertTrue(progress.onRoute);
        assertEquals(55.6, progress.remainingToTurnMeters, 2.0);
        assertEquals(0, progress.nextTurnIndex);
    }

    @Test public void rejectsClearlyOffRouteFix() {
        RouteProgressEngine.Progress progress = engine().update(0.01, 0.001, 5f);
        assertFalse(progress.onRoute);
    }

    @Test public void routeProgressDoesNotMoveBackwardsWithGpsJitter() {
        RouteProgressEngine engine = engine();
        RouteProgressEngine.Progress first = engine.update(0.0, 0.0018, 5f);
        RouteProgressEngine.Progress second = engine.update(0.0, 0.0015, 5f);
        assertTrue(first.onRoute);
        assertTrue(second.onRoute);
        assertTrue(second.progressMeters >= first.progressMeters);
    }

    @Test public void reportsRemainingTripDistance() {
        RouteProgressEngine.Progress progress = engine().update(0.0, 0.0010, 5f);
        assertTrue(progress.onRoute);
        assertEquals(222.4, progress.remainingRouteMeters, 2.0);
    }

    @Test public void rejectsOppositeDirectionWhileVehicleIsMoving() {
        RouteProgressEngine.Progress progress = engine().update(0.0, 0.0015, 5f, 270f, 10f);
        assertFalse(progress.onRoute);
    }

    @Test public void reportsNextTurnAfterPassingPreviousTurn() {
        RouteProgressEngine engine = engine();
        RouteProgressEngine.Progress progress = engine.update(0.0, 0.0022, 5f);
        assertTrue(progress.onRoute);
        assertEquals(1, progress.nextTurnIndex);
        assertEquals(0.0, progress.remainingToTurnMeters, 2.0);
    }

    @Test public void canRecoverAfterTemporaryGpsLossOffRouteFix() {
        RouteProgressEngine engine = engine();
        RouteProgressEngine.Progress first = engine.update(0.0, 0.0010, 5f);
        RouteProgressEngine.Progress lost = engine.update(0.02, 0.0015, 5f);
        RouteProgressEngine.Progress recovered = engine.update(0.0, 0.0018, 5f);
        assertTrue(first.onRoute);
        assertFalse(lost.onRoute);
        assertTrue(recovered.onRoute);
        assertTrue(recovered.progressMeters >= first.progressMeters);
    }

    @Test public void rejectsInvalidGpsCoordinatesWithoutCrashing() {
        RouteProgressEngine.Progress progress = engine().update(Double.NaN, 0.0, 5f);
        assertFalse(progress.onRoute);
        assertEquals(0.0, progress.progressMeters, 0.01);
    }
}
