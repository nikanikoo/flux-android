package org.nikanikoo.flux.ui.fragments.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.nikanikoo.flux.data.models.Notification;
import org.nikanikoo.flux.ui.custom.NotificationBadgeListener;
import org.nikanikoo.flux.ui.adapters.notifications.NotificationsAdapter;
import org.nikanikoo.flux.data.managers.NotificationsManager;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.data.models.Photo;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.activities.PhotoViewerActivity;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.ui.fragments.comments.CommentsFragment;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;
import org.nikanikoo.flux.utils.Logger;
import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends BaseFragment implements NotificationsAdapter.OnNotificationClickListener {
    
    private static final String TAG = "NotificationsFragment";
    private static final int PAGE_SIZE = 10; // Загружаем по 10 уведомлений
    
    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton markAllReadFab;
    private NotificationsManager notificationsManager;
    private List<Notification> notifications;
    private NotificationBadgeListener badgeListener;
    
    // Переменные для пагинации
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private int currentOffset = 0;
    private boolean isLoadingArchived = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);
        
        // Получаем ссылку на MainActivity для обновления бейджа
        if (getActivity() instanceof NotificationBadgeListener) {
            badgeListener = (NotificationBadgeListener) getActivity();
        }
        
        initViews(view);
        setupRecyclerView();
        setupFab();
        setupToolbarTitle();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(() -> loadNotifications());

        loadNotifications();

        return view;
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(getString(R.string.notifications_title));
        }
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        markAllReadFab = view.findViewById(R.id.mark_all_read_fab);
        notificationsManager = NotificationsManager.getInstance(requireContext());
        notifications = new ArrayList<>();
        
        swipeRefreshLayout.setOnRefreshListener(this::loadNotifications);
    }

    private void setupRecyclerView() {
        adapter = new NotificationsAdapter(requireContext(), notifications);
        adapter.setOnNotificationClickListener(this);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        
        // Добавляем обработчик скролла для бесконечной загрузки
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                if (dy > 0) { // Скроллим вниз
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int pastVisibleItems = layoutManager.findFirstVisibleItemPosition();
                    
                    if (!isLoading && hasMoreData && (visibleItemCount + pastVisibleItems) >= totalItemCount - 3) {
                        // Загружаем следующую страницу когда остается 3 элемента до конца
                        loadMoreNotifications();
                    }
                }
            }
        });
    }

    private void setupFab() {
        markAllReadFab.setOnClickListener(v -> markAllAsRead());
    }

    private void loadNotifications() {
        if (isLoading) return;
        
        isLoading = true;
        swipeRefreshLayout.setRefreshing(true);
        
        // Сбрасываем состояние пагинации при обновлении
        currentOffset = 0;
        hasMoreData = true;
        isLoadingArchived = false;
        notifications.clear();
        
        Logger.d(TAG, "Loading notifications from beginning");
        
        // Сначала проверяем наличие новых уведомлений
        notificationsManager.checkForNewNotifications(new NotificationsManager.NotificationsCallback() {
            @Override
            public void onSuccess(List<Notification> allNotifications) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Фильтруем непрочитанные уведомления
                        List<Notification> unreadNotifications = new ArrayList<>();
                        for (Notification notification : allNotifications) {
                            if (!notification.isRead()) {
                                unreadNotifications.add(notification);
                            }
                        }
                        
                        if (!unreadNotifications.isEmpty()) {
                            // Есть новые уведомления - показываем их
                            Logger.d(TAG, "Found " + unreadNotifications.size() + " new notifications");
                            notifications.clear();
                            notifications.addAll(unreadNotifications);
                            adapter.updateNotifications(notifications);
                            markAllReadFab.setVisibility(View.VISIBLE);
                            currentOffset = allNotifications.size();
                            if (allNotifications.size() < PAGE_SIZE) {
                                isLoadingArchived = true;
                                currentOffset = 0;
                                loadArchivedNotifications(0);
                                return;
                            }
                        } else {
                            // Нет новых уведомлений - загружаем архивные
                            Logger.d(TAG, "No new notifications, loading archived");
                            notifications.clear();
                            isLoadingArchived = true;
                            loadArchivedNotifications(0);
                            return; // Выходим, чтобы не сбросить флаг isLoading
                        }
                        
                        isLoading = false;
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // При ошибке загружаем архивные уведомления
                        Logger.d(TAG, "Error checking new notifications, loading archived: " + error);
                        notifications.clear();
                        isLoadingArchived = true;
                        loadArchivedNotifications(0);
                    });
                }
            }
        });
    }
    
    private void loadMoreNotifications() {
        if (isLoading || !hasMoreData) return;
        
        isLoading = true;
        Logger.d(TAG, "Loading more notifications, offset: " + currentOffset + ", archived: " + isLoadingArchived);
        
        if (isLoadingArchived) {
            loadArchivedNotifications(currentOffset);
        } else {
            loadMoreNewNotifications();
        }
    }

    private void loadMoreNewNotifications() {
        notificationsManager.getNotifications(PAGE_SIZE, currentOffset, new NotificationsManager.NotificationsCallback() {
            @Override
            public void onSuccess(List<Notification> newNotifications) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    List<Notification> unread = new ArrayList<>();
                    for (Notification notification : newNotifications) {
                        if (!notification.isRead()) unread.add(notification);
                    }
                    notifications.addAll(unread);
                    currentOffset += newNotifications.size();
                    adapter.updateNotifications(notifications);

                    if (newNotifications.size() < PAGE_SIZE) {
                        isLoadingArchived = true;
                        currentOffset = 0;
                        loadArchivedNotifications(0);
                        return;
                    }
                    isLoading = false;
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Logger.e(TAG, "Error loading more new notifications: " + error);
                    isLoading = false;
                    isLoadingArchived = true;
                    currentOffset = 0;
                    loadArchivedNotifications(0);
                });
            }
        });
    }

    private void loadArchivedNotifications(int offset) {
        Logger.d(TAG, "Loading archived notifications with offset: " + offset);
        
        notificationsManager.getArchivedNotifications(PAGE_SIZE, offset, new NotificationsManager.NotificationsCallback() {
            @Override
            public void onSuccess(List<Notification> archivedNotifications) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Logger.d(TAG, "Loaded " + archivedNotifications.size() + " archived notifications");
                        
                        if (archivedNotifications.isEmpty()) {
                            // Больше нет данных
                            hasMoreData = false;
                            Logger.d(TAG, "No more archived notifications available");
                        } else {
                            notifications.addAll(archivedNotifications);
                            currentOffset += archivedNotifications.size();
                            
                            // Если получили меньше чем PAGE_SIZE, значит это последняя страница
                            if (archivedNotifications.size() < PAGE_SIZE) {
                                hasMoreData = false;
                                Logger.d(TAG, "Last page of archived notifications loaded");
                            }
                        }
                        
                        adapter.updateNotifications(notifications);
                        markAllReadFab.setVisibility(View.GONE); // Архивные уже прочитаны
                        
                        isLoading = false;
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showErrorAuto(error);
                        hasMoreData = false; // Останавливаем дальнейшие попытки загрузки

                        isLoading = false;
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        });
    }

    private void markAllAsRead() {
        notificationsManager.markAsRead(new NotificationsManager.MarkAsReadCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Пометить все уведомления как прочитанные
                        for (Notification notification : notifications) {
                            notification.setRead(true);
                        }
                        notifications.clear();
                        currentOffset = 0;
                        adapter.updateNotifications(notifications);
                        markAllReadFab.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), getString(R.string.notifications_all_read), Toast.LENGTH_SHORT).show();
                        
                        // После пометки как прочитанные, сбрасываем состояние и загружаем архивные
                        currentOffset = 0;
                        hasMoreData = true;
                        isLoadingArchived = true;
                        loadArchivedNotifications(0);
                        
                        // Уведомляем MainActivity об изменении бейджа
                        if (badgeListener != null) {
                            badgeListener.onNotificationBadgeUpdate();
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    @Override
    public void onNotificationClick(Notification notification) {
        // Пометить уведомление как прочитанное при клике
        notification.setRead(true);
        adapter.notifyDataSetChanged();
        
        // Обработка переходов для разных типов уведомлений
        String type = notification.getType();
        switch (type) {
            case "mention":
            case "comment_post":
            case "reply_comment":
            case "reply_comment_unknown":
            case "reply_comment_photo":
            case "reply_comment_video":
            case "mention_comment_photo":
            case "mention_comment_video":
            case "mention_comment_note":
            case "mention_comments":
            case "mention_comment_unknown":
                // Переход к посту
                navigateToPost(notification);
                break;
                
            case "comment_photo":
                Photo photo = notification.getPhoto();
                if (photo != null && photo.getBestUrl() != null && !photo.getBestUrl().isEmpty()) {
                    PhotoViewerActivity.startWithPhotos(requireContext(), java.util.Collections.singletonList(photo),
                            0, notification.getFromName());
                } else {
                    navigateToProfile(notification.getFromId(), notification.getFromName());
                }
                break;

            case "comment_video":
            case "comment_note":
            case "comment_topic":
                // Переход к посту с комментариями
                navigateToPost(notification);
                break;
                
            case "like_post":
            case "copy_post":
                // Переход к посту
                navigateToPost(notification);
                break;
                
            case "sent_gift":
            case "voices_transfer":
            case "up_rating":
                // Переход к профилю отправителя
                navigateToProfile(notification.getFromId(), notification.getFromName());
                break;

            case "make_you_admin":
            case "new_posts_in_club":
            case "wall_publish":
                navigateToProfile(notification.getFromId(), notification.getFromName());
                break;
                
            case "wall":
                // Переход к посту на стене
                navigateToPost(notification);
                break;
                
            default:
                // Для неизвестных типов показываем информацию
                Toast.makeText(requireContext(), 
                    notification.getFromName() + " " + notification.getReadableType(), 
                    Toast.LENGTH_SHORT).show();
                break;
        }
    }
    
    /**
     * Переход к посту
     */
    private void navigateToPost(Notification notification) {
        if (notification.getPostId() != 0 && notification.getPostOwnerId() != 0) {
            // Создаем объект поста для перехода к комментариям
            Post post = createPostFromNotification(notification);
            
            CommentsFragment commentsFragment = CommentsFragment.newInstance(post);
            
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, commentsFragment)
                        .addToBackStack("comments_" + notification.getPostId())
                        .commit();
                
                System.out.println("NotificationsFragment: Navigating to post " + notification.getPostId() + " from " + notification.getType() + " notification");
            }
        } else {
            // Если нет данных поста, переходим к профилю автора
            navigateToProfile(notification.getFromId(), notification.getFromName());
        }
    }
    
    /**
     * Переход к профилю пользователя
     */
    private void navigateToProfile(int userId, String userName) {
        if (userId != 0) {
            androidx.fragment.app.Fragment destination = userId < 0
                    ? GroupProfileFragment.newInstance(-userId, userName)
                    : ProfileFragment.newInstanceWithId(userId, userName);
            
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, destination)
                        .addToBackStack("profile_" + userId)
                        .commit();
                
                System.out.println("NotificationsFragment: Navigating to profile of user " + userId + " (" + userName + ")");
            }
        } else {
            Toast.makeText(requireContext(), 
                getString(R.string.notifications_error_user),
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Создание объекта поста из уведомления
     */
    private Post createPostFromNotification(Notification notification) {
        // Создаем минимальный объект поста для перехода к комментариям
        // Не используем данные комментатора, а создаем пустой пост с ID
        Post post = new Post(
            getString(R.string.loading), // Имя автора будет загружено в CommentsFragment
            notification.getDate(),
            "", // Текст поста будет загружен в CommentsFragment
            0, // likes - будут загружены при открытии
            0  // comments - будут загружены при открытии
        );
        
        post.setPostId(notification.getPostId());
        post.setOwnerId(notification.getPostOwnerId());
        // Не устанавливаем authorId и authorAvatarUrl - они будут загружены из API
        
        return post;
    }
    
    @Override
    public void onAvatarClick(int userId, String userName) {
        System.out.println("NotificationsFragment: Avatar clicked - userId: " + userId + ", userName: " + userName);
        
        if (getActivity() != null) {
            if (userId > 0) {
                // Пользователь
                ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(userId, userName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, profileFragment)
                        .addToBackStack("profile_" + userId)
                        .commit();
                System.out.println("NotificationsFragment: Navigating to profile of user " + userId + " (" + userName + ")");
            } else if (userId < 0) {
                // Группа (отрицательный ID)
                GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(-userId, userName);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, groupProfileFragment)
                        .addToBackStack("group_" + (-userId))
                        .commit();
                System.out.println("NotificationsFragment: Navigating to group profile " + (-userId) + " (" + userName + ")");
            }
        }
    }
}
