package org.nikanikoo.flux.ui.activities;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
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
    private View miniPlayerContainer;
    private View miniPlayerCard;
    private ImageView miniPlayerIcon;
    private TextView miniPlayerTitle;
    private TextView miniPlayerArtist;
    private ImageButton miniPlayerPlayPause;
    private ImageButton miniPlayerNext;
    private ImageButton miniPlayerStop;
    private LinearProgressIndicator miniPlayerProgress;

    private View miniPlayerCardPreview;
    private ImageView miniPlayerPreviewIcon;
    private TextView miniPlayerPreviewTitle;
    private TextView miniPlayerPreviewArtist;
    private ImageButton miniPlayerPreviewPlayPause;
    private ImageButton miniPlayerPreviewNext;
    private ImageButton miniPlayerPreviewStop;
    private LinearProgressIndicator miniPlayerPreviewProgress;

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

        miniPlayerContainer = miniPlayerView;
        miniPlayerCard = miniPlayerContainer.findViewById(R.id.mini_player_card);
        miniPlayerIcon = miniPlayerContainer.findViewById(R.id.mini_player_icon);
        miniPlayerTitle = miniPlayerContainer.findViewById(R.id.mini_player_title);
        miniPlayerArtist = miniPlayerContainer.findViewById(R.id.mini_player_artist);
        miniPlayerPlayPause = miniPlayerContainer.findViewById(R.id.mini_player_play_pause);
        miniPlayerNext = miniPlayerContainer.findViewById(R.id.mini_player_next);
        miniPlayerStop = miniPlayerContainer.findViewById(R.id.mini_player_stop);
        miniPlayerProgress = miniPlayerContainer.findViewById(R.id.mini_player_progress);

        miniPlayerCardPreview = miniPlayerContainer.findViewById(R.id.mini_player_card_preview);
        if (miniPlayerCardPreview != null) {
            miniPlayerPreviewIcon = miniPlayerCardPreview.findViewById(R.id.mini_player_preview_icon);
            miniPlayerPreviewTitle = miniPlayerCardPreview.findViewById(R.id.mini_player_preview_title);
            miniPlayerPreviewArtist = miniPlayerCardPreview.findViewById(R.id.mini_player_preview_artist);
            miniPlayerPreviewPlayPause = miniPlayerCardPreview.findViewById(R.id.mini_player_preview_play_pause);
            miniPlayerPreviewNext = miniPlayerCardPreview.findViewById(R.id.mini_player_preview_next);
            miniPlayerPreviewStop = miniPlayerCardPreview.findViewById(R.id.mini_player_preview_stop);
            miniPlayerPreviewProgress = miniPlayerCardPreview.findViewById(R.id.mini_player_preview_progress);

            if (miniPlayerCard instanceof MaterialCardView && miniPlayerCardPreview instanceof MaterialCardView) {
                MaterialCardView mainCard = (MaterialCardView) miniPlayerCard;
                MaterialCardView previewCard = (MaterialCardView) miniPlayerCardPreview;
                previewCard.setCardBackgroundColor(mainCard.getCardBackgroundColor());
                previewCard.setStrokeColor(mainCard.getStrokeColorStateList());
                previewCard.setStrokeWidth(mainCard.getStrokeWidth());
                previewCard.setCardElevation(mainCard.getCardElevation());
                previewCard.setMaxCardElevation(mainCard.getMaxCardElevation());
                previewCard.setRadius(mainCard.getRadius());
            }
        }

        setupClickListeners();
    }

    /**
     * Настройка обработчиков кликов
     */
    private void setupClickListeners() {
        if (miniPlayerPlayPause != null) {
            miniPlayerPlayPause.setOnClickListener(v -> togglePlayPause());
        }

        if (miniPlayerNext != null) {
            miniPlayerNext.setOnClickListener(v -> {
                if (playerServiceBound && playerService != null) {
                    playerService.next();
                }
            });
        }

        if (miniPlayerStop != null) {
            miniPlayerStop.setOnClickListener(v -> stopAudio());
        }

        setupSwipeListener();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSwipeListener() {
        View targetView = miniPlayerCard != null ? miniPlayerCard : miniPlayerContainer;
        if (targetView == null) return;

        final float density = activity.getResources().getDisplayMetrics().density;
        final float swipeThreshold = 52 * density;
        final float swipeVelocityThreshold = 700 * density;

        GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                openFullPlayer();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > 20 * density && Math.abs(velocityX) > swipeVelocityThreshold) {
                        finishSwipe(diffX < 0);
                        return true;
                    }
                }
                return false;
            }
        };

        GestureDetector gestureDetector = new GestureDetector(activity, gestureListener);

        targetView.setOnTouchListener(new View.OnTouchListener() {
            private float downX = 0f;
            private float downY = 0f;
            private boolean isDragging = false;
            private Boolean lastDragDirection = null; // true = next (left), false = prev (right)

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }

                float cardWidth = v.getWidth() > 0 ? v.getWidth() : (activity.getResources().getDisplayMetrics().widthPixels - 24 * density);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        isDragging = false;
                        lastDragDirection = null;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downX;
                        float deltaY = event.getRawY() - downY;
                        if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 6 * density) {
                            if (!isDragging) {
                                isDragging = true;
                                v.setPressed(false);
                                v.cancelPendingInputEvents();
                            }
                            boolean draggingNext = deltaX < 0;

                            if (lastDragDirection == null || lastDragDirection != draggingNext) {
                                lastDragDirection = draggingNext;
                                preparePreviewCard(draggingNext);
                            }

                            v.setTranslationX(deltaX);

                            if (miniPlayerCardPreview != null && miniPlayerCardPreview.getVisibility() == View.VISIBLE) {
                                float previewOffset = draggingNext ? (cardWidth + deltaX + 16 * density) : (-cardWidth + deltaX - 16 * density);
                                miniPlayerCardPreview.setTranslationX(previewOffset);
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        float totalDeltaX = event.getRawX() - downX;
                        if (isDragging) {
                            if (totalDeltaX < -swipeThreshold) {
                                finishSwipe(true);
                            } else if (totalDeltaX > swipeThreshold) {
                                finishSwipe(false);
                            } else {
                                cancelSwipe(cardWidth, lastDragDirection != null && lastDragDirection);
                            }
                            isDragging = false;
                            return true;
                        } else {
                            v.animate().translationX(0f).setDuration(150).start();
                            if (miniPlayerCardPreview != null) {
                                miniPlayerCardPreview.setVisibility(View.GONE);
                            }
                        }
                        break;
                }
                return false;
            }
        });
    }

    private void preparePreviewCard(boolean isNext) {
        if (miniPlayerCardPreview == null || !playerServiceBound || playerService == null) return;
        Audio previewTrack = isNext ? playerService.getNextAudio() : playerService.getPreviousAudio();
        if (previewTrack == null) {
            miniPlayerCardPreview.setVisibility(View.GONE);
            return;
        }

        if (miniPlayerPreviewTitle != null) {
            miniPlayerPreviewTitle.setText(previewTrack.getTitle());
        }
        if (miniPlayerPreviewArtist != null) {
            miniPlayerPreviewArtist.setText(previewTrack.getArtist());
        }
        if (miniPlayerPreviewPlayPause != null) {
            miniPlayerPreviewPlayPause.setImageResource(playerService.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (miniPlayerPreviewProgress != null) {
            miniPlayerPreviewProgress.setProgress(0);
        }
        loadAlbumArt(previewTrack.getArtist(), previewTrack.getTitle(), miniPlayerPreviewIcon);

        miniPlayerCardPreview.setVisibility(View.VISIBLE);
        miniPlayerCardPreview.setAlpha(1.0f);
    }

    private void finishSwipe(boolean isNext) {
        if (miniPlayerCard == null || !playerServiceBound || playerService == null) return;

        float density = activity.getResources().getDisplayMetrics().density;
        float cardWidth = miniPlayerCard.getWidth() > 0 ? miniPlayerCard.getWidth() : (activity.getResources().getDisplayMetrics().widthPixels - 24 * density);
        float exitTarget = isNext ? (-cardWidth - 24 * density) : (cardWidth + 24 * density);

        miniPlayerCard.animate()
                .translationX(exitTarget)
                .alpha(0.2f)
                .setDuration(180)
                .start();

        if (miniPlayerCardPreview != null && miniPlayerCardPreview.getVisibility() == View.VISIBLE) {
            miniPlayerCardPreview.animate()
                    .translationX(0f)
                    .alpha(1.0f)
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (isNext) {
                            playerService.next();
                        } else {
                            playerService.previous();
                        }
                        miniPlayerCard.setTranslationX(0f);
                        miniPlayerCard.setAlpha(1.0f);
                        miniPlayerCard.setPressed(false);
                        miniPlayerCardPreview.setVisibility(View.GONE);
                    })
                    .start();
        } else {
            if (isNext) {
                playerService.next();
            } else {
                playerService.previous();
            }
            miniPlayerCard.setTranslationX(0f);
            miniPlayerCard.setAlpha(1.0f);
            miniPlayerCard.setPressed(false);
        }
    }

    private void cancelSwipe(float cardWidth, boolean isNext) {
        if (miniPlayerCard != null) {
            miniPlayerCard.setPressed(false);
            miniPlayerCard.animate().translationX(0f).alpha(1.0f).setDuration(200).start();
        }
        if (miniPlayerCardPreview != null && miniPlayerCardPreview.getVisibility() == View.VISIBLE) {
            float resetX = isNext ? (cardWidth + 20) : (-cardWidth - 20);
            miniPlayerCardPreview.animate()
                    .translationX(resetX)
                    .setDuration(200)
                    .withEndAction(() -> miniPlayerCardPreview.setVisibility(View.GONE))
                    .start();
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
        loadAlbumArt(artist, title, miniPlayerIcon);
    }

    private void loadAlbumArt(String artist, String title, ImageView targetView) {
        if (targetView == null || artist == null || title == null || 
            artist.isEmpty() || title.isEmpty()) {
            if (targetView != null) {
                targetView.setImageResource(R.drawable.ic_music_note);
            }
            return;
        }

        albumArtFetcher.loadAlbumArt(artist, title, targetView, R.drawable.ic_music_note);
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
