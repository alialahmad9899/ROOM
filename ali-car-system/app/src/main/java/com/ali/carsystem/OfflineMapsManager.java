package com.ali.carsystem;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.Locale;

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
    private static final long STORAGE_SAFETY_BYTES = 32L * 1024L * 1024L;
    private static final String MANIFEST = "manifest.sha256";
    private static final String[] REQUIRED_FILES = {
            "syria.map", "syria-places.sqlite",
            "brouter/segments4/E35_N30.rd5", "brouter/segments4/E35_N35.rd5",
            "brouter/segments4/E40_N30.rd5", "brouter/segments4/E40_N35.rd5",
            "brouter/profiles2/car-fast.brf", "brouter/profiles2/lookups.dat"
    };
    private OfflineMapsManager() { }

    static File getRoot(Context context) {
        // Offline map bundles are large and must install reliably even when an
        // emulator/device SD card is missing, read-only, or not mounted yet.
        // Keep the verified data under this app's private internal files area.
        File privateFiles = context.getFilesDir();
        if (privateFiles == null)
            throw new IllegalStateException("مسار ملفات التطبيق الداخلي غير متاح");
        File root = new File(privateFiles, "offline");
        if (!root.isDirectory() && !root.mkdirs())
            throw new IllegalStateException("تعذر إنشاء مجلد الخرائط داخل مساحة التطبيق: " + root.getAbsolutePath());
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
        File root = getRoot(context);
        return getMapFile(context) != null && getPlacesDatabase(context) != null
                && hasOfflineRouting(context) && hasValidManifest(root);
    }

    static long requiredBundleBytes(Context context) {
        try {
            List<ManifestEntry> entries = readManifest(context.getAssets().open("offline/" + MANIFEST));
            long total = 0L;
            for (ManifestEntry entry : entries) total = Math.addExact(total, entry.size);
            return total;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    static long availableStorageBytes(Context context) {
        try {
            StatFs stat = new StatFs(getRoot(context).getParentFile().getAbsolutePath());
            return stat.getAvailableBytes();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    static boolean hasValidManifest(File root) {
        File manifest = new File(root, MANIFEST);
        if (!manifest.isFile() || manifest.length() < 64L) return false;
        try {
            List<ManifestEntry> entries = readManifest(new FileInputStream(manifest));
            if (!containsRequired(entries)) return false;
            for (ManifestEntry entry : entries) {
                File file = new File(root, entry.path);
                if (!file.isFile() || file.length() != entry.size) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean verifyInstalledBundle(Context context) {
        File root = getRoot(context);
        try {
            List<ManifestEntry> entries = readManifest(new FileInputStream(new File(root, MANIFEST)));
            if (!containsRequired(entries)) return false;
            for (ManifestEntry entry : entries) {
                File file = new File(root, entry.path);
                if (!file.isFile() || file.length() != entry.size
                        || !entry.sha256.equalsIgnoreCase(sha256(file))) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean containsRequired(List<ManifestEntry> entries) {
        java.util.HashSet<String> found = new java.util.HashSet<>();
        for (ManifestEntry entry : entries) found.add(entry.path);
        for (String required : REQUIRED_FILES) if (!found.contains(required)) return false;
        return true;
    }

    private static final class ManifestEntry {
        final String sha256;
        final long size;
        final String path;
        ManifestEntry(String sha256, long size, String path) {
            this.sha256 = sha256;
            this.size = size;
            this.path = path;
        }
    }

    private static List<ManifestEntry> readManifest(InputStream input) throws Exception {
        ArrayList<ManifestEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = trimmed.split(" ", 3);
                if (parts.length != 3 || parts[0].length() != 64)
                    throw new IllegalArgumentException("صيغة manifest.sha256 غير صالحة");
                long size = Long.parseLong(parts[1]);
                if (size <= 0L || parts[2].startsWith("/") || parts[2].contains("../"))
                    throw new IllegalArgumentException("مسار أو حجم غير صالح في manifest.sha256");
                entries.add(new ManifestEntry(parts[0], size, parts[2]));
            }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("manifest.sha256 فارغ");
        return entries;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format(Locale.US, "%02x", value & 0xff));
        return hex.toString();
    }

    private static long directoryBytes(File root) {
        if (root == null || !root.exists()) return 0L;
        if (root.isFile()) return root.length();
        long total = 0L;
        File[] children = root.listFiles();
        if (children != null) for (File child : children) total += directoryBytes(child);
        return total;
    }

    private static void requireStorage(Context context, long requiredBytes, long existingBytes) {
        long required = Math.max(0L, requiredBytes) + Math.max(0L, existingBytes)
                + STORAGE_SAFETY_BYTES;
        long available = availableStorageBytes(context);
        if (available < required) {
            throw new IllegalStateException("مساحة التخزين غير كافية لتثبيت الخرائط. المطلوب نحو "
                    + (required / (1024L * 1024L)) + " MB والمتاح "
                    + (available / (1024L * 1024L)) + " MB. حرّر مساحة أو اختر بطاقة تخزين أخرى عند استيراد الحزمة.");
        }
    }

    private static void writeManifest(File root, List<ManifestEntry> entries) throws Exception {
        File manifest = new File(root, MANIFEST);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(manifest, false))) {
            for (ManifestEntry entry : entries) {
                writer.write(entry.sha256 + " " + entry.size + " " + entry.path);
                writer.newLine();
            }
        }
    }

    private static List<ManifestEntry> createManifest(File root) throws Exception {
        ArrayList<ManifestEntry> entries = new ArrayList<>();
        for (String relative : REQUIRED_FILES) {
            File file = new File(root, relative);
            if (!file.isFile() || file.length() <= 0L)
                throw new IllegalArgumentException("ملف خريطة مطلوب مفقود: " + relative);
            entries.add(new ManifestEntry(sha256(file), file.length(), relative));
        }
        return entries;
    }

    static String status(Context context) {
        boolean map = getMapFile(context) != null;
        boolean places = getPlacesDatabase(context) != null;
        boolean routing = hasOfflineRouting(context);
        boolean integrity = hasValidManifest(getRoot(context));
        return "الخريطة: " + (map ? "جاهزة" : "غير مثبتة")
                + " • الأماكن: " + (places ? "جاهزة" : "غير مثبتة")
                + " • توجيه دون إنترنت: " + (routing ? "جاهز" : "بيانات التوجيه ناقصة")
                + " • سلامة الملفات: " + (integrity ? "البيانات موثقة" : "يلزم التحقق أو إعادة التثبيت");
    }

    static synchronized void ensureBundledAssets(Context context) throws Exception {
        AssetManager assets = context.getAssets();
        List<ManifestEntry> expected = readManifest(assets.open("offline/" + MANIFEST));
        File root = getRoot(context);
        if (getMapFile(context) != null && getPlacesDatabase(context) != null
                && hasOfflineRouting(context) && hasValidManifest(root)) return;
        long expectedBytes = 0L;
        for (ManifestEntry entry : expected) expectedBytes = Math.addExact(expectedBytes, entry.size);
        requireStorage(context, expectedBytes, directoryBytes(root));
        String[] bundled = assets.list("offline");
        if (bundled == null || bundled.length == 0) return;

        File staging = new File(root.getParentFile(), "offline-assets-staging");
        deleteTree(staging);
        if (!staging.mkdirs() && !staging.isDirectory())
            throw new IllegalStateException("تعذر إنشاء مجلد تثبيت الخرائط المضمّنة");
        copyAssetDirectory(assets, "offline", staging);

        if (!containsRequired(expected) || !hasValidManifest(staging)
                || !verifyDirectory(staging, expected)) {
            deleteTree(staging);
            throw new IllegalStateException("فشل التحقق من SHA-256 لملفات الخرائط المنسوخة؛ لم يتم استبدال الحزمة السابقة");
        }

        File backup = new File(root.getParentFile(), "offline-bundled-backup");
        deleteTree(backup);
        if (root.exists() && !root.renameTo(backup))
            throw new IllegalStateException("تعذر حفظ نسخة الخرائط المحلية السابقة");
        if (!staging.renameTo(root)) {
            if (backup.exists()) backup.renameTo(root);
            throw new IllegalStateException("تعذر تثبيت بيانات الخرائط المضمّنة");
        }
        deleteTree(backup);
    }

    private static boolean verifyDirectory(File root, List<ManifestEntry> entries) throws Exception {
        for (ManifestEntry entry : entries) {
            File file = new File(root, entry.path);
            if (!file.isFile() || file.length() != entry.size
                    || !entry.sha256.equalsIgnoreCase(sha256(file))) return false;
        }
        return true;
    }

    private static void copyAssetDirectory(AssetManager assets, String assetPath, File target) throws Exception {
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            if (!target.exists() && !target.mkdirs()) throw new IllegalStateException("تعذر إنشاء " + target.getName());
            for (String child : children)
                copyAssetDirectory(assets, assetPath + "/" + child, new File(target, child));
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IllegalStateException("تعذر إنشاء مجلد " + parent.getName());
        try (InputStream input = new BufferedInputStream(assets.open(assetPath));
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        }
    }

    static void installArchive(Context context, Uri source) throws Exception {
        if (source == null) throw new IllegalArgumentException("لم يتم اختيار ملف");
        File root = getRoot(context);
        File staging = new File(root.getParentFile(), "offline-import-staging");
        requireStorage(context, 250L * 1024L * 1024L, directoryBytes(root));
        deleteTree(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) throw new IllegalStateException("تعذر إنشاء مجلد الاستيراد");

        long total = 0L;
        InputStream opened = context.getContentResolver().openInputStream(source);
        if (opened == null) throw new IllegalArgumentException("تعذر فتح ملف الحزمة");
        try (InputStream raw = opened;
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                while (name.startsWith("./")) name = name.substring(2);
                if (name.isEmpty()) { zip.closeEntry(); continue; }
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
        String[] requiredSegments = {"E35_N30.rd5", "E35_N35.rd5", "E40_N30.rd5", "E40_N35.rd5"};
        boolean segmentsReady = true;
        for (String segmentName : requiredSegments) {
            File segment = new File(staging, "brouter/segments4/" + segmentName);
            if (!segment.isFile() || segment.length() < 4096) segmentsReady = false;
        }
        File carProfile = new File(staging, "brouter/profiles2/car-fast.brf");
        File lookups = new File(staging, "brouter/profiles2/lookups.dat");
        if (!map.isFile() || map.length() < 1024 || !places.isFile() || places.length() < 1024
                || !segmentsReady || !carProfile.isFile() || !lookups.isFile()) {
            deleteTree(staging);
            throw new IllegalArgumentException("الحزمة ناقصة: يلزم ملف الخريطة وقاعدة الأماكن وبيانات التوجيه");
        }
        List<ManifestEntry> importedManifest = createManifest(staging);
        writeManifest(staging, importedManifest);
        if (!verifyDirectory(staging, importedManifest)) {
            deleteTree(staging);
            throw new IllegalStateException("فشل التحقق من سلامة ملفات حزمة الخرائط بعد فك الضغط");
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
