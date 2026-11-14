package org.nikanikoo.flux.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.LikesManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.activities.MainActivity;

import java.util.List;

public final class LikedUsersDialog {
    private LikedUsersDialog() { }

    public static void show(Context context, int ownerId, int postId) {
        FrameLayout content = new FrameLayout(context);
        int padding = dp(context, 16);
        content.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(context);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        content.addView(progressBar, progressParams);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.post_likes_title_loading)
                .setView(content)
                .setPositiveButton(R.string.cancel, null)
                .create();
        dialog.show();

        LikesManager.getInstance(context).getLikedUsers("post", ownerId, postId,
                new LikesManager.LikedUsersCallback() {
                    @Override
                    public void onSuccess(List<UserProfile> users, int totalCount) {
                        if (!dialog.isShowing() || isActivityFinishing(context)) return;
                        dialog.setTitle(context.getString(R.string.post_likes_title, totalCount));
                        replaceContent(content, context);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok);
                        if (users.isEmpty()) {
                            addMessage(content, context.getString(R.string.post_likes_empty));
                            return;
                        }

                        String[] names = new String[users.size()];
                        for (int i = 0; i < users.size(); i++) {
                            UserProfile user = users.get(i);
                            String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " +
                                    (user.getLastName() == null ? "" : user.getLastName())).trim();
                            names[i] = name.isEmpty() ? "ID " + user.getId() : name;
                        }
                        ListView usersList = new ListView(context);
                        usersList.setAdapter(new ArrayAdapter<>(context,
                                android.R.layout.simple_list_item_1, names));
                        usersList.setOnItemClickListener((parent, view, position, id) -> {
                            UserProfile selectedUser = users.get(position);
                            dialog.dismiss();
                            Intent intent = new Intent(context, MainActivity.class);
                            intent.putExtra("open_profile", true);
                            intent.putExtra("user_id", selectedUser.getId());
                            intent.putExtra("user_name", names[position]);
                            intent.putExtra("is_group", false);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            context.startActivity(intent);
                        });
                        int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6f);
                        int listHeight = Math.min(dp(context, 48) * names.length, maxHeight);
                        content.addView(usersList, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, listHeight));
                    }

                    @Override
                    public void onError(String error) {
                        if (!dialog.isShowing() || isActivityFinishing(context)) return;
                        dialog.setTitle(R.string.post_likes_title);
                        replaceContent(content, context);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok);
                        addMessage(content, context.getString(R.string.error_loading) + error);
                    }
                });
    }

    private static void replaceContent(FrameLayout content, Context context) {
        content.removeAllViews();
        content.setMinimumHeight(dp(context, 48));
    }

    private static void addMessage(FrameLayout content, String message) {
        TextView text = new TextView(content.getContext());
        text.setText(message);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(content.getContext(), 16), dp(content.getContext(), 16),
                dp(content.getContext(), 16), dp(content.getContext(), 16));
        content.addView(text, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static boolean isActivityFinishing(Context context) {
        if (!(context instanceof Activity)) return false;
        Activity activity = (Activity) context;
        return activity.isFinishing() || activity.isDestroyed();
    }
}
