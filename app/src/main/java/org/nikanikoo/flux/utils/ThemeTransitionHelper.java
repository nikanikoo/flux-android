package org.nikanikoo.flux.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ThemeTransitionHelper {
    private static Bitmap sScreenshot;
    private static int sClickX;
    private static int sClickY;
    private static boolean sIsTransitioning = false;
    private static boolean sDrawerOpen = false;

    public static void setTransitionData(Bitmap screenshot, int x, int y) {
        setTransitionData(screenshot, x, y, false);
    }

    public static void setTransitionData(Bitmap screenshot, int x, int y, boolean drawerOpen) {
        if (sScreenshot != null && !sScreenshot.isRecycled()) {
            sScreenshot.recycle();
        }
        sScreenshot = screenshot;
        sClickX = x;
        sClickY = y;
        sIsTransitioning = true;
        sDrawerOpen = drawerOpen;
    }

    public static boolean isTransitioning() {
        return sIsTransitioning;
    }

    public static boolean wasDrawerOpen() {
        return sDrawerOpen;
    }

    public static Bitmap getScreenshot() {
        return sScreenshot;
    }

    public static void clear() {
        if (sScreenshot != null && !sScreenshot.isRecycled()) {
            sScreenshot.recycle();
        }
        sScreenshot = null;
        sIsTransitioning = false;
        sDrawerOpen = false;
    }

    public static void captureAndSwitchTheme(Activity activity, int x, int y, boolean drawerOpen, Runnable applyThemeAction) {
        if (activity == null || activity.isFinishing()) {
            if (applyThemeAction != null) {
                applyThemeAction.run();
            }
            return;
        }

        View decorView = activity.getWindow().getDecorView();
        int width = decorView.getWidth();
        int height = decorView.getHeight();

        if (width <= 0 || height <= 0) {
            performFallbackCapture(activity, x, y, drawerOpen, applyThemeAction);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                PixelCopy.request(activity.getWindow(), bitmap, copyResult -> {
                    if (copyResult == PixelCopy.SUCCESS) {
                        setTransitionData(bitmap, x, y, drawerOpen);
                        if (applyThemeAction != null) {
                            applyThemeAction.run();
                        }
                        activity.recreate();
                        activity.overridePendingTransition(0, 0);
                    } else {
                        bitmap.recycle();
                        performFallbackCapture(activity, x, y, drawerOpen, applyThemeAction);
                    }
                }, new Handler(Looper.getMainLooper()));
                return;
            } catch (Exception e) {
                Logger.e("ThemeTransition", "PixelCopy request failed, falling back to software capture", e);
            }
        }

        performFallbackCapture(activity, x, y, drawerOpen, applyThemeAction);
    }

    private static void performFallbackCapture(Activity activity, int x, int y, boolean drawerOpen, Runnable applyThemeAction) {
        Bitmap screenshot = takeScreenshot(activity);
        setTransitionData(screenshot, x, y, drawerOpen);
        if (applyThemeAction != null) {
            applyThemeAction.run();
        }
        activity.recreate();
        activity.overridePendingTransition(0, 0);
    }

    public static Bitmap takeScreenshot(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        int width = decorView.getWidth();
        int height = decorView.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (decorView.getBackground() != null) {
            decorView.getBackground().draw(canvas);
        } else {
            canvas.drawColor(0xFF000000);
        }
        decorView.draw(canvas);
        return bitmap;
    }

    public static void animateThemeChange(Activity activity) {
        if (!sIsTransitioning || sScreenshot == null || sScreenshot.isRecycled()) {
            clear();
            return;
        }

        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor.getChildCount() == 0) {
            clear();
            return;
        }

        final View root = decor.getChildAt(0);

        final ImageView imageView = new ImageView(activity);
        imageView.setImageBitmap(sScreenshot);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        decor.addView(imageView, 0);
        root.setVisibility(View.INVISIBLE);

        root.post(() -> {
            root.setVisibility(View.VISIBLE);
            
            int cx = sClickX;
            int cy = sClickY;

            int w = root.getWidth();
            int h = root.getHeight();

            if (cx == 0 && cy == 0) {
                cx = w / 2;
                cy = h / 2;
            }

            float maxRadius = (float) Math.hypot(
                Math.max(cx, w - cx),
                Math.max(cy, h - cy)
            );

            try {
                Animator anim = ViewAnimationUtils.createCircularReveal(root, cx, cy, 0f, maxRadius);
                anim.setDuration(450);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        decor.removeView(imageView);
                        clear();
                    }
                });
                anim.start();
            } catch (Exception e) {
                Logger.e("ThemeTransition", "Error during reveal animation", e);
                decor.removeView(imageView);
                clear();
            }
        });
    }
}
