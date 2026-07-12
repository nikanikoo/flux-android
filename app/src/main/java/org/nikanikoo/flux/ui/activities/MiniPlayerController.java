package org.nikanikoo.flux.ui.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.services.AudioPlayerService;
import org.nikanikoo.flux.utils.AlbumArtFetcher;
import org.nikanikoo.flux.utils.Logger;

/**
 * Controller для управления Mini Player в MainActivity.
 * Инкапсулирует логику работы с AudioPlayerService и обновления UI плеера.
 */
public class MiniPlayerController {
    
    private static final String TAG = "MiniPlayerController";
    
    private final MainActivity activity;
    
    // Views
    private LinearLayout miniPlayerContainer;
    private ImageView miniPlayerIcon;
    private TextView miniPlayerTitle;
    private TextView miniPlayerArtist;
    private ImageButton miniPlayerPlayPause;
    private ImageButton miniPlayerStop;
    private LinearProgressIndicator miniPlayerProgress;

    private AlbumArtFetcher albumArtFetcher;

    // Service
    private AudioPlayerService playerService;
    private boolean playerServiceBound = false;
    
    // Callback для уведомления Activity об изменениях
    private OnPlayerStateChangeListener stateChangeListener;
    
    public interface OnPlayerStateChangeListener {
        void onPlayerConnected();
        void onPlayerDisconnected();
        void onTrackChanged(Audio audio);
    }
    
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlayerService.AudioBinder binder = (AudioPlayerService.AudioBinder) service;
            playerService = binder.getService();
            playerServiceBound = true;
            Logger.d(TAG, "AudioPlayerService connected");

            playerService.registerCallback(playerCallback);

            updateUI();

