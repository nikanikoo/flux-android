package org.nikanikoo.flux.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ThemeTransitionHelper {
    private static Bitmap sScreenshot;
    private static int sClickX;
    private static int sClickY;
    private static boolean sIsTransitioning = false;

    public static void setTransitionData(Bitmap screenshot, int x, int y) {
        if (sScreenshot != null && !sScreenshot.isRecycled()) {
            sScreenshot.recycle();
        }
        sScreenshot = screenshot;
        sClickX = x;
        sClickY = y;
        sIsTransitioning = true;
    }

    public static boolean isTransitioning() {
        return sIsTransitioning;
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
    }

    public static Bitmap takeScreenshot(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(decorView.getWidth(), decorView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
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
                anim.setDuration(500);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        decor.removeView(imageView);
                        clear();
                    }
                });
                anim.start();
            } catch (Exception e) {
                root.setVisibility(View.VISIBLE);
                decor.removeView(imageView);
                clear();
            }
        });
    }
}
