package org.nikanikoo.flux.ui.fragments.profile;

import org.nikanikoo.flux.data.models.UserProfile;

/**
 * Contract для ProfileFragment и ProfilePresenter.
 * Определяет интерфейсы View и Presenter для четкого разделения ответственности.
 */
public interface ProfileContract {
    
    /**
     * Интерфейс View - реализуется ProfileFragment
     */
    interface View {
        /**
         * Показать индикатор загрузки профиля
         */
        void showProfileLoading();
        
        /**
         * Скрыть индикатор загрузки профиля
         */
        void hideProfileLoading();
        
        /**
         * Обновить UI с данными профиля
         */
        void displayProfile(UserProfile profile);
        
        /**
         * Показать ошибку загрузки профиля
         */
        void showProfileError(String error);
        
        /**
         * Показать сообщение
         */
        void showMessage(String message);
        
        /**
         * Открыть список друзей
         */
        void navigateToFriends(int userId, String userName);
        
        /**
         * Открыть фото профиля
         */
        void openProfilePhoto(String photoUrl, String userName);
        
        /**
         * Открыть экран создания поста
         */
        void openCreatePost(int ownerId);
        
        /**
         * Открыть чат с пользователем
         */
        void openChat(int userId, String userName);
        
        /**
         * Показать/скрыть кнопку создания поста
         */
        void setCreatePostButtonVisible(boolean visible);
        
        /**
         * Получить ID текущего пользователя
         */
        int getCurrentUserId();
        
        /**
         * Проверяет, является ли это чужой профиль
         */
        boolean isForeignProfile();
        
        /**
         * Получить контекст Activity
         */
        android.content.Context getContext();
    }
    
    /**
     * Интерфейс Presenter - управляет бизнес-логикой
     */
    interface Presenter {
        /**
         * Привязать View к Presenter
         */
        void attachView(View view);
        
        /**
         * Отвязать View (очистка ресурсов)
         */
        void detachView();
        
        /**
         * Загрузить профиль пользователя
         */
        void loadProfile();
        
        /**
         * Обновить профиль (pull-to-refresh)
         */
        void refreshProfile();
        
        /**
         * Обработать клик по аватару
         */
        void onAvatarClick();
        
        /**
         * Обработать клик по кнопке друзей
         */
        void onFriendsClick();
        
        /**
         * Обработать клик по кнопке создания поста
         */
        void onCreatePostClick();
        
        /**
         * Обработать клик по деталям профиля
         */
        void onDetailsClick();
        
        /**
         * Обработать клик по кнопке сообщения
         */
        void onMessageButtonClick();
        
        /**
         * Обработать клик по кнопке добавления/удаления из друзей
         */
        void onFriendButtonClick(boolean isFriend);
        
        /**
         * Обработать клик по кнопке редактирования профиля
         */
        void onEditProfileClick();
        
        /**
         * Получить текущий профиль
         */
        UserProfile getCurrentProfile();
    }
    
    /**
     * Callback для загрузки профиля
     */
    interface ProfileLoadCallback {
        void onProfileLoaded(UserProfile profile);
        void onProfileError(String error);
    }
}
