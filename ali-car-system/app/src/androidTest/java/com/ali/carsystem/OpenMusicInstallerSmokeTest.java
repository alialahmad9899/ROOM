package com.ali.carsystem;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class OpenMusicInstallerSmokeTest {
    @Test public void firstRunInstallsPublicDomainArabicTracksIntoMediaLibraryFolder() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OpenMusicInstaller.install(context);
        File archive = OpenMusicInstaller.getArchiveDirectory();
        assertTrue("Arabic open-music archive directory must exist", archive.isDirectory());
        File[] tracks = archive.listFiles((dir, name) -> name.toLowerCase().endsWith(".ogg"));
        assertNotNull(tracks);
        assertTrue("At least 30 public-domain Arabic OGG recordings must be installed",
                tracks.length >= 30);
        for (File track : tracks) {
            assertTrue("Bundled audio must not be an empty placeholder: " + track.getName(),
                    track.isFile() && track.length() > 20000L);
        }
    }
}
