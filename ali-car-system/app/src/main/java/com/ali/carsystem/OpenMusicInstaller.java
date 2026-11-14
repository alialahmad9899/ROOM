package com.ali.carsystem;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaMetadataRetriever;
import android.provider.MediaStore;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Installs public-domain Arabic recordings bundled in the APK into the shared
 * Music/ALI CAR/Open Arabic Archive folder and indexes them with MediaStore.
 */
public final class OpenMusicInstaller {
    private static final String TAG = "ALI-CAR-OpenMusic";
    private static final String ASSET_FOLDER = "music";
    private static final String DIRECTORY_NAME = "ALI CAR/Open Arabic Archive";

    private OpenMusicInstaller() { }

    private static boolean ensureMediaStoreEntry(Context context, File audioFile) {
        if (audioFile == null || !audioFile.isFile() || audioFile.length() < 20000L) return false;
        String path = audioFile.getAbsolutePath();
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.DATA + "=?",
                new String[]{path}, null)) {
            if (cursor != null && cursor.moveToFirst()) return true;
        } catch (Exception error) {
            AppDiagnostics.record(context, "Check Arabic music MediaStore entry", error);
        }

        String filename = audioFile.getName();
        String[] parts = filename.split("__", 3);
        String artist = parts.length == 3 ? parts[1].trim() : "Open Arabic Archive";
        String title = parts.length == 3 ? parts[2] : filename;
        int dot = title.lastIndexOf('.');
        if (dot > 0) title = title.substring(0, dot);
        title = title.replace('_', ' ').trim();

        long duration = 0L;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(path);
            String value = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (value != null) duration = Long.parseLong(value);
        } catch (Exception error) {
            AppDiagnostics.record(context, "Read Arabic music metadata", error);
        } finally {
            try { metadata.release(); } catch (Exception ignored) { }
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DATA, path);
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        values.put(MediaStore.Audio.Media.ALBUM, "Open Arabic Historical Archive");
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        values.put(MediaStore.Audio.Media.DURATION, duration);
        try {
            Uri inserted = context.getContentResolver().insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (inserted != null) return true;
            Log.w(TAG, "MediaStore refused insert for " + path);
        } catch (Exception error) {
            AppDiagnostics.record(context, "Index Arabic music in MediaStore", error);
            Log.e(TAG, "Could not insert MediaStore row for " + path, error);
        }
        return false;
    }

    public static File getArchiveDirectory(Context context) {
        File shared = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                DIRECTORY_NAME);
        if (shared.isDirectory() || shared.mkdirs()) return shared;

        File appExternal = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (appExternal != null) {
            File appArchive = new File(appExternal, "Open Arabic Archive");
            if (appArchive.isDirectory() || appArchive.mkdirs()) return appArchive;
        }

        File privateArchive = new File(new File(context.getFilesDir(), "Music"), "Open Arabic Archive");
        if (!privateArchive.isDirectory() && !privateArchive.mkdirs())
            throw new IllegalStateException("Cannot create private Arabic music directory");
        return privateArchive;
    }

    public static int install(Context context) {
        if (context == null) return 0;
        ArrayList<String> installedOrExistingFiles = new ArrayList<>();
        try {
            File directory = getArchiveDirectory(context);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IllegalStateException("Cannot create shared music directory: " + directory);
            }

            String[] assets = context.getAssets().list(ASSET_FOLDER);
            if (assets == null || assets.length == 0) {
                throw new IllegalStateException("No bundled open music assets were packaged");
            }

            for (String assetName : assets) {
                if (assetName == null || !assetName.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) continue;
                File destination = new File(directory, assetName);
                if (destination.isFile() && destination.length() > 20000L) {
                    installedOrExistingFiles.add(destination.getAbsolutePath());
                    continue;
                }

                File temporary = new File(directory, assetName + ".tmp");
                try (InputStream input = new BufferedInputStream(
                        context.getAssets().open(ASSET_FOLDER + "/" + assetName));
                     BufferedOutputStream output = new BufferedOutputStream(
                             new FileOutputStream(temporary, false))) {
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                }
                if (!temporary.isFile() || temporary.length() < 20000L) {
                    temporary.delete();
                    throw new IllegalStateException("Bundled audio copy was incomplete: " + assetName);
                }
                if (destination.exists() && !destination.delete()) {
                    temporary.delete();
                    throw new IllegalStateException("Cannot replace incomplete audio file: " + destination);
                }
                if (!temporary.renameTo(destination)) {
                    temporary.delete();
                    throw new IllegalStateException("Cannot install audio file: " + destination);
                }
                installedOrExistingFiles.add(destination.getAbsolutePath());
            }

            if (!installedOrExistingFiles.isEmpty()) {
                CountDownLatch scanned = new CountDownLatch(installedOrExistingFiles.size());
                String[] paths = installedOrExistingFiles.toArray(new String[0]);
                MediaScannerConnection.scanFile(context.getApplicationContext(), paths,
                        new String[]{"audio/mpeg"}, (path, uri) -> scanned.countDown());
                scanned.await(20L, TimeUnit.SECONDS);
                int indexed = 0;
                for (String audioPath : paths) {
                    if (ensureMediaStoreEntry(context, new File(audioPath))) indexed++;
                }
                Log.i(TAG, "openArchiveFiles=" + paths.length
                        + " indexed=" + indexed + " path=" + directory.getAbsolutePath());
            }

            Log.i(TAG, "openArchiveFiles=" + assets.length
                    + " installedOrExisting=" + installedOrExistingFiles.size()
                    + " path=" + directory.getAbsolutePath());
            return installedOrExistingFiles.size();
        } catch (SecurityException error) {
            AppDiagnostics.record(context, "Install public-domain Arabic music (storage permission)", error);
            Log.e(TAG, "Shared storage permission denied; bundled music remains inside the APK", error);
        } catch (Exception error) {
            AppDiagnostics.record(context, "Install public-domain Arabic music", error);
            Log.e(TAG, "Could not install bundled Arabic music", error);
        }
        return 0;
    }
}
