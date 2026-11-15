package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DashboardGesturePolicyTest {
    @Test public void recognizesAllFourDirections() {
        assertEquals(DashboardGesturePolicy.LEFT,DashboardGesturePolicy.direction(-120f,8f,50f));
        assertEquals(DashboardGesturePolicy.RIGHT,DashboardGesturePolicy.direction(120f,8f,50f));
        assertEquals(DashboardGesturePolicy.UP,DashboardGesturePolicy.direction(8f,-120f,50f));
        assertEquals(DashboardGesturePolicy.DOWN,DashboardGesturePolicy.direction(8f,120f,50f));
    }

    @Test public void ignoresTapAndShortMovement() {
        assertEquals(DashboardGesturePolicy.NONE,DashboardGesturePolicy.direction(12f,8f,50f));
        assertEquals(DashboardGesturePolicy.NONE,DashboardGesturePolicy.direction(49f,0f,50f));
    }

    @Test public void dominantAxisWinsForDiagonalMovement() {
        assertEquals(DashboardGesturePolicy.LEFT,DashboardGesturePolicy.direction(-100f,60f,50f));
        assertEquals(DashboardGesturePolicy.UP,DashboardGesturePolicy.direction(60f,-100f,50f));
    }
}
