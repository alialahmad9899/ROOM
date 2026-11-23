package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BluetoothPlaybackPolicyTest {
    @Test public void disconnectPausesOnlyActivePlaybackAndReconnectResumesOnce() {
        BluetoothPlaybackPolicy policy = new BluetoothPlaybackPolicy();
        assertTrue(policy.onProfileDisconnected(true));
        assertTrue(policy.isWaitingForReconnect());
        assertTrue(policy.onProfileConnected());
        assertFalse(policy.isWaitingForReconnect());
        assertFalse(policy.onProfileConnected());
    }

    @Test public void disconnectWhileAlreadyPausedDoesNotQueueAnUnexpectedResume() {
        BluetoothPlaybackPolicy policy = new BluetoothPlaybackPolicy();
        assertFalse(policy.onProfileDisconnected(false));
        assertFalse(policy.isWaitingForReconnect());
        assertFalse(policy.onProfileConnected());
    }

    @Test public void duplicateDisconnectCannotReplaceTheOriginalResumeIntent() {
        BluetoothPlaybackPolicy policy = new BluetoothPlaybackPolicy();
        assertTrue(policy.onProfileDisconnected(true));
        assertFalse(policy.onProfileDisconnected(false));
        assertTrue(policy.onProfileConnected());
    }

    @Test public void manualActionCancelsPendingAutomaticReconnect() {
        BluetoothPlaybackPolicy policy = new BluetoothPlaybackPolicy();
        assertTrue(policy.onProfileDisconnected(true));
        policy.userRequestedPlayback();
        assertFalse(policy.isWaitingForReconnect());
        assertFalse(policy.onProfileConnected());
    }

    @Test public void persistedWaitCanBeRestoredAfterServiceRestart() {
        BluetoothPlaybackPolicy policy = new BluetoothPlaybackPolicy();
        policy.restore(true);
        assertTrue(policy.isWaitingForReconnect());
        assertTrue(policy.onProfileConnected());
    }
}
