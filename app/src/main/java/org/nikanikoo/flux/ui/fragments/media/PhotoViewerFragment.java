package org.nikanikoo.flux.ui.fragments.media;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import org.nikanikoo.flux.Constants;

import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.utils.Logger;
import java.util.ArrayList;
import java.util.List;

public class PhotoViewerFragment extends Fragment {
    
    private static final String TAG = "PhotoViewerFragment";
    private static final String ARG_IMAGE_URLS = "image_urls";
    private static final String ARG_CURRENT_POSITION = "current_position";
    private static final String ARG_POST = "post";
    private static final String ARG_AUTHOR_NAME = "author_name";
    
    private static final int UI_HIDE_DELAY = 3000; // 3 секунды до автоскрытия UI
    
    private ViewPager2 viewPager;
    private PhotoPagerAdapter adapter;
    private List<String> imageUrls;
    private int currentPosition;
    private Post post;
    private String authorName;
    
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private ImageView btnBack;
    private ImageView btnLike;
    private ImageView btnComments;
    private ImageView btnShare;
    private View bottomPanel;
    
    private boolean isUIVisible = true;
    private Handler uiHandler;
    private Runnable hideUIRunnable;
    
    public static PhotoViewerFragment newInstance(List<String> imageUrls, int position, Post post, String authorName) {
        PhotoViewerFragment fragment = new PhotoViewerFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_IMAGE_URLS, new ArrayList<>(imageUrls));
        args.putInt(ARG_CURRENT_POSITION, position);
        args.putSerializable(ARG_POST, post);
        args.putString(ARG_AUTHOR_NAME, authorName);
        fragment.setArguments(args);
        return fragment;
    }
    
    // Перегруженный метод для случаев без поста (например, просмотр аватара)
    public static PhotoViewerFragment newInstance(List<String> imageUrls, int position, String title) {
        PhotoViewerFragment fragment = new PhotoViewerFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_IMAGE_URLS, new ArrayList<>(imageUrls));
        args.putInt(ARG_CURRENT_POSITION, position);
        args.putSerializable(ARG_POST, null);
        args.putString(ARG_AUTHOR_NAME, title);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            imageUrls = getArguments().getStringArrayList(ARG_IMAGE_URLS);
            currentPosition = getArguments().getInt(ARG_CURRENT_POSITION, 0);
            post = (Post) getArguments().getSerializable(ARG_POST);
            authorName = getArguments().getString(ARG_AUTHOR_NAME);
        }
        
        uiHandler = new Handler(Looper.getMainLooper());
        hideUIRunnable = this::hideUI;
        
        Logger.d(TAG, "PhotoViewerFragment created with " + (imageUrls != null ? imageUrls.size() : 0) + " images");
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_photo_viewer, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupFullscreen();
        setupToolbar();
        setupViewPager();
        setupActionButtons();
        setupTapToToggle();
        
        // Запускаем таймер автоскрытия UI
        scheduleUIHide();
    }
    
    private void initViews(View view) {
        viewPager = view.findViewById(R.id.viewPager);
        toolbar = view.findViewById(R.id.toolbar);
        toolbarTitle = view.findViewById(R.id.toolbarTitle);
        btnBack = view.findViewById(R.id.btnBack);
        btnLike = view.findViewById(R.id.btnLike);
        btnComments = view.findViewById(R.id.btnComments);
        btnShare = view.findViewById(R.id.btnShare);
        bottomPanel = view.findViewById(R.id.bottomPanel);
    }
    
    private void setupFullscreen() {
        if (getActivity() != null) {
            // Скрываем ActionBar главной активности
            if (getActivity() instanceof AppCompatActivity) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().hide();
                }
            }
            
            // Устанавливаем полноэкранный режим
            getActivity().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
            
            // Скрываем системные UI элементы (статус бар и навигационная панель)
            View decorView = getActivity().getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
            
            // Добавляем слушатель изменений системного UI для поддержания immersive режима
            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    // Системный UI стал видимым, скрываем его снова через небольшую задержку
                    decorView.postDelayed(() -> {
                        if (getActivity() != null && !getActivity().isFinishing()) {
                            decorView.setSystemUiVisibility(uiOptions);
                        }
                    }, 2000);
                }
            });
            
            Logger.d(TAG, "Fullscreen mode enabled");
        }
    }
    
    private void restoreNormalMode() {
        if (getActivity() != null) {
            // Восстанавливаем ActionBar главной активности
            if (getActivity() instanceof AppCompatActivity) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().show();
                }
            }
            
            // Убираем полноэкранный режим
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            // Показываем системные UI элементы
            View decorView = getActivity().getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            
            // Убираем слушатель изменений системного UI
            decorView.setOnSystemUiVisibilityChangeListener(null);
            
            Logger.d(TAG, "Normal mode restored");
        }
    }
    
    private void setupTapToToggle() {
        // Тапы обрабатываются в PhotoFragment через PhotoView
        // Здесь можно добавить дополнительную логику если нужно
        Logger.d(TAG, "Tap-to-toggle setup completed");
    }
    
    public void toggleUI() {
        if (isUIVisible) {
            hideUI();
        } else {
            showUI();
        }
    }
    
    private void hideUI() {
        if (!isUIVisible) return;
        
        isUIVisible = false;
        
        // Анимация скрытия toolbar
        toolbar.animate()
                .translationY(-toolbar.getHeight())
                .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                .start();
        
        // Анимация скрытия нижней панели
        if (bottomPanel != null) {
            bottomPanel.animate()
                    .translationY(bottomPanel.getHeight())
                    .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                    .start();
        }
        
        // Отменяем таймер автоскрытия
        cancelUIHide();
        
        Logger.d(TAG, "UI hidden");
    }
    
    private void showUI() {
        if (isUIVisible) return;
        
        isUIVisible = true;
        
        // Анимация показа toolbar
        toolbar.animate()
                .translationY(0)
                .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                .start();
        
        // Анимация показа нижней панели
        if (bottomPanel != null) {
            bottomPanel.animate()
                    .translationY(0)
                    .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                    .start();
        }
        
        // Запускаем таймер автоскрытия
        scheduleUIHide();
        
        Logger.d(TAG, "UI shown");
    }
    
    private void scheduleUIHide() {
        cancelUIHide();
        if (uiHandler != null && hideUIRunnable != null) {
            uiHandler.postDelayed(hideUIRunnable, UI_HIDE_DELAY);
        }
    }
    
    private void cancelUIHide() {
        if (uiHandler != null && hideUIRunnable != null) {
            uiHandler.removeCallbacks(hideUIRunnable);
        }
    }
    private void setupToolbar() {
        toolbarTitle.setText(authorName != null ? authorName : getString(R.string.photo_viewer));
        
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });
    }
    
    private void setupViewPager() {
        adapter = new PhotoPagerAdapter(this, imageUrls);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPosition, false);
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateToolbarTitle();
            }
        });
        
        updateToolbarTitle();
    }
    
    private void setupActionButtons() {
        btnLike.setOnClickListener(v -> {
            if (post != null) {
                toggleLike();
            }
            // Показываем UI при взаимодействии с кнопками
            showUI();
        });
        
        btnComments.setOnClickListener(v -> {
            if (post != null) {
                // Закрываем текущую активность и открываем комментарии в MainActivity
                if (getActivity() != null) {
                    getActivity().finish();
                    
                    // Открываем комментарии через Intent
                    Intent intent = new Intent(getContext(), MainActivity.class);
                    intent.putExtra("open_comments", true);
                    intent.putExtra("post", post);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            }
        });
        
        btnShare.setOnClickListener(v -> {
            // Share functionality - placeholder for future implementation
            Toast.makeText(getContext(), getString(R.string.share), Toast.LENGTH_SHORT).show();
            showUI();
        });
        
        updateLikeButton();
    }
    
    private void updateToolbarTitle() {
        if (imageUrls != null && imageUrls.size() > 1) {
            String title = (authorName != null ? authorName : getString(R.string.photo_viewer)) +
                          " (" + (currentPosition + 1) + "/" + imageUrls.size() + ")";
            toolbarTitle.setText(title);
        }
    }
    
    private void toggleLike() {
        if (post == null) return;
        
        // Сохраняем оригинальное состояние
        final boolean originalLikedState = post.isLiked();
        final int originalLikeCount = post.getLikeCount();
        
        // Optimistic UI - сразу обновляем интерфейс
        final boolean newLikedState = !originalLikedState;
        final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;
        
        post.setLiked(newLikedState);
        post.setLikeCount(newLikeCount);
        updateLikeButton();
        
        PostsManager postsManager = PostsManager.getInstance(getContext());
        postsManager.toggleLikeOptimistic(post, originalLikedState, new PostsManager.LikeToggleCallback() {
            @Override
            public void onSuccess(int serverLikesCount, boolean serverIsLiked) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        post.setLikeCount(serverLikesCount);
                        post.setLiked(serverIsLiked);
                        updateLikeButton();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Возвращаем оригинальное состояние при ошибке
                        post.setLiked(originalLikedState);
                        post.setLikeCount(originalLikeCount);
                        updateLikeButton();
                        Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void updateLikeButton() {
        if (post != null) {
            btnLike.setImageResource(post.isLiked() ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite);
            if (getContext() != null) {
                int color = post.isLiked() ? 
                    androidx.core.content.ContextCompat.getColor(getContext(), R.color.like_color) : 
                    androidx.core.content.ContextCompat.getColor(getContext(), R.color.icon_color);
                btnLike.setColorFilter(color);
            }
        }
    }
    
    private static class PhotoPagerAdapter extends FragmentStateAdapter {
        private final List<String> imageUrls;
        
        public PhotoPagerAdapter(@NonNull Fragment fragment, List<String> imageUrls) {
            super(fragment);
            this.imageUrls = imageUrls;
        }
        
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return PhotoFragment.newInstance(imageUrls.get(position));
        }
        
        @Override
        public int getItemCount() {
            return imageUrls != null ? imageUrls.size() : 0;
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Восстанавливаем полноэкранный режим при возврате к фрагменту
        setupFullscreen();
        
        // Показываем UI при возврате
        if (!isUIVisible) {
            showUI();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        // Отменяем таймеры при уходе с фрагмента
        cancelUIHide();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Отменяем таймеры
        cancelUIHide();
        
        // Восстанавливаем нормальный режим при выходе из фрагмента
        restoreNormalMode();
        
        Logger.d(TAG, "PhotoViewerFragment destroyed");
    }
    
    @Override
    public void onDetach() {
        super.onDetach();
        
        // Очищаем handler
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
            uiHandler = null;
        }
        hideUIRunnable = null;
    }
}