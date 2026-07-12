package org.nikanikoo.flux.ui.fragments.news;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.appcompat.widget.SearchView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.ui.fragments.comments.CommentsFragment;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.FeedPaginationHelper;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.managers.LikesManager;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.ui.adapters.posts.PostAdapter;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.activities.CreatePostActivity;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.dialogs.RepostDialog;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;


public class NewsFragment extends BaseFragment implements PostAdapter.OnPostClickListener {
    private static final String TAG = "NewsFragment";

    private RecyclerView recyclerPosts;
    private PostAdapter postAdapter;
    private PostsManager postsManager;
    private ProfileManager profileManager;
    private SwipeRefreshLayout swipeRefresh;
    private ImageView arrow;
    private FloatingActionButton fabCreatePost;
    private ProgressBar progressLoading;
    private LinearLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private FeedPaginationHelper paginationHelper;

    private boolean hasLoadedPosts = false;
    private volatile boolean isLoadingPosts = false;
    private volatile int loadRequestCounter = 0;
    private List<Post> savedPosts = new ArrayList<>();
    private String savedNextFrom;

    private static List<Post> sCachedPosts = null;
    private static String sCachedNextFrom = null;
    private static boolean sHasLoadedPosts = false;

    public static void clearCache() {
        sCachedPosts = null;
        sCachedNextFrom = null;
        sHasLoadedPosts = false;
    }

