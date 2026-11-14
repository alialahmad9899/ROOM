package com.ali.carsystem;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class MusicPlaybackLifecycleTest {
    @Rule public final ServiceTestRule serviceRule = new ServiceTestRule();

    @Test public void localTrackCanBeIndexedPlayedPausedAndResumed() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File wav = new File(context.getCacheDir(), "ali-car-lifecycle-test.wav");
        writeTestWave(wav, 18);
        Uri row = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DATA, wav.getAbsolutePath());
            values.put(MediaStore.Audio.Media.TITLE, "ALI CAR Lifecycle Test");
            values.put(MediaStore.Audio.Media.ARTIST, "ALI CAR Test Artist");
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
            values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
            values.put(MediaStore.Audio.Media.DURATION, 18000);
            row = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            assertNotNull("Test audio must be indexed by MediaStore", row);
            long id = Long.parseLong(row.getLastPathSegment());

            Intent intent = new Intent(context, MusicPlaybackService.class);
            IBinder binder = serviceRule.bindService(intent);
            assertTrue(binder instanceof MusicPlaybackService.LocalBinder);
            MusicPlaybackService service = ((MusicPlaybackService.LocalBinder) binder).getService();

            long deadline = System.currentTimeMillis() + 20000L;
            boolean found = false;
            while (System.currentTimeMillis() < deadline) {
                for (MusicPlaybackService.Song song : service.getSongs()) {
                    if (song.id == id) { found = true; break; }
                }
                if (found) break;
                service.refreshLibrary();
                Thread.sleep(250L);
            }
            assertTrue("Service must discover the local test recording", found);

            service.playSongId(id);
            deadline = System.currentTimeMillis() + 15000L;
            while (System.currentTimeMillis() < deadline && !service.isPlaying()) {
                Thread.sleep(100L);
            }
            assertTrue("MediaPlayer must prepare and start local audio", service.isPlaying());
            Thread.sleep(400L);
            service.toggle();
            assertFalse("Pause must stop playback", service.isPlaying());
            service.toggle();
            deadline = System.currentTimeMillis() + 5000L;
            while (System.currentTimeMillis() < deadline && !service.isPlaying()) {
                Thread.sleep(100L);
            }
            assertTrue("Resume must restart playback", service.isPlaying());
        } finally {
            if (row != null) {
                try { context.getContentResolver().delete(row, null, null); } catch (Exception ignored) { }
            }
            if (wav.exists()) wav.delete();
        }
    }

    private static void writeTestWave(File file, int seconds) throws IOException {
        final int sampleRate = 22050;
        final int channels = 1;
        final int bits = 16;
        int dataLength = sampleRate * seconds * channels * (bits / 8);
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeAscii(out, "RIFF"); writeIntLE(out, 36 + dataLength);
            writeAscii(out, "WAVEfmt "); writeIntLE(out, 16);
            writeShortLE(out, 1); writeShortLE(out, channels);
            writeIntLE(out, sampleRate); writeIntLE(out, sampleRate * channels * bits / 8);
            writeShortLE(out, channels * bits / 8); writeShortLE(out, bits);
            writeAscii(out, "data"); writeIntLE(out, dataLength);
            for (int i = 0; i < sampleRate * seconds; i++) {
                short sample = (short) (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 3500.0);
                writeShortLE(out, sample);
            }
        }
    }

    private static void writeAscii(FileOutputStream out, String text) throws IOException {
        out.write(text.getBytes("US-ASCII"));
    }
    private static void writeIntLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 255); out.write((value >>> 8) & 255);
        out.write((value >>> 16) & 255); out.write((value >>> 24) & 255);
    }
    private static void writeShortLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 255); out.write((value >>> 8) & 255);
    }
}
