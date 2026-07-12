package org.nikanikoo.flux.ui.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.data.models.Post;

public class RepostDialog {

    public interface OnRepostListener {
        void onRepost(Post post, String comment);
    }

    public static void show(Context context, Post post, OnRepostListener listener) {
        BottomSheetDialog chooseDialog = new BottomSheetDialog(context);
        View chooseView = LayoutInflater.from(context).inflate(R.layout.dialog_repost_choose, null);

        View btnWall = chooseView.findViewById(R.id.btn_share_wall);
        View btnGroup = chooseView.findViewById(R.id.btn_share_group);
        View btnDm = chooseView.findViewById(R.id.btn_share_dm);

        btnWall.setOnClickListener(v -> {
            chooseDialog.dismiss();
            showCommentDialog(context, post, listener);
        });

        btnGroup.setOnClickListener(v -> {
            Toast.makeText(context, context.getString(R.string.feature_not_available), Toast.LENGTH_SHORT).show();
        });

        btnDm.setOnClickListener(v -> {
            Toast.makeText(context, context.getString(R.string.feature_not_available), Toast.LENGTH_SHORT).show();
        });

        chooseDialog.setContentView(chooseView);
        chooseDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        chooseDialog.show();
    }

    private static void showCommentDialog(Context context, Post post, OnRepostListener listener) {
        BottomSheetDialog commentDialog = new BottomSheetDialog(context);
        View commentView = LayoutInflater.from(context).inflate(R.layout.dialog_repost, null);

        TextInputEditText editComment = commentView.findViewById(R.id.edit_repost_comment);
        MaterialButton btnSend = commentView.findViewById(R.id.btn_send_repost);
        MaterialButton btnCancel = commentView.findViewById(R.id.btn_cancel_repost);

        btnCancel.setOnClickListener(v -> commentDialog.dismiss());

        btnSend.setOnClickListener(v -> {
            String comment = editComment.getText() != null ? editComment.getText().toString().trim() : "";
            performRepost(context, post, comment, listener);
            commentDialog.dismiss();
        });

        commentDialog.setContentView(commentView);
        commentDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        commentDialog.show();
    }

    private static void performRepost(Context context, Post post, String comment, OnRepostListener listener) {
        PostsManager postsManager = PostsManager.getInstance(context);
        String object = "wall" + post.getOwnerId() + "_" + post.getPostId();
        
        postsManager.repostPost(object, comment, new PostsManager.RepostCallback() {
            @Override
            public void onSuccess(int postId, int likeCount) {
                if (listener != null) {
                    listener.onRepost(post, comment);
                }
                Toast.makeText(context, context.getString(R.string.repost_success), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(context, context.getString(R.string.repost_error) + ": " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
