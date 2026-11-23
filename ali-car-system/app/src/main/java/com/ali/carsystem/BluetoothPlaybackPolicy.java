package com.ali.carsystem;

/**
 * Tracks only pauses caused by an A2DP disconnect. A reconnect may resume
 * playback only when that disconnect interrupted audio that was actually playing.
 */
final class BluetoothPlaybackPolicy {
    private boolean waitingForReconnect;

    boolean onProfileDisconnected(boolean wasPlaying) {
        if (!wasPlaying || waitingForReconnect) return false;
        waitingForReconnect = true;
        return true;
    }

    boolean onProfileConnected() {
        if (!waitingForReconnect) return false;
        waitingForReconnect = false;
        return true;
    }

    void restore(boolean waiting) {
        waitingForReconnect = waiting;
    }

    void userRequestedPlayback() {
        waitingForReconnect = false;
    }

    boolean isWaitingForReconnect() {
        return waitingForReconnect;
    }
}
