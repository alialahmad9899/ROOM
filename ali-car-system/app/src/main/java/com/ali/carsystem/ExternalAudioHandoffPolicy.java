package com.ali.carsystem;

/** Tracks one external-video interruption without turning an already-paused song back on. */
final class ExternalAudioHandoffPolicy {
    private boolean active;
    private boolean resumeMusic;

    boolean begin(boolean musicWasPlaying) {
        if (active) return false;
        active = true;
        resumeMusic = musicWasPlaying;
        return resumeMusic;
    }

    boolean isActive() { return active; }

    void cancelResume() { resumeMusic = false; }

    boolean finish(boolean resumeStillAllowed) {
        if (!active) return false;
        boolean shouldResume = resumeMusic && resumeStillAllowed;
        active = false;
        resumeMusic = false;
        return shouldResume;
    }
}
