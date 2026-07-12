package org.nikanikoo.flux.ui.fragments.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.LikesManager;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.ui.adapters.posts.PostAdapter;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый класс для фрагментов профилей (пользователь и группа).
 * Содержит общую логику для работы с постами, пагинацией и RecyclerView.
 */
public abstract class BaseProfileFragment extends BaseFragment implements PostAdapter.OnPostClickListener {
    
    private static final String TAG = "BaseProfileFragment";
    
    // Views
    protected RecyclerView recyclerPosts;
    protected SwipeRefreshLayout swipeRefresh;
    protected View progressLoading;
    
    // Adapters and Managers
    protected PostAdapter postAdapter;
    protected PostsManager postsManager;
    protected LikesManager likesManager;
    protected org.nikanikoo.flux.data.managers.ProfileManager profileManager;
    
    // Pagination
    protected LinearLayoutManager layoutManager;
    protected EndlessScrollListener scrollListener;
    protected PaginationHelper paginationHelper;
    
    // Data
    protected List<Post> posts = new ArrayList<>();
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(getLayoutResourceId(), container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        postsManager = PostsManager.getInstance(requireContext());
        likesManager = LikesManager.getInstance(requireContext());
        profileManager = org.nikanikoo.flux.data.managers.ProfileManager.getInstance(requireContext());

        // Инициализируем paginationHelper всегда
        paginationHelper = new PaginationHelper(Constants.Api.POSTS_PER_PAGE);

        initViews(view);
        int contentId = swipeRefresh != null ? swipeRefresh.getId() : (recyclerPosts != null ? recyclerPosts.getId() : android.R.id.content);
        setupErrorView(view, contentId);
        setRetryCallback(() -> {
            paginationHelper.reset();
            posts.clear();
            loadPosts(true);
        });

        setupSwipeRefresh();
        setupRecyclerView();

        loadData();
    }
    
    /**
     * Возвращает ID layout ресурса
     */
    protected abstract int getLayoutResourceId();
    
    /**
     * Инициализация views
     */
    protected void initViews(View view) {
        // Пытаемся найти RecyclerView по разным ID
        recyclerPosts = view.findViewById(R.id.recycler_posts);
        if (recyclerPosts == null) {
            recyclerPosts = view.findViewById(R.id.recycler_profile_posts);
        }
        if (recyclerPosts == null) {
            recyclerPosts = view.findViewById(R.id.recycler_group_posts);
        }
        
        // Пытаемся найти SwipeRefreshLayout по разным ID
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        if (swipeRefresh == null) {
            swipeRefresh = view.findViewById(R.id.swipe_refresh_profile);
        }
        if (swipeRefresh == null) {
            swipeRefresh = view.findViewById(R.id.swipe_refresh_group);
        }
        
        progressLoading = view.findViewById(R.id.progress_loading);
    }
    
