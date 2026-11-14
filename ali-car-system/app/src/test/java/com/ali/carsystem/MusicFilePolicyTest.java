package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MusicFilePolicyTest {
    @Test public void acceptsFilesUpToAndIncludingFourMiB() {
        assertTrue(MusicFilePolicy.isAllowed(1L));
        assertTrue(MusicFilePolicy.isAllowed(MusicFilePolicy.MAX_TRACK_BYTES));
    }

    @Test public void rejectsEmptyAndOversizedFiles() {
        assertFalse(MusicFilePolicy.isAllowed(0L));
        assertFalse(MusicFilePolicy.isAllowed(-1L));
        assertFalse(MusicFilePolicy.isAllowed(MusicFilePolicy.MAX_TRACK_BYTES + 1L));
    }
}
