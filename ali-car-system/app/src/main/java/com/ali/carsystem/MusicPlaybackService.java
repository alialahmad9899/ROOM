package com.ali.carsystem;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MusicPlaybackService extends Service
        implements AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_STATE = "com.ali.carsystem.MUSIC_STATE";
    private static final String AUDIO_PREFS = "ali_car_audio";
    private static final int NOTIFICATION_ID = 7420;
    private static final String ACTION_TOGGLE = "com.ali.carsystem.TOGGLE";
    private static final String ACTION_NEXT = "com.ali.carsystem.NEXT";
    private static final String ACTION_PREVIOUS = "com.ali.carsystem.PREVIOUS";

    private final IBinder binder = new LocalBinder();
    private final ArrayList<Song> songs = new ArrayList<>();
    private final ExecutorService libraryExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ali-car-music-library");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private MediaPlayer player;
    private int index = -1;
    private int pendingIndex = -1;
    private boolean prepared, repeat, shuffle, libraryLoading, libraryLoaded, foreground;
    private String pendingAction;
    private Listener listener;

    public static boolean isAudioPlaying(Context context) {
        return context.getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE)
                .getBoolean("playing", false);
    }

    public interface Listener {
        void onMusicStateChanged();
    }

    public final class LocalBinder extends Binder {
        MusicPlaybackService getService() { return MusicPlaybackService.this; }
    }

    public static final class Song {
        public final long id, duration, albumId;
        public final String name, artist;

        Song(long id, String name, String artist, long duration, long albumId) {
            this.id = id;
            this.name = name == null || name.trim().isEmpty() ? "مقطع صوتي" : name;
            this.artist = artist == null ? "" : artist;
            this.duration = duration;
            this.albumId = albumId;
        }
    }

    public static void dispatch(Context context, String action) {
        Intent intent = new Intent(context, MusicPlaybackService.class);
        if ("previous".equals(action)) intent.setAction(ACTION_PREVIOUS);
        else if ("next".equals(action)) intent.setAction(ACTION_NEXT);
        else intent.setAction(ACTION_TOGGLE);
        try { context.startService(intent); } catch (Exception ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        SharedPreferences prefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
        shuffle = prefs.getBoolean("shuffle", false);
        repeat = prefs.getBoolean("repeat", false);
        loadLibraryAsync();
    }

    @Override public IBinder onBind(Intent intent) {
        loadLibraryAsync();
        return binder;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        loadLibraryAsync();
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (!libraryLoaded) pendingAction = action;
            else performAction(action);
        } else {
            notifyChanged();
        }
        return START_STICKY;
    }

    private void loadLibraryAsync() {
        if (libraryLoading || libraryLoaded) return;
        libraryLoading = true;
        libraryExecutor.execute(() -> {
            ArrayList<Song> loaded = new ArrayList<>();
            String[] columns = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID
            };
            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                    MediaStore.Audio.Media.IS_MUSIC + "=1", null,
                    MediaStore.Audio.Media.TITLE + " ASC")) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                    int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                    int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                    int durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                    int albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                    while (cursor.moveToNext()) {
                        long duration = cursor.getLong(durationColumn);
                        if (duration < 10000L) continue;
                        loaded.add(new Song(cursor.getLong(idColumn), cursor.getString(titleColumn),
                                cursor.getString(artistColumn), duration, cursor.getLong(albumColumn)));
                    }
                }
            } catch (SecurityException ignored) {
                // On Android 5 the user grants shared-storage access at install time.
            } catch (Exception ignored) { }

            mainHandler.post(() -> {
                songs.clear();
                songs.addAll(loaded);
                libraryLoading = false;
                libraryLoaded = true;
                restoreLastSong();
                notifyChanged();
                if (pendingAction != null) {
                    String action = pendingAction;
                    pendingAction = null;
                    performAction(action);
                }
                if (pendingIndex >= 0) {
                    int next = pendingIndex;
                    pendingIndex = -1;
                    playAt(next);
                }
            });
        });
    }

    private void restoreLastSong() {
        long lastId = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).getLong("last_song_id", -1L);
        if (lastId < 0L) return;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == lastId) {
                index = i;
                return;
            }
        }
    }

    private void performAction(String action) {
        if (ACTION_TOGGLE.equals(action)) toggle();
        else if (ACTION_NEXT.equals(action)) next();
        else if (ACTION_PREVIOUS.equals(action)) previous();
    }

    public List<Song> getSongs() {
        return new ArrayList<>(songs);
    }

    public Song getCurrentSong() {
        return index >= 0 && index < songs.size() ? songs.get(index) : null;
    }

    public int getPosition() {
        try { return player != null && prepared ? player.getCurrentPosition() : 0; }
        catch (Exception ignored) { return 0; }
    }

    public int getDuration() {
        try { return player != null && prepared ? player.getDuration() : 0; }
        catch (Exception ignored) { return 0; }
    }

    public int getAudioSessionId() {
        try { return player == null ? 0 : player.getAudioSessionId(); }
        catch (Exception ignored) { return 0; }
    }

    public boolean isPlaying() {
        try { return player != null && prepared && player.isPlaying(); }
        catch (Exception ignored) { return false; }
    }

    public boolean isPrepared() { return prepared; }
    public boolean isLibraryLoaded() { return libraryLoaded; }

    public void refreshLibrary() {
        if (libraryLoading) return;
        libraryLoaded = false;
        loadLibraryAsync();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) mainHandler.post(listener::onMusicStateChanged);
    }

    public void playSongId(long songId) {
        if (!libraryLoaded) { loadLibraryAsync(); return; }
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == songId) {
                playAt(i);
                return;
            }
        }
    }

    public void playAt(int requestedIndex) {
        if (!libraryLoaded) {
            pendingIndex = requestedIndex;
            loadLibraryAsync();
            return;
        }
        if (requestedIndex < 0 || requestedIndex >= songs.size()) return;
        releasePlayer();
        index = requestedIndex;
        Song song = songs.get(index);
        SharedPreferences prefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
        prefs.edit().putLong("last_song_id", song.id).putString("last_song_name", song.name).apply();
        rememberRecent(song.id);

        try {
            if (audioManager != null) {
                int focus = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    notifyChanged();
                    return;
                }
            }
            player = new MediaPlayer();
            prepared = false;
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    String.valueOf(song.id));
            player.setDataSource(this, uri);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                try { mp.start(); } catch (Exception ignored) { }
                notifyChanged();
            });
            player.setOnCompletionListener(mp -> {
                if (repeat) {
                    try { mp.seekTo(0); mp.start(); } catch (Exception ignored) { next(); }
                } else {
                    next();
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                prepared = false;
                notifyChanged();
                releasePlayer();
                return true;
            });
            player.prepareAsync();
            notifyChanged();
        } catch (Exception error) {
            releasePlayer();
            notifyChanged();
        }
    }

    public void toggle() {
        if (player == null || index < 0) {
            if (!songs.isEmpty()) playAt(index < 0 ? 0 : index);
            else loadLibraryAsync();
            return;
        }
        try {
            if (prepared) {
                if (player.isPlaying()) player.pause();
                else {
                    if (audioManager != null) audioManager.requestAudioFocus(this,
                            AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                    player.start();
                }
            }
        } catch (Exception ignored) { }
        notifyChanged();
    }

    public void next() {
        if (songs.isEmpty()) return;
        int nextIndex = shuffle ? new Random().nextInt(songs.size())
                : positiveModulo(index + 1, songs.size());
        playAt(nextIndex);
    }

    public void previous() {
        if (songs.isEmpty()) return;
        if (player != null && prepared && getPosition() > 3000) {
            try { player.seekTo(0); } catch (Exception ignored) { }
            notifyChanged();
            return;
        }
        playAt(index < 0 ? 0 : positiveModulo(index - 1, songs.size()));
    }

    private int positiveModulo(int value, int modulus) {
        return ((value % modulus) + modulus) % modulus;
    }

    public void seekTo(int milliseconds) {
        if (player != null && prepared) {
            try { player.seekTo(Math.max(0, Math.min(milliseconds, getDuration()))); }
            catch (Exception ignored) { }
        }
    }

    public void setShuffle(boolean enabled) {
        shuffle = enabled;
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit().putBoolean("shuffle", enabled).apply();
        notifyChanged();
    }
    public boolean isShuffle() { return shuffle; }

    public void setRepeat(boolean enabled) {
        repeat = enabled;
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit().putBoolean("repeat", enabled).apply();
        notifyChanged();
    }
    public boolean isRepeat() { return repeat; }

    public boolean isFavorite(long songId) {
        return getStringSet("favorites").contains(String.valueOf(songId));
    }

    public boolean toggleFavorite(long songId) {
        Set<String> favorites = getStringSet("favorites");
        String id = String.valueOf(songId);
        boolean added;
        if (favorites.contains(id)) {
            favorites.remove(id);
            added = false;
        } else {
            favorites.add(id);
            added = true;
        }
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                .putStringSet("favorites", favorites).apply();
        notifyChanged();
        return added;
    }

    public void addSongToPlaylist(long songId, String playlistName) {
        if (playlistName == null || playlistName.trim().isEmpty()) return;
        String key = playlistKey(playlistName);
        Set<String> ids = getStringSet(key);
        ids.add(String.valueOf(songId));
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit().putStringSet(key, ids).apply();
        notifyChanged();
    }

    public List<Long> getPlaylistSongIds(String playlistName) {
        Set<String> ids = getStringSet(playlistKey(playlistName));
        ArrayList<Long> result = new ArrayList<>();
        for (String id : ids) {
            try { result.add(Long.parseLong(id)); } catch (Exception ignored) { }
        }
        return result;
    }

    public List<Long> getRecentSongIds() {
        String recent = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).getString("recent_ids", "");
        ArrayList<Long> result = new ArrayList<>();
        if (recent == null || recent.isEmpty()) return result;
        for (String id : recent.split(",")) {
            try { result.add(Long.parseLong(id)); } catch (Exception ignored) { }
        }
        return result;
    }

    private String playlistKey(String name) { return "playlist_" + name; }

    private Set<String> getStringSet(String key) {
        Set<String> saved = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE)
                .getStringSet(key, Collections.<String>emptySet());
        return saved == null ? new HashSet<>() : new HashSet<>(saved);
    }

    private void rememberRecent(long songId) {
        List<Long> current = getRecentSongIds();
        current.remove(songId);
        current.add(0, songId);
        while (current.size() > 30) current.remove(current.size() - 1);
        StringBuilder value = new StringBuilder();
        for (Long id : current) {
            if (value.length() > 0) value.append(',');
            value.append(id);
        }
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                .putString("recent_ids", value.toString()).apply();
    }

    private Notification buildNotification() {
        Song song = getCurrentSong();
        String title = song == null ? "ALI CAR • الموسيقى" : song.name;
        String subtitle = song == null ? "المكتبة المحلية" : song.artist;
        Intent open = new Intent(this, MusicActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT);
        Intent previousIntent = new Intent(this, MusicPlaybackService.class).setAction(ACTION_PREVIOUS);
        Intent toggleIntent = new Intent(this, MusicPlaybackService.class).setAction(ACTION_TOGGLE);
        Intent nextIntent = new Intent(this, MusicPlaybackService.class).setAction(ACTION_NEXT);
        PendingIntent previous = PendingIntent.getService(this, NOTIFICATION_ID + 1, previousIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent toggle = PendingIntent.getService(this, NOTIFICATION_ID + 2, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent next = PendingIntent.getService(this, NOTIFICATION_ID + 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(subtitle == null ? "" : subtitle)
                .setContentIntent(content)
                .setOngoing(isPlaying())
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_previous, "السابق", previous)
                .addAction(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying() ? "إيقاف مؤقت" : "تشغيل", toggle)
                .addAction(android.R.drawable.ic_media_next, "التالي", next)
                .build();
    }

    private void notifyChanged() {
        boolean playing = isPlaying();
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit().putBoolean("playing", playing).apply();
        if (listener != null) listener.onMusicStateChanged();
        Song song = getCurrentSong();
        Intent update = new Intent(ACTION_STATE);
        update.setPackage(getPackageName());
        update.putExtra("title", song == null ? "" : song.name);
        update.putExtra("playing", playing);
        update.putExtra("artist", song == null ? "" : song.artist);
        update.putExtra("library_loaded", libraryLoaded);
        sendBroadcast(update);

        if (playing) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification());
                foreground = true;
            } catch (Exception ignored) { }
        } else if (foreground) {
            stopForeground(true);
            foreground = false;
        }
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override public void onAudioFocusChange(int focusChange) {
        if (player == null || !prepared) return;
        try {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (player.isPlaying()) player.pause();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                player.setVolume(0.25f, 0.25f);
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                player.setVolume(1f, 1f);
            }
        } catch (Exception ignored) { }
        notifyChanged();
    }

    @Override public void onDestroy() {
        releasePlayer();
        if (audioManager != null) audioManager.abandonAudioFocus(this);
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit().putBoolean("playing", false).apply();
        mainHandler.removeCallbacksAndMessages(null);
        libraryExecutor.shutdownNow();
        listener = null;
        if (foreground) stopForeground(true);
        super.onDestroy();
    }
}
