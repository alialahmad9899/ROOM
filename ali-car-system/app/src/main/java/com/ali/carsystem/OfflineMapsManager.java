package com.ali.carsystem;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Locates and safely imports the separately delivered Syria offline data package. */
final class OfflineMapsManager {
    private static final long MAX_UNPACKED_BYTES = 1400L * 1024L * 1024L;
    private OfflineMapsManager() { }

    static File getRoot(Context context) {
        File external = context.getExternalFilesDir("offline");
        File root = external != null ? external : new File(context.getFilesDir(), "offline");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    static File getMapFile(Context context) {
        File direct = new File(getRoot(context), "syria.map");
        if (direct.isFile() && direct.length() > 1024) return direct;
        File nested = new File(new File(getRoot(context), "maps"), "syria.map");
        return nested.isFile() && nested.length() > 1024 ? nested : null;
    }

    static File[] getMapFiles(Context context) {
        File root = getRoot(context);
        File[] maps = root.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.US).endsWith(".map"));
        if (maps != null && maps.length > 0) return maps;
        File nested = new File(root, "maps");
        maps = nested.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.US).endsWith(".map"));
        return maps == null ? new File[0] : maps;
    }

    static File getPlacesDatabase(Context context) {
        File root = getRoot(context);
        File a = new File(root, "syria-places.sqlite");
        if (a.isFile() && a.length() > 1024) return a;
        File b = new File(root, "places.sqlite");
        return b.isFile() && b.length() > 1024 ? b : null;
    }

    static File getBrouterRoot(Context context) {
        return new File(getRoot(context), "brouter");
    }

    static boolean hasOfflineRouting(Context context) {
        File root = getBrouterRoot(context);
        File segments = new File(root, "segments4");
        File profiles = new File(root, "profiles2");
        String[] required = {"E35_N30.rd5", "E35_N35.rd5", "E40_N30.rd5", "E40_N35.rd5"};
        for (String name : required) {
            File file = new File(segments, name);
            if (!file.isFile() || file.length() < 4096) return false;
        }
        return new File(profiles, "car-fast.brf").isFile()
                && new File(profiles, "lookups.dat").isFile();
    }

    static boolean isInstalled(Context context) {
        return getMapFile(context) != null && getPlacesDatabase(context) != null;
    }

    static String status(Context context) {
        boolean map = getMapFile(context) != null;
        boolean places = getPlacesDatabase(context) != null;
        boolean routing = hasOfflineRouting(context);
        return "الخريطة: " + (map ? "جاهزة" : "غير مثبتة")
                + " • الأماكن: " + (places ? "جاهزة" : "غير مثبتة")
                + " • توجيه دون إنترنت: " + (routing ? "جاهز" : "بيانات التوجيه ناقصة");
    }

    static void installArchive(Context context, Uri source) throws Exception {
        if (source == null) throw new IllegalArgumentException("لم يتم اختيار ملف");
        File root = getRoot(context);
        File staging = new File(root.getParentFile(), "offline-import-staging");
        deleteTree(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) throw new IllegalStateException("تعذر إنشاء مجلد الاستيراد");

        long total = 0L;
        try (InputStream raw = context.getContentResolver().openInputStream(source);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            if (raw == null) throw new IllegalArgumentException("تعذر فتح ملف الحزمة");
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals(".."))
                    throw new SecurityException("مسار غير آمن داخل حزمة الخرائط");
                File out = new File(staging, name);
                String canonicalRoot = staging.getCanonicalPath() + File.separator;
                if (!out.getCanonicalPath().startsWith(canonicalRoot))
                    throw new SecurityException("مسار ملف خارج مجلد الخرائط");
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream stream = new FileOutputStream(out)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            total += n;
                            if (total > MAX_UNPACKED_BYTES) throw new IllegalArgumentException("حجم حزمة الخرائط يتجاوز 1.4 GB");
                            stream.write(buffer, 0, n);
                        }
                        stream.flush();
                    }
                }
                zip.closeEntry();
            }
        }

        File map = new File(staging, "syria.map");
        File places = new File(staging, "syria-places.sqlite");
        File segment = new File(staging, "brouter/segments4/E35_N30.rd5");
        if (!map.isFile() || map.length() < 1024 || !places.isFile() || places.length() < 1024
                || !segment.isFile()) {
            deleteTree(staging);
            throw new IllegalArgumentException("الحزمة ناقصة: يلزم ملف الخريطة وقاعدة الأماكن وبيانات التوجيه");
        }

        File backup = new File(root.getParentFile(), "offline-previous");
        deleteTree(backup);
        if (root.exists() && !root.renameTo(backup))
            throw new IllegalStateException("تعذر حفظ الحزمة السابقة");
        if (!staging.renameTo(root)) {
            if (backup.exists()) backup.renameTo(root);
            throw new IllegalStateException("تعذر تثبيت حزمة الخرائط");
        }
        deleteTree(backup);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
