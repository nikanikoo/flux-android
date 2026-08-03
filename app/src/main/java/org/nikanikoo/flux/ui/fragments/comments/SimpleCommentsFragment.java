package org.nikanikoo.flux.ui.fragments.comments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.ui.adapters.comments.CommentsAdapter;
import org.nikanikoo.flux.data.managers.CommentsManager;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SimpleCommentsFragment extends Fragment implements CommentsAdapter.OnCommentClickListener {
    
    private Post post;
    private RecyclerView recyclerComments;
    private CommentsAdapter commentsAdapter;
    private CommentsManager commentsManager;
    private List<Comment> comments;
    private EditText editComment;
    private ImageView btnSendComment;
    
    private View editCommentHeader;
    private ImageView btnCancelEdit;
    private Comment editingComment;
    
    public static SimpleCommentsFragment newInstance(Post post) {
        SimpleCommentsFragment fragment = new SimpleCommentsFragment();
        Bundle args = new Bundle();
        args.putSerializable("post", post);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            post = (Post) getArguments().getSerializable("post");
        }
        
        commentsManager = CommentsManager.getInstance(getContext());
        comments = new ArrayList<>();
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_simple_comments, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupRecyclerView();
        setupCommentInput();
        loadComments();
        checkAdminStatus();
    }
    
    private void checkAdminStatus() {
        if (post != null && post.getOwnerId() < 0) {
            int groupId = -post.getOwnerId();
            org.nikanikoo.flux.data.managers.GroupsManager.getInstance(requireContext())
                    .getGroupById(groupId, new org.nikanikoo.flux.data.managers.GroupsManager.GroupCallback() {
                @Override
                public void onSuccess(org.nikanikoo.flux.data.models.Group group) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (commentsAdapter != null) {
                                commentsAdapter.setIsAdmin(group.isAdmin());
                            }
                        });
                    }
                }
                @Override
                public void onError(String error) {}
            });
        }
    }
    
    private void initViews(View view) {
        recyclerComments = view.findViewById(R.id.recycler_comments);
        editComment = view.findViewById(R.id.edit_comment);
        btnSendComment = view.findViewById(R.id.btn_send_comment);
        editCommentHeader = view.findViewById(R.id.edit_comment_header);
        btnCancelEdit = view.findViewById(R.id.btn_cancel_edit);
    }
    
    private void setupRecyclerView() {
        recyclerComments.setLayoutManager(new LinearLayoutManager(getContext()));
        commentsAdapter = new CommentsAdapter(getContext(), comments, 
                post != null ? post.getOwnerId() : 0, 
                post != null ? post.getAuthorId() : 0);
        commentsAdapter.setOnCommentClickListener(this);
        recyclerComments.setAdapter(commentsAdapter);
        
        // Всегда проверяем админство для групп
        if (post != null && post.getOwnerId() < 0) {
            checkAdminStatus();
        }
    }
    
    private void setupCommentInput() {
        btnSendComment.setOnClickListener(v -> {
            String commentText = editComment.getText().toString().trim();
            if (commentText.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.comments_add_error), Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (editingComment != null) {
                updateComment(editingComment, commentText);
            } else {
                sendComment(commentText);
            }
        });
        
        btnCancelEdit.setOnClickListener(v -> cancelEditing());
    }
    
    private void loadComments() {
        if (post == null) return;
        
        commentsManager.loadComments(post.getOwnerId(), post.getPostId(), new CommentsManager.CommentsCallback() {
            @Override
            public void onSuccess(List<Comment> loadedComments) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comments.clear();
                        comments.addAll(loadedComments);
                        commentsAdapter.notifyDataSetChanged();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getString(R.string.comments_loading_error) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void sendComment(String text) {
        if (post == null) return;
        
        commentsManager.createComment(post.getOwnerId(), post.getPostId(), text, new CommentsManager.CreateCommentCallback() {
            @Override
            public void onSuccess(Comment comment) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comments.add(comment);
                        commentsAdapter.notifyItemInserted(comments.size() - 1);
                        editComment.setText("");
                        recyclerComments.scrollToPosition(comments.size() - 1);
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getString(R.string.comments_send_error) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    @Override
    public void onAuthorClick(int authorId, String authorName, boolean isGroup) {
        if (getActivity() != null) {
            if (isGroup) {
                int groupId = authorId < 0 ? -authorId : authorId;
                GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(groupId, authorName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, groupProfileFragment)
                        .addToBackStack("group_" + groupId)
                        .commit();
            } else {
                ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(authorId, authorName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, profileFragment)
                        .addToBackStack("profile_" + authorId)
                        .commit();
            }
        }
    }
    
    @Override
    public void onLikeClick(Comment comment) {
        final boolean originalLikedState = comment.isLiked();
        final int originalLikeCount = comment.getLikesCount();
        
        final boolean newLikedState = !originalLikedState;
        final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;
        
        comment.setLiked(newLikedState);
        comment.setLikesCount(newLikeCount);
        
        int index = comments.indexOf(comment);
        if (index >= 0) {
            commentsAdapter.notifyItemChanged(index, "LIKE_UPDATE");
        }
        
        commentsManager.toggleCommentLikeWithOriginalState(comment, post.getOwnerId(), post.getPostId(), 
                originalLikedState, new CommentsManager.LikeCommentCallback() {
            @Override
            public void onSuccess(int newLikesCount, boolean isLiked) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comment.setLikesCount(newLikesCount);
                        comment.setLiked(isLiked);
                        int successIndex = comments.indexOf(comment);
                        if (successIndex >= 0) {
                            commentsAdapter.notifyItemChanged(successIndex, "LIKE_UPDATE");
                        }
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comment.setLiked(originalLikedState);
                        comment.setLikesCount(originalLikeCount);
                        int errorIndex = comments.indexOf(comment);
                        if (errorIndex >= 0) {
                            commentsAdapter.notifyItemChanged(errorIndex, "LIKE_UPDATE");
                        }
                        Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    @Override
    public void onReplyClick(Comment comment) {
        String replyText = "[id" + comment.getFromId() + "|" + comment.getAuthorName() + "] ";
        editComment.setText(replyText);
        editComment.setSelection(replyText.length());
        editComment.requestFocus();
    }
    
    @Override
    public void onImageClick(String imageUrl) {
        // Можно добавить открытие изображения
    }

    @Override
    public void onDeleteClick(Comment comment) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.comments_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteComment(comment);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onEditClick(Comment comment) {
        editingComment = comment;
        editComment.setText(comment.getText());
        if (comment.getText() != null) {
            editComment.setSelection(comment.getText().length());
        }
        editCommentHeader.setVisibility(View.VISIBLE);
        editComment.requestFocus();
    }

    private void cancelEditing() {
        editingComment = null;
        editComment.setText("");
        editCommentHeader.setVisibility(View.GONE);
    }

    private void deleteComment(Comment comment) {
        commentsManager.deleteComment(comment.getId(), new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        int index = comments.indexOf(comment);
                        if (index >= 0) {
                            comments.remove(index);
                            commentsAdapter.notifyItemRemoved(index);
                            Toast.makeText(getContext(), R.string.comments_deleted, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void updateComment(Comment comment, String newText) {
        commentsManager.editComment(comment.getId(), newText, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comment.setText(newText);
                        int index = comments.indexOf(comment);
                        if (index >= 0) {
                            commentsAdapter.notifyItemChanged(index);
                        }
                        cancelEditing();
                        Toast.makeText(getContext(), R.string.comments_edited, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}
