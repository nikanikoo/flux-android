package org.nikanikoo.flux.ui.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.services.AudioPlayerService;
import org.nikanikoo.flux.ui.adapters.audio.PlaylistAdapter;
import org.nikanikoo.flux.ui.custom.SwipeToCloseHelper;
import org.nikanikoo.flux.utils.AlbumArtFetcher;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;

import java.util.List;
import java.util.Locale;

public class AudioPlayerActivity extends AppCompatActivity implements AudioPlayerService.PlayerCallback {

    private static final String TAG = "AudioPlayerActivity";

    private AudioPlayerService playerService;
    private boolean serviceBound = false;

    private TextView trackTitle;
    private TextView trackArtist;
    private ImageView albumArt;
    private ImageView albumArtEmpty;
    private TextView currentTime;
    private TextView totalTime;
    private SeekBar seekBar;
    private ImageView btnPlayPause;
    private ImageButton btnPrevious;
    private ImageButton btnNext;
    private ImageButton btnShuffle;
    private ImageButton btnRepeat;
    private ImageButton btnLyrics;
    private ImageButton btnAddToLibrary;

    private AudioManager audioManager;
    private boolean isAudioActionInProgress;

    private DrawerLayout drawerLayout;
    private RecyclerView playlistRecycler;
    private TextView playlistCount;
    private PlaylistAdapter playlistAdapter;

    private AlbumArtFetcher albumArtFetcher;
    private boolean isUserSeeking = false;

