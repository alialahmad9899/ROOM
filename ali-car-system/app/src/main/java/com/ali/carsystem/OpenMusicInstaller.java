package com.ali.carsystem;

import android.content.Context;
import android.provider.MediaStore;
import android.database.Cursor;
import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Removes the obsolete bundled historical archive from upgrades.
 * The player now uses only audio already present in the user's local library.
 */
public final class OpenMusicInstaller {
    private static final String TAG = "ALI-CAR-MusicCleanup";
    private static final String DIRECTORY_NAME = "ALI CAR/Open Arabic Archive";

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
            if (directory == null) continue;
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file == null || !file.isFile()) continue;
                    String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                    if (!name.endsWith(".mp3") && !name.endsWith(".ogg")) continue;
                    String path = file.getAbsolutePath();
                    try {
                        context.getContentResolver().delete(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                MediaStore.Audio.Media.DATA + "=?",
                                new String[]{path});
                    } catch (Exception error) {
                        AppDiagnostics.record(context, "Remove obsolete bundled music index", error);
                    }
                    if (file.delete()) removed++;
                }
            }
            if (directory.isDirectory()) {
                File[] remaining = directory.listFiles();
                if (remaining != null && remaining.length == 0) directory.delete();
            }
        }
        File sharedParent = shared.getParentFile();
        if (sharedParent != null && sharedParent.isDirectory()) {
            File[] remaining = sharedParent.listFiles();
            if (remaining != null && remaining.length == 0) sharedParent.delete();
        }
        Log.i(TAG, "removedObsoleteArchiveTracks=" + removed);
        return removed;
    }
}
