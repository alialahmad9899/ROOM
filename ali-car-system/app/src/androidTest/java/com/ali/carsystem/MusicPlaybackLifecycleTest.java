package com.ali.carsystem;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.Environment;
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
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (!musicDir.isDirectory()) assertTrue(musicDir.mkdirs());
        File wav = new File(musicDir, "ali-car-lifecycle-test.wav");
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
            // Service may have completed its first scan before the test inserted the WAV row.
            service.refreshLibrary();

            long deadline = System.currentTimeMillis() + 20000L;
            while (System.currentTimeMillis() < deadline && !service.isLibraryLoaded()) {
                Thread.sleep(100L);
            }
            assertTrue("Service library scan must finish", service.isLibraryLoaded());
            boolean found = false;
            for (MusicPlaybackService.Song song : service.getSongs()) {
                if (song.id == id) { found = true; break; }
            }
            assertTrue("Service must discover the local test recording after a fresh MediaStore scan", found);

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
            assertTrue("Playback position must advance after resume", service.getPosition() > 0);

            service.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
            assertFalse("Transient focus loss pauses the track",service.isPlaying());
            service.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_GAIN);
            deadline=System.currentTimeMillis()+5000L;
            while(System.currentTimeMillis()<deadline&&!service.isPlaying())Thread.sleep(100L);
            assertTrue("A transient focus interruption resumes the previously playing track",service.isPlaying());

            // Exercise the real started-service handoff, not only the isolated policy object.
            int beforeVideoPosition=service.getPosition();
            assertTrue("Local music state is published before the video handoff",
                    MusicPlaybackService.isAudioPlaying(context));
            MusicPlaybackService.pauseForExternalVideo(context);
            deadline=System.currentTimeMillis()+5000L;
            while(System.currentTimeMillis()<deadline&&service.isPlaying())Thread.sleep(100L);
            assertFalse("External video start pauses the real MediaPlayer service",service.isPlaying());
            int videoPausedPosition=service.getPosition();
            assertTrue("Pause preserves the previous song position",videoPausedPosition>=beforeVideoPosition);

            MusicPlaybackService.resumeAfterExternalVideo(context);
            deadline=System.currentTimeMillis()+5000L;
            while(System.currentTimeMillis()<deadline&&!service.isPlaying())Thread.sleep(100L);
            assertTrue("External video stop resumes the same local track",service.isPlaying());
            assertTrue("The resumed track continues from its saved position",
                    service.getPosition()>=videoPausedPosition);

            // A song the user paused before opening a video must remain paused afterwards.
            service.toggle();
            assertFalse("Manual pause succeeds before simulated video",service.isPlaying());
            MusicPlaybackService.pauseForExternalVideo(context);
            MusicPlaybackService.resumeAfterExternalVideo(context);
            Thread.sleep(400L);
            assertFalse("Video completion never starts music that was already paused",
                    service.isPlaying());
            service.toggle();
            deadline=System.currentTimeMillis()+5000L;
            while(System.currentTimeMillis()<deadline&&!service.isPlaying())Thread.sleep(100L);
            assertTrue("Manual playback can restart after the handoff test",service.isPlaying());

            service.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS);
            assertFalse("Permanent focus loss pauses the track",service.isPlaying());
            service.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_GAIN);
            Thread.sleep(500L);
            assertFalse("Permanent focus loss must not restart music on focus gain",service.isPlaying());
            service.toggleFavorite(id);
            service.addSongToPlaylist(id,"ALI CAR Test Playlist");
            assertTrue("The test song is in favorites before delete",service.isFavorite(id));
            assertTrue("The test song is in its test playlist before delete",
                    service.getPlaylistSongIds("ALI CAR Test Playlist").contains(id));
            assertTrue("Confirmed delete removes the MediaStore song",service.deleteSong(id));
            boolean stillPresent=false;
            for(MusicPlaybackService.Song song:service.getSongs())
                if(song.id==id)stillPresent=true;
            assertFalse("Deleted song is removed from the service library",stillPresent);
            assertFalse("Deleting a song also clears its favorite reference",service.isFavorite(id));
            assertFalse("Deleting a song also clears playlist references",
                    service.getPlaylistSongIds("ALI CAR Test Playlist").contains(id));
            assertFalse("The local test audio file is deleted",wav.exists());
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
