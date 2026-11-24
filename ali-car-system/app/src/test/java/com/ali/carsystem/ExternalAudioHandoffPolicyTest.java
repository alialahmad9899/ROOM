package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ExternalAudioHandoffPolicyTest {
    @Test public void resumesOnlyWhenMusicWasPlayingBeforeVideo() {
        ExternalAudioHandoffPolicy policy=new ExternalAudioHandoffPolicy();
        assertTrue(policy.begin(true));
        assertTrue(policy.isActive());
        assertTrue(policy.finish(true));
        assertFalse(policy.isActive());
    }

    @Test public void leavesManuallyPausedMusicStopped() {
        ExternalAudioHandoffPolicy policy=new ExternalAudioHandoffPolicy();
        assertFalse(policy.begin(false));
        assertFalse(policy.finish(true));
    }

    @Test public void userCanCancelAutomaticResumeDuringVideo() {
        ExternalAudioHandoffPolicy policy=new ExternalAudioHandoffPolicy();
        policy.begin(true);
        policy.cancelResume();
        assertFalse(policy.finish(true));
    }

    @Test public void deniedResumeAndDuplicateStopsAreSafe() {
        ExternalAudioHandoffPolicy policy=new ExternalAudioHandoffPolicy();
        policy.begin(true);
        assertFalse(policy.finish(false));
        assertFalse(policy.finish(true));
    }
}
