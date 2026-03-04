package org.nikanikoo.flux.ui.fragments.comments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.ui.adapters.comments.CommentsAdapter;
import org.nikanikoo.flux.data.managers.CommentsManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.activities.PhotoViewerActivity;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsFragment extends Fragment implements CommentsAdapter.OnCommentClickListener {
    
    private static final int PICK_IMAGE_REQUEST = 1;
    
    private Post originalPost;
    private RecyclerView recyclerComments;
    private CommentsAdapter commentsAdapter;
    private CommentsManager commentsManager;
    private PostsManager postsManager;
    private List<Comment> comments;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    
    // Views для оригинального поста
    private ImageView originalPostAvatar;
    private TextView originalPostAuthorName;
    private TextView originalPostTimestamp;
    private TextView originalPostContent;
    private ImageView originalPostImage;
    private View originalPostLikeButton;
    private ImageView originalPostLikeIcon;
    private TextView originalPostLikeCount;
    private TextView originalPostCommentCount;
    
    // Views для ввода комментария
    private EditText editComment;
    private ImageView btnAttachImage;
    private ImageView btnSendComment;
    private Uri selectedImageUri;

    public static CommentsFragment newInstance(Post post) {
        CommentsFragment fragment = new CommentsFragment();
        Bundle args = new Bundle();
        args.putSerializable("post", post);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comments, container, false);
        
        // Получаем пост из аргументов
        if (getArguments() != null) {
            originalPost = (Post) getArguments().getSerializable("post");
        }
        
        if (originalPost == null) {
            Toast.makeText(getContext(), "Ошибка: пост не найден", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
            return view;
        }
        
        commentsManager = CommentsManager.getInstance(requireContext());
        postsManager = PostsManager.getInstance(requireContext());
        comments = new ArrayList<>();
        
        initViews(view);
        setupToolbar();
        setupOriginalPost();
        setupRecyclerView();
        setupSwipeRefresh();
        setupCommentInput();
        loadComments();
        
        return view;
    }

    private void initViews(View view) {
        // SwipeRefresh
        swipeRefresh = view.findViewById(R.id.swipe_refresh_comments);
        
        // Оригинальный пост
        originalPostAvatar = view.findViewById(R.id.original_post_avatar);
        originalPostAuthorName = view.findViewById(R.id.original_post_author_name);
        originalPostTimestamp = view.findViewById(R.id.original_post_timestamp);
        originalPostContent = view.findViewById(R.id.original_post_content);
        originalPostImage = view.findViewById(R.id.original_post_image);
        originalPostLikeButton = view.findViewById(R.id.original_post_like_button);
        originalPostLikeIcon = view.findViewById(R.id.original_post_like_icon);
        originalPostLikeCount = view.findViewById(R.id.original_post_like_count);
        originalPostCommentCount = view.findViewById(R.id.original_post_comment_count);
        
        // Комментарии
        recyclerComments = view.findViewById(R.id.recycler_comments);
        
        // Ввод комментария
        editComment = view.findViewById(R.id.edit_comment);
        btnAttachImage = view.findViewById(R.id.btn_attach_image);
        btnSendComment = view.findViewById(R.id.btn_send_comment);
    }

    private void setupToolbar() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle("Комментарии");
        }
    }

    private void setupOriginalPost() {
        // Если данные поста неполные, загружаем их
        if (originalPost.getAuthorName().equals("Загрузка...") || originalPost.getContent().isEmpty()) {
            loadPostData();
        } else {
            displayOriginalPost();
        }
    }
    
    private void loadPostData() {
        if (originalPost.getPostId() == 0 || originalPost.getOwnerId() == 0) {
            displayOriginalPost(); // Показываем то что есть
            return;
        }
        
        postsManager.getPostById(originalPost.getOwnerId(), originalPost.getPostId(), new PostsManager.PostCallback() {
            @Override
            public void onSuccess(Post post) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Обновляем данные поста
                        originalPost.setAuthorName(post.getAuthorName());
                        originalPost.setContent(post.getContent());
                        originalPost.setLikeCount(post.getLikeCount());
                        originalPost.setCommentCount(post.getCommentCount());
                        originalPost.setAuthorId(post.getAuthorId());
                        originalPost.setAuthorAvatarUrl(post.getAuthorAvatarUrl());
                        originalPost.setLiked(post.isLiked());
                        originalPost.setImageUrls(post.getImageUrls());
                        
                        displayOriginalPost();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        System.out.println("CommentsFragment: Error loading post data: " + error);
                        displayOriginalPost(); // Показываем то что есть
                    });
                }
            }
        });
    }
    
    private void displayOriginalPost() {
        originalPostAuthorName.setText(originalPost.getAuthorName());
        originalPostTimestamp.setText(originalPost.getTimestamp());
        originalPostContent.setText(ValidationUtils.SanitizeText(originalPost.getContent()));
        originalPostLikeCount.setText(String.valueOf(originalPost.getLikeCount()));
        originalPostCommentCount.setText(String.valueOf(originalPost.getCommentCount()));
        
        // Загружаем аватарку автора
        if (originalPost.getAuthorAvatarUrl() != null && !originalPost.getAuthorAvatarUrl().isEmpty()) {
            Picasso.get()
                    .load(originalPost.getAuthorAvatarUrl())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(originalPostAvatar);
        } else {
            originalPostAvatar.setImageResource(R.drawable.camera_200);
        }
        
        // Обработчик клика на аватар и имя автора оригинального поста
        View.OnClickListener authorClickListener = v -> {
            if (originalPost.getAuthorId() != 0) {
                if (originalPost.getAuthorId() > 0) {
                    // Пользователь
                    ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(
                            originalPost.getAuthorId(), 
                            originalPost.getAuthorName()
                    );
                    if (getActivity() != null) {
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, profileFragment)
                                .addToBackStack("profile_" + originalPost.getAuthorId())
                                .commit();
                    }
                } else {
                    // Группа (отрицательный ID)
                    GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(
                            -originalPost.getAuthorId(), 
                            originalPost.getAuthorName()
                    );
                    if (getActivity() != null) {
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, groupProfileFragment)
                                .addToBackStack("group_" + (-originalPost.getAuthorId()))
                                .commit();
                    }
                }
            }
        };
        
        originalPostAvatar.setOnClickListener(authorClickListener);
        originalPostAuthorName.setOnClickListener(authorClickListener);
        
        // Загружаем изображения поста
        List<String> imageUrls = originalPost.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            originalPostImage.setVisibility(View.VISIBLE);
            Picasso.get()
                    .load(imageUrls.get(0))
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(originalPostImage);
            
            // Добавляем обработчик клика на изображение поста
            originalPostImage.setOnClickListener(v -> {
                PhotoViewerActivity.start(getContext(), imageUrls, 0, originalPost, originalPost.getAuthorName());
            });
        } else {
            originalPostImage.setVisibility(View.GONE);
        }
        
        // Обновляем состояние лайка
        updateOriginalPostLikeState();
        
        // Обработчик лайка оригинального поста
        originalPostLikeButton.setOnClickListener(v -> {
            if (originalPost.getPostId() == 0 || originalPost.getOwnerId() == 0) {
                Toast.makeText(getContext(), "Невозможно поставить лайк этому посту", Toast.LENGTH_SHORT).show();
                return;
            }

            // Optimistic UI
            final boolean originalLikedState = originalPost.isLiked();
            final int originalLikeCount = originalPost.getLikeCount();
            
            final boolean newLikedState = !originalLikedState;
            final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;
            
            originalPost.setLiked(newLikedState);
            originalPost.setLikeCount(newLikeCount);
            updateOriginalPostLikeState();

            postsManager.toggleLikeOptimistic(originalPost, originalLikedState, new PostsManager.LikeToggleCallback() {
                @Override
                public void onSuccess(int serverLikesCount, boolean serverIsLiked) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            originalPost.setLikeCount(serverLikesCount);
                            originalPost.setLiked(serverIsLiked);
                            updateOriginalPostLikeState();
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            originalPost.setLiked(originalLikedState);
                            originalPost.setLikeCount(originalLikeCount);
                            updateOriginalPostLikeState();
                            Toast.makeText(getContext(), "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });
    }

    private void updateOriginalPostLikeState() {
        originalPostLikeCount.setText(String.valueOf(originalPost.getLikeCount()));
        
        if (originalPost.isLiked()) {
            originalPostLikeIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.like_active));
            originalPostLikeCount.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.like_active));
        } else {
            originalPostLikeIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.like_inactive));
            originalPostLikeCount.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.like_inactive));
        }
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            System.out.println("CommentsFragment: Refreshing comments...");
            refreshComments();
        });
        
        // Настройка цветов для SwipeRefresh
        swipeRefresh.setColorSchemeResources(
                R.color.primary_color,
                R.color.like_active
        );
    }

    private void setupRecyclerView() {
        recyclerComments.setLayoutManager(new LinearLayoutManager(getContext()));
        commentsAdapter = new CommentsAdapter(getContext(), comments);
        commentsAdapter.setOnCommentClickListener(this);
        recyclerComments.setAdapter(commentsAdapter);
    }

    private void setupCommentInput() {
        btnAttachImage.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Выберите изображение"), PICK_IMAGE_REQUEST);
        });
        
        btnSendComment.setOnClickListener(v -> {
            String commentText = editComment.getText().toString().trim();
            if (commentText.isEmpty() && selectedImageUri == null) {
                Toast.makeText(getContext(), "Введите текст комментария или выберите изображение", Toast.LENGTH_SHORT).show();
                return;
            }
            
            sendComment(commentText);
        });
        
        // Обновляем индикатор выбранного изображения
        updateImageAttachmentIndicator();
    }

    private void updateImageAttachmentIndicator() {
        if (selectedImageUri != null) {
            btnAttachImage.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary_color));
        } else {
            btnAttachImage.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.like_inactive));
        }
    }

    private void loadComments() {
        loadComments(false);
    }

    private void refreshComments() {
        // Сначала обновляем данные оригинального поста
        refreshOriginalPost();
        // Затем загружаем комментарии
        loadComments(true);
    }

    private void loadComments(boolean isRefresh) {
        if (originalPost.getPostId() == 0 || originalPost.getOwnerId() == 0) {
            Toast.makeText(getContext(), "Невозможно загрузить комментарии", Toast.LENGTH_SHORT).show();
            if (isRefresh) {
                swipeRefresh.setRefreshing(false);
            }
            return;
        }
        
        System.out.println("Loading comments for post " + originalPost.getOwnerId() + "_" + originalPost.getPostId() + " (refresh: " + isRefresh + ")");
        
        commentsManager.loadComments(originalPost.getOwnerId(), originalPost.getPostId(), new CommentsManager.CommentsCallback() {
            @Override
            public void onSuccess(List<Comment> loadedComments) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comments.clear();
                        comments.addAll(loadedComments);
                        commentsAdapter.notifyDataSetChanged();
                        
                        if (isRefresh) {
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(getContext(), "Комментарии обновлены", Toast.LENGTH_SHORT).show();
                        }
                        
                        System.out.println("Comments loaded successfully: " + loadedComments.size() + " comments");
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isRefresh) {
                            swipeRefresh.setRefreshing(false);
                        }
                        Toast.makeText(getContext(), "Ошибка загрузки комментариев: " + error, Toast.LENGTH_SHORT).show();
                        System.out.println("Error loading comments: " + error);
                    });
                }
            }
        });
    }

    private void refreshOriginalPost() {
        // Загружаем актуальные данные поста
        if (originalPost.getPostId() == 0 || originalPost.getOwnerId() == 0) {
            return;
        }
        
        System.out.println("Refreshing original post data...");
        
        // Используем PostsManager для загрузки одного поста
        Map<String, String> params = new HashMap<>();
        params.put("posts", originalPost.getOwnerId() + "_" + originalPost.getPostId());
        params.put("extended", "1");
        
        OpenVKApi api = OpenVKApi.getInstance(requireContext());
        api.callMethod("wall.getById", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                try {
                    System.out.println("Original post refresh response: " + response.toString());
                    
                    org.json.JSONObject responseObj = response.getJSONObject("response");
                    org.json.JSONArray items = responseObj.getJSONArray("items");
                    
                    if (items.length() > 0) {
                        org.json.JSONObject postData = items.getJSONObject(0);
                        
                        // Обновляем счетчики лайков и комментариев
                        org.json.JSONObject likesObj = postData.optJSONObject("likes");
                        if (likesObj != null) {
                            int likes = likesObj.optInt("count", 0);
                            boolean userLikes = likesObj.optInt("user_likes", 0) == 1;
                            originalPost.setLikeCount(likes);
                            originalPost.setLiked(userLikes);
                        }
                        
                        org.json.JSONObject commentsObj = postData.optJSONObject("comments");
                        if (commentsObj != null) {
                            int comments = commentsObj.optInt("count", 0);
                            originalPost.setCommentCount(comments);
                        }
                        
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                updateOriginalPostLikeState();
                                originalPostCommentCount.setText(String.valueOf(originalPost.getCommentCount()));
                                System.out.println("Original post data refreshed");
                            });
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error parsing original post refresh: " + e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                System.out.println("Error refreshing original post: " + error);
            }
        });
    }

    private void sendComment(String commentText) {
        // Используем единый метод для создания комментария (с изображением или без)
        commentsManager.createComment(originalPost.getOwnerId(), originalPost.getPostId(),
                commentText, selectedImageUri, new CommentsManager.CreateCommentCallback() {
            @Override
            public void onSuccess(Comment comment) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comments.add(comment);
                        commentsAdapter.notifyItemInserted(comments.size() - 1);
                        editComment.setText("");
                        selectedImageUri = null;
                        updateImageAttachmentIndicator();
                        
                        // Обновляем счетчик комментариев
                        originalPost.setCommentCount(originalPost.getCommentCount() + 1);
                        originalPostCommentCount.setText(String.valueOf(originalPost.getCommentCount()));
                        
                        Toast.makeText(getContext(), "Комментарий добавлен", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            updateImageAttachmentIndicator();
            Toast.makeText(getContext(), "Изображение выбрано", Toast.LENGTH_SHORT).show();
        }
    }

    // Реализация интерфейса OnCommentClickListener
    @Override
    public void onAuthorClick(int authorId, String authorName, boolean isGroup) {
        // Переход в профиль автора комментария (пользователя или группы)
        if (getActivity() != null) {
            if (isGroup) {
                // Группа
                int groupId = authorId < 0 ? -authorId : authorId;
                GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(groupId, authorName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, groupProfileFragment)
                        .addToBackStack("group_" + groupId)
                        .commit();
            } else {
                // Пользователь
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
        System.out.println("Comment like clicked: commentId=" + comment.getId() + 
                          " isLiked=" + comment.isLiked() + " likesCount=" + comment.getLikesCount());
        
        // Сохраняем оригинальное состояние ДО изменения UI
        final boolean originalLikedState = comment.isLiked();
        final int originalLikeCount = comment.getLikesCount();
        
        // Optimistic UI для лайка комментария
        final boolean newLikedState = !originalLikedState;
        final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;
        
        System.out.println("Updating UI optimistically: newLiked=" + newLikedState + " newCount=" + newLikeCount);
        
        comment.setLiked(newLikedState);
        comment.setLikesCount(newLikeCount);
        commentsAdapter.notifyDataSetChanged();

        // Передаем ОРИГИНАЛЬНОЕ состояние в toggleCommentLike
        commentsManager.toggleCommentLikeWithOriginalState(comment, originalPost.getOwnerId(), originalPost.getPostId(), 
                originalLikedState, new CommentsManager.LikeCommentCallback() {
            @Override
            public void onSuccess(int serverLikesCount, boolean serverIsLiked) {
                System.out.println("Comment like success: serverCount=" + serverLikesCount + " serverLiked=" + serverIsLiked);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comment.setLikesCount(serverLikesCount);
                        comment.setLiked(serverIsLiked);
                        commentsAdapter.notifyDataSetChanged();
                    });
                }
            }

            @Override
            public void onError(String error) {
                System.out.println("Comment like error: " + error);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        comment.setLiked(originalLikedState);
                        comment.setLikesCount(originalLikeCount);
                        commentsAdapter.notifyDataSetChanged();
                        Toast.makeText(getContext(), "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    @Override
    public void onReplyClick(Comment comment) {
        System.out.println("Reply clicked for comment: " + comment.getId() + " by " + comment.getAuthorName());
        
        // Формируем текст ответа в формате [id|Имя]
        String replyText = "[id" + comment.getFromId() + "|" + comment.getAuthorName() + "], ";
        
        // Вставляем текст в поле ввода
        editComment.setText(replyText);
        editComment.setSelection(replyText.length()); // Устанавливаем курсор в конец
        
        // Фокусируемся на поле ввода
        editComment.requestFocus();
        
        // Показываем клавиатуру
        if (getActivity() != null) {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editComment, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }
    
    @Override
    public void onImageClick(String imageUrl) {
        System.out.println("Image clicked in comment: " + imageUrl);
        
        List<String> imageUrls = new ArrayList<>();
        imageUrls.add(imageUrl);
        
        // Создаем фиктивный пост для изображения комментария
        Post fakePost = new Post("", "", "", 0, 0);
        fakePost.setImageUrl(imageUrl);
        
        PhotoViewerActivity.start(getContext(), imageUrls, 0, fakePost, "Изображение из комментария");
    }
}