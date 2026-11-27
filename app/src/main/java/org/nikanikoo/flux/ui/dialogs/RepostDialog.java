package org.nikanikoo.flux.ui.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.GroupsManager;
import org.nikanikoo.flux.data.managers.MessagesManager;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.data.models.Group;
import org.nikanikoo.flux.data.models.Post;

import java.util.ArrayList;
import java.util.List;

public class RepostDialog {

    private interface CommentAction {
        void onSend(String comment);
    }

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
            chooseDialog.dismiss();
            showGroupPicker(context, post, listener);
        });

        btnDm.setOnClickListener(v -> {
            chooseDialog.dismiss();
            showConversationPicker(context, post);
        });

        chooseDialog.setContentView(chooseView);
        chooseDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        chooseDialog.show();
    }

    private static void showCommentDialog(Context context, Post post, OnRepostListener listener) {
        showCommentDialog(context, comment -> performRepost(context, post, comment, 0, listener));
    }

    private static void showCommentDialog(Context context, CommentAction action) {
        BottomSheetDialog commentDialog = new BottomSheetDialog(context);
        View commentView = LayoutInflater.from(context).inflate(R.layout.dialog_repost, null);

        TextInputEditText editComment = commentView.findViewById(R.id.edit_repost_comment);
        MaterialButton btnSend = commentView.findViewById(R.id.btn_send_repost);
        MaterialButton btnCancel = commentView.findViewById(R.id.btn_cancel_repost);

        btnCancel.setOnClickListener(v -> commentDialog.dismiss());

        btnSend.setOnClickListener(v -> {
            String comment = editComment.getText() != null ? editComment.getText().toString().trim() : "";
            commentDialog.dismiss();
            action.onSend(comment);
        });

        commentDialog.setContentView(commentView);
        commentDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        commentDialog.show();
    }

    /**
     * Показывает список групп, в которых пользователь может публиковать репосты,
     * а затем открывает поле для комментария к выбранному репосту.
     * @param context контекст для запросов и показа диалогов
     * @param post исходный пост
     * @param listener вызывается после успешной публикации репоста
     */
    private static void showGroupPicker(Context context, Post post, OnRepostListener listener) {
        GroupsManager.getInstance(context).getAdminGroups(new GroupsManager.GroupsCallback() {
            @Override
            public void onSuccess(List<Group> groups) {
                ArrayList<Group> writableGroups = new ArrayList<>();
                ArrayList<String> names = new ArrayList<>();
                for (Group group : groups) {
                    if (group.isAdmin()) {
                        writableGroups.add(group);
                        names.add(group.getName());
                    }
                }
                if (writableGroups.isEmpty()) {
                    Toast.makeText(context, R.string.repost_no_groups, Toast.LENGTH_SHORT).show();
                    return;
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.repost_choose_group)
                        .setItems(names.toArray(new String[0]), (dialog, which) -> {
                            Group selected = writableGroups.get(which);
                            showCommentDialog(context, comment ->
                                    performRepost(context, post, comment, selected.getId(), listener));
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(context, context.getString(R.string.repost_error) + ": " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Показывает список диалогов для выбора получателя репоста, а затем предлагает
     * добавить комментарий к сообщению.
     * @param context контекст для загрузки диалогов и показа окон выбора
     * @param post пост
     */
    private static void showConversationPicker(Context context, Post post) {
        MessagesManager.getInstance(context).getConversations(100, 0,
                new MessagesManager.ConversationsCallback() {
                    @Override
                    public void onSuccess(List<Conversation> conversations) {
                        ArrayList<Conversation> targets = new ArrayList<>(conversations);
                        if (targets.isEmpty()) {
                            Toast.makeText(context, R.string.repost_no_conversations, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String[] names = new String[targets.size()];
                        for (int i = 0; i < targets.size(); i++) names[i] = targets.get(i).getTitle();
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.repost_choose_conversation)
                                .setItems(names, (dialog, which) -> {
                                    Conversation selected = targets.get(which);
                                    showCommentDialog(context, comment ->
                                            sendPostToConversation(context, post, selected.getPeerId(), comment));
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(context, context.getString(R.string.repost_error) + ": " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Отправляет пост выбранному получателю как вложение сообщения.
     * @param context контекст для доступа к API и показа результата
     * @param post пост, который нужно прикрепить
     * @param peerId ID пользователя или чата-получателя
     * @param comment текст сообщения, отправляемого вместе с постом
     */
    private static void sendPostToConversation(Context context, Post post, int peerId, String comment) {
        String attachment = "wall" + post.getOwnerId() + "_" + post.getPostId();
        MessagesManager.getInstance(context).sendMessage(peerId, comment, attachment,
                new MessagesManager.SendMessageCallback() {
                    @Override
                    public void onSuccess(int messageId) {
                        Toast.makeText(context, R.string.repost_success, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(context, context.getString(R.string.repost_error) + ": " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Создаёт репост на стене пользователя или выбранной группы.
     * Нулевой groupId означает личную стену; положительный ID — стену группы.
     * @param context контекст для доступа к API и показа результата
     * @param post исходный пост
     * @param comment комментарий к репосту
     * @param groupId ID группы-получателя или 0 для личной стены
     * @param listener вызывается после успешной публикации
     */
    private static void performRepost(Context context, Post post, String comment, int groupId,
                                      OnRepostListener listener) {
        PostsManager postsManager = PostsManager.getInstance(context);
        String object = "wall" + post.getOwnerId() + "_" + post.getPostId();
        
        postsManager.repostPost(object, comment, groupId, false, false, new PostsManager.RepostCallback() {
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
