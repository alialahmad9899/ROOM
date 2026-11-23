package com.ali.carsystem;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private boolean destroyed;
    private final Runnable debouncedLibraryRefresh = () -> {
        if (!destroyed) refreshLibrary();
    };
    private final ContentObserver mediaStoreObserver = new ContentObserver(mainHandler) {
        @Override public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mainHandler.removeCallbacks(debouncedLibraryRefresh);
            mainHandler.postDelayed(debouncedLibraryRefresh, 1400L);
        }
        @Override public void onChange(boolean selfChange, Uri uri) {
            onChange(selfChange);
        }
    };
    private AudioManager audioManager;
    private MediaPlayer player;
    private int index = -1;
    private int pendingIndex = -1;
    private boolean prepared, repeat, shuffle, libraryLoading, libraryLoaded, foreground, refreshPending, resumePlaybackAfterLoad;
    private boolean resumeAfterTransientFocusLoss;
    private final BluetoothPlaybackPolicy bluetoothPlaybackPolicy = new BluetoothPlaybackPolicy();
    private BroadcastReceiver bluetoothReceiver;
    private boolean bluetoothReceiverRegistered;
    private int restoredPositionMs;
    private String pendingAction;
    private Listener listener;
    private final Runnable positionSaver = new Runnable() {
        @Override public void run() {
            if (!isPlaying()) return;
            savePlaybackPosition();
            mainHandler.postDelayed(this, 5000L);
        }
    };

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
        public final String name, artist, dataPath;
        public final boolean openArchive;

        Song(long id, String name, String artist, long duration, long albumId, String dataPath) {
            this.id = id;
            this.duration = duration;
            this.albumId = albumId;
            this.dataPath = dataPath == null ? "" : dataPath;
            String resolvedName = name == null || name.trim().isEmpty() ? "مقطع صوتي" : name;
            String resolvedArtist = artist == null ? "" : artist;
            this.openArchive = dataPath != null
                    && dataPath.replace((char)92, '/').contains("/ALI CAR/Open Arabic Archive/");
            if (openArchive && dataPath != null) {
                String filename = new java.io.File(dataPath).getName();
                String[] parts = filename.split("__", 3);
                if (parts.length == 3) {
                    resolvedArtist = parts[1].trim();
                    String title = parts[2];
                    int dot = title.lastIndexOf('.');
                    if (dot > 0) title = title.substring(0, dot);
                    resolvedName = title.replace('_', ' ').trim();
                }
            }
            this.name = resolvedName;
            this.artist = resolvedArtist;
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
        boolean waitingForBluetooth = prefs.getBoolean("bluetooth_auto_paused", false);
        bluetoothPlaybackPolicy.restore(waitingForBluetooth);
        resumePlaybackAfterLoad = prefs.getBoolean("resume_enabled", false) && !waitingForBluetooth;
        restoredPositionMs = Math.max(0, prefs.getInt("position_ms", 0));
        registerBluetoothReceiver();
        if (waitingForBluetooth && audioManager != null) {
            try {
                if (audioManager.isBluetoothA2dpOn() && bluetoothPlaybackPolicy.onProfileConnected()) {
                    prefs.edit().putBoolean("bluetooth_auto_paused", false)
                            .putBoolean("resume_enabled", true).apply();
                    resumePlaybackAfterLoad = true;
                }
            } catch (Exception error) {
                AppDiagnostics.record(this, "Restore Bluetooth music route", error);
            }
        }
        try {
            getContentResolver().registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaStoreObserver);
        } catch (Exception error) {
            AppDiagnostics.record(this, "Register local music library observer", error);
        }
        loadLibraryAsync();
    }

    private void registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return;
        bluetoothReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || !BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) return;
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                SharedPreferences prefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!bluetoothPlaybackPolicy.onProfileDisconnected(isPlaying())) return;
                    resumeAfterTransientFocusLoss = false;
                    savePlaybackPosition();
                    mainHandler.removeCallbacks(positionSaver);
                    try {
                        if (player != null && prepared && player.isPlaying()) player.pause();
                    } catch (Exception error) {
                        AppDiagnostics.record(MusicPlaybackService.this,
                                "Pause music after car Bluetooth disconnect", error);
                    }
                    prefs.edit().putBoolean("resume_enabled", true)
                            .putBoolean("bluetooth_auto_paused", true).apply();
                    notifyChanged();
                } else if (state == BluetoothProfile.STATE_CONNECTED
                        && bluetoothPlaybackPolicy.onProfileConnected()) {
                    prefs.edit().putBoolean("resume_enabled", true)
                            .putBoolean("bluetooth_auto_paused", false).apply();
                    resumeMusicAfterBluetoothReconnect();
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        try {
            registerReceiver(bluetoothReceiver, filter);
            bluetoothReceiverRegistered = true;
        } catch (Exception error) {
            bluetoothReceiver = null;
            AppDiagnostics.record(this, "Register car Bluetooth audio receiver", error);
        }
    }

    private void resumeMusicAfterBluetoothReconnect() {
        try {
            if (player != null && prepared) {
                int focus = audioManager == null ? AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        : audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN);
                if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    notifyChanged();
                    return;
                }
                player.start();
                mainHandler.removeCallbacks(positionSaver);
                mainHandler.postDelayed(positionSaver, 5000L);
                notifyChanged();
                return;
            }
            resumePlaybackAfterLoad = true;
            if (!libraryLoaded) {
                loadLibraryAsync();
                return;
            }
            if (index < 0) restoreLastSong();
            if (index >= 0) playAt(index);
            else {
                resumePlaybackAfterLoad = false;
                notifyChanged();
            }
        } catch (Exception error) {
            AppDiagnostics.record(this, "Resume music after car Bluetooth reconnect", error);
            notifyChanged();
        }
    }

    private void clearBluetoothAutoResumeForManualAction() {
        bluetoothPlaybackPolicy.userRequestedPlayback();
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE)
                .edit().putBoolean("bluetooth_auto_paused", false).apply();
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
            OpenMusicInstaller.install(MusicPlaybackService.this);
            ArrayList<Song> loaded = new ArrayList<>();
            String[] columns = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATA
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
                    int dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                    while (cursor.moveToNext()) {
                        long duration = cursor.getLong(durationColumn);
                        if (duration < 10000L) continue;
                        String dataPath = dataColumn >= 0 ? cursor.getString(dataColumn) : "";
                        if (dataPath == null || dataPath.isEmpty()
                                || !MusicFilePolicy.isAllowed(new File(dataPath).length())) continue;
                        loaded.add(new Song(cursor.getLong(idColumn), cursor.getString(titleColumn),
                                cursor.getString(artistColumn), duration, cursor.getLong(albumColumn), dataPath));
                    }
                }
            } catch (SecurityException ignored) {
                // On Android 5 the user grants shared-storage access at install time.
            } catch (Exception error) {
                AppDiagnostics.record(MusicPlaybackService.this, "Music MediaStore scan", error);
            }

            mainHandler.post(() -> {
                songs.clear();
                songs.addAll(loaded);
                libraryLoading = false;
                libraryLoaded = true;
                restoreLastSong();
                notifyChanged();
                if (refreshPending) {
                    refreshPending = false;
                    libraryLoaded = false;
                    loadLibraryAsync();
                }
                if (pendingAction != null) {
                    String action = pendingAction;
                    pendingAction = null;
                    resumePlaybackAfterLoad = false;
                    performAction(action);
                } else if (pendingIndex >= 0) {
                    int next = pendingIndex;
                    pendingIndex = -1;
                    resumePlaybackAfterLoad = false;
                    playAt(next);
                } else if (resumePlaybackAfterLoad && index >= 0) {
                    playAt(index);
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
        if (libraryLoading) {
            refreshPending = true;
            return;
        }
        libraryLoaded = false;
        loadLibraryAsync();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) mainHandler.post(listener::onMusicStateChanged);
    }

    public void playSongId(long songId) {
        clearBluetoothAutoResumeForManualAction();
        if (!libraryLoaded) { loadLibraryAsync(); return; }
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == songId) {
                playAt(i);
                return;
            }
        }
    }

    public void playAt(int requestedIndex) {
        resumeAfterTransientFocusLoss = false;
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
        prefs.edit().putLong("last_song_id", song.id).putString("last_song_name", song.name).putInt("position_ms", 0).apply();
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
                try {
                    if (resumePlaybackAfterLoad && restoredPositionMs > 0) {
                        mp.seekTo(Math.min(restoredPositionMs, mp.getDuration()));
                    }
                    resumePlaybackAfterLoad = false;
                    restoredPositionMs = 0;
                    mp.start();
                    getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                            .putBoolean("resume_enabled", true).apply();
                    mainHandler.removeCallbacks(positionSaver);
                    mainHandler.postDelayed(positionSaver, 5000L);
                } catch (Exception error) {
                    AppDiagnostics.record(this, "Resume music after reload", error);
                }
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
                AppDiagnostics.record(this, "MediaPlayer playback error what=" + what
                        + " extra=" + extra + " track=" + song.name,
                        new IllegalStateException("Android MediaPlayer rejected this local track"));
                notifyChanged();
                releasePlayer();
                return true;
            });
            player.prepareAsync();
            notifyChanged();
        } catch (Exception error) {
            AppDiagnostics.record(this, "Music MediaPlayer setup", error);
            releasePlayer();
            notifyChanged();
        }
    }

    public void toggle() {
        // A deliberate user action overrides pending Bluetooth and focus resumes.
        clearBluetoothAutoResumeForManualAction();
        resumeAfterTransientFocusLoss = false;
        if (player == null || index < 0) {
            if (!songs.isEmpty()) playAt(index < 0 ? 0 : index);
            else loadLibraryAsync();
            return;
        }
        try {
            if (prepared) {
                if (player.isPlaying()) {
                    player.pause();
                    savePlaybackPosition();
                    getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                            .putBoolean("resume_enabled", false).apply();
                    mainHandler.removeCallbacks(positionSaver);
                } else {
                    if (audioManager != null) {
                        int focus = audioManager.requestAudioFocus(this,
                                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
                    }
                    player.start();
                    getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                            .putBoolean("resume_enabled", true).apply();
                    mainHandler.removeCallbacks(positionSaver);
                    mainHandler.postDelayed(positionSaver, 5000L);
                }
            }
        } catch (Exception ignored) { }
        notifyChanged();
    }

    public void next() {
        clearBluetoothAutoResumeForManualAction();
        if (songs.isEmpty()) return;
        int nextIndex = shuffle ? new Random().nextInt(songs.size())
                : positiveModulo(index + 1, songs.size());
        playAt(nextIndex);
    }

    public void previous() {
        clearBluetoothAutoResumeForManualAction();
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
            try {
                player.seekTo(Math.max(0, Math.min(milliseconds, getDuration())));
                getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                        .putInt("position_ms", getPosition()).apply();
            } catch (Exception ignored) { }
        }
    }

    private void savePlaybackPosition() {
        if (player != null && prepared) {
            getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                    .putInt("position_ms", getPosition()).apply();
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

    public boolean removeSongFromPlaylist(long songId, String playlistName) {
        if (playlistName == null || playlistName.trim().isEmpty()
                || "كل الأغاني".equals(playlistName)) return false;
        String id = String.valueOf(songId);
        SharedPreferences prefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if ("المفضلة".equals(playlistName)) {
            Set<String> favorites = getStringSet("favorites");
            boolean removed = favorites.remove(id);
            if (removed) editor.putStringSet("favorites", favorites).apply();
            notifyChanged();
            return removed;
        }
        if ("آخر تشغيل".equals(playlistName)) {
            List<Long> recent = getRecentSongIds();
            boolean removed = recent.remove(songId);
            if (removed) {
                StringBuilder value = new StringBuilder();
                for (Long item : recent) {
                    if (value.length() > 0) value.append(',');
                    value.append(item);
                }
                editor.putString("recent_ids", value.toString()).apply();
            }
            notifyChanged();
            return removed;
        }
        Set<String> ids = getStringSet(playlistKey(playlistName));
        boolean removed = ids.remove(id);
        Set<String> hidden = getStringSet("playlist_hidden_" + playlistName);
        boolean newlyHidden = hidden.add(id);
        if (removed || newlyHidden) editor.putStringSet(playlistKey(playlistName), ids)
                .putStringSet("playlist_hidden_" + playlistName, hidden).apply();
        notifyChanged();
        return removed || newlyHidden;
    }

    /** Permanently remove one local MediaStore track after the UI has confirmed the action. */
    public boolean deleteSong(long songId) {
        int position = -1;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == songId) { position = i; break; }
        }
        if (position < 0) return false;
        Song target = songs.get(position);
        boolean wasCurrent = position == index;
        boolean wasPlaying = wasCurrent && isPlaying();
        if (wasCurrent) {
            savePlaybackPosition();
            mainHandler.removeCallbacks(positionSaver);
            releasePlayer();
        }
        int removedRows;
        try {
            Uri item = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    String.valueOf(songId));
            removedRows = getContentResolver().delete(item, null, null);
        } catch (Exception error) {
            AppDiagnostics.record(this, "Delete local music track " + songId, error);
            if (wasCurrent && wasPlaying) playAt(position);
            return false;
        }
        if (removedRows <= 0) {
            if (wasCurrent && wasPlaying) playAt(position);
            return false;
        }
        // MediaProvider normally removes the file itself. This guarded fallback handles
        // providers that remove only the index row on legacy Android 5 devices.
        try {
            File file = target.dataPath.isEmpty() ? null : new File(target.dataPath);
            if (file != null && file.isFile()) file.delete();
        } catch (Exception ignored) { }
        removeSongReferences(songId);
        songs.remove(position);
        if (wasCurrent) {
            index = -1;
            getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                    .remove("last_song_id").remove("last_song_name")
                    .putInt("position_ms", 0).putBoolean("resume_enabled", false).apply();
            if (!songs.isEmpty()) {
                int next = Math.min(position, songs.size() - 1);
                if (wasPlaying) {
                    playAt(next);
                } else {
                    index = next;
                    Song current = songs.get(index);
                    getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                            .putLong("last_song_id", current.id)
                            .putString("last_song_name", current.name).apply();
                    notifyChanged();
                }
            } else {
                index = -1;
                notifyChanged();
            }
        } else {
            if (position < index) index--;
            notifyChanged();
        }
        return true;
    }

    private void removeSongReferences(long songId) {
        String id = String.valueOf(songId);
        SharedPreferences prefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (!("favorites".equals(key) || key.startsWith("playlist_") || key.startsWith("playlist_hidden_"))) continue;
            Object saved = entry.getValue();
            if (!(saved instanceof Set)) continue;
            Set<String> clean = new HashSet<>();
            for (Object value : (Set<?>) saved) {
                if (value instanceof String && !id.equals(value)) clean.add((String) value);
            }
            editor.putStringSet(key, clean);
        }
        List<Long> recent = getRecentSongIds();
        recent.remove(songId);
        StringBuilder recentValue = new StringBuilder();
        for (Long value : recent) {
            if (recentValue.length() > 0) recentValue.append(',');
            recentValue.append(value);
        }
        editor.putString("recent_ids", recentValue.toString()).apply();
    }

    public boolean isHiddenFromPlaylist(long songId, String playlistName) {
        if (playlistName == null) return false;
        return getStringSet("playlist_hidden_" + playlistName).contains(String.valueOf(songId));
    }

    public void addSongToPlaylist(long songId, String playlistName) {
        if (playlistName == null || playlistName.trim().isEmpty()) return;
        String key = playlistKey(playlistName);
        Set<String> ids = getStringSet(key);
        ids.add(String.valueOf(songId));
        Set<String> hidden = getStringSet("playlist_hidden_" + playlistName);
        hidden.remove(String.valueOf(songId));
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit()
                .putStringSet(key, ids).putStringSet("playlist_hidden_" + playlistName, hidden).apply();
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
                .setOngoing(isPlaying() || bluetoothPlaybackPolicy.isWaitingForReconnect())
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

        if (playing || bluetoothPlaybackPolicy.isWaitingForReconnect()) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification());
                foreground = true;
            } catch (Exception error) {
                AppDiagnostics.record(this, "Keep music service alive for Bluetooth reconnect", error);
            }
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
            SharedPreferences prefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                // Permanent focus loss must never trigger an automatic restart.
                resumeAfterTransientFocusLoss = false;
                if (player.isPlaying()) player.pause();
                savePlaybackPosition();
                prefs.edit().putBoolean("resume_enabled", false).apply();
                mainHandler.removeCallbacks(positionSaver);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // Remember only playback that was active before the interruption.
                resumeAfterTransientFocusLoss = player.isPlaying()
                        && prefs.getBoolean("resume_enabled", false);
                if (player.isPlaying()) player.pause();
                savePlaybackPosition();
                mainHandler.removeCallbacks(positionSaver);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (player.isPlaying()) player.setVolume(0.25f, 0.25f);
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                player.setVolume(1f, 1f);
                boolean mayResume = !bluetoothPlaybackPolicy.isWaitingForReconnect()
                        && AudioFocusPolicy.shouldResumeAfterTransientLoss(
                                resumeAfterTransientFocusLoss, prefs.getBoolean("resume_enabled", false));
                resumeAfterTransientFocusLoss = false;
                if (mayResume && !player.isPlaying()) {
                    player.start();
                    prefs.edit().putBoolean("resume_enabled", true).apply();
                    mainHandler.removeCallbacks(positionSaver);
                    mainHandler.postDelayed(positionSaver, 5000L);
                }
            }
        } catch (Exception ignored) { }
        notifyChanged();
    }

    @Override public void onDestroy() {
        destroyed = true;
        savePlaybackPosition();
        if (bluetoothReceiverRegistered && bluetoothReceiver != null) {
            try { unregisterReceiver(bluetoothReceiver); } catch (Exception ignored) { }
            bluetoothReceiverRegistered = false;
        }
        try { getContentResolver().unregisterContentObserver(mediaStoreObserver); }
        catch (Exception ignored) { }
        mainHandler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (audioManager != null) audioManager.abandonAudioFocus(this);
        getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE).edit().putBoolean("playing", false).apply();
        libraryExecutor.shutdownNow();
        listener = null;
        if (foreground) stopForeground(true);
        super.onDestroy();
    }
}
