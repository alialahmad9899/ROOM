package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AudioFocusPolicyTest {
    @Test public void resumesOnlyWhenPlaybackWasActiveAndStillWanted() {
        assertTrue(AudioFocusPolicy.shouldResumeAfterTransientLoss(true,true));
    }
    @Test public void doesNotResumeAfterUserPause() {
        assertFalse(AudioFocusPolicy.shouldResumeAfterTransientLoss(true,false));
    }
    @Test public void doesNotResumeAfterPermanentLossOrAlreadyPausedState() {
        assertFalse(AudioFocusPolicy.shouldResumeAfterTransientLoss(false,true));
        assertFalse(AudioFocusPolicy.shouldResumeAfterTransientLoss(false,false));
    }
}
