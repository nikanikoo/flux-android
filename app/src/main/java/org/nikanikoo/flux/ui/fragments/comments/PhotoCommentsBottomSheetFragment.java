package org.nikanikoo.flux.ui.fragments.comments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.LikesManager;
import org.nikanikoo.flux.data.managers.PhotosManager;
import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.adapters.comments.CommentsAdapter;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class PhotoCommentsBottomSheetFragment extends BottomSheetDialogFragment
        implements CommentsAdapter.OnCommentClickListener {

    private static final String TAG = "PhotoCommentsDialog";
    private static final String ARG_OWNER_ID = "owner_id";
    private static final String ARG_PHOTO_ID = "photo_id";

    private int ownerId;
    private int photoId;

    private RecyclerView recyclerComments;
    private ProgressBar progressBar;
    private View emptyContainer;
    private EditText editComment;
    private ImageView btnSendComment;
    private ImageView btnClose;

    private CommentsAdapter commentsAdapter;
    private final List<Comment> comments = new ArrayList<>();
    private PhotosManager photosManager;

    public static PhotoCommentsBottomSheetFragment newInstance(int ownerId, int photoId) {
        PhotoCommentsBottomSheetFragment fragment = new PhotoCommentsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_OWNER_ID, ownerId);
        args.putInt(ARG_PHOTO_ID, photoId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ownerId = getArguments().getInt(ARG_OWNER_ID, 0);
            photoId = getArguments().getInt(ARG_PHOTO_ID, 0);
        }
        photosManager = PhotosManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_photo_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupInput();
        loadComments();
    }

    private void initViews(View view) {
        recyclerComments = view.findViewById(R.id.recycler_comments);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyContainer = view.findViewById(R.id.empty_container);
        editComment = view.findViewById(R.id.edit_comment);
        btnSendComment = view.findViewById(R.id.btn_send_comment);
        btnClose = view.findViewById(R.id.btn_close);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    private void setupRecyclerView() {
        recyclerComments.setLayoutManager(new LinearLayoutManager(getContext()));
        commentsAdapter = new CommentsAdapter(getContext(), comments);
        commentsAdapter.setOnCommentClickListener(this);
        recyclerComments.setAdapter(commentsAdapter);
    }

    private void setupInput() {
        btnSendComment.setOnClickListener(v -> {
            String text = editComment.getText().toString().trim();
            if (text.isEmpty()) {
                return;
            }
            sendComment(text);
        });
    }

    private void loadComments() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (emptyContainer != null) emptyContainer.setVisibility(View.GONE);

        photosManager.getPhotoComments(ownerId, photoId, 0, 100, new PhotosManager.PhotoCommentsCallback() {
            @Override
            public void onSuccess(List<Comment> loaded, int totalCount) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    comments.clear();
                    comments.addAll(loaded);
                    commentsAdapter.notifyDataSetChanged();

                    if (emptyContainer != null) {
                        emptyContainer.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Logger.e(TAG, "Error loading photo comments: " + error);
                    Toast.makeText(requireContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    if (emptyContainer != null) {
                        emptyContainer.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    private void sendComment(String message) {
        btnSendComment.setEnabled(false);

        photosManager.createPhotoComment(ownerId, photoId, message, new PhotosManager.CreateCommentCallback() {
            @Override
            public void onSuccess(int commentId) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    editComment.setText("");
                    Toast.makeText(requireContext(), R.string.comments_added, Toast.LENGTH_SHORT).show();
                    loadComments();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    Toast.makeText(requireContext(), getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onAuthorClick(int authorId, String authorName, boolean isGroup) {
        dismiss();
        if (getActivity() != null) {
            getActivity().finish();
        }
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
        LikesManager likesManager = LikesManager.getInstance(requireContext());
        likesManager.toggleLike("photo_comment", ownerId, comment.getId(), comment.isLiked(), new LikesManager.LikeCallback() {
            @Override
            public void onSuccess(int likesCount) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    comment.setLikesCount(likesCount);
                    comment.setLiked(!comment.isLiked());
                    int pos = comments.indexOf(comment);
                    if (pos != -1) {
                        commentsAdapter.notifyItemChanged(pos, "LIKE_UPDATE");
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onReplyClick(Comment comment) {
        editComment.setText(comment.getAuthorName() + ", ");
        editComment.setSelection(editComment.getText().length());
        editComment.requestFocus();
    }

    @Override
    public void onImageClick(String imageUrl) {}
}
