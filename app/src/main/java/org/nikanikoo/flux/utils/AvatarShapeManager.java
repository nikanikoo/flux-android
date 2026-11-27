package org.nikanikoo.flux.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.nikanikoo.flux.ui.fragments.menu.MenuDashboardFragment;
import org.nikanikoo.flux.ui.fragments.settings.AppearanceSettingsFragment;

public final class AvatarShapeManager {
    private AvatarShapeManager() { }

    public static void register(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) {
                View root = activity.getWindow().getDecorView();
                if (root.getTag(org.nikanikoo.flux.R.id.avatar_layout_listener) == null) {
                    long[] lastScan = {0L};
                    root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - lastScan[0] < 250L) return;
                        lastScan[0] = now;
                        applyToTree(activity, root);
                    });
                    root.setTag(org.nikanikoo.flux.R.id.avatar_layout_listener, true);
                }
                root.post(() -> applyToTree(activity, root));
            }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    private static void applyToTree(Activity activity, View view) {
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                String name = activity.getResources().getResourceEntryName(id).toLowerCase(java.util.Locale.ROOT);
                if (name.contains("avatar") && view instanceof ImageView) {
                    boolean round = activity.getSharedPreferences(MenuDashboardFragment.PREFS_NAME, Activity.MODE_PRIVATE)
                            .getBoolean(AppearanceSettingsFragment.KEY_ROUND_AVATARS, true);
                    applyShape((ImageView) view, round);
                }
            } catch (Exception ignored) {
                // Dynamically generated view IDs do not have resource names.
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyToTree(activity, group.getChildAt(i));
        }
    }

    private static void applyShape(ImageView avatar, boolean round) {
        Boolean previous = (Boolean) avatar.getTag(org.nikanikoo.flux.R.id.avatar_shape_state);
        if (previous != null && previous == round) return;

        if (avatar instanceof ShapeableImageView) {
            ShapeAppearanceModel model = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(new RelativeCornerSize(round ? 0.5f : 0f))
                    .build();
            ((ShapeableImageView) avatar).setShapeAppearanceModel(model);
        } else {
            avatar.setOutlineProvider(round ? ViewOutlineProvider.BACKGROUND : ViewOutlineProvider.BOUNDS);
            if (round) {
                avatar.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
            }
            avatar.setClipToOutline(true);
        }
        avatar.setTag(org.nikanikoo.flux.R.id.avatar_shape_state, round);
    }
}
