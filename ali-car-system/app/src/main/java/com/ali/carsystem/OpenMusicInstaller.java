package com.ali.carsystem;

import android.content.Context;
import android.provider.MediaStore;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * Removes the obsolete downloaded historical archive from legacy install locations.
 * It deliberately never touches the user's other Music folders or local audio files.
 */
public final class OpenMusicInstaller {
    private static final String TAG = "ALI-CAR-MusicCleanup";
    private static final String DIRECTORY_NAME = "ALI CAR/Open Arabic Archive";
    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".ogg", ".wav", ".m4a", ".aac", ".flac", ".opus", ".wma", ".aiff", ".ape"
    };

    private OpenMusicInstaller() { }

    public static File getArchiveDirectory(Context context) {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), DIRECTORY_NAME);
    }

    public static int install(Context context) {
        if (context == null) return 0;
        File shared = getArchiveDirectory(context);
        File appExternal = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File appSpecific = appExternal == null ? null : new File(appExternal, "Open Arabic Archive");
        File privateSpecific = new File(new File(context.getFilesDir(), "Music"), "Open Arabic Archive");
        File[] locations = {shared, appSpecific, privateSpecific};
        int removed = 0;
        for (File directory : locations) {
            if (directory != null && directory.exists())
                removed += removeLegacyAudioTree(context, directory);
        }
        Log.i(TAG, "removedObsoleteHistoricalAudio=" + removed);
        return removed;
    }

    private static int removeLegacyAudioTree(Context context, File directory) {
        File[] entries = directory.listFiles();
        if (entries == null) return 0;
        int removed = 0;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                removed += removeLegacyAudioTree(context, entry);
                File[] remaining = entry.listFiles();
                if (remaining != null && remaining.length == 0) entry.delete();
                continue;
            }
            if (!entry.isFile() || !isAudioFile(entry.getName())) continue;
            String path = entry.getAbsolutePath();
            try {
                context.getContentResolver().delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Audio.Media.DATA + "=?",
                        new String[]{path});
            } catch (Exception error) {
                AppDiagnostics.record(context, "Remove obsolete historical audio index", error);
            }
            if (entry.delete()) removed++;
        }
        File[] remaining = directory.listFiles();
        if (remaining != null && remaining.length == 0) directory.delete();
        return removed;
    }

    private static boolean isAudioFile(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String extension : AUDIO_EXTENSIONS) {
            if (lower.endsWith(extension)) return true;
        }
        return false;
    }
}