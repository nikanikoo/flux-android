package org.nikanikoo.flux.ui.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.ui.fragments.media.PhotoViewerPageFragment;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.custom.SwipeToCloseHelper;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.SSLHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PhotoViewerActivity extends AppCompatActivity {

    private static final String TAG = "PhotoViewerActivity";
    private static final String EXTRA_IMAGE_URLS = "image_urls";
    private static final String EXTRA_CURRENT_POSITION = "current_position";
    private static final String EXTRA_POST = "post";
    private static final String EXTRA_AUTHOR_NAME = "author_name";

    private static final int UI_HIDE_DELAY = 3000;

    private ViewPager2 viewPager;
    private View rootContainer;
    private View topPanel;
    private View bottomPanel;
    private TextView titleText;
    private ImageView btnBack;
    private ImageView btnLike;
    private ImageView btnComments;
    private ImageView btnShare;
    private ImageView btnMore;
    
    private List<String> imageUrls;
    private int currentPosition;
    private Post post;
    private String authorName;

    private boolean isUIVisible = true;
    private Handler uiHandler;
    private Runnable hideUIRunnable;

    private GestureDetector gestureDetector;
    private SwipeToCloseHelper swipeHelper;
    private float currentTranslationY = 0f;

    private ActivityResultLauncher<String> storagePermissionLauncher;
    private String pendingDownloadUrl;
    private List<String> downloadQueue = new ArrayList<>();
    private int downloadIndex = 0;
    private int downloadSuccessCount = 0;
    private int downloadErrorCount = 0;
    private Runnable pendingSuccessCallback;
    private Runnable pendingErrorCallback;
    
    private final OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            animateExit();
        }
    };
    
    public static void start(Context context, List<String> imageUrls, int position, Post post, String authorName) {
        Intent intent = new Intent(context, PhotoViewerActivity.class);
        intent.putStringArrayListExtra(EXTRA_IMAGE_URLS, new ArrayList<>(imageUrls));
        intent.putExtra(EXTRA_CURRENT_POSITION, position);
        intent.putExtra(EXTRA_POST, post);
        intent.putExtra(EXTRA_AUTHOR_NAME, authorName);
        context.startActivity(intent);
    }
    
    public static void start(Context context, List<String> imageUrls, int position, String title) {
        start(context, imageUrls, position, null, title);
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

        setupTransparentWindow();
        setContentView(R.layout.activity_photo_viewer);
        setupPermissionLauncher();
        extractIntentData();
        initViews();
        setupGestureDetector();
        setupViewPager();
        setupUI();
        animateEnter();
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        Logger.d(TAG, "PhotoViewerActivity created with " + (imageUrls != null ? imageUrls.size() : 0) + " images");
    }

    private void setupTransparentWindow() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }
    
    private void extractIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            imageUrls = intent.getStringArrayListExtra(EXTRA_IMAGE_URLS);
            currentPosition = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0);
            post = (Post) intent.getSerializableExtra(EXTRA_POST);
            authorName = intent.getStringExtra(EXTRA_AUTHOR_NAME);
        }
    }
    
    private void initViews() {
        rootContainer = findViewById(R.id.rootContainer);
        viewPager = findViewById(R.id.viewPager);
        topPanel = findViewById(R.id.topPanel);
        bottomPanel = findViewById(R.id.bottomPanel);
        titleText = findViewById(R.id.titleText);
        btnBack = findViewById(R.id.btnBack);
        btnLike = findViewById(R.id.btnLike);
        btnComments = findViewById(R.id.btnComments);
        btnShare = findViewById(R.id.btnShare);
        btnMore = findViewById(R.id.btnMore);

        uiHandler = new Handler(Looper.getMainLooper());
        hideUIRunnable = this::hideUI;
    }
    
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleUI();
                return true;
            }
        });

        swipeHelper = new SwipeToCloseHelper(rootContainer, new SwipeToCloseHelper.OnSwipeListener() {
            @Override
            public void onSwipeStart() {
                hideUI();
                viewPager.setUserInputEnabled(false);
            }
            
            @Override
            public void onSwipeProgress(float progress, float translationY) {
                currentTranslationY = translationY;
                rootContainer.setTranslationY(currentTranslationY);

                float alpha = 1f - progress * 0.7f;
                alpha = Math.max(0.3f, Math.min(1f, alpha));
                rootContainer.setAlpha(alpha);
            }
            
            @Override
            public void onSwipeEnd(boolean shouldClose) {
                viewPager.setUserInputEnabled(true);
                
                if (shouldClose) {
                    animateExit();
                } else {
                    animateReturn();
                }
            }
        });

        btnMore.setOnClickListener(v -> {
            showPopupMenu();
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        
        if (swipeHelper != null && rootContainer != null) {
            if (swipeHelper.onTouchEvent(ev, rootContainer)) {
                return true;
            }
        }
        
        return super.dispatchTouchEvent(ev);
    }

    public void toggleUI() {
        if (isUIVisible) {
            hideUI();
        } else {
            showUI();
        }
    }

    public void setSwipeToCloseEnabled(boolean enabled) {
        if (swipeHelper != null) {
            swipeHelper.setCanStartSwipe(enabled);
        }
    }
    
    private void animateReturn() {
        ValueAnimator animator = ValueAnimator.ofFloat(currentTranslationY, 0f);
        animator.setDuration(Constants.UI.ANIMATION_DURATION_SHORT);
        animator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            rootContainer.setTranslationY(value);
            
            float alpha = 1f - Math.abs(value) / (getWindow().getDecorView().getHeight() * 0.5f);
            alpha = Math.max(0.3f, Math.min(1f, alpha));
            rootContainer.setAlpha(alpha);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentTranslationY = 0f;
                scheduleUIHide();
            }
        });
        animator.start();
    }
    
    private void setupViewPager() {
        PhotoPagerAdapter adapter = new PhotoPagerAdapter(this, imageUrls);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPosition, false);
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateTitle();
                showUI();
            }
        });
        
        updateTitle();
    }
    
    private void setupUI() {
        titleText.setText(authorName != null ? authorName : getString(R.string.photo_viewer));

        btnBack.setOnClickListener(v -> animateExit());

        btnLike.setOnClickListener(v -> {
            if (post != null) {
                toggleLike();
            }
            showUI();
        });
        
        btnComments.setOnClickListener(v -> {
            if (post != null) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("open_comments", true);
                intent.putExtra("post", post);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });
        
        btnShare.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.share), Toast.LENGTH_SHORT).show();
            showUI();
        });
        
        updateLikeButton();
        scheduleUIHide();

        if (swipeHelper != null) {
            swipeHelper.setCanStartSwipe(true);
        }
    }
    
    private void updateTitle() {
        if (imageUrls != null && imageUrls.size() > 1) {
            String title = (authorName != null ? authorName : getString(R.string.photo_viewer)) +
                          " (" + (currentPosition + 1) + "/" + imageUrls.size() + ")";
            titleText.setText(title);
        }
    }
    
    private void hideUI() {
        if (!isUIVisible) return;
        
        isUIVisible = false;
        
        topPanel.animate()
                .translationY(-topPanel.getHeight())
                .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                .start();
        
        bottomPanel.animate()
                .translationY(bottomPanel.getHeight())
                .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                .start();
        
        cancelUIHide();
        Logger.d(TAG, "UI hidden");
    }
    
    private void showUI() {
        if (isUIVisible) return;
        
        isUIVisible = true;
        
        topPanel.animate()
                .translationY(0)
                .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                .start();
        
        bottomPanel.animate()
                .translationY(0)
                .setDuration(Constants.UI.ANIMATION_DURATION_MEDIUM)
                .start();
        
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
    
    private void animateEnter() {
        rootContainer.setAlpha(0f);
        rootContainer.setScaleX(0.9f);
        rootContainer.setScaleY(0.9f);
        
        rootContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(Constants.UI.ANIMATION_DURATION_SHORT)
                .start();
    }
    
    private void animateExit() {
        cancelUIHide();
        
        rootContainer.animate()
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
    
    private void toggleLike() {
        if (post == null) return;
        
        final boolean originalLikedState = post.isLiked();
        final int originalLikeCount = post.getLikeCount();
        
        final boolean newLikedState = !originalLikedState;
        final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;
        
        post.setLiked(newLikedState);
        post.setLikeCount(newLikeCount);
        updateLikeButton();
        
        PostsManager postsManager = PostsManager.getInstance(this);
        postsManager.toggleLikeOptimistic(post, originalLikedState, new PostsManager.LikeToggleCallback() {
            @Override
            public void onSuccess(int serverLikesCount, boolean serverIsLiked) {
                runOnUiThread(() -> {
                    post.setLikeCount(serverLikesCount);
                    post.setLiked(serverIsLiked);
                    updateLikeButton();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    post.setLiked(originalLikedState);
                    post.setLikeCount(originalLikeCount);
                    updateLikeButton();
                    Toast.makeText(PhotoViewerActivity.this, getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void updateLikeButton() {
        if (post != null) {
            btnLike.setImageResource(post.isLiked() ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite);

            int color = post.isLiked() ?
                androidx.core.content.ContextCompat.getColor(this, R.color.like_color) :
                androidx.core.content.ContextCompat.getColor(this, R.color.icon_color);
            btnLike.setColorFilter(color);
        }
    }

    private void showPopupMenu() {
        cancelUIHide();
        
        PopupMenu popupMenu = new PopupMenu(this, btnMore);
        popupMenu.getMenuInflater().inflate(R.menu.menu_photo_viewer, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_download) {
                showDownloadChoiceDialog();
                return true;
            }
            return false;
        });
        
        popupMenu.setOnDismissListener(menu -> scheduleUIHide());
        popupMenu.show();
    }

    private void showDownloadChoiceDialog() {
        if (imageUrls == null || imageUrls.size() <= 1) {
            downloadCurrentImage();
            return;
        }
        
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.download_image)
                .setItems(new String[]{
                        getString(R.string.download_current_image),
                        getString(R.string.download_all_images)
                }, (dialog, which) -> {
                    if (which == 0) {
                        downloadCurrentImage();
                    } else {
                        downloadAllImages();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void downloadCurrentImage() {
        if (imageUrls == null || imageUrls.isEmpty() || currentPosition >= imageUrls.size()) {
            Toast.makeText(this, getString(R.string.download_error), Toast.LENGTH_SHORT).show();
            return;
        }

        String url;
        List<String> maxResUrls = post != null ? post.getImageMaxResUrls() : null;
        if (maxResUrls != null && !maxResUrls.isEmpty() && currentPosition < maxResUrls.size()) {
            url = maxResUrls.get(currentPosition);
        } else {
            url = imageUrls.get(currentPosition);
        }

        downloadImageWithPermission(url,
            () -> {
                Toast.makeText(this, getString(R.string.download_success), Toast.LENGTH_SHORT).show();
                scheduleUIHide();
            },
            () -> {
                Toast.makeText(this, getString(R.string.download_error), Toast.LENGTH_SHORT).show();
                scheduleUIHide();
            }
        );
    }

    private void downloadAllImages() {
        if (imageUrls == null || imageUrls.isEmpty()) {
            Toast.makeText(this, getString(R.string.download_error), Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUrls.size() == 1) {
            downloadCurrentImage();
            return;
        }

        // Initialize download queue with maxRes URLs if available
        downloadQueue = new ArrayList<>();
        List<String> maxResUrls = post != null ? post.getImageMaxResUrls() : null;

        for (int i = 0; i < imageUrls.size(); i++) {
            if (maxResUrls != null && !maxResUrls.isEmpty() && i < maxResUrls.size()) {
                downloadQueue.add(maxResUrls.get(i));
            } else {
                downloadQueue.add(imageUrls.get(i));
            }
        }
        downloadIndex = 0;
        downloadSuccessCount = 0;
        downloadErrorCount = 0;

        Toast.makeText(this, getString(R.string.download_starting), Toast.LENGTH_SHORT).show();
        scheduleUIHide();

        downloadNextInQueue();
    }

    private void setupPermissionLauncher() {
        storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    if (pendingDownloadUrl != null) {
                        String url = pendingDownloadUrl;
                        pendingDownloadUrl = null;
                        Runnable onSuccess = pendingSuccessCallback;
                        Runnable onError = pendingErrorCallback;
                        pendingSuccessCallback = null;
                        pendingErrorCallback = null;
                        startDownload(url, onSuccess, onError);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.download_permission_denied), Toast.LENGTH_SHORT).show();
                    pendingDownloadUrl = null;
                    pendingSuccessCallback = null;
                    pendingErrorCallback = null;
                    scheduleUIHide();
                }
            }
        );
    }

    private void downloadNextInQueue() {
        if (downloadIndex >= downloadQueue.size()) {
            String message = getString(R.string.download_success) + ": " + downloadSuccessCount + "/" + downloadQueue.size();
            if (downloadErrorCount > 0) {
                message += " (" + downloadErrorCount + " " + getString(R.string.download_error) + ")";
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            scheduleUIHide();
            return;
        }

        String url = downloadQueue.get(downloadIndex);
        downloadImageWithPermission(url, () -> {
            downloadIndex++;
            downloadSuccessCount++;
            downloadNextInQueue();
        }, () -> {
            downloadIndex++;
            downloadErrorCount++;
            downloadNextInQueue();
        });
    }

    private void downloadImageWithPermission(String url) {
        downloadImageWithPermission(url, null, null);
    }

    private void downloadImageWithPermission(String url, Runnable onSuccess, Runnable onError) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+
            startDownload(url, onSuccess, onError);
        } else {
            // Android 9 and below
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                startDownload(url, onSuccess, onError);
            } else {
                pendingDownloadUrl = url;
                pendingSuccessCallback = onSuccess;
                pendingErrorCallback = onError;
                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    private void startDownload(String url, Runnable onSuccess) {
        startDownload(url, onSuccess, () -> {
            if (onSuccess != null) {}
        });
    }

    private void startDownload(String url, Runnable onSuccess, Runnable onError) {
        try {
            String folderName = getString(R.string.download_folder_name);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+
                downloadWithMediaStoreApi(url, folderName, onSuccess, onError);
            } else {
                // Android 9 and below
                downloadWithManualFolder(url, folderName, onSuccess, onError);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Download failed: " + e.getMessage());
            if (onError != null) {
                onError.run();
            }
        }
    }

    private OkHttpClient createOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        SSLHelper.configureToIgnoreSSL(builder);
        return builder.build();
    }

    private void downloadWithMediaStoreApi(String url, String folderName, Runnable onSuccess, Runnable onError) {
        new Thread(() -> {
            OkHttpClient client = createOkHttpClient();
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP error: " + response.code());
                }
                
                InputStream inputStream = response.body().byteStream();
                
                String fileName = "IMG_" + System.currentTimeMillis() + "_" +
                                 (int)(Math.random() * 1000) + ".jpg";
                
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + folderName);
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                
                if (uri == null) {
                    values.remove(MediaStore.Images.Media.IS_PENDING);
                    uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        throw new RuntimeException("Failed to create MediaStore entry. Check storage permissions.");
                    }
                }
                
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    throw new RuntimeException("Cannot open output stream for URI: " + uri);
                }
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                int totalBytes = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                if (totalBytes == 0) {
                    outputStream.close();
                    getContentResolver().delete(uri, null, null);
                    throw new RuntimeException("Downloaded file is empty");
                }
                
                outputStream.flush();
                outputStream.close();
                inputStream.close();
                response.close();
                
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                int updated = getContentResolver().update(uri, values, null, null);
                if (updated == 0) {
                    Logger.w(TAG, "Failed to update IS_PENDING flag for " + uri);
                }
                
                Logger.d(TAG, "Image saved to MediaStore: " + folderName + "/" + fileName + " (" + totalBytes + " bytes)");
                
                if (onSuccess != null) {
                    runOnUiThread(onSuccess);
                }
                
            } catch (SecurityException e) {
                Logger.e(TAG, "SecurityException while saving image: " + e.getMessage(), e);
                if (onError != null) {
                    runOnUiThread(() -> {
                        Toast.makeText(PhotoViewerActivity.this,
                            getString(R.string.download_permission_denied), Toast.LENGTH_LONG).show();
                        onError.run();
                    });
                }
            } catch (Exception e) {
                Logger.e(TAG, "Download failed: " + e.getMessage(), e);
                if (onError != null) {
                    runOnUiThread(onError);
                }
            }
        }).start();
    }

    private void downloadWithManualFolder(String url, String folderName, Runnable onSuccess, Runnable onError) {
        new Thread(() -> {
            OkHttpClient client = createOkHttpClient();
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP error: " + response.code());
                }
                
                InputStream inputStream = response.body().byteStream();
                
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File fluxFolder = new File(picturesDir, folderName);
                
                if (!fluxFolder.exists()) {
                    if (!fluxFolder.mkdirs()) {
                        throw new RuntimeException("Failed to create folder: " + fluxFolder.getAbsolutePath());
                    }
                }
                
                String fileName = "IMG_" + System.currentTimeMillis() + "_" +
                                 (int)(Math.random() * 1000) + ".jpg";
                File outputFile = new File(fluxFolder, fileName);
                
                FileOutputStream outputStream = new FileOutputStream(outputFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                int totalBytes = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                if (totalBytes == 0) {
                    outputStream.close();
                    outputFile.delete();
                    throw new RuntimeException("Downloaded file is empty");
                }
                
                outputStream.flush();
                outputStream.close();
                inputStream.close();
                response.close();
                
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(outputFile));
                sendBroadcast(mediaScanIntent);
                
                Logger.d(TAG, "Image saved to: " + outputFile.getAbsolutePath() + " (" + totalBytes + " bytes)");
                
                if (onSuccess != null) {
                    runOnUiThread(onSuccess);
                }
                
            } catch (SecurityException e) {
                Logger.e(TAG, "SecurityException while saving image: " + e.getMessage(), e);
                if (onError != null) {
                    runOnUiThread(() -> {
                        Toast.makeText(PhotoViewerActivity.this,
                            getString(R.string.download_permission_denied), Toast.LENGTH_LONG).show();
                        onError.run();
                    });
                }
            } catch (Exception e) {
                Logger.e(TAG, "Download failed: " + e.getMessage(), e);
                if (onError != null) {
                    runOnUiThread(onError);
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelUIHide();
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }
        Logger.d(TAG, "PhotoViewerActivity destroyed");
    }

    private static class PhotoPagerAdapter extends FragmentStateAdapter {
        private final List<String> imageUrls;
        
        public PhotoPagerAdapter(@NonNull AppCompatActivity activity, List<String> imageUrls) {
            super(activity);
            this.imageUrls = imageUrls;
        }
        
        @NonNull
        @Override
        public PhotoViewerPageFragment createFragment(int position) {
            return PhotoViewerPageFragment.newInstance(imageUrls.get(position));
        }
        
        @Override
        public int getItemCount() {
            return imageUrls != null ? imageUrls.size() : 0;
        }
    }
}