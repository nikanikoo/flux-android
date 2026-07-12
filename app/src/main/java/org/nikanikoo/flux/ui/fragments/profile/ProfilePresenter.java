package org.nikanikoo.flux.ui.fragments.profile;

import org.nikanikoo.flux.data.managers.FriendsManager;
import org.nikanikoo.flux.data.managers.MessagesManager;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.utils.Logger;

/**
 * Presenter для ProfileFragment.
 * Управляет бизнес-логикой загрузки и обработки данных профиля.
 */
public class ProfilePresenter implements ProfileContract.Presenter {
    
    private static final String TAG = "ProfilePresenter";
    
    private ProfileContract.View view;
    private ProfileManager profileManager;
    private UserProfile currentProfile;
    private boolean isLoading = false;
    
    public ProfilePresenter(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }
    
    @Override
    public void attachView(ProfileContract.View view) {
        this.view = view;
    }
    
    @Override
    public void detachView() {
        this.view = null;
    }
    
    @Override
    public void loadProfile() {
        if (view == null || isLoading) {
            return;
        }
        
        isLoading = true;
        view.showProfileLoading();
        
        int userId = view.getCurrentUserId();
        
        if (view.isForeignProfile()) {
            loadForeignProfile(userId);
        } else {
            loadOwnProfile();
        }
    }
    
    @Override
    public void refreshProfile() {
        if (view == null || isLoading) {
            return;
        }
        
        isLoading = true;
        int userId = view.getCurrentUserId();
        
        if (view.isForeignProfile()) {
            loadForeignProfile(userId);
        } else {
            loadOwnProfileWithRefresh();
        }
    }
    
    private void loadForeignProfile(int userId) {
        profileManager.loadProfileById(userId, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                isLoading = false;
                currentProfile = profile;
                if (view != null) {
                    view.hideProfileLoading();
                    view.displayProfile(profile);
                    view.setCreatePostButtonVisible(profile.canPost());
                }
            }
            
            @Override
            public void onError(String error) {
                isLoading = false;
                Logger.e(TAG, "Error loading foreign profile: " + error);
                if (view != null) {
                    view.hideProfileLoading();
                    view.showProfileError(error);
                }
            }
        });
    }
    
    private void loadOwnProfile() {
        profileManager.loadProfile(true, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                isLoading = false;
                currentProfile = profile;
                if (view != null) {
                    view.hideProfileLoading();
                    view.displayProfile(profile);
                    view.setCreatePostButtonVisible(true);
                }
            }

            @Override
            public void onError(String error) {
                isLoading = false;
                Logger.e(TAG, "Error loading own profile: " + error);
                if (view != null) {
                    view.hideProfileLoading();
                    view.showProfileError(error);
                }
            }
        });
    }
    
    private void loadOwnProfileWithRefresh() {
        profileManager.loadProfile(true, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                isLoading = false;
                currentProfile = profile;
                if (view != null) {
                    view.displayProfile(profile);
                    view.setCreatePostButtonVisible(true);
                }
            }
            
            @Override
            public void onError(String error) {
                isLoading = false;
                Logger.e(TAG, "Error refreshing profile: " + error);
                if (view != null) {
                    view.showProfileError(error);
                }
            }
        });
    }
    
    @Override
    public void onAvatarClick() {
        if (currentProfile == null || view == null) {
            return;
        }
        
        String photoUrl = currentProfile.getPhoto200();
        if (photoUrl != null && !photoUrl.isEmpty()) {
            view.openProfilePhoto(photoUrl, currentProfile.getFullName());
        }
    }
    
    @Override
    public void onFriendsClick() {
        if (currentProfile == null || view == null) {
            return;
        }
        
        int targetUserId = view.isForeignProfile() ? view.getCurrentUserId() : currentProfile.getId();
        view.navigateToFriends(targetUserId, currentProfile.getFullName());
    }
    
    @Override
    public void onCreatePostClick() {
        if (view == null) {
            return;
        }
        
        int ownerId = view.isForeignProfile() ? view.getCurrentUserId() : 0;
        view.openCreatePost(ownerId);
    }
    
    @Override
    public void onDetailsClick() {
        // Логика раскрытия деталей остается во View
        // Presenter не управляет UI-анимациями
    }
    
    @Override
    public void onMessageButtonClick() {
        if (view == null || currentProfile == null) {
            return;
        }
        
        int targetUserId = currentProfile.getId();
        String userName = currentProfile.getFullName();
        view.openChat(targetUserId, userName);
    }
    
    @Override
    public void onFriendButtonClick(boolean isFriend) {
        if (view == null || currentProfile == null) {
            return;
        }
        
        int targetUserId = currentProfile.getId();
        if (view.getContext() == null) {
            return;
        }
        
        FriendsManager friendsManager = FriendsManager.getInstance(view.getContext());
        if (isFriend) {
            friendsManager.declineFriendRequest(targetUserId, new FriendsManager.ActionCallback() {
                @Override
                public void onSuccess() {
                    if (view != null) {
                        view.showMessage("Пользователь удален из друзей");
                    }
                }
                
                @Override
                public void onError(String error) {
                    if (view != null) {
                        view.showMessage("Ошибка удаления из друзей: " + error);
                    }
                }
            });
        } else {
            friendsManager.acceptFriendRequest(targetUserId, new FriendsManager.ActionCallback() {
                @Override
                public void onSuccess() {
                    if (view != null) {
                        view.showMessage("Запрос в друзья отправлен");
                    }
                }
                
                @Override
                public void onError(String error) {
                    if (view != null) {
                        view.showMessage("Ошибка добавления в друзья: " + error);
                    }
                }
            });
        }
    }
    
    @Override
    public void onEditProfileClick() {
        if (view == null) {
            return;
        }
        
        android.content.Context context = view.getContext();
        if (context != null) {
            org.nikanikoo.flux.ui.activities.ProfileEditActivity.start(context);
        }
    }
    
    @Override
    public UserProfile getCurrentProfile() {
        return currentProfile;
    }
    
    /**
     * Проверяет, загружен ли профиль
     */
    public boolean isProfileLoaded() {
        return currentProfile != null;
    }
    
    /**
     * Получает ID профиля
     */
    public int getProfileId() {
        return currentProfile != null ? currentProfile.getId() : 0;
    }
}