            if (stateChangeListener != null) {
                stateChangeListener.onPlayerConnected();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (playerService != null) {
                playerService.unregisterCallback(playerCallback);
            }
            playerServiceBound = false;
            playerService = null;
            Logger.d(TAG, "AudioPlayerService disconnected");

            if (stateChangeListener != null) {
                stateChangeListener.onPlayerDisconnected();
            }
        }
    };

    private final AudioPlayerService.PlayerCallback playerCallback = new AudioPlayerService.PlayerCallback() {
        @Override
        public void onPlaybackStateChanged(boolean isPlaying) {
            activity.runOnUiThread(() -> {
                updatePlayPauseButton();
                updateNotificationVisibility();
            });
        }

        @Override
        public void onTrackChanged(Audio audio, int position) {
            activity.runOnUiThread(() -> {
                updateUI();
            });
        }

        @Override
        public void onProgressUpdate(int currentPosition, int duration) {
            activity.runOnUiThread(() -> {
                updateProgress(currentPosition, duration);
            });
        }

        @Override
        public void onError(String error) {
            Logger.e(TAG, "Player error: " + error);
        }
    };
    
    public MiniPlayerController(MainActivity activity) {
        this.activity = activity;
        this.albumArtFetcher = new AlbumArtFetcher(activity);
    }
    
    /**
     * Инициализация View
     */
    public void initViews(View rootView) {
        View miniPlayerView = rootView.findViewById(R.id.mini_player_container);
        if (miniPlayerView == null) {
            miniPlayerView = rootView;
        }

        miniPlayerContainer = (LinearLayout) miniPlayerView;
        miniPlayerIcon = miniPlayerContainer.findViewById(R.id.mini_player_icon);
        miniPlayerTitle = miniPlayerContainer.findViewById(R.id.mini_player_title);
        miniPlayerArtist = miniPlayerContainer.findViewById(R.id.mini_player_artist);
        miniPlayerPlayPause = miniPlayerContainer.findViewById(R.id.mini_player_play_pause);
        miniPlayerStop = miniPlayerContainer.findViewById(R.id.mini_player_stop);
        miniPlayerProgress = miniPlayerContainer.findViewById(R.id.mini_player_progress);

        setupClickListeners();
    }

    /**
     * Настройка обработчиков кликов
     */
    private void setupClickListeners() {
        if (miniPlayerPlayPause != null) {
            miniPlayerPlayPause.setOnClickListener(v -> togglePlayPause());
        }

        if (miniPlayerStop != null) {
            miniPlayerStop.setOnClickListener(v -> stopAudio());
        }

        if (miniPlayerContainer != null) {
            miniPlayerContainer.setOnClickListener(v -> openFullPlayer());
        }
    }
    
    /**
     * Привязка к AudioPlayerService
     */
    public void bindService() {
        Intent intent = new Intent(activity, AudioPlayerService.class);
        activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * Отвязка от AudioPlayerService
     */
    public void unbindService() {
        if (playerServiceBound && playerService != null) {
            playerService.unregisterCallback(playerCallback);
        }
        if (playerServiceBound) {
            activity.unbindService(serviceConnection);
            playerServiceBound = false;
        }
    }
    
    /**
     * Play/Pause
     */
    private void togglePlayPause() {
        if (!playerServiceBound || playerService == null) {
            return;
        }

        if (playerService.isPlaying()) {
            playerService.pause();
        } else {
            playerService.play();
        }

        updatePlayPauseButton();
    }

    private void stopAudio() {
        if (!playerServiceBound || playerService == null) {
            return;
        }

        playerService.stop();
        playerService.clearPlaylist();
        hidePlayer();
    }

    /**
     * Открыть полноэкранный плеер
     */
    private void openFullPlayer() {
        if (!playerServiceBound || playerService == null) {
            return;
        }

        Intent intent = new Intent(activity, AudioPlayerActivity.class);
        activity.startActivity(intent);
    }
    
    /**
     * Обновить UI плеера
     */
    public void updateUI() {
        if (!playerServiceBound || playerService == null) {
            Logger.d(TAG, "updateUI: service not bound, hiding player");
            hidePlayer();
            return;
        }

        Audio currentTrack = playerService.getCurrentAudio();
        if (currentTrack == null) {
            Logger.d(TAG, "updateUI: no current track, hiding player");
            hidePlayer();
            return;
        }

        Logger.d(TAG, "updateUI: showing player for " + currentTrack.getFullTitle());
        showPlayer();

        if (miniPlayerTitle != null) {
            miniPlayerTitle.setText(currentTrack.getTitle());
        }

        if (miniPlayerArtist != null) {
            miniPlayerArtist.setText(currentTrack.getArtist());
        }

        loadAlbumArt(currentTrack.getArtist(), currentTrack.getTitle());

        updatePlayPauseButton();

        if (stateChangeListener != null) {
            stateChangeListener.onTrackChanged(currentTrack);
        }
    }

    private void loadAlbumArt(String artist, String title) {
        if (miniPlayerIcon == null || artist == null || title == null || 
            artist.isEmpty() || title.isEmpty()) {
            if (miniPlayerIcon != null) {
                miniPlayerIcon.setImageResource(R.drawable.ic_music_note);
            }
            return;
        }

        albumArtFetcher.loadAlbumArt(artist, title, miniPlayerIcon, R.drawable.ic_music_note);
    }
    
    /**
     * Обновить кнопку Play/Pause
     */
    private void updatePlayPauseButton() {
        if (miniPlayerPlayPause == null || !playerServiceBound) {
            return;
        }

        if (playerService.isPlaying()) {
            miniPlayerPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            miniPlayerPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void updateProgress(int currentPosition, int duration) {
        if (miniPlayerProgress == null || !playerServiceBound || duration <= 0) {
            return;
        }

        int progress = (int) ((currentPosition / (float) duration) * 100);
        miniPlayerProgress.setProgress(progress);
    }

    private void updateNotificationVisibility() {
        if (!playerServiceBound || playerService == null) {
            hidePlayer();
            return;
        }

        Audio currentTrack = playerService.getCurrentAudio();
        if (currentTrack == null) {
            hidePlayer();
        } else {
            showPlayer();
            updatePlayPauseButton();
        }
    }

    /**
     * Показать плеер
     */
    private void showPlayer() {
        if (miniPlayerContainer != null) {
            miniPlayerContainer.setVisibility(View.VISIBLE);
            Logger.d(TAG, "Mini player shown");
        } else {
            Logger.e(TAG, "miniPlayerContainer is null!");
        }
    }

    /**
     * Скрыть плеер
     */
    private void hidePlayer() {
        if (miniPlayerContainer != null) {
            miniPlayerContainer.setVisibility(View.GONE);
            Logger.d(TAG, "Mini player hidden");
        }
    }
    
    /**
     * Проверить, привязан ли сервис
     */
    public boolean isBound() {
        return playerServiceBound;
    }
    
    /**
     * Получить сервис плеера
     */
    public AudioPlayerService getPlayerService() {
        return playerService;
    }
    
    /**
     * Установить слушатель изменений состояния
     */
    public void setOnPlayerStateChangeListener(OnPlayerStateChangeListener listener) {
        this.stateChangeListener = listener;
    }
    
    /**
     * Получить текущий трек
     */
    public Audio getCurrentTrack() {
        if (playerServiceBound && playerService != null) {
            return playerService.getCurrentAudio();
        }
        return null;
    }
    
    /**
     * Проверить, воспроизводится ли музыка
     */
    public boolean isPlaying() {
        if (playerServiceBound && playerService != null) {
            return playerService.isPlaying();
        }
        return false;
    }
}
