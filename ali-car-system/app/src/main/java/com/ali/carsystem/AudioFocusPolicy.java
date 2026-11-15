package com.ali.carsystem;

/** Prevents focus gain from restarting music after a permanent loss or user pause. */
final class AudioFocusPolicy {
    private AudioFocusPolicy() { }

    static boolean shouldResumeAfterTransientLoss(boolean wasPlayingBeforeLoss, boolean userWantsPlayback) {
        return wasPlayingBeforeLoss && userWantsPlayback;
    }
}
