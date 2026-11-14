package com.ali.carsystem;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small private crash log for field diagnosis. The original Android crash handler
 * is always invoked; this class never swallows or resumes an uncaught exception.
 */
public final class AppDiagnostics {
    private static final String TAG = "ALI-CAR-Diagnostics";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Object LOCK = new Object();
    private static volatile Context applicationContext;

    private AppDiagnostics() { }

    public static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        applicationContext = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            record(applicationContext, "UNCAUGHT EXCEPTION on " + thread.getName(), error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    public static void record(Context context, String operation, Throwable error) {
        if (context == null) return;
        try {
            File directory = new File(context.getFilesDir(), "diagnostics");
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            File log = new File(directory, "app-errors.log");
            synchronized (LOCK) {
                if (log.isFile() && log.length() > 512L * 1024L) {
                    File previous = new File(directory, "app-errors.previous.log");
                    if (previous.exists()) previous.delete();
                    if (!log.renameTo(previous)) log.delete();
                }
                try (PrintWriter writer = new PrintWriter(new FileWriter(log, true))) {
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                            Locale.US).format(new Date());
                    writer.println("[" + timestamp + "] " + (operation == null ? "unknown" : operation));
                    if (error != null) error.printStackTrace(writer);
                    writer.println();
                    writer.flush();
                }
            }
        } catch (Exception writeError) {
            Log.e(TAG, "Could not persist internal diagnostic log", writeError);
        }
    }

    public static File getLogFile(Context context) {
        return new File(new File(context.getFilesDir(), "diagnostics"), "app-errors.log");
    }
}
