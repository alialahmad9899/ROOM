package com.ali.carsystem;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.provider.MediaStore;

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
        File archive = OpenMusicInstaller.getArchiveDirectory(context);
        assertTrue("Arabic open-music archive directory must exist", archive.isDirectory());
        File[] tracks = archive.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));
        assertNotNull(tracks);
        assertTrue("At least 30 public-domain Arabic OGG recordings must be installed",
                tracks.length >= 30);
        File firstTrack = null;
        for (File track : tracks) {
            assertTrue("Bundled audio must not be an empty placeholder: " + track.getName(),
                    track.isFile() && track.length() > 20000L);
            if (firstTrack == null) firstTrack = track;
        }
        assertNotNull(firstTrack);
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.IS_MUSIC + "=1 AND "
                        + MediaStore.Audio.Media.DATA + " LIKE ?",
                new String[]{"%/ALI CAR/Open Arabic Archive/%"}, null)) {
            assertNotNull("MediaStore query must be available for the music archive", cursor);
            assertTrue("At least 20 open Arabic tracks must be indexed for the player",
                    cursor.getCount() >= 20);
        }
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(firstTrack.getAbsolutePath());
            player.prepare();
            assertTrue("Android 5 MediaPlayer must decode the bundled MP3",
                    player.getDuration() >= 60000);
        } catch (Exception error) {
            fail("Bundled Arabic MP3 could not be decoded by API21 MediaPlayer: " + error);
        } finally {
            player.release();
        }
    }
}