    /**
     * Настройка SwipeRefreshLayout
     */
    protected void setupSwipeRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                paginationHelper.reset();
                posts.clear();
                loadPosts(true);
            });
        }
    }
    
    /**
     * Настройка RecyclerView и пагинации
     */
    protected void setupRecyclerView() {
        if (recyclerPosts == null) {
            Logger.w(TAG, "RecyclerView not found in layout, skipping setup");
            return;
        }
        
        Logger.d(TAG, "setupRecyclerView: setting up adapter and scroll listener");

        layoutManager = new LinearLayoutManager(getContext());
        recyclerPosts.setLayoutManager(layoutManager);

        // RecyclerView optimizations
        recyclerPosts.setItemViewCacheSize(20);

        // Enable item animator for smooth animations
        recyclerPosts.setItemAnimator(null);
        
        postAdapter = new PostAdapter(getContext(), posts);
        postAdapter.setProfileWall(true);
        postAdapter.setOnPostClickListener(this);
        recyclerPosts.setAdapter(postAdapter);
        
        if (getView() != null) {
            androidx.core.widget.NestedScrollView nestedScrollView = getView().findViewById(R.id.profile_content);
            if (nestedScrollView == null) {
                nestedScrollView = getView().findViewById(R.id.group_content);
            }
            
            if (nestedScrollView != null) {
                final androidx.core.widget.NestedScrollView finalScrollView = nestedScrollView;
                finalScrollView.setOnScrollChangeListener(new androidx.core.widget.NestedScrollView.OnScrollChangeListener() {
                    private int lastLoggedDiff = -1;
                    
                    @Override
                    public void onScrollChange(androidx.core.widget.NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                        View child = v.getChildAt(v.getChildCount() - 1);
                        if (child != null) {
                            int diff = (child.getBottom() - (v.getHeight() + scrollY));
                            
                            if (Math.abs(diff - lastLoggedDiff) > 500) {
                                Logger.d(TAG, "Scroll diff=" + diff + ", childBottom=" + child.getBottom() + ", height=" + v.getHeight() + ", scrollY=" + scrollY);
                                lastLoggedDiff = diff;
                            }
                            
                            if (diff <= 1500) {
                                if (paginationHelper.canLoadMore() && !paginationHelper.isLoading()) {
                                    Logger.d(TAG, "NestedScrollView reached bottom (diff=" + diff + "), loading more");
                                    loadPosts(false);
                                }
                            }
                        }
                    }
                });
            } else {
                scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
                    @Override
                    public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                        Logger.d(TAG, "EndlessScrollListener.onLoadMore: offset=" + offset + ", canLoadMore=" + paginationHelper.canLoadMore());
                        if (paginationHelper.canLoadMore()) {
                            loadPosts(false);
                        }
                    }
                };
                recyclerPosts.addOnScrollListener(scrollListener);
            }
        }
        
        Logger.d(TAG, "setupRecyclerView: complete, postAdapter=" + (postAdapter != null ? "created" : "null"));
    }
    
    /**
     * Загрузка данных профиля
     */
    protected abstract void loadData();
    
    /**
     * Загрузка постов
     */
    protected abstract void loadPosts(boolean isRefresh);
    
    /**
     * Показать индикатор загрузки
     */
    protected void showLoading() {
        if (progressLoading != null) {
            progressLoading.setVisibility(View.VISIBLE);
        }
        if (recyclerPosts != null) {
            recyclerPosts.setVisibility(View.GONE);
        }
    }
    
    /**
     * Скрыть индикатор загрузки
     */
    protected void hideLoading() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
        }
        if (recyclerPosts != null) {
            recyclerPosts.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Обработка успешной загрузки постов
     */
    protected void onPostsLoaded(List<Post> loadedPosts, boolean isRefresh) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            hideLoading();
            hideError();

            if (postAdapter == null) {
                Logger.e(TAG, "postAdapter is null, cannot update posts");
                return;
            }
            
            int postsBeforeAdd = postAdapter.getPostsCount();
            
            if (isRefresh) {
                postAdapter.setPosts(loadedPosts);
                posts.clear();
                posts.addAll(loadedPosts);
            } else {
                postAdapter.addPosts(loadedPosts);
                posts.addAll(loadedPosts);
            }
            
            int postsAfterAdd = postAdapter.getPostsCount();
            int actuallyAdded = postsAfterAdd - postsBeforeAdd;
            
            Logger.d(TAG, "onPostsLoaded: isRefresh=" + isRefresh + ", loaded=" + loadedPosts.size() + 
                    ", before=" + postsBeforeAdd + ", after=" + postsAfterAdd + ", added=" + actuallyAdded);

            int unpinnedPostsCount = 0;
            for (Post post : loadedPosts) {
                if (!post.isPinned()) {
                    unpinnedPostsCount++;
                }
            }

            paginationHelper.onDataLoaded(unpinnedPostsCount);
            
            if (unpinnedPostsCount == 0) {
                paginationHelper.setNoMoreData();
            }
            
            if (loadedPosts.isEmpty() && postAdapter.getPostsCount() == 0) {
                showEmptyState();
            }
        });
    }
    
    /**
     * Обработка ошибки загрузки постов
     */
    protected void onPostsError(String error, boolean isRefresh) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            Logger.e(TAG, "Error loading posts: " + error);
            paginationHelper.stopLoading();
            hideLoading();
            
            if (postAdapter != null) {
                postAdapter.hideLoading();
            }

            showErrorAuto(error);
        });
    }
    
    /**
     * Показать состояние "Нет данных"
     */
    protected void showEmptyState() {
        // Переопределяется в наследниках при необходимости
    }
    
    @Override
    public void onLikeClick(Post post) {
        if (post == null) return;
        
        final boolean originalLikedState = post.isLiked();
        final int originalLikeCount = post.getLikeCount();
        final boolean newLikedState = !originalLikedState;
        final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;
        
        post.setLiked(newLikedState);
        post.setLikeCount(newLikeCount);
        
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getPostId() == post.getPostId() && 
                posts.get(i).getOwnerId() == post.getOwnerId()) {
                postAdapter.notifyItemChanged(i, "LIKE_UPDATE");
                break;
            }
        }
        
        likesManager.toggleLike("post", post.getOwnerId(), post.getPostId(), originalLikedState, 
            new LikesManager.LikeCallback() {
                @Override
                public void onSuccess(int newLikesCount) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            post.setLikeCount(newLikesCount);
                            post.setLiked(!originalLikedState);
                            for (int i = 0; i < posts.size(); i++) {
                                if (posts.get(i).getPostId() == post.getPostId() && 
                                    posts.get(i).getOwnerId() == post.getOwnerId()) {
                                    postAdapter.notifyItemChanged(i, "LIKE_UPDATE");
                                    break;
                                }
                            }
                        });
                    }
                }
                
                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            post.setLiked(originalLikedState);
                            post.setLikeCount(originalLikeCount);
                            for (int i = 0; i < posts.size(); i++) {
                                if (posts.get(i).getPostId() == post.getPostId() && 
                                    posts.get(i).getOwnerId() == post.getOwnerId()) {
                                    postAdapter.notifyItemChanged(i, "LIKE_UPDATE");
                                    break;
                                }
                            }
                            Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
    }

    @Override
    public void onPostLongClick(Post post, View view) {
        showPostContextMenu(post, view);
    }

    protected void showPostContextMenu(Post post, View view) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), view);
        
        org.nikanikoo.flux.data.models.UserProfile currentProfile = profileManager.getCachedProfileSync();
        boolean isOwnPost = currentProfile != null && post.getAuthorId() == currentProfile.getId();
        boolean isOnOwnWall = currentProfile != null && post.getOwnerId() == currentProfile.getId();
        
        if (post.canEdit()) {
            popup.getMenu().add(0, 1, 0, getString(R.string.edit));
        }
        if (post.canPin()) {
            popup.getMenu().add(0, 3, 0, post.isPinned() ? getString(R.string.unpin) : getString(R.string.pin));
        }
        popup.getMenu().add(0, 4, 0, getString(R.string.copy_link));
        if (post.canDelete()) {
            popup.getMenu().add(0, 5, 0, getString(R.string.delete));
        }
        
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: // Редактировать
                    editPost(post);
                    return true;
                case 5: // Удалить
                    deletePost(post);
                    return true;
                case 3: // Закрепить/Открепить
                    if (post.isPinned()) {
                        unpinPost(post);
                    } else {
                        pinPost(post);
                    }
                    return true;
                case 4: // Скопировать ссылку
                    copyPostLink(post);
                    return true;
                default:
                    return false;
            }
        });
        
        popup.show();
    }

    protected void editPost(Post post) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_post, null);
        com.google.android.material.textfield.TextInputEditText editMessage = dialogView.findViewById(R.id.edit_post_message);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_edit);
        com.google.android.material.button.MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_edit);

        editMessage.setText(post.getContent());
        
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        dialog.setContentView(dialogView);

        dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newMessage = editMessage.getText() != null ? editMessage.getText().toString() : "";
            postsManager.editPost(post.getOwnerId(), post.getPostId(), newMessage, new PostsManager.EditCallback() {
                @Override
                public void onSuccess(int postId) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), getString(R.string.success), Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadPosts(true);
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });

        dialog.show();
    }

    protected void deletePost(Post post) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.post_delete_confirm))
                .setMessage(getString(R.string.post_delete_message))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    postsManager.deletePost(post.getOwnerId(), post.getPostId(), new PostsManager.DeleteCallback() {
                        @Override
                        public void onSuccess() {
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), getString(R.string.success), Toast.LENGTH_SHORT).show();
                                    loadPosts(true);
                                });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    protected void pinPost(Post post) {
        postsManager.pinPost(post.getOwnerId(), post.getPostId(), new PostsManager.PinCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        post.setPinned(true);
                        loadPosts(true);
                        Toast.makeText(getContext(), "Пост закреплен", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка при закреплении: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    protected void unpinPost(Post post) {
        postsManager.unpinPost(post.getOwnerId(), post.getPostId(), new PostsManager.PinCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        post.setPinned(false);
                        loadPosts(true);
                        Toast.makeText(getContext(), "Пост откреплен", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка при откреплении: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    protected void copyPostLink(Post post) {
        org.nikanikoo.flux.data.managers.api.OpenVKApi api = 
            org.nikanikoo.flux.data.managers.api.OpenVKApi.getInstance(requireContext());
        String baseUrl = api.getBaseUrl();
        if (baseUrl.endsWith("/method")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 7);
        }
        String postUrl = baseUrl + "/wall" + post.getOwnerId() + "_" + post.getPostId();
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Post URL", postUrl);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), getString(R.string.copied_link), Toast.LENGTH_SHORT).show();
    }
}
