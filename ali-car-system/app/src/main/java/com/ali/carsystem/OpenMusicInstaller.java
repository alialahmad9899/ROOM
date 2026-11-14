package com.ali.carsystem;

import android.content.Context;
import android.media.MediaScannerConnection;
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

    public static File getArchiveDirectory() {
        File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return new File(root, DIRECTORY_NAME);
    }

    public static int install(Context context) {
        if (context == null) return 0;
        ArrayList<String> newFiles = new ArrayList<>();
        try {
            File directory = getArchiveDirectory();
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
                if (destination.isFile() && destination.length() > 20000L) continue;

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
                newFiles.add(destination.getAbsolutePath());
            }

            if (!newFiles.isEmpty()) {
                CountDownLatch indexed = new CountDownLatch(newFiles.size());
                String[] paths = newFiles.toArray(new String[0]);
                MediaScannerConnection.scanFile(context.getApplicationContext(), paths,
                        new String[]{"audio/mpeg"}, (path, uri) -> indexed.countDown());
                indexed.await(20L, TimeUnit.SECONDS);
            }

            Log.i(TAG, "openArchiveFiles=" + assets.length
                    + " newlyInstalled=" + newFiles.size() + " path=" + directory.getAbsolutePath());
            return newFiles.size();
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
