package com.ali.carsystem;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class OpenMusicInstallerSmokeTest {
    @Test public void upgradeRemovesLegacyAudioRecursivelyWithoutBundlingTracks() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File archive = OpenMusicInstaller.getArchiveDirectory(context);
        File nested = new File(archive, "old-test-nested");
        assertTrue("Legacy archive folder must be writable in this test environment",
                nested.isDirectory() || nested.mkdirs());
        File oldMp3 = new File(archive, "legacy-test-track.mp3");
        File oldFlac = new File(nested, "legacy-test-track.flac");
        try {
            try (FileOutputStream stream = new FileOutputStream(oldMp3)) {
                stream.write(new byte[]{1, 2, 3});
            }
            try (FileOutputStream stream = new FileOutputStream(oldFlac)) {
                stream.write(new byte[]{1, 2, 3});
            }
            OpenMusicInstaller.install(context);
            assertFalse("Old MP3 must be deleted from the legacy archive", oldMp3.exists());
            assertFalse("Nested old audio must also be deleted", oldFlac.exists());
            assertEquals("No legacy archive files should remain", 0, countFiles(archive));
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID},
                    MediaStore.Audio.Media.IS_MUSIC + "=1 AND "
                            + MediaStore.Audio.Media.DATA + " LIKE ?",
                    new String[]{"%/ALI CAR/Open Arabic Archive/%"}, null)) {
                assertNotNull(cursor);
                assertEquals("Obsolete archive must not remain indexed", 0, cursor.getCount());
            }
        } finally {
            if (oldMp3.exists()) oldMp3.delete();
            if (oldFlac.exists()) oldFlac.delete();
            if (nested.exists()) nested.delete();
            if (archive.exists()) archive.delete();
        }
    }

    private static int countFiles(File directory) {
        File[] entries = directory.listFiles();
        if (entries == null) return 0;
        int count = 0;
        for (File entry : entries) {
            if (entry.isDirectory()) count += countFiles(entry);
            else if (entry.isFile()) count++;
        }
        return count;
    }
}