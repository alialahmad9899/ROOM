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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackgroundColor(0xFF080D13);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = text("ALI MUSIC", 24, 0xFFF2F6F9, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView subtitle = text("مشغل الموسيقى المحلي", 13, 0xFF42D9D0, false);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(25)));

        LinearLayout nowPlayingPanel = new LinearLayout(this);
        nowPlayingPanel.setOrientation(LinearLayout.HORIZONTAL);
        nowPlayingPanel.setGravity(Gravity.CENTER_VERTICAL);
        nowPlayingPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        nowPlayingPanel.setBackground(background(0xFF111C27, dp(18)));

        FrameLayout artFrame = new FrameLayout(this);
        artFrame.setGravity(Gravity.CENTER);
        artFrame.setBackground(background(0xFF172633, dp(12)));
        albumArt = new ImageView(this);
        albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artFallback = text("♫", 42, 0xFF42D9D0, true);
        artFallback.setGravity(Gravity.CENTER);
        artFrame.addView(albumArt, new LinearLayout.LayoutParams(-1, -1));
        artFrame.addView(artFallback, new LinearLayout.LayoutParams(-1, -1));
        nowPlayingPanel.addView(artFrame, new LinearLayout.LayoutParams(dp(112), dp(112)));

        nowPlaying = text("اختر أغنية للبدء", 17, 0xFFF2F6F9, true);
        nowPlaying.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        nowPlaying.setPadding(dp(14), 0, dp(8), 0);
        nowPlayingPanel.addView(nowPlaying, new LinearLayout.LayoutParams(0, dp(105), 1f));
        root.addView(nowPlayingPanel, new LinearLayout.LayoutParams(-1, dp(128)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(5), 0, dp(5));
        TextView previous = control("السابق");
        playButton = control("▶ تشغيل");
        TextView next = control("التالي");
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(55), 1f));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, dp(55), 1.25f);
        playParams.leftMargin = dp(6);
        playParams.rightMargin = dp(6);
        controls.addView(playButton, playParams);
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(55), 1f));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(65)));

        previous.setOnClickListener(v -> playIndex(Math.max(0, current - 1)));
        next.setOnClickListener(v -> playIndex(Math.min(songs.size() - 1, current + 1)));
        playButton.setOnClickListener(v -> togglePlayback());

        TextView listTitle = text("قائمة الأغاني", 15, 0xFFF2F6F9, true);
        root.addView(listTitle, new LinearLayout.LayoutParams(-1, dp(30)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (songs.isEmpty()) {
            TextView empty = text(
                "لم يتم العثور على ملفات موسيقى. أضف ملفات MP3 أو AAC إلى الذاكرة أو بطاقة SD.",
                14, 0xFF91A4B2, false);
            empty.setPadding(dp(8), dp(18), dp(8), dp(18));
            list.addView(empty);
        }
        for (int index = 0; index < songs.size(); index++) {
            final int selectedIndex = index;
            Song song = songs.get(index);
            String rowText = (index + 1) + ".  " + song.title + "\n      " + song.artist;
            TextView row = text(rowText, 14, 0xFFF2F6F9, false);
            row.setPadding(dp(12), dp(7), dp(12), dp(7));
            row.setBackgroundColor(index % 2 == 0 ? 0xFF111C27 : 0xFF172633);
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(58)));
            row.setOnClickListener(v -> playIndex(selectedIndex));
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView back = control("العودة إلى ALI CAR");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(45)));
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
        TextView view = text(value, 15, 0xFF42D9D0, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackground(background(0xFF172633, dp(13)));
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
