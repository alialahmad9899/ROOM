package com.ali.carsystem;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;

public final class MusicActivity extends Activity {
    private final ArrayList<Song> songs = new ArrayList<>();
    private MediaPlayer player;
    private int current = -1;
    private boolean prepared = false;
    private TextView nowPlaying;
    private TextView playButton;
    private ImageView albumArt;
    private TextView artFallback;
    private boolean receiverRegistered = false;
    private boolean screenOn = true;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenOn = false;
                pausePlayback();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                screenOn = true;
            }
        }
    };

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF080D13);
        getWindow().setNavigationBarColor(0xFF080D13);
        loadSongs();
        build();
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOff, screenFilter);
        receiverRegistered = true;
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(screenOff); } catch (RuntimeException ignored) { }
            receiverRegistered = false;
        }
        release();
        if (albumArt != null && albumArt.getDrawable() != null) {
            albumArt.setImageDrawable(null);
        }
        super.onDestroy();
    }

    private void loadSongs() {
        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String title = cursor.getString(1);
                    String artist = cursor.getString(2);
                    long duration = cursor.getLong(3);
                    long albumId = cursor.getLong(4);
                    Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    songs.add(new Song(
                        title == null ? "مقطع بدون اسم" : title,
                        artist == null ? "فنان غير معروف" : artist,
                        uri, duration, albumId
                    ));
                }
            }
        } catch (RuntimeException ignored) {
            // An unavailable removable storage volume must not crash the player.
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void build() {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(10));
        root.setBackgroundColor(0xFF081426);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ALI MUSIC", 25, 0xFF67D9FF, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView back = control("رجوع");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(92), dp(40)));
        root.addView(header);

        TextView subtitle = text("مشغل الموسيقى", 13, 0xFFB5C9E2, false);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout playerPanel = new LinearLayout(this);
        playerPanel.setGravity(Gravity.CENTER_VERTICAL);
        playerPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        playerPanel.setBackground(background(0xFF172C4C, dp(22)));
        FrameLayout artFrame = new FrameLayout(this);
        artFrame.setBackground(background(0xFF5B3BA8, dp(18)));
        albumArt = new ImageView(this);
        albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artFallback = text("♫", 48, 0xFFFFD66B, true);
        artFallback.setGravity(Gravity.CENTER);
        artFrame.addView(albumArt, new FrameLayout.LayoutParams(-1, -1));
        artFrame.addView(artFallback, new FrameLayout.LayoutParams(-1, -1));
        playerPanel.addView(artFrame, new LinearLayout.LayoutParams(dp(124), dp(124)));
        nowPlaying = text("اختر أغنية للبدء", 18, 0xFFFFFFFF, true);
        nowPlaying.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        nowPlaying.setPadding(dp(16), 0, dp(8), 0);
        playerPanel.addView(nowPlaying, new LinearLayout.LayoutParams(0, dp(116), 1f));
        root.addView(playerPanel, new LinearLayout.LayoutParams(-1, dp(148)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(7), 0, dp(7));
        TextView previous = control("⏮ السابق");
        playButton = control("▶ تشغيل");
        TextView next = control("التالي ⏭");
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(56), 1f));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, dp(56), 1.25f);
        playParams.leftMargin = dp(8); playParams.rightMargin = dp(8);
        controls.addView(playButton, playParams);
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(56), 1f));
        root.addView(controls);
        previous.setOnClickListener(v -> playIndex(Math.max(0, current - 1)));
        next.setOnClickListener(v -> playIndex(Math.min(songs.size() - 1, current + 1)));
        playButton.setOnClickListener(v -> togglePlayback());

        TextView listTitle = text("قائمة الأغاني", 17, 0xFFFFD66B, true);
        root.addView(listTitle, new LinearLayout.LayoutParams(-1, dp(32)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (songs.isEmpty()) {
            TextView empty = text("لم يتم العثور على ملفات موسيقى في الذاكرة أو بطاقة SD.", 14, 0xFFB5C9E2, false);
            empty.setPadding(dp(10), dp(18), dp(10), dp(18));
            list.addView(empty);
        }
        for (int index = 0; index < songs.size(); index++) {
            final int selectedIndex = index;
            Song song = songs.get(index);
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(5), dp(12), dp(5));
            item.setBackground(background(index % 2 == 0 ? 0xFF142844 : 0xFF1A3152, dp(12)));
            TextView number = text(String.format(java.util.Locale.US, "%02d", index + 1), 15,
                index % 2 == 0 ? 0xFF54D8C9 : 0xFFFFC66D, true);
            item.addView(number, new LinearLayout.LayoutParams(dp(42), dp(52)));
            TextView row = text(song.title + "\\n" + song.artist, 14, 0xFFF4F7FB, false);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            item.addView(row, new LinearLayout.LayoutParams(0, dp(52), 1f));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, dp(60));
            itemParams.bottomMargin = dp(5);
            list.addView(item, itemParams);
            item.setOnClickListener(v -> playIndex(selectedIndex));
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private android.graphics.drawable.GradientDrawable background(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable =
            new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), 0xFF263B49);
        return drawable;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create("sans-serif",
            bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private TextView control(String value) {
        TextView view = text(value, 15, 0xFFFFFFFF, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackground(background(0xFF176D83, dp(13)));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void togglePlayback() {
        if (player == null) {
            if (!songs.isEmpty()) playIndex(current < 0 ? 0 : current);
            return;
        }
        if (!prepared) {
            playButton.setText("جارٍ التحميل...");
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                playButton.setText("▶ متابعة");
            } else {
                player.start();
                playButton.setText("Ⅱ إيقاف مؤقت");
            }
        } catch (IllegalStateException ignored) {
            playButton.setText("▶ تشغيل");
        }
    }

    private void playIndex(int index) {
        if (index < 0 || index >= songs.size()) return;
        release();
        current = index;
        Song song = songs.get(index);
        updateAlbumArt(song.albumId);
        nowPlaying.setText(song.title + "\n" + song.artist);
        playButton.setText("جارٍ التحميل...");
        try {
            player = new MediaPlayer();
            prepared = false;
            player.setOnPreparedListener(mediaPlayer -> {
                if (isFinishing()) return;
                prepared = true;
                if (!screenOn) {
                    playButton.setText("متوقف عند إطفاء الشاشة");
                    return;
                }
                try {
                    mediaPlayer.start();
                    playButton.setText("Ⅱ إيقاف مؤقت");
                } catch (IllegalStateException ignored) {
                    playButton.setText("▶ تشغيل");
                }
            });
            player.setOnCompletionListener(mediaPlayer -> {
                prepared = false;
                if (current + 1 < songs.size()) playIndex(current + 1);
                else playButton.setText("▶ إعادة التشغيل");
            });
            player.setOnErrorListener((mediaPlayer, what, extra) -> {
                release();
                nowPlaying.setText("تعذّر تشغيل هذا الملف");
                playButton.setText("▶ تشغيل");
                return true;
            });
            player.setDataSource(this, song.uri);
            player.prepareAsync();
        } catch (IOException | RuntimeException error) {
            release();
            nowPlaying.setText("تعذّر فتح الملف الصوتي");
            playButton.setText("▶ تشغيل");
        }
    }

    private void updateAlbumArt(long albumId) {
        albumArt.setImageDrawable(null);
        artFallback.setVisibility(View.VISIBLE);
        Cursor cursor = null;
        try {
            Uri albumUri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId);
            cursor = getContentResolver().query(
                albumUri, new String[]{MediaStore.Audio.Albums.ALBUM_ART},
                null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String path = cursor.getString(0);
                if (path != null && !path.trim().isEmpty()) {
                    Bitmap bitmap = decodeSampledBitmap(path, 256, 256);
                    if (bitmap != null) {
                        albumArt.setImageBitmap(bitmap);
                        artFallback.setVisibility(View.GONE);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Missing or unreadable art is normal for some audio files.
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private Bitmap decodeSampledBitmap(String path, int targetWidth, int targetHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        int sample = 1;
        while (options.outWidth / (sample * 2) >= targetWidth
            && options.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2;
        }
        options.inSampleSize = sample;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private void pausePlayback() {
        if (player != null && prepared) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    playButton.setText("متوقف عند إطفاء الشاشة");
                }
            } catch (IllegalStateException ignored) { }
        }
    }

    private void release() {
        prepared = false;
        if (player != null) {
            try { player.setOnPreparedListener(null); } catch (RuntimeException ignored) { }
            try { player.stop(); } catch (RuntimeException ignored) { }
            try { player.release(); } catch (RuntimeException ignored) { }
            player = null;
        }
    }

    private static final class Song {
        final String title;
        final String artist;
        final Uri uri;
        final long duration;
        final long albumId;

        Song(String title, String artist, Uri uri, long duration, long albumId) {
            this.title = title;
            this.artist = artist;
            this.uri = uri;
            this.duration = duration;
            this.albumId = albumId;
        }
    }
}
