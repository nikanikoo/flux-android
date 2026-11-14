package org.nikanikoo.flux.ui.fragments.comments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.LikesManager;
import org.nikanikoo.flux.data.managers.VideoManager;
import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.ui.adapters.comments.CommentsAdapter;
import org.nikanikoo.flux.ui.activities.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class VideoCommentsBottomSheetFragment extends BottomSheetDialogFragment
        implements CommentsAdapter.OnCommentClickListener {
    private static final String ARG_OWNER_ID = "owner_id";
    private static final String ARG_VIDEO_ID = "video_id";
    private static final String ARG_CAN_COMMENT = "can_comment";
    private int ownerId;
    private int videoId;
    private boolean canComment = true;
    private VideoManager videoManager;
    private RecyclerView recycler;
    private ProgressBar progress;
    private View empty;
    private EditText input;
    private ImageView send;
    private CommentsAdapter adapter;
    private final List<Comment> comments = new ArrayList<>();
    private int replyToCommentId;

    public static VideoCommentsBottomSheetFragment newInstance(int ownerId, int videoId) {
        return newInstance(ownerId, videoId, true);
    }

    public static VideoCommentsBottomSheetFragment newInstance(int ownerId, int videoId, boolean canComment) {
        VideoCommentsBottomSheetFragment fragment = new VideoCommentsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_OWNER_ID, ownerId);
        args.putInt(ARG_VIDEO_ID, videoId);
        args.putBoolean(ARG_CAN_COMMENT, canComment);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ownerId = getArguments().getInt(ARG_OWNER_ID);
            videoId = getArguments().getInt(ARG_VIDEO_ID);
            canComment = getArguments().getBoolean(ARG_CAN_COMMENT, true);
        }
        videoManager = VideoManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_video_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.recycler_comments);
        progress = view.findViewById(R.id.progress_bar);
        empty = view.findViewById(R.id.empty_container);
        input = view.findViewById(R.id.edit_comment);
        send = view.findViewById(R.id.btn_send_comment);
        input.setEnabled(canComment);
        send.setEnabled(canComment);
        if (!canComment) input.setHint(R.string.video_comments_read_only);
        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CommentsAdapter(requireContext(), comments, ownerId);
        adapter.setCanEdit(false);
        adapter.setOnCommentClickListener(this);
        recycler.setAdapter(adapter);
        send.setOnClickListener(v -> sendComment());
        loadComments();
    }

    private void loadComments() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        videoManager.getComments(ownerId, videoId, 0, 100, new VideoManager.VideoCommentsCallback() {
            @Override
            public void onSuccess(List<Comment> loaded, int totalCount) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    comments.clear();
                    comments.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    empty.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    empty.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void sendComment() {
        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;
        send.setEnabled(false);
        videoManager.addComment(ownerId, videoId, message, replyToCommentId,
                new VideoManager.CommentActionCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            send.setEnabled(true);
                            input.setText("");
                            replyToCommentId = 0;
                            loadComments();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            send.setEnabled(true);
                            Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    @Override
    public void onAuthorClick(int authorId, String authorName, boolean isGroup) {
        dismiss();
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.putExtra("open_profile", true);
        intent.putExtra("user_id", authorId);
        intent.putExtra("user_name", authorName);
        intent.putExtra("is_group", isGroup);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onLikeClick(Comment comment) {
        LikesManager.getInstance(requireContext()).toggleLike("comment", ownerId, comment.getId(),
                comment.isLiked(), new LikesManager.LikeCallback() {
                    @Override
                    public void onSuccess(int likesCount) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            comment.setLiked(!comment.isLiked());
                            comment.setLikesCount(likesCount);
                            int position = comments.indexOf(comment);
                            if (position >= 0) adapter.notifyItemChanged(position, "LIKE_UPDATE");
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onReplyClick(Comment comment) {
        replyToCommentId = comment.getId();
        input.setText(comment.getAuthorName() + ", ");
        input.setSelection(input.length());
        input.requestFocus();
    }

    @Override
    public void onImageClick(String imageUrl) { }

    @Override
    public void onDeleteClick(Comment comment) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.comments_delete_confirm)
                .setMessage(R.string.comments_delete_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> videoManager.deleteComment(
                        ownerId, videoId, comment.getId(), new VideoManager.CommentActionCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    comments.remove(comment);
                                    adapter.notifyDataSetChanged();
                                    empty.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onEditClick(Comment comment) { }
}
