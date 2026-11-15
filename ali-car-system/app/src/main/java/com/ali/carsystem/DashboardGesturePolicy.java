package com.ali.carsystem;

/** Deterministic four-direction gesture classification for the dashboard swipe surface. */
public final class DashboardGesturePolicy {
    public static final int NONE=0, LEFT=1, RIGHT=2, UP=3, DOWN=4;
    private DashboardGesturePolicy() { }

    public static int direction(float dx,float dy,float minimumDistance) {
        if(minimumDistance<0f)minimumDistance=0f;
        float ax=Math.abs(dx),ay=Math.abs(dy);
        if(Math.max(ax,ay)<minimumDistance)return NONE;
        if(ax>ay)return dx<0f?LEFT:RIGHT;
        return dy<0f?UP:DOWN;
    }
}
