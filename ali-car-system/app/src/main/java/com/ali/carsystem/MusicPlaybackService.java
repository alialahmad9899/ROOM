package com.ali.carsystem;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.io.IOException;

/** Persistent low-overhead local music playback with transient ducking for navigation speech. */
public final class MusicPlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    public static final String ACTION_PLAY = "com.ali.carsystem.music.PLAY";
    public static final String ACTION_TOGGLE = "com.ali.carsystem.music.TOGGLE";
    public static final String ACTION_PAUSE = "com.ali.carsystem.music.PAUSE";
    public static final String ACTION_SEEK = "com.ali.carsystem.music.SEEK";
    public static final String ACTION_STOP = "com.ali.carsystem.music.STOP";
    public static final String ACTION_STATE = "com.ali.carsystem.music.STATE";

    public static final String EXTRA_URI = "song_uri";
    public static final String EXTRA_TITLE = "song_title";
    public static final String EXTRA_ARTIST = "song_artist";
    public static final String EXTRA_POSITION = "position_ms";

    private static final String PREFS = "ali_car_music_state";
    private static final int NOTIFICATION_ID = 2401;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private MediaPlayer player;
    private String currentUri = "";
    private String currentTitle = "";
    private String currentArtist = "";
    private int lastPosition;
    private int duration;
    private boolean prepared;
    private boolean pausedByFocus;
    private boolean ducked;
    private boolean shuttingDown;
    private long lastNotificationUpdate;

    private final Runnable progressTicker = new Runnable() {
        @Override public void run() {
            if (player != null && prepared) {
                try {
                    if (player.isPlaying()) lastPosition = player.getCurrentPosition();
                } catch (IllegalStateException ignored) { }
                publishState();
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastNotificationUpdate > 2500L) {
                    lastNotificationUpdate = now;
                    updateNotification();
                }
            }
            handler.postDelayed(this, 500L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        handler.post(progressTicker);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action)) {
            String uri = intent.getStringExtra(EXTRA_URI);
            if (uri != null && !uri.trim().isEmpty()) {
                play(uri, intent.getStringExtra(EXTRA_TITLE), intent.getStringExtra(EXTRA_ARTIST), 0);
            }
        } else if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_SEEK.equals(action)) {
            seek(intent.getIntExtra(EXTRA_POSITION, 0));
        } else if (intent == null) {
            SharedPreferences saved = getPrefs();
            if (saved.getBoolean("resume_after_process_restart", false)) {
                String uri = saved.getString("song_uri", "");
                if (!uri.isEmpty()) {
                    play(uri, saved.getString("song_title", ""), saved.getString("song_artist", ""),
                            saved.getInt("position_ms", 0));
                }
            }
        }
        return START_STICKY;
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean requestFocus() {
        if (audioManager == null) return true;
        return audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void play(String uri, String title, String artist, int resumePosition) {
        if (!requestFocus()) {
            publishError("تعذر الحصول على أولوية الصوت");
            return;
        }
        releasePlayer();
        currentUri = uri;
        currentTitle = title == null ? "ملف صوتي" : title;
        currentArtist = artist == null ? "غير معروف" : artist;
        lastPosition = Math.max(0, resumePosition);
        duration = 0;
        prepared = false;
        pausedByFocus = false;
        ducked = false;
        getPrefs().edit()
                .putString("song_uri", currentUri)
                .putString("song_title", currentTitle)
                .putString("song_artist", currentArtist)
                .putBoolean("is_playing", true)
                .putBoolean("resume_after_process_restart", true)
                .putInt("position_ms", lastPosition)
                .apply();
        startForeground(NOTIFICATION_ID, buildNotification("جارٍ تشغيل " + currentTitle));
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, Uri.parse(uri));
            player.setOnPreparedListener(mp -> {
                if (player != mp) return;
                prepared = true;
                duration = Math.max(0, mp.getDuration());
                if (lastPosition > 0 && duration > 0) {
                    mp.seekTo(Math.min(lastPosition, Math.max(0, duration - 1000)));
                }
                try {
                    mp.start();
                    publishState();
                    updateNotification();
                } catch (IllegalStateException error) {
                    publishError("تعذر بدء تشغيل الملف الصوتي");
                }
            });
            player.setOnCompletionListener(mp -> {
                lastPosition = duration;
                getPrefs().edit().putBoolean("is_playing", false)
                        .putBoolean("resume_after_process_restart", false).apply();
                pause();
                publishState();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                publishError("حدث خطأ أثناء تشغيل الملف الصوتي");
                stopPlayback();
                return true;
            });
            player.prepareAsync();
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            publishError("تعذر فتح الملف الصوتي");
            stopPlayback();
        }
    }

    private void toggle() {
        if (player == null || !prepared) {
            SharedPreferences saved = getPrefs();
            String uri = saved.getString("song_uri", "");
            if (!uri.isEmpty()) {
                play(uri, saved.getString("song_title", ""), saved.getString("song_artist", ""),
                        saved.getInt("position_ms", 0));
            }
            return;
        }
        try {
            if (player.isPlaying()) pause();
            else {
                if (requestFocus()) {
                    player.start();
                    pausedByFocus = false;
                    getPrefs().edit().putBoolean("is_playing", true)
                            .putBoolean("resume_after_process_restart", true).apply();
                    publishState();
                    updateNotification();
                }
            }
        } catch (IllegalStateException ignored) { }
    }

    private void pause() {
        if (player != null && prepared) {
            try {
                if (player.isPlaying()) {
                    lastPosition = player.getCurrentPosition();
                    player.pause();
                }
            } catch (IllegalStateException ignored) { }
        }
        getPrefs().edit().putBoolean("is_playing", false)
                .putBoolean("resume_after_process_restart", false)
                .putInt("position_ms", lastPosition).apply();
        if (audioManager != null) {
            try { audioManager.abandonAudioFocus(this); } catch (Exception ignored) { }
        }
        publishState();
        updateNotification();
    }

    private void seek(int position) {
        if (player == null || !prepared) return;
        try {
            int safe = Math.max(0, Math.min(position, Math.max(0, duration - 1000)));
            player.seekTo(safe);
            lastPosition = safe;
            publishState();
        } catch (IllegalStateException ignored) { }
    }

    private void stopPlayback() {
        getPrefs().edit().putBoolean("is_playing", false)
                .putBoolean("resume_after_process_restart", false)
                .putInt("position_ms", lastPosition).apply();
        releasePlayer();
        if (audioManager != null) {
            try { audioManager.abandonAudioFocus(this); } catch (Exception ignored) { }
        }
        publishState();
    }

    private void releasePlayer() {
        handler.removeCallbacks(progressTicker);
        if (player != null) {
            try { player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.setOnErrorListener(null); } catch (Exception ignored) { }
            try { player.stop(); } catch (Exception ignored) { }
            try { player.reset(); player.release(); } catch (Exception ignored) { }
            player = null;
        }
        prepared = false;
        if (!shuttingDown) handler.post(progressTicker);
    }

    @Override public void onAudioFocusChange(int focusChange) {
        if (player == null || !prepared) return;
        try {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                player.setVolume(0.28f, 0.28f);
                ducked = true;
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                if (ducked) {
                    player.setVolume(1.0f, 1.0f);
                    ducked = false;
                }
                if (pausedByFocus) {
                    player.start();
                    pausedByFocus = false;
                }
                publishState();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                if (player.isPlaying()) {
                    lastPosition = player.getCurrentPosition();
                    player.pause();
                    pausedByFocus = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                }
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    getPrefs().edit().putBoolean("is_playing", false)
                            .putBoolean("resume_after_process_restart", false).apply();
                }
                publishState();
            }
        } catch (IllegalStateException ignored) { }
    }

    private void publishState() {
        boolean playing = false;
        int position = lastPosition;
        if (player != null && prepared) {
            try {
                playing = player.isPlaying();
                position = playing ? player.getCurrentPosition() : lastPosition;
                if (duration <= 0) duration = player.getDuration();
            } catch (IllegalStateException ignored) { }
        }
        lastPosition = Math.max(0, position);
        getPrefs().edit().putString("song_uri", currentUri)
                .putString("song_title", currentTitle)
                .putString("song_artist", currentArtist)
                .putBoolean("is_playing", playing)
                .putInt("position_ms", lastPosition)
                .putInt("duration_ms", duration)
                .putLong("updated_at_ms", System.currentTimeMillis()).apply();

        Intent update = new Intent(ACTION_STATE);
        update.setPackage(getPackageName());
        update.putExtra("playing", playing);
        update.putExtra("title", currentTitle);
        update.putExtra("artist", currentArtist);
        update.putExtra("position_ms", lastPosition);
        update.putExtra("duration_ms", duration);
        update.putExtra("error", "");
        sendBroadcast(update);
    }

    private void publishError(String message) {
        Intent update = new Intent(ACTION_STATE);
        update.setPackage(getPackageName());
        update.putExtra("playing", false);
        update.putExtra("error", message);
        sendBroadcast(update);
    }

    private Notification buildNotification(String content) {
        Intent open = new Intent(this, MusicActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 24011, open, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent toggle = new Intent(this, MusicPlaybackService.class);
        toggle.setAction(ACTION_TOGGLE);
        PendingIntent togglePending = PendingIntent.getService(this, 24012, toggle, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, MusicPlaybackService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 24013, stop, PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("ALI CAR • الموسيقى")
                .setContentText(content == null ? currentTitle : content)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_media_pause, "تشغيل / إيقاف", togglePending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopPending)
                .build();
    }

    private void updateNotification() {
        try {
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(currentTitle));
        } catch (Exception ignored) { }
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        shuttingDown = true;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (audioManager != null) {
            try { audioManager.abandonAudioFocus(this); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