    private GestureDetector gestureDetector;
    private SwipeToCloseHelper swipeHelper;
    private float currentTranslationY = 0f;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlayerService.AudioBinder binder = (AudioPlayerService.AudioBinder) service;
            playerService = binder.getService();
            serviceBound = true;
            playerService.registerCallback(AudioPlayerActivity.this);
            updateUI();
            Logger.d(TAG, "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            Logger.d(TAG, "Service disconnected");
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = LocaleManager.getInstance(newBase);
        Context context = localeManager.updateContext(newBase);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applySavedTheme();
        themeManager.applyThemeToActivity(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        
        ThemeManager.applySystemBarsAppearance(this);
        
        initViews();
        setupGestureDetector();
        setupToolbar();
        setupControls();
        bindService();
    }

    private void initViews() {
        trackTitle = findViewById(R.id.track_title);
        trackArtist = findViewById(R.id.track_artist);
        albumArt = findViewById(R.id.album_art);
        albumArtEmpty = findViewById(R.id.album_art_empty);
        currentTime = findViewById(R.id.current_time);
        totalTime = findViewById(R.id.total_time);
        seekBar = findViewById(R.id.seek_bar);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnPrevious = findViewById(R.id.btn_previous);
        btnNext = findViewById(R.id.btn_next);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnLyrics = findViewById(R.id.btn_lyrics);
        btnAddToLibrary = findViewById(R.id.btn_add_to_library);
        drawerLayout = findViewById(R.id.drawer_layout);
        audioManager = AudioManager.getInstance(this);
        playlistRecycler = findViewById(R.id.playlist_recycler);
        playlistCount = findViewById(R.id.playlist_count);
        
        albumArtFetcher = new AlbumArtFetcher(this);

    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return true;
            }
        });

        swipeHelper = new SwipeToCloseHelper(drawerLayout, new SwipeToCloseHelper.OnSwipeListener() {
            @Override
            public void onSwipeStart() {
            }

            @Override
            public void onSwipeProgress(float progress, float translationY) {
                currentTranslationY = translationY;
                drawerLayout.setTranslationY(currentTranslationY);

                float alpha = 1f - progress * 0.7f;
                alpha = Math.max(0.3f, Math.min(1f, alpha));
                drawerLayout.setAlpha(alpha);
            }

            @Override
            public void onSwipeEnd(boolean shouldClose) {
                if (shouldClose) {
                    animateExit();
                } else {
                    animateReturn();
                }
            }
        });

        drawerLayout.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return swipeHelper.onTouchEvent(event, drawerLayout);
        });
    }

    private void animateEnter() {
        drawerLayout.setAlpha(0f);
        drawerLayout.setScaleX(0.9f);
        drawerLayout.setScaleY(0.9f);

        drawerLayout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(Constants.UI.ANIMATION_DURATION_SHORT)
                .start();
    }

    private void animateExit() {
         drawerLayout.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .translationY(currentTranslationY > 0 ? Constants.UI.ANIMATION_DURATION_MEDIUM : -Constants.UI.ANIMATION_DURATION_MEDIUM)
                .setDuration(Constants.UI.ANIMATION_DURATION_SHORT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finish();
                        overridePendingTransition(0, 0);
                    }
                })
                .start();
    }

    private void animateReturn() {
        ValueAnimator animator = ValueAnimator.ofFloat(currentTranslationY, 0f);
        animator.setDuration(Constants.UI.ANIMATION_DURATION_SHORT);
        animator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            drawerLayout.setTranslationY(value);

            float alpha = 1f - Math.abs(value) / (getWindow().getDecorView().getHeight() * 0.5f);
            alpha = Math.max(0.3f, Math.min(1f, alpha));
            drawerLayout.setAlpha(alpha);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentTranslationY = 0f;
            }
        });
        animator.start();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_audio_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_playlist) {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END);
            } else {
                drawerLayout.openDrawer(GravityCompat.END);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLyrics() {
        if (!serviceBound || playerService == null || playerService.getCurrentAudio() == null) return;
        Audio audio = playerService.getCurrentAudio();
        org.nikanikoo.flux.ui.fragments.media.LyricsBottomSheetDialogFragment dialog =
                org.nikanikoo.flux.ui.fragments.media.LyricsBottomSheetDialogFragment.newInstance(
                        audio.getTitle(),
                        audio.getArtist(),
                        audio.getLyrics_id(),
                        null
                );
        dialog.show(getSupportFragmentManager(), "audio_lyrics");
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> {
            if (serviceBound) {
                if (playerService.isPlaying()) {
                    playerService.pause();
                } else {
                    playerService.play();
                }
            }
        });

        btnPrevious.setOnClickListener(v -> {
            if (serviceBound) {
                playerService.previous();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (serviceBound) {
                playerService.next();
            }
        });

        btnShuffle.setOnClickListener(v -> {
            if (serviceBound) {
                playerService.toggleShuffle();
                updateShuffleButton();
            }
        });

        btnRepeat.setOnClickListener(v -> {
            if (serviceBound) {
                playerService.toggleRepeatMode();
                updateRepeatButton();
            }
        });

        btnLyrics.setOnClickListener(v -> showLyrics());

        btnAddToLibrary.setOnClickListener(v -> toggleAddToLibrary());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (serviceBound) {
                    playerService.seekTo(seekBar.getProgress());
                }
                isUserSeeking = false;
            }
        });

        setupPlaylist();
    }

    private void setupPlaylist() {
        playlistRecycler.setLayoutManager(new LinearLayoutManager(this));
        playlistAdapter = new PlaylistAdapter(
                new java.util.ArrayList<>(),
                -1,
                position -> {
                    if (serviceBound) {
                        playerService.seekToTrack(position);
                        drawerLayout.closeDrawer(GravityCompat.END);
                    }
                }
        );
        playlistRecycler.setAdapter(playlistAdapter);
    }

    private void bindService() {
        Intent intent = new Intent(this, AudioPlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void updateUI() {
        if (!serviceBound) return;

        Audio currentAudio = playerService.getCurrentAudio();
        if (currentAudio != null) {
            trackTitle.setText(currentAudio.getTitle());
            trackArtist.setText(currentAudio.getArtist());
            loadAlbumArt(currentAudio.getArtist(), currentAudio.getTitle());
            updateLyricsButton(currentAudio);
        }

        updatePlaylist();
        updatePlayPauseButton();
        updateAddToLibraryButton();
        updateShuffleButton();
        updateRepeatButton();
    }

    private void toggleAddToLibrary() {
        if (!serviceBound || isAudioActionInProgress) {
            return;
        }

        Audio audio = playerService.getCurrentAudio();
        if (audio == null) {
            return;
        }

        isAudioActionInProgress = true;
        btnAddToLibrary.setEnabled(false);

        AudioManager.AudioActionCallback callback = new AudioManager.AudioActionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    isAudioActionInProgress = false;
                    updateAddToLibraryButton();
                    if (audio.isAdded()) {
                        Toast.makeText(AudioPlayerActivity.this, R.string.audio_added, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AudioPlayerActivity.this, R.string.audio_removed, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isAudioActionInProgress = false;
                    updateAddToLibraryButton();
                    int messageId = audio.isAdded() ? R.string.audio_remove_error : R.string.audio_add_error;
                    Toast.makeText(AudioPlayerActivity.this, getString(messageId) + error, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (audio.isAdded()) {
            audioManager.deleteAudio(audio.getId(), audio.getOwnerId(), new AudioManager.AudioActionCallback() {
                @Override
                public void onSuccess() {
                    audio.setAdded(false);
                    callback.onSuccess();
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } else {
            audioManager.addAudio(audio.getId(), audio.getOwnerId(), new AudioManager.AudioActionCallback() {
                @Override
                public void onSuccess() {
                    audio.setAdded(true);
                    callback.onSuccess();
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        }
    }

    private void updateAddToLibraryButton() {
        if (btnAddToLibrary == null) {
            return;
        }

        Audio audio = serviceBound ? playerService.getCurrentAudio() : null;
        boolean hasAudio = audio != null;
        btnAddToLibrary.setEnabled(hasAudio && !isAudioActionInProgress);
        if (!hasAudio) {
            btnAddToLibrary.setImageResource(R.drawable.ic_add);
            btnAddToLibrary.setContentDescription(getString(R.string.audio_add_to_library));
            return;
        }

        if (audio.isAdded()) {
            btnAddToLibrary.setImageResource(R.drawable.ic_check);
            btnAddToLibrary.setContentDescription(getString(R.string.audio_remove_from_library));
        } else {
            btnAddToLibrary.setImageResource(R.drawable.ic_add);
            btnAddToLibrary.setContentDescription(getString(R.string.audio_add_to_library));
        }
    }

    private void loadAlbumArt(String artist, String title) {
        albumArt.setVisibility(android.view.View.GONE);
        albumArtEmpty.setVisibility(android.view.View.VISIBLE);
        
        if (artist == null || title == null || artist.isEmpty() || title.isEmpty()) {
            return;
        }

        albumArtFetcher.loadAlbumArt(artist, title, albumArt, R.drawable.ic_library_music, new AlbumArtFetcher.AlbumArtCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                albumArt.setVisibility(android.view.View.VISIBLE);
                albumArtEmpty.setVisibility(android.view.View.GONE);
            }

            @Override
            public void onError(String error) {
                Logger.d(TAG, "Failed to load album art: " + error);
            }
        });
    }

    private void updatePlaylist() {
        if (serviceBound && playlistAdapter != null) {
            List<Audio> playlist = playerService.getPlaylist();
            int currentPosition = playerService.getCurrentTrackPosition();
            playlistAdapter.updatePlaylist(playlist, currentPosition);
            playlistCount.setText(playlist.size() > 0 ? String.valueOf(playlist.size()) : "");
        }
    }

    private void updatePlayPauseButton() {
        if (serviceBound && playerService.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(this::updatePlayPauseButton);
    }

    @Override
    public void onTrackChanged(Audio audio, int position) {
        runOnUiThread(() -> {
            trackTitle.setText(audio.getTitle());
            trackArtist.setText(audio.getArtist());
            loadAlbumArt(audio.getArtist(), audio.getTitle());
            updateLyricsButton(audio);
            updateShuffleButton();
            updateRepeatButton();
            if (audio.getDuration() > 0) {
                totalTime.setText(formatTime(audio.getDuration() * 1000));
                seekBar.setMax(audio.getDuration() * 1000);
            }
            seekBar.setProgress(0);
            currentTime.setText("0:00");
            updatePlaylist();
            updateAddToLibraryButton();
        });
    }

    private void updateLyricsButton(Audio audio) {
        if (btnLyrics == null) return;
        if (audio != null && audio.getLyrics_id() > 0) {
            btnLyrics.setVisibility(View.VISIBLE);
        } else {
            btnLyrics.setVisibility(View.GONE);
        }
    }

    private void updateShuffleButton() {
        if (btnShuffle == null || !serviceBound || playerService == null) return;
        boolean shuffle = playerService.isShuffle();
        int color = getThemeColor(shuffle ? androidx.appcompat.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant);
        btnShuffle.setColorFilter(color);
        btnShuffle.setAlpha(shuffle ? 1.0f : 0.5f);
    }

    private void updateRepeatButton() {
        if (btnRepeat == null || !serviceBound || playerService == null) return;
        int mode = playerService.getRepeatMode();
        if (mode == AudioPlayerService.REPEAT_MODE_ONE) {
            btnRepeat.setImageResource(R.drawable.ic_repeat_one);
            btnRepeat.setColorFilter(getThemeColor(androidx.appcompat.R.attr.colorPrimary));
            btnRepeat.setAlpha(1.0f);
        } else if (mode == AudioPlayerService.REPEAT_MODE_ALL) {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setColorFilter(getThemeColor(androidx.appcompat.R.attr.colorPrimary));
            btnRepeat.setAlpha(1.0f);
        } else {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setColorFilter(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            btnRepeat.setAlpha(0.5f);
        }
    }

    private int getThemeColor(int attrRes) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    @Override
    public void onProgressUpdate(int currentPosition, int duration) {
        runOnUiThread(() -> {
            if (!isUserSeeking) {
                seekBar.setMax(duration);
                seekBar.setProgress(currentPosition);
                currentTime.setText(formatTime(currentPosition));
                totalTime.setText(formatTime(duration));
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Logger.e(TAG, "Playback error: " + error);
        });
    }

    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            playerService.unregisterCallback(this);
            unbindService(serviceConnection);
            serviceBound = false;
        }
        if (albumArtFetcher != null) {
            albumArtFetcher.shutdown();
        }
    }
}
