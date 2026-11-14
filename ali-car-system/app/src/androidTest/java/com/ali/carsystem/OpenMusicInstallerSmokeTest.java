package com.ali.carsystem;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class OpenMusicInstallerSmokeTest {
    @Test public void upgradeRemovesObsoleteHistoricalArchiveAndDoesNotBundleTracks() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OpenMusicInstaller.install(context);
        File archive = OpenMusicInstaller.getArchiveDirectory(context);
        File[] tracks = archive.listFiles((dir, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".mp3") || lower.endsWith(".ogg");
        });
        assertTrue("Old historical archive tracks must be removed on upgrade",
                tracks == null || tracks.length == 0);
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.IS_MUSIC + "=1 AND "
                        + MediaStore.Audio.Media.DATA + " LIKE ?",
                new String[]{"%/ALI CAR/Open Arabic Archive/%"}, null)) {
            assertNotNull(cursor);
            assertEquals("Obsolete archive must not remain indexed", 0, cursor.getCount());
        } catch (Exception error) {
            fail("Could not verify removal of obsolete archive: " + error);
        }
    }
}
