package com.ali.carsystem;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import java.util.concurrent.ConcurrentHashMap;

public final class CarApplication extends Application implements ComponentCallbacks2 {
    private static final ConcurrentHashMap<String, Bitmap> BITMAPS = new ConcurrentHashMap<>();
    public static void putBitmap(String key, Bitmap bitmap) {
        if (key == null || bitmap == null || bitmap.isRecycled()) return;
        Bitmap old = BITMAPS.put(key, bitmap);
        if (old != null && old != bitmap && !old.isRecycled()) old.recycle();
    }
    public static Bitmap getBitmap(String key) {
        Bitmap b = BITMAPS.get(key);
        return b == null || b.isRecycled() ? null : b;
    }
    public static void clearBitmapCache() {
        BITMAPS.clear();
    }
    public static long availableMemoryMb(Context c) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        return info.availMem / (1024L * 1024L);
    }
    @Override public void onCreate() { super.onCreate(); AppDiagnostics.install(this); registerComponentCallbacks(this); }
    @Override public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_UI_HIDDEN) clearBitmapCache();
        else if (level >= TRIM_MEMORY_RUNNING_LOW) BITMAPS.clear();
    }
    @Override public void onLowMemory() { clearBitmapCache(); }
    @Override public void onConfigurationChanged(Configuration c) {}
}