    // Тип новостей: true = подписки, false = все новости
    private boolean isSubscriptionMode = true;
    private String[] newsTypes;
    private boolean isSearchMode = false;
    private String currentSearchQuery = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_news, container, false);

        newsTypes = new String[] { getString(R.string.toolbar_subsfeed), getString(R.string.toolbar_globalfeed) };
        postsManager = PostsManager.getInstance(requireContext());
        profileManager = ProfileManager.getInstance(requireContext());

        boolean restoringFromBundle = savedInstanceState != null;
        if (restoringFromBundle) {
            hasLoadedPosts = savedInstanceState.getBoolean("has_loaded_posts", false);
            savedNextFrom = savedInstanceState.getString("saved_next_from");
        }

        if (savedPosts.isEmpty() && sHasLoadedPosts && sCachedPosts != null) {
            hasLoadedPosts = true;
            savedPosts = new ArrayList<>(sCachedPosts);
            savedNextFrom = sCachedNextFrom;
        }

        // Инициализация ErrorViewHandler
        setupErrorView(view, R.id.swipe_refresh_news);
        setRetryCallback(() -> {
            scrollListener.resetState();
            savedPosts.clear();
            savedNextFrom = null;
            hasLoadedPosts = false;
            isLoadingPosts = false;
            loadPosts(true);
        });

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupEndlessScroll();
        setupToolbarTitle();
        setHasOptionsMenu(true);

        if (hasLoadedPosts && !savedPosts.isEmpty()) {
            postAdapter.setPosts(new ArrayList<>(savedPosts));
            if (savedNextFrom != null) {
                paginationHelper.onDataLoaded(savedPosts.size(), savedNextFrom);
            }
            progressLoading.setVisibility(View.GONE);
            recyclerPosts.setVisibility(View.VISIBLE);
        } else {
            isLoadingPosts = false;
            loadPosts(true);
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("has_loaded_posts", hasLoadedPosts);
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            String currentTitle = isSubscriptionMode ? newsTypes[0] : newsTypes[1];
            mainActivity.setToolbarTitleClickable(currentTitle, v -> showNewsTypeDialog());
        }
    }
    
    private void showNewsTypeDialog() {
        if (getContext() == null) return;

        if (isLoadingPosts) {
            Toast.makeText(getContext(), getString(R.string.news_wait_for_load), Toast.LENGTH_SHORT).show();
            return;
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.news_select_type));

        int currentSelection = isSubscriptionMode ? 0 : 1;

        builder.setSingleChoiceItems(newsTypes, currentSelection, (dialog, which) -> {
            boolean newMode = (which == 0);
            if (newMode != isSubscriptionMode) {
                isSubscriptionMode = newMode;
                setupToolbarTitle();

                savedPosts.clear();
                savedNextFrom = null;
                hasLoadedPosts = false;
                isLoadingPosts = false;

                scrollListener.resetState();
                loadPosts(true);
            }
            dialog.dismiss();
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void initViews(View view) {
        recyclerPosts = view.findViewById(R.id.recycler_posts);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_news);
        fabCreatePost = view.findViewById(R.id.fab_create_post);
        progressLoading = view.findViewById(R.id.progress_loading);
        arrow = view.findViewById(R.id.news_toolbar_arrow);
        
        // Обработчик клика на FAB
        if (fabCreatePost != null) {
            fabCreatePost.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), CreatePostActivity.class);
                startActivity(intent);
            });
        }
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(getContext());
        recyclerPosts.setLayoutManager(layoutManager);
        postAdapter = new PostAdapter(getContext(), null);
        postAdapter.setOnPostClickListener(this);
        recyclerPosts.setAdapter(postAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            Logger.d(TAG, " Refreshing posts...");
            scrollListener.resetState();
            savedPosts.clear();
            savedNextFrom = null;
            hasLoadedPosts = false;
            isLoadingPosts = false;
            
            sCachedPosts = null;
            sCachedNextFrom = null;
            sHasLoadedPosts = false;
            
            loadPosts(true);
        });
    }

    private void setupEndlessScroll() {
        paginationHelper = new FeedPaginationHelper(Constants.Api.POSTS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                Logger.d(TAG, " Loading more posts, next_from: " + paginationHelper.getNextFromCursor());
                loadPosts(false);
            }
        };
        recyclerPosts.addOnScrollListener(scrollListener);
    }

    private void loadPosts(boolean isRefresh) {
        if (isLoadingPosts) {
            Logger.d(TAG, " loadPosts called but already loading, skipping");
            return;
        }

        int currentRequest = ++loadRequestCounter;

        Logger.d(TAG, " loadPosts called, isRefresh=" + isRefresh +
            ", canLoadMore=" + paginationHelper.canLoadMore() +
            ", nextFrom=" + paginationHelper.getNextFromCursor());

        if (!paginationHelper.canLoadMore() && !isRefresh) {
            Logger.d(TAG, " Cannot load more, skipping request");
            return;
        }

        isLoadingPosts = true;

        String nextFrom = isRefresh ? null : paginationHelper.getNextFromCursor();

        // Показываем прогрессбар при первой загрузке или обновлении
        if (isRefresh && postAdapter.getPostsCount() == 0) {
            progressLoading.setVisibility(View.VISIBLE);
            recyclerPosts.setVisibility(View.GONE);
        }

        // Показываем индикатор загрузки только при загрузке новых постов
        if (!isRefresh && postAdapter.getPostsCount() > 0) {
            postAdapter.showLoading();
        }

        Logger.d(TAG, " Loading posts with next_from: " + nextFrom + ", isRefresh: " + isRefresh + 
            ", mode: " + (isSubscriptionMode ? "subscriptions" : "global"));

        // Вызываем startLoading ПОСЛЕ проверки canLoadMore
        paginationHelper.startLoading();

        // Выбираем метод загрузки в зависимости от режима
        if (isSearchMode) {
            postsManager.searchNews(currentSearchQuery, nextFrom, new PostsManager.FeedPostsCallback() {
                @Override
                public void onSuccess(List<Post> loadedPosts, String nextFrom) {
                    Logger.d(TAG, " Successfully loaded " + loadedPosts.size() + " search posts, next_from: " + nextFrom);
                    handlePostsLoaded(loadedPosts, nextFrom, isRefresh, currentRequest);
                }

                @Override
                public void onError(String error) {
                    Logger.d(TAG, " Error searching newsfeed: " + error);
                    handlePostsError(error, isRefresh, currentRequest);
                }
            });
        } else if (isSubscriptionMode) {
            postsManager.loadSubscriptionNewsFeed(nextFrom, new PostsManager.FeedPostsCallback() {
                @Override
                public void onSuccess(List<Post> loadedPosts, String nextFrom) {
                    Logger.d(TAG, " Successfully loaded " + loadedPosts.size() + " subscription posts, next_from: " + nextFrom);
                    handlePostsLoaded(loadedPosts, nextFrom, isRefresh, currentRequest);
                }

                @Override
                public void onError(String error) {
                    Logger.d(TAG, " Error loading subscription newsfeed: " + error);
                    handlePostsError(error, isRefresh, currentRequest);
                }
            });
        } else {
            postsManager.loadGlobalNewsFeed(nextFrom, new PostsManager.FeedPostsCallback() {
                @Override
                public void onSuccess(List<Post> loadedPosts, String nextFrom) {
                    Logger.d(TAG, " Successfully loaded " + loadedPosts.size() + " global posts, next_from: " + nextFrom);
                    handlePostsLoaded(loadedPosts, nextFrom, isRefresh, currentRequest);
                }

                @Override
                public void onError(String error) {
                    Logger.d(TAG, " Error loading global newsfeed: " + error);
                    handlePostsError(error, isRefresh, currentRequest);
                }
            });
        }
    }
    
    private void handlePostsLoaded(List<Post> loadedPosts, String nextFrom, boolean isRefresh, int requestNumber) {
        if (requestNumber != loadRequestCounter) {
            Logger.d(TAG, " Ignoring stale handlePostsLoaded from request " + requestNumber +
                ", current is " + loadRequestCounter);
            return;
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (requestNumber != loadRequestCounter) {
                    Logger.d(TAG, " Ignoring stale handlePostsLoaded (UI thread check)");
                    return;
                }

                swipeRefresh.setRefreshing(false);
                hideError();

                // Скрываем прогрессбар и показываем список
                progressLoading.setVisibility(View.GONE);
                recyclerPosts.setVisibility(View.VISIBLE);

                int postsBeforeAdd = postAdapter.getPostsCount();

                if (isRefresh) {
                    postAdapter.setPosts(loadedPosts);
                } else {
                    postAdapter.addPosts(loadedPosts);
                }

                // Вычисляем сколько постов реально добавилось (после фильтрации дубликатов)
                int postsAfterAdd = postAdapter.getPostsCount();
                int actuallyAdded = postsAfterAdd - postsBeforeAdd;

                Logger.d(TAG, " Loaded " + loadedPosts.size() + " posts from API, " +
                    actuallyAdded + " actually added after duplicate filtering");

                paginationHelper.onDataLoaded(loadedPosts.size(), nextFrom);

                hasLoadedPosts = true;
                savedPosts.clear();
                for (int i = 0; i < postAdapter.getPostsCount(); i++) {
                    Post p = postAdapter.getPost(i);
                    if (p != null) savedPosts.add(p);
                }
                savedNextFrom = nextFrom;
                
                sCachedPosts = new ArrayList<>(savedPosts);
                sCachedNextFrom = savedNextFrom;
                sHasLoadedPosts = true;
                
                isLoadingPosts = false;
                if (loadedPosts.isEmpty() && postAdapter.getPostsCount() == 0) {
                    Logger.d(TAG, " No posts received, trying alternative method...");
                    loadPublicPosts(isRefresh, requestNumber);
                }
            });
        }
    }

    private void handlePostsError(String error, boolean isRefresh, int requestNumber) {
        if (requestNumber != loadRequestCounter) {
            Logger.d(TAG, " Ignoring stale handlePostsError from request " + requestNumber);
            return;
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (requestNumber != loadRequestCounter) {
                    Logger.d(TAG, " Ignoring stale handlePostsError (UI thread check)");
                    return;
                }

                isLoadingPosts = false;

                paginationHelper.stopLoading();
                swipeRefresh.setRefreshing(false);
                postAdapter.hideLoading();

                progressLoading.setVisibility(View.GONE);

                if (postAdapter.getPostsCount() == 0) {
                    showErrorAuto(error);
                    // Пробуем альтернативный метод только если нет постов
                    loadPublicPosts(isRefresh, requestNumber);
                } else {
                    showErrorAuto(error);
                }
            });
        }
    }

    private void loadPublicPosts(boolean isRefresh, int requestNumber) {
        Logger.d(TAG, " Trying public posts...");

        postsManager.loadPublicPosts(new PostsManager.PostsCallback() {
            @Override
            public void onSuccess(List<Post> loadedPosts) {
                Logger.d(TAG, " Successfully loaded " + loadedPosts.size() + " public posts");
                if (requestNumber != loadRequestCounter) {
                    Logger.d(TAG, " Ignoring stale loadPublicPosts success");
                    return;
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (requestNumber != loadRequestCounter) return;
                        isLoadingPosts = false;

                        if (isRefresh) {
                            postAdapter.setPosts(loadedPosts);
                        } else {
                            postAdapter.addPosts(loadedPosts);
                        }
                        hasLoadedPosts = true;

                        savedPosts.clear();
                        for (int i = 0; i < postAdapter.getPostsCount(); i++) {
                            Post p = postAdapter.getPost(i);
                            if (p != null) savedPosts.add(p);
                        }

                        if (loadedPosts.isEmpty() && postAdapter.getPostsCount() == 0) {
                            Logger.d(TAG, " Still no posts, loading test data...");
                            loadTestPosts();
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                Logger.d(TAG, " Error loading public posts: " + error);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (requestNumber != loadRequestCounter) return;
                        isLoadingPosts = false;
                        if (postAdapter.getPostsCount() == 0) {
                            Toast.makeText(getContext(), getString(R.string.news_public_loading_error) + error, Toast.LENGTH_SHORT).show();
                            loadTestPosts();
                        }
                    });
                }
            }
        });
    }

    private void loadTestPosts() {
        isLoadingPosts = false;
        List<Post> testPosts = new ArrayList<>();
        postAdapter.setPosts(testPosts);
    }

    private void checkLikeStatusForPosts(int count) {
        for (int i = 0; i < count; i++) {
            final Post post = postAdapter.getPost(i);
            if (post == null) continue;
            final int position = i;

            // Проверяем статус лайка только если есть необходимые данные
            if (post.getPostId() != 0 && post.getOwnerId() != 0) {
                postsManager.checkLikeStatus(post, new LikesManager.LikeStatusCallback() {
                    @Override
                    public void onSuccess(boolean isLiked) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Post currentPost = postAdapter.getPost(position);
                                if (currentPost != null) {
                                    currentPost.setLiked(isLiked);
                                    postAdapter.notifyItemChanged(position, "LIKE_UPDATE");
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Error checking like status for post " + post.getPostId() + ": " + error);
                        // Не показываем ошибку пользователю, просто логируем
                    }
                });
            }
        }
    }

    // Реализация интерфейса OnPostClickListener
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
    public void onLikeClick(Post post) {
        // Проверяем, есть ли необходимые данные для лайка
        if (post.getPostId() == 0 || post.getOwnerId() == 0) {
            Toast.makeText(getContext(), getString(R.string.post_like_error), Toast.LENGTH_SHORT).show();
            return;
        }

        int position = postAdapter.findPostPosition(post.getPostId(), post.getOwnerId());
        if (position < 0) {
            Logger.w(TAG, "Could not find post position for like click");
            return;
        }

        // Сохраняем текущее состояние для возможного отката
        Post adapterPost = postAdapter.getPost(position);
        if (adapterPost == null) return;

        final boolean originalLikedState = adapterPost.isLiked();
        final int originalLikeCount = adapterPost.getLikeCount();

        // Сразу обновляем UI (optimistic update)
        final boolean newLikedState = !originalLikedState;
        final int newLikeCount = originalLikedState ? originalLikeCount - 1 : originalLikeCount + 1;

        adapterPost.setLiked(newLikedState);
        adapterPost.setLikeCount(newLikeCount);
        postAdapter.notifyItemChanged(position, "LIKE_UPDATE");

        // Отправляем запрос на сервер
        postsManager.toggleLikeOptimistic(adapterPost, originalLikedState, new PostsManager.LikeToggleCallback() {
            @Override
            public void onSuccess(int serverLikesCount, boolean serverIsLiked) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        int pos = postAdapter.findPostPosition(post.getPostId(), post.getOwnerId());
                        if (pos >= 0) {
                            Post p = postAdapter.getPost(pos);
                            if (p != null) {
                                p.setLikeCount(serverLikesCount);
                                p.setLiked(serverIsLiked);
                                postAdapter.notifyItemChanged(pos, "LIKE_UPDATE");
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Откатываем изменения при ошибке
                        int pos = postAdapter.findPostPosition(post.getPostId(), post.getOwnerId());
                        if (pos >= 0) {
                            Post p = postAdapter.getPost(pos);
                            if (p != null) {
                                p.setLiked(originalLikedState);
                                p.setLikeCount(originalLikeCount);
                                postAdapter.notifyItemChanged(pos, "LIKE_UPDATE");
                            }
                        }

                        Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    @Override
    public void onCommentClick(Post post) {
        // Переход к комментариям поста
        CommentsFragment commentsFragment = CommentsFragment.newInstance(post);
        
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, commentsFragment)
                    .addToBackStack("comments_" + post.getPostId())
                    .commit();
        }
    }

    @Override
    public void onShareClick(Post post) {
        RepostDialog.show(requireContext(), post, (repostedPost, comment) -> {
            Logger.d(TAG, "Repost with comment: " + comment);
        });
    }
    
    @Override
    public void onPostLongClick(Post post, View view) {
        // Показываем контекстное меню для поста
        showPostContextMenu(post, view);
    }
    
    private void showPostContextMenu(Post post, View view) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), view);
        
        // Получаем текущий профиль пользователя для проверки прав
        UserProfile currentProfile = profileManager.getCachedProfileSync();
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
    
    private void editPost(Post post) {
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
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), getString(R.string.success), Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadPosts(true); 
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });

        dialog.show();
    }
    
    private void deletePost(Post post) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.post_delete_confirm))
                .setMessage(getString(R.string.post_delete_message))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    postsManager.deletePost(post.getOwnerId(), post.getPostId(), new PostsManager.DeleteCallback() {
                        @Override
                        public void onSuccess() {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), getString(R.string.success), Toast.LENGTH_SHORT).show();
                                    loadPosts(true); 
                                });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
    
    private void pinPost(Post post) {
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

    private void unpinPost(Post post) {
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
    
    private void copyPostLink(Post post) {
        OpenVKApi api = OpenVKApi.getInstance(requireContext());
        String baseUrl = api.getBaseUrl();
        // Убираем /method из URL если он есть
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

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() != null) {
            arrow = getActivity().findViewById(R.id.news_toolbar_arrow);
            if (arrow != null) {
                arrow.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() != null) {
            arrow = getActivity().findViewById(R.id.news_toolbar_arrow);
            if (arrow != null) {
                arrow.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_search, menu);
        
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        if (!query.trim().isEmpty()) {
                            isSearchMode = true;
                            currentSearchQuery = query;
                            savedPosts.clear();
                            postAdapter.setPosts(new ArrayList<>());
                            loadPosts(true);
                        }
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        return false;
                    }
                });
            }

            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    // Скрываем стрелку выбора типа новостей при поиске
                    if (arrow != null) {
                        arrow.setVisibility(View.GONE);
                    }
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    if (isSearchMode) {
                        isSearchMode = false;
                        currentSearchQuery = "";
                        savedPosts.clear();
                        postAdapter.setPosts(new ArrayList<>());
                        loadPosts(true);
                    }
                    if (arrow != null) {
                        arrow.setVisibility(View.VISIBLE);
                    }
                    return true;
                }
            });
        }
        
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Очищаем слушатель скролла
        if (recyclerPosts != null && scrollListener != null) {
            recyclerPosts.removeOnScrollListener(scrollListener);
        }
    }
}