package org.nikanikoo.flux.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.ui.activities.AudioPlayerActivity;
import org.nikanikoo.flux.utils.AlbumArtFetcher;
import org.nikanikoo.flux.utils.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioPlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final String TAG = "AudioPlayerService";
    private static final String CHANNEL_ID = "audio_player_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_PLAY = "org.nikanikoo.flux.ACTION_PLAY";
    public static final String ACTION_PAUSE = "org.nikanikoo.flux.ACTION_PAUSE";
    public static final String ACTION_NEXT = "org.nikanikoo.flux.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "org.nikanikoo.flux.ACTION_PREVIOUS";
    public static final String ACTION_STOP = "org.nikanikoo.flux.ACTION_STOP";

    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private final List<Audio> playlist = new CopyOnWriteArrayList<>();
    private volatile int currentPosition = 0;
    private volatile int prepareGeneration = 0;
    private volatile boolean isPrepared = false;
    private final IBinder binder = new AudioBinder();
    private final List<PlayerCallback> callbacks = new CopyOnWriteArrayList<>();
    private ExecutorService audioLoadExecutor;
    private ExecutorService audioPreloadExecutor;
    private final Object preloadLock = new Object();
    private volatile String preloadingAudioKey;
    private volatile String preloadedAudioKey;
    private volatile java.io.File preloadedAudioFile;
    private AlbumArtFetcher albumArtFetcher;
    private AudioCacheManager audioCacheManager;

    public interface PlayerCallback {
        void onPlaybackStateChanged(boolean isPlaying);
        void onTrackChanged(Audio audio, int position);
        void onProgressUpdate(int currentPosition, int duration);
        void onError(String error);
    }

    public class AudioBinder extends Binder {
        public AudioPlayerService getService() {
            return AudioPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d(TAG, "Service created");
        createNotificationChannel();
        initMediaSession();
        initMediaPlayer();
        audioLoadExecutor = Executors.newSingleThreadExecutor();
        audioPreloadExecutor = Executors.newSingleThreadExecutor();
        albumArtFetcher = new AlbumArtFetcher(this);
        audioCacheManager = AudioCacheManager.getInstance(this);
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onStop() {
                stop();
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
        });
        
        mediaSession.setActive(true);
        Logger.d(TAG, "MediaSession initialized");
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                MediaButtonReceiver.handleIntent(mediaSession, intent);
            } else if (action != null) {
                handleAction(action);
            }
        }
        return START_STICKY;
    }

    private void handleAction(String action) {
        switch (action) {
            case ACTION_PLAY:
                play();
                break;
            case ACTION_PAUSE:
                pause();
                break;
            case ACTION_NEXT:
                next();
                break;
            case ACTION_PREVIOUS:
                previous();
                break;
            case ACTION_STOP:
                stop();
                stopSelf();
                break;
        }
    }

    public void setPlaylist(List<Audio> audios, int startPosition) {
        if (audios == null || audios.isEmpty()) {
            Logger.e(TAG, "Playlist is empty");
            return;
        }

        this.playlist.clear();
        this.playlist.addAll(audios);
        this.currentPosition = startPosition;
        clearPreloadedAudio();
        
        Logger.d(TAG, "Playlist set: " + playlist.size() + " tracks, starting at " + startPosition);
        
        prepareAudio(playlist.get(currentPosition));
    }

    public void appendToPlaylist(List<Audio> audios) {
        if (audios == null || audios.isEmpty()) {
            return;
        }

        this.playlist.addAll(audios);
        Logger.d(TAG, "Playlist appended: +" + audios.size() + " tracks, total=" + playlist.size());
        if (isPrepared) {
            preloadNextTrack();
        }
    }

    public void playNext(Audio audio) {
        if (audio == null) {
            return;
        }

        int insertPosition = Math.min(currentPosition + 1, playlist.size());
        playlist.add(insertPosition, audio);
        Logger.d(TAG, "Track inserted next: " + audio.getFullTitle());

        if (isPrepared) {
            clearPreloadedAudio();
            preloadNextTrack();
        } else if (playlist.size() == 1) {
            currentPosition = 0;
            prepareAudio(audio);
        }
    }

    private void prepareAudio(Audio audio) {
        if (audio == null || audio.getUrl() == null || audio.getUrl().isEmpty()) {
            Logger.e(TAG, "Invalid audio URL");
            notifyError("Невозможно воспроизвести аудио");
            return;
        }

        int generation = ++prepareGeneration;

        // Выполняем загрузку в фоновом потоке через ExecutorService
        audioLoadExecutor.execute(() -> {
            try {
                // Для HTTPS URL с самоподписанными сертификатами используем OkHttp
                String url = audio.getUrl();
                String dataSource;
                if (url.startsWith("https://")) {
                    java.io.File audioFile = consumePreloadedAudio(audio);
                    if (audioFile == null) {
                        audioFile = audioCacheManager.getCachedFile(audio);
                    }
                    if (audioFile == null) {
                        if (audioCacheManager.isSaveOnListeningEnabled()) {
                            Logger.d(TAG, "Downloading and caching HTTPS audio: " + url);
                            audioFile = audioCacheManager.downloadAudioBlocking(audio);
                        } else {
                            Logger.d(TAG, "Downloading HTTPS audio to temp file: " + url);
                            audioFile = audioCacheManager.downloadAudioToTempBlocking(audio);
                        }
                    } else {
                        Logger.d(TAG, "Using cached audio: " + audio.getFullTitle());
                    }

                    dataSource = audioFile.getAbsolutePath();
                    Logger.d(TAG, "Audio loaded via OkHttp: " + audio.getFullTitle());
                } else {
                    // Для HTTP или локальных файлов используем стандартный способ
                    Logger.d(TAG, "Loading audio via standard method: " + url);
                    dataSource = url;
                }

                if (generation != prepareGeneration) {
                    Logger.d(TAG, "Skipping stale audio prepare: " + audio.getFullTitle());
                    return;
                }

                mediaPlayer.reset();
                isPrepared = false;
                mediaPlayer.setDataSource(dataSource);
                mediaPlayer.prepareAsync();
                
                Logger.d(TAG, "Preparing audio: " + audio.getFullTitle());
                
                notifyTrackChanged(audio, currentPosition);
                updateNotification();
            } catch (IOException e) {
                Logger.e(TAG, "Error preparing audio", e);
                notifyError("Ошибка загрузки аудио");
            }
        });
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        Logger.d(TAG, "Audio prepared, starting playback");
        mp.start();
        updateMediaSessionMetadata();
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
        notifyPlaybackStateChanged(true);
        updateNotification();
        startProgressUpdates();
        preloadNextTrack();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Logger.d(TAG, "Audio completed");
        next();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Logger.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
        notifyError("Ошибка воспроизведения");
        return true;
    }

    public void play() {
        if (isPrepared && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            notifyPlaybackStateChanged(true);
            updateNotification();
            startProgressUpdates();
            Logger.d(TAG, "Playback started");
        }
    }

    public void pause() {
        if (isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
            notifyPlaybackStateChanged(false);
            updateNotification();
            Logger.d(TAG, "Playback paused");
        }
    }

    public void next() {
        if (playlist.isEmpty()) return;
        
        currentPosition = (currentPosition + 1) % playlist.size();
        prepareAudio(playlist.get(currentPosition));
        Logger.d(TAG, "Next track: " + currentPosition);
    }

    public void previous() {
        if (playlist.isEmpty()) return;
        
        currentPosition = (currentPosition - 1 + playlist.size()) % playlist.size();
        prepareAudio(playlist.get(currentPosition));
        Logger.d(TAG, "Previous track: " + currentPosition);
    }

    public void seekTo(int position) {
        if (isPrepared) {
            mediaPlayer.seekTo(position);
        }
    }

    public void seekToTrack(int trackPosition) {
        if (trackPosition < 0 || trackPosition >= playlist.size()) {
            return;
        }
        
        currentPosition = trackPosition;
        prepareAudio(playlist.get(currentPosition));
        Logger.d(TAG, "Seek to track: " + currentPosition);
    }

    public void stop() {
        prepareGeneration++;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            isPrepared = false;
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
            notifyPlaybackStateChanged(false);
            Logger.d(TAG, "Playback stopped");
        }
    }

    public void clearPlaylist() {
        prepareGeneration++;
        playlist.clear();
        currentPosition = 0;
        isPrepared = false;
        clearPreloadedAudio();

        stopForeground(true);
        stopSelf();
        
        Logger.d(TAG, "Playlist cleared and service stopped");
    }

    public boolean isPlaying() {
        return isPrepared && mediaPlayer.isPlaying();
    }

    public Audio getCurrentAudio() {
        if (playlist.isEmpty() || currentPosition >= playlist.size()) {
            return null;
        }
        return playlist.get(currentPosition);
    }

    public int getCurrentPosition() {
        return isPrepared ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return isPrepared ? mediaPlayer.getDuration() : 0;
    }

    public List<Audio> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public int getCurrentTrackPosition() {
        return currentPosition;
    }

    public void registerCallback(PlayerCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void unregisterCallback(PlayerCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyPlaybackStateChanged(boolean isPlaying) {
        for (PlayerCallback callback : callbacks) {
            callback.onPlaybackStateChanged(isPlaying);
        }
    }

    private void notifyTrackChanged(Audio audio, int position) {
        for (PlayerCallback callback : callbacks) {
            callback.onTrackChanged(audio, position);
        }
    }

    private void notifyProgressUpdate(int currentPos, int duration) {
        for (PlayerCallback callback : callbacks) {
            callback.onProgressUpdate(currentPos, duration);
        }
    }

    private void notifyError(String error) {
        for (PlayerCallback callback : callbacks) {
            callback.onError(error);
        }
    }

    private void startProgressUpdates() {
        new Thread(() -> {
            while (isPrepared && mediaPlayer.isPlaying()) {
                try {
                    notifyProgressUpdate(getCurrentPosition(), getDuration());
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Аудио плеер",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Управление воспроизведением музыки");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void updateMediaSessionMetadata() {
        Audio currentAudio = getCurrentAudio();
        if (currentAudio == null) return;

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentAudio.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "OpenVK Flux")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());

        // Загружаем обложку асинхронно
        audioLoadExecutor.execute(() -> {
            Bitmap albumArt = null;
            if (currentAudio.getCoverUrl() != null && !currentAudio.getCoverUrl().isEmpty()) {
                albumArt = loadAlbumArt(currentAudio.getCoverUrl());
            }
            if (albumArt == null) {
                albumArt = loadAlbumArtFromLastFm(currentAudio);
            }
            
            if (albumArt != null) {
                Bitmap scaledArt = Bitmap.createScaledBitmap(albumArt, 512, 512, true);
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, scaledArt);
                mediaSession.setMetadata(metadataBuilder.build());
                updateNotification();
            } else {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
                mediaSession.setMetadata(metadataBuilder.build());
            }
        });
    }

    private Bitmap loadAlbumArtFromLastFm(Audio audio) {
        try {
            String artist = audio.getArtist();
            String title = audio.getTitle();
            
            if (artist == null || title == null) return null;
            String encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8");
            String encodedTitle = java.net.URLEncoder.encode(title, "UTF-8");
            String apiKey = "TOKEN";
            
            String urlString = "https://ws.audioscrobbler.com/2.0?method=track.getInfo" +
                    "&api_key=" + apiKey +
                    "&artist=" + encodedArtist +
                    "&track=" + encodedTitle +
                    "&format=json";
            
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                org.json.JSONObject json = new org.json.JSONObject(response.toString());
                if (json.has("track") && json.getJSONObject("track").has("album")) {
                    org.json.JSONObject track = json.getJSONObject("track");
                    org.json.JSONObject album = track.getJSONObject("album");
                    if (album.has("image")) {
                        org.json.JSONArray images = album.getJSONArray("image");
                        for (int i = images.length() - 1; i >= 0; i--) {
                            org.json.JSONObject image = images.getJSONObject(i);
                            String imageUrl = image.optString("#text", null);
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                return loadAlbumArt(imageUrl);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error loading album art from Last.fm", e);
        }
        return null;
    }

    private void updatePlaybackState(int state) {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, getCurrentPosition(), 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private Bitmap loadAlbumArt(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            connection.disconnect();
            
            if (bitmap != null) {
                int size = 512;
                return Bitmap.createScaledBitmap(bitmap, size, size, true);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error loading album art", e);
        }
        return null;
    }

    private void preloadNextTrack() {
        if (audioPreloadExecutor == null || playlist.size() < 2) {
            return;
        }

        int nextPosition = (currentPosition + 1) % playlist.size();
        Audio nextAudio = playlist.get(nextPosition);
        if (nextAudio == null || nextAudio.getUrl() == null || !nextAudio.getUrl().startsWith("https://")) {
            return;
        }

        String nextAudioKey = getAudioKey(nextAudio);
        synchronized (preloadLock) {
            if (nextAudioKey.equals(preloadedAudioKey) || nextAudioKey.equals(preloadingAudioKey)) {
                return;
            }
            deletePreloadedAudioLocked();
            preloadingAudioKey = nextAudioKey;
        }

        audioPreloadExecutor.execute(() -> {
            java.io.File downloadedFile = null;
            try {
                Logger.d(TAG, "Preloading next audio: " + nextAudio.getFullTitle());
                if (audioCacheManager.isSaveOnListeningEnabled()) {
                    downloadedFile = audioCacheManager.downloadAudioBlocking(nextAudio);
                } else {
                    downloadedFile = audioCacheManager.downloadAudioToTempBlocking(nextAudio);
                }

                synchronized (preloadLock) {
                    if (nextAudioKey.equals(getExpectedNextAudioKey())) {
                        preloadedAudioKey = nextAudioKey;
                        preloadedAudioFile = downloadedFile;
                        Logger.d(TAG, "Next audio preloaded: " + nextAudio.getFullTitle());
                        downloadedFile = null;
                    }
                    preloadingAudioKey = null;
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error preloading next audio", e);
                synchronized (preloadLock) {
                    if (nextAudioKey.equals(preloadingAudioKey)) {
                        preloadingAudioKey = null;
                    }
                }
            } finally {
            }
        });
    }

    private java.io.File consumePreloadedAudio(Audio audio) {
        String audioKey = getAudioKey(audio);
        synchronized (preloadLock) {
            if (!audioKey.equals(preloadedAudioKey) || preloadedAudioFile == null || !preloadedAudioFile.exists()) {
                return null;
            }

            java.io.File audioFile = preloadedAudioFile;
            preloadedAudioKey = null;
            preloadedAudioFile = null;
            return audioFile;
        }
    }

    private String getExpectedNextAudioKey() {
        if (playlist.isEmpty()) {
            return "";
        }
        int nextPosition = (currentPosition + 1) % playlist.size();
        return getAudioKey(playlist.get(nextPosition));
    }

    private String getAudioKey(Audio audio) {
        if (audio == null) {
            return "";
        }
        if (audio.getUniqueId() != null && !audio.getUniqueId().isEmpty()) {
            return audio.getUniqueId();
        }
        return audio.getOwnerId() + "_" + audio.getId() + "_" + audio.getUrl();
    }

    private void clearPreloadedAudio() {
        synchronized (preloadLock) {
            deletePreloadedAudioLocked();
            preloadingAudioKey = null;
        }
    }

    private void deletePreloadedAudioLocked() {
        preloadedAudioKey = null;
        preloadedAudioFile = null;
    }

    private void updateNotification() {
        Audio currentAudio = getCurrentAudio();
        if (currentAudio == null) return;

        Intent intent = new Intent(this, AudioPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
            new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this, PlaybackStateCompat.ACTION_STOP));

        Bitmap albumArt = null;
        MediaMetadataCompat metadata = mediaSession.getController().getMetadata();
        if (metadata != null) {
            albumArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_library_music)
                .setLargeIcon(albumArt != null ? albumArt : getFallbackIcon())
                .setContentTitle(currentAudio.getTitle())
                .setContentText(currentAudio.getArtist())
                .setSubText("OpenVK Flux")
                .setContentIntent(pendingIntent)
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this, PlaybackStateCompat.ACTION_STOP))
                .setOngoing(isPlaying())
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setStyle(mediaStyle)
                .addAction(createAction(R.drawable.ic_skip_previous, "Назад", ACTION_PREVIOUS))
                .addAction(createAction(
                        isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play,
                        isPlaying() ? "Пауза" : "Играть",
                        isPlaying() ? ACTION_PAUSE : ACTION_PLAY
                ))
                .addAction(createAction(R.drawable.ic_skip_next, "Вперед", ACTION_NEXT));

        Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);
        Logger.d(TAG, "Notification updated");
    }

    private Bitmap getFallbackIcon() {
        try {
            return BitmapFactory.decodeResource(getResources(), R.drawable.logo_flux);
        } catch (Exception e) {
            return BitmapFactory.decodeResource(getResources(), R.drawable.ic_library_music);
        }
    }

    private NotificationCompat.Action createAction(int icon, String title, String action) {
        Intent intent = new Intent(this, AudioPlayerService.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(
                this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Action.Builder(icon, title, pendingIntent).build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (audioLoadExecutor != null) {
            audioLoadExecutor.shutdown();
            try {
                if (!audioLoadExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    audioLoadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                audioLoadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (audioPreloadExecutor != null) {
            audioPreloadExecutor.shutdownNow();
            audioPreloadExecutor = null;
        }
        clearPreloadedAudio();
        
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        callbacks.clear();
        Logger.d(TAG, "Service destroyed");
    }
}
