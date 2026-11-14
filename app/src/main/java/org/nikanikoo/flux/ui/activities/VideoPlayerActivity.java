package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.android.material.button.MaterialButton;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;
import org.nikanikoo.flux.data.models.Video;
import org.nikanikoo.flux.data.managers.LikesManager;
import org.nikanikoo.flux.ui.fragments.comments.VideoCommentsBottomSheetFragment;

import java.lang.ref.WeakReference;

@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";
    private static final String EXTRA_VIDEO = "extra_video";
    private static final String EXTRA_VIDEO_URL = "extra_video_url";
    private static final String EXTRA_VIDEO_TITLE = "extra_video_title";
    private static final String STATE_PLAYBACK_POSITION = "playback_position";
    private static final String STATE_PLAY_WHEN_READY = "play_when_ready";

    private PlayerView playerView;
    private ExoPlayer player;
    private Video video;
    private String videoUrl;
    
    private TextView titleText;
    private TextView descriptionText;
    private MaterialButton likeButton;
    private MaterialButton commentsButton;
    private ProgressBar loadingProgress;
    private View rewindZone;
    private View forwardZone;
    private TextView rewindIndicator;
    private TextView forwardIndicator;
    
    private long playbackPosition = 0;
    private boolean playWhenReady = true;
    private static final long SEEK_INCREMENT = 5000; // 5 seconds
    
    // Use static handler with WeakReference to avoid memory leaks
    private static class SeekHandler extends android.os.Handler {
        private final WeakReference<VideoPlayerActivity> activityRef;
        
        SeekHandler(VideoPlayerActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }
    }
    
    private SeekHandler seekHandler;
    private Runnable hideRewindIndicator;
    private Runnable hideForwardIndicator;
    
    // Static GestureListener to avoid holding reference to Activity
    private static class DoubleTapGestureListener extends android.view.GestureDetector.SimpleOnGestureListener {
        private final WeakReference<VideoPlayerActivity> activityRef;
        private final boolean isRewind;
        
        DoubleTapGestureListener(VideoPlayerActivity activity, boolean isRewind) {
            this.activityRef = new WeakReference<>(activity);
            this.isRewind = isRewind;
        }
        
        @Override
        public boolean onDoubleTap(android.view.MotionEvent e) {
            VideoPlayerActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return false;
            
            Logger.d(TAG, "Double tap detected on " + (isRewind ? "rewind" : "forward") + " zone");
            if (activity.player != null) {
                long currentPosition = activity.player.getCurrentPosition();
                long newPosition;
                
                if (isRewind) {
                    newPosition = Math.max(0, currentPosition - SEEK_INCREMENT);
                    activity.showSeekIndicator(activity.rewindIndicator, "« 5");
                    Logger.d(TAG, "Rewind to: " + newPosition);
                } else {
                    long duration = activity.player.getDuration();
                    newPosition = Math.min(duration, currentPosition + SEEK_INCREMENT);
                    activity.showSeekIndicator(activity.forwardIndicator, "5 »");
                    Logger.d(TAG, "Forward to: " + newPosition);
                }
                
                activity.player.seekTo(newPosition);
            }
            return true;
        }
        
        @Override
        public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
            VideoPlayerActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return false;
            
            Logger.d(TAG, "Single tap confirmed on zone");
            // Toggle controls visibility
            if (activity.playerView.isControllerFullyVisible()) {
                activity.playerView.hideController();
            } else {
                activity.playerView.showController();
            }
            return true;
        }
        
        @Override
        public boolean onDown(android.view.MotionEvent e) {
            // Must return true to indicate we want to handle this gesture
            return true;
        }
    }

    public static void start(Context context, Video video) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra(EXTRA_VIDEO, video);
        context.startActivity(intent);
    }

    public static void start(Context context, String videoUrl, String videoTitle) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra(EXTRA_VIDEO_URL, videoUrl);
        intent.putExtra(EXTRA_VIDEO_TITLE, videoTitle);
        context.startActivity(intent);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = LocaleManager.getInstance(newBase);
        Context context = localeManager.updateContext(newBase);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        
        ThemeManager.applySystemBarsAppearance(this);
        
        // Keep screen on during playback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Restore state if available
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong(STATE_PLAYBACK_POSITION, 0);
            playWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY, true);
        }

        // Initialize handler with WeakReference
        seekHandler = new SeekHandler(this);

        initViews();
        loadVideoData();
        setupVideoActions();
        setupListeners();
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        titleText = findViewById(R.id.video_title);
        descriptionText = findViewById(R.id.video_description);
        likeButton = findViewById(R.id.video_like_button);
        commentsButton = findViewById(R.id.video_comments_button);
        loadingProgress = findViewById(R.id.loading_progress);
        rewindZone = findViewById(R.id.rewind_zone);
        forwardZone = findViewById(R.id.forward_zone);
        rewindIndicator = findViewById(R.id.rewind_indicator);
        forwardIndicator = findViewById(R.id.forward_indicator);
    }

    private void loadVideoData() {
        Intent intent = getIntent();
        
        if (intent.hasExtra(EXTRA_VIDEO)) {
            video = (Video) intent.getSerializableExtra(EXTRA_VIDEO);
            if (video != null) {
                videoUrl = video.getPlayer();
                titleText.setText(video.getTitle());
                descriptionText.setText(video.getDescription());
                descriptionText.setVisibility(video.getDescription() == null || video.getDescription().trim().isEmpty()
                        ? View.GONE : View.VISIBLE);
            }
        } else if (intent.hasExtra(EXTRA_VIDEO_URL)) {
            videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
            titleText.setText(intent.getStringExtra(EXTRA_VIDEO_TITLE));
        }

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, getString(R.string.video_url_not_found), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupVideoActions() {
        boolean hasApiVideo = video != null && video.getId() > 0 && video.getOwnerId() != 0;
        likeButton.setEnabled(hasApiVideo);
        commentsButton.setEnabled(hasApiVideo);
        if (!hasApiVideo) {
            likeButton.setVisibility(View.GONE);
            commentsButton.setVisibility(View.GONE);
            return;
        }
        updateVideoActionLabels();
        likeButton.setOnClickListener(v -> LikesManager.getInstance(this).toggleLike(
                "video", video.getOwnerId(), video.getId(), video.isUserLikes(),
                new LikesManager.LikeCallback() {
                    @Override
                    public void onSuccess(int likesCount) {
                        runOnUiThread(() -> {
                            video.setUserLikes(!video.isUserLikes());
                            video.setLikes(likesCount);
                            updateVideoActionLabels();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(VideoPlayerActivity.this, error, Toast.LENGTH_SHORT).show());
                    }
                }));
        commentsButton.setOnClickListener(v -> VideoCommentsBottomSheetFragment
                .newInstance(video.getOwnerId(), video.getId(), video.isCanComment())
                .show(getSupportFragmentManager(), "video_comments"));
    }

    private void updateVideoActionLabels() {
        if (video == null) return;
        likeButton.setText(getString(video.isUserLikes() ? R.string.video_liked : R.string.video_not_liked,
                video.getLikes()));
        commentsButton.setText(getString(R.string.video_comments_count, video.getComments()));
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializePlayer() {
        if (player != null) {
            return;
        }

        // Создаем DataSource.Factory с поддержкой SSL для самоподписанных сертификатов
        androidx.media3.datasource.DataSource.Factory dataSourceFactory = 
            org.nikanikoo.flux.utils.ExoPlayerHelper.createDataSourceFactory(this);
        
        // Create player with custom data source
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                )
                .build();
        
        playerView.setPlayer(player);

        // Build media item
        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
        player.setMediaItem(mediaItem);

        // Restore playback state
        player.seekTo(playbackPosition);
        player.setPlayWhenReady(playWhenReady);

        // Add listener for player state
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        loadingProgress.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        loadingProgress.setVisibility(View.GONE);
                        break;
                    case Player.STATE_ENDED:
                        // Video ended
                        break;
                    case Player.STATE_IDLE:
                        loadingProgress.setVisibility(View.GONE);
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Logger.e(TAG, "Player error: " + error.getMessage(), error);
                loadingProgress.setVisibility(View.GONE);

                Toast.makeText(VideoPlayerActivity.this,
                        getString(R.string.video_error) + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        player.prepare();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (androidx.media3.common.util.Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (androidx.media3.common.util.Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (androidx.media3.common.util.Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (androidx.media3.common.util.Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (player != null) {
            outState.putLong(STATE_PLAYBACK_POSITION, player.getCurrentPosition());
            outState.putBoolean(STATE_PLAY_WHEN_READY, player.getPlayWhenReady());
        }
    }

    private void setupListeners() {
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        
        // Setup double tap zones with static gesture listeners
        setupDoubleTapZone(rewindZone, true);
        setupDoubleTapZone(forwardZone, false);
    }

    private void setupDoubleTapZone(View zone, boolean isRewind) {
        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(
            this,
            new DoubleTapGestureListener(this, isRewind)
        );
        
        zone.setOnTouchListener((v, event) -> {
            boolean handled = gestureDetector.onTouchEvent(event);
            Logger.d(TAG, "Touch event on zone: " + event.getAction() + ", handled: " + handled);
            // Don't consume the event completely - let it pass through to PlayerView for other interactions
            return handled;
        });
    }

    private void showSeekIndicator(TextView indicator, String text) {
        indicator.setText(text);
        indicator.setVisibility(View.VISIBLE);
        indicator.setAlpha(0f);
        indicator.animate()
            .alpha(1f)
            .setDuration(200)
            .start();
        
        // Hide after delay using WeakReference
        Runnable hideRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                indicator.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        if (!isFinishing()) {
                            indicator.setVisibility(View.GONE);
                        }
                    })
                    .start();
            }
        };
        
        seekHandler.removeCallbacks(hideRunnable);
        seekHandler.postDelayed(hideRunnable, 800);
    }

    private void toggleFullscreen() {
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Update fullscreen button icon
        View fullscreenBtn = playerView.findViewById(R.id.exo_fullscreen);
        if (fullscreenBtn != null) {
            ImageButton btn = (ImageButton) fullscreenBtn;
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                btn.setImageResource(R.drawable.ic_fullscreen_exit);
            } else {
                btn.setImageResource(R.drawable.ic_fullscreen);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        
        // Clean up handler and callbacks
        if (seekHandler != null) {
            seekHandler.removeCallbacksAndMessages(null);
            seekHandler = null;
        }
        
        // Clear view references to help GC
        playerView = null;
        rewindZone = null;
        forwardZone = null;
        rewindIndicator = null;
        forwardIndicator = null;
        
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
