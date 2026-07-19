package org.nikanikoo.flux.data.managers;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.data.models.Notification;
import org.nikanikoo.flux.utils.AsyncTaskHelper;
import org.nikanikoo.flux.utils.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationsManager extends BaseManager<NotificationsManager> {
    private static final String TAG = "NotificationsManager";

    private NotificationsManager(Context context) {
        super(context);
    }

    public static synchronized NotificationsManager getInstance(Context context) {
        return BaseManager.getInstance(NotificationsManager.class, context);
    }

    public interface NotificationsCallback {
        void onSuccess(List<Notification> notifications);
        void onError(String error);
    }

    public interface MarkAsReadCallback {
        void onSuccess();
        void onError(String error);
    }

    // Получение новых уведомлений
    public void getNotifications(int count, int startFrom, NotificationsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(count));
        if (startFrom > 0) {
            params.put("offset", String.valueOf(startFrom));
        }
        params.put("filters", "wall,mentions,comments,likes,reposts,followers");

        api.callMethod("notifications.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse notifications in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    return parseNotificationsResponse(response, false);
                }, new AsyncTaskHelper.AsyncCallback<List<Notification>>() {
                    @Override
                    public void onSuccess(List<Notification> notifications) {
                        callback.onSuccess(notifications);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Error parsing notifications: " + error);
                        callback.onError("Не удалось загрузить уведомления");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading notifications: " + error);
                callback.onError("Не удалось загрузить уведомления");
            }
        });
    }

    // Получение архивных уведомлений
    public void getArchivedNotifications(int count, int startFrom, NotificationsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(count));
        if (startFrom > 0) {
            params.put("offset", String.valueOf(startFrom));
        }
        params.put("archived", "1"); // Получаем архивные уведомления
        params.put("filters", "wall,mentions,comments,likes,reposts,followers");

        api.callMethod("notifications.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse archived notifications in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    return parseNotificationsResponse(response, true);
                }, new AsyncTaskHelper.AsyncCallback<List<Notification>>() {
                    @Override
                    public void onSuccess(List<Notification> notifications) {
                        callback.onSuccess(notifications);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Error parsing archived notifications: " + error);
                        callback.onError("Не удалось загрузить архивные уведомления");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading archived notifications: " + error);
                callback.onError("Не удалось загрузить архивные уведомления");
            }
        });
    }
    
    // Проверка наличия новых уведомлений
    public void checkForNewNotifications(NotificationsCallback callback) {
        getNotifications(10, 0, new NotificationsCallback() {
            @Override
            public void onSuccess(List<Notification> notifications) {
                callback.onSuccess(notifications); // Возвращаем все уведомления, статус прочитано/непрочитано определяется в парсинге
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Пометить уведомления как прочитанные
    public void markAsRead(MarkAsReadCallback callback) {
        api.callMethod("notifications.markAsViewed", new HashMap<>(), new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error marking as read: " + error);
                callback.onError("Не удалось пометить как прочитанное");
            }
        });
    }

    // Форматирование даты
    private String formatDate(long timestamp) {
        Date date = new Date(timestamp * 1000);
        Date now = new Date();
        
        long diff = now.getTime() - date.getTime();
        long diffMinutes = diff / (60 * 1000);
        long diffHours = diff / (60 * 60 * 1000);
        long diffDays = diff / (24 * 60 * 60 * 1000);
        
        if (diffMinutes < 1) {
            return "только что";
        } else if (diffMinutes < 60) {
            return diffMinutes + " мин назад";
        } else if (diffHours < 24) {
            return diffHours + " ч назад";
        } else if (diffDays < 7) {
            return diffDays + " дн назад";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            return sdf.format(date);
        }
    }
    
    // Обработка вложений в комментариях
    private String processCommentAttachments(JSONObject feedbackObj, String originalText) {
        try {
            if (feedbackObj.has("attachments") && !feedbackObj.isNull("attachments")) {
                JSONArray attachments = feedbackObj.getJSONArray("attachments");
                if (attachments.length() > 0) {
                    StringBuilder attachmentText = new StringBuilder();
                    for (int k = 0; k < attachments.length(); k++) {
                        JSONObject attachment = attachments.getJSONObject(k);
                        String attachmentType = attachment.optString("type", "");
                        
                        switch (attachmentType) {
                            case "photo":
                                attachmentText.append("[Фотография]");
                                break;
                            case "video":
                                attachmentText.append("[Видео]");
                                break;
                            case "audio":
                                attachmentText.append("[Аудио]");
                                break;
                            case "doc":
                                attachmentText.append("[Документ]");
                                break;
                            case "link":
                                attachmentText.append("[Ссылка]");
                                break;
                            case "wall":
                                attachmentText.append("[Запись]");
                                break;
                            default:
                                attachmentText.append("[Вложение]");
                                break;
                        }
                        if (k < attachments.length() - 1) {
                            attachmentText.append(" ");
                        }
                    }
                    
                    // Добавляем информацию о вложениях к тексту
                    if (originalText == null || originalText.isEmpty()) {
                        return attachmentText.toString();
                    } else {
                        return originalText + " " + attachmentText.toString();
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error processing attachments: " + e.getMessage(), e);
        }
        
        return originalText;
    }
    
    // Интерфейс для получения данных комментария
    public interface CommentDataCallback {
        void onSuccess(String commentText, String authorName, String authorPhoto, int authorId);
        void onError(String error);
    }
    
    // Загрузка данных комментария для уведомления comment_post и comment_photo
    public void loadCommentDataForNotification(Notification notification, Runnable onComplete) {
        if ((!"comment_post".equals(notification.getType()) && !"comment_photo".equals(notification.getType())) || notification.isCommentDataLoaded()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        if (notification.getPostId() == 0 || notification.getPostOwnerId() == 0) {
            Logger.d(TAG, "Missing post data for " + notification.getType() + " notification");
            notification.setCommentDataLoaded(true); // Помечаем как загруженное, чтобы не пытаться снова
            if (onComplete != null) onComplete.run();
            return;
        }
        
        // Помечаем как "в процессе загрузки", чтобы избежать повторных запросов
        notification.setCommentDataLoaded(true);
        
        Logger.d(TAG, "Loading comment data for notification ID: " + notification.getId());
        
        long notificationDate = 0;
        try {
            // Извлекаем timestamp из ID уведомления
            // Формат: type_timestamp_index или archived_type_timestamp_index
            // Проблема: тип может содержать подчеркивания (например, comment_post)
            String notificationId = notification.getId();
            
            if (notificationId.startsWith("archived_")) {
                // Для архивных: archived_type_timestamp_index
                // Убираем префикс "archived_" и ищем последние два числа
                String withoutPrefix = notificationId.substring("archived_".length());
                String[] parts = withoutPrefix.split("_");
                if (parts.length >= 2) {
                    // Берем предпоследний элемент как timestamp
                    notificationDate = Long.parseLong(parts[parts.length - 2]);
                }
            } else {
                // Для обычных: type_timestamp_index
                String[] parts = notificationId.split("_");
                if (parts.length >= 2) {
                    // Берем предпоследний элемент как timestamp
                    notificationDate = Long.parseLong(parts[parts.length - 2]);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing notification date from ID '" + notification.getId() + "': " + e.getMessage(), e);
            if (onComplete != null) onComplete.run();
            return;
        }
        
        if (notificationDate == 0) {
            Logger.d(TAG, "Invalid notification date (0) for ID: " + notification.getId());
            if (onComplete != null) onComplete.run();
            return;
        }
        
        Logger.d(TAG, "Parsed notification date: " + notificationDate + " for ID: " + notification.getId());
        Logger.d(TAG, "ID parts analysis - full ID: '" + notification.getId() + "'");
        
        getCommentData(notification.getPostOwnerId(), notification.getPostId(), notificationDate, 
            new CommentDataCallback() {
                @Override
                public void onSuccess(String commentText, String authorName, String authorPhoto, int authorId) {
                    notification.updateCommentData(commentText, authorName, authorPhoto, authorId);
                    Logger.d(TAG, "Updated comment data for notification: " + commentText);
                    Logger.d(TAG, "Updated fromId from " + notification.getFromId() + " to " + authorId);
                    Logger.d(TAG, "Updated fromName to: " + authorName);
                    if (onComplete != null) onComplete.run();
                }
                
                @Override
                public void onError(String error) {
                    Logger.e(TAG, "Failed to load comment data: " + error);
                    // Данные уже помечены как загруженные выше
                    if (onComplete != null) onComplete.run();
                }
            });
    }
    
    // Получение данных комментария для уведомления comment_post
    public void getCommentData(int postOwnerId, int postId, long commentDate, CommentDataCallback callback) {
        Logger.d(TAG, "Getting comment data for post " + postOwnerId + "_" + postId + " at " + commentDate);
        
        CommentsManager commentsManager = CommentsManager.getInstance(context);
        commentsManager.loadComments(postOwnerId, postId, new CommentsManager.CommentsCallback() {
            @Override
            public void onSuccess(List<Comment> comments) {
                Logger.d(TAG, "Loaded " + comments.size() + " comments for post " + postOwnerId + "_" + postId);
                
                // Ищем комментарий по времени (с допуском ±5 секунд)
                Comment targetComment = null;
                long minTimeDiff = Long.MAX_VALUE;
                
                for (Comment comment : comments) {
                    long timeDiff = Math.abs(comment.getDate() - commentDate);
                    Logger.d(TAG, "Comment " + comment.getId() + " date=" + comment.getDate() + " diff=" + timeDiff);
                    
                    if (timeDiff <= 5 && timeDiff < minTimeDiff) { // Допуск ±5 секунд
                        targetComment = comment;
                        minTimeDiff = timeDiff;
                    }
                }
                
                if (targetComment != null) {
                    Logger.d(TAG, "Found matching comment: " + targetComment.getText());
                    callback.onSuccess(
                        targetComment.getText(), 
                        targetComment.getAuthorName(), 
                        targetComment.getAuthorAvatarUrl(),
                        targetComment.getFromId()
                    );
                } else {
                    Logger.d(TAG, "No matching comment found for date " + commentDate);
                    callback.onError("Комментарий не найден");
                }
            }
            
            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading comments: " + error);
                callback.onError("Не удалось загрузить комментарии");
            }
        });
    }
    
    // Вспомогательный метод для обработки sent_gift уведомлений
    private void processSentGiftNotification(JSONObject item, Notification notification, JSONArray profiles) {
        try {
            if (item.has("parent") && !item.isNull("parent")) {
                JSONObject parentObj = item.getJSONObject("parent");
                
                // Для sent_gift отправитель находится в parent
                int fromId = parentObj.optInt("id", 0);
                notification.setFromId(fromId);
                
                // Ищем профиль отправителя в массиве profiles
                if (fromId != 0 && profiles != null) {
                    for (int j = 0; j < profiles.length(); j++) {
                        JSONObject profile = profiles.getJSONObject(j);
                        int profileId = profile.has("uid") ? profile.getInt("uid") : profile.getInt("id");
                        if (profileId == Math.abs(fromId)) {
                            String name = profile.getString("first_name") + " " + profile.getString("last_name");
                            String photo = profile.optString("photo", "");
                            notification.setFromName(name);
                            notification.setFromPhoto(photo);
                            break;
                        }
                    }
                }
                
                // Если имя не найдено в profiles, извлекаем из parent
                if (notification.getFromName() == null || notification.getFromName().isEmpty()) {
                    String firstName = parentObj.optString("first_name", "");
                    String lastName = parentObj.optString("last_name", "");
                    if (!firstName.isEmpty() || !lastName.isEmpty()) {
                        notification.setFromName(firstName + " " + lastName);
                    } else {
                        notification.setFromName("Пользователь");
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error processing sent_gift: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse notifications response in background thread
     * @param response JSON response from API
     * @param isArchived Whether these are archived notifications
     * @return List of parsed notifications
     */
    private List<Notification> parseNotificationsResponse(JSONObject response, boolean isArchived) throws Exception {
        Logger.d(TAG, "Parsing notifications response (archived=" + isArchived + ")");
        
        JSONObject responseObj = response.getJSONObject("response");
        JSONArray items = responseObj.getJSONArray("items");
        JSONArray profiles = responseObj.optJSONArray("profiles");
        JSONArray groups = responseObj.optJSONArray("groups");
        long lastViewed = responseObj.optLong("last_viewed", 0);

        Logger.d(TAG, "Found " + items.length() + " notifications, last_viewed: " + lastViewed);

        List<Notification> notifications = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                
                String type = item.getString("type");
                String date = formatDate(item.getLong("date"));
                String text = "";
                String feedback = "";
                String parent = "";
                
                // Обработка разных типов уведомлений
                if (item.has("feedback") && !item.isNull("feedback")) {
                    JSONObject feedbackObj = item.getJSONObject("feedback");
                    feedback = feedbackObj.optString("text", "");
                    
                    // Для mention, comment_post и comment_photo извлекаем текст комментария/упоминания
                    if ("mention".equals(type)) {
                        text = feedback;
                    } else if ("comment_post".equals(type) || "comment_photo".equals(type)) {
                        // Для comment_post и comment_photo обрабатываем текст и вложения
                        text = processCommentAttachments(feedbackObj, feedback);
                    } else {
                        text = feedback;
                    }
                }
                
                if (item.has("parent") && !item.isNull("parent")) {
                    JSONObject parentObj = item.getJSONObject("parent");
                    
                    // Для sent_gift извлекаем информацию о подарке
                    if ("sent_gift".equals(type)) {
                        // Для sent_gift parent содержит информацию о пользователе
                        String firstName = parentObj.optString("first_name", "");
                        String lastName = parentObj.optString("last_name", "");
                        if (!firstName.isEmpty() || !lastName.isEmpty()) {
                            text = "Подарок от " + firstName + " " + lastName;
                        } else {
                            text = "Подарок";
                        }
                    } else if ("comment_photo".equals(type)) {
                        // Для comment_photo parent содержит информацию о фотографии
                        text = text.isEmpty() ? "[Комментарий к фотографии]" : text;
                    } else {
                        parent = parentObj.optString("text", "");
                        
                        // Для comment_post НЕ показываем контекст поста, только если совсем нет текста комментария
                        if ("comment_post".equals(type) && (text == null || text.isEmpty())) {
                            text = "[Комментарий без текста]";
                        } else if (text == null || text.isEmpty()) {
                            text = parent;
                        }
                    }
                }
                
                // Генерируем уникальный ID для уведомления
                String notificationId = (isArchived ? "archived_" : "") + type + "_" + item.getLong("date") + "_" + i;
                Logger.d(TAG, "Created notification ID: " + notificationId + " for type: " + type);
                
                Notification notification = new Notification(
                    notificationId, type, date, text, feedback, parent
                );
                
                // Определяем статус прочитано/непрочитано
                if (isArchived) {
                    // Архивные уведомления всегда должны быть прочитанными
                    notification.setRead(true);
                } else {
                    long notificationDate = item.getLong("date");
                    boolean isRead = notificationDate <= lastViewed;
                    notification.setRead(isRead);
                }

                // Найдем информацию об отправителе
                int fromId = 0;
                if ("mention".equals(type)) {
                    // Для mention отправитель находится в feedback
                    if (item.has("feedback") && !item.isNull("feedback")) {
                        JSONObject feedbackObj = item.getJSONObject("feedback");
                        fromId = feedbackObj.optInt("from_id", 0);
                    }
                } else if ("comment_post".equals(type) || "comment_photo".equals(type)) {
                    // Для comment_post и comment_photo отправитель находится в feedback
                    if (item.has("feedback") && !item.isNull("feedback")) {
                        JSONObject feedbackObj = item.getJSONObject("feedback");
                        if (feedbackObj.has("id")) {
                            fromId = feedbackObj.getInt("id");
                        }
                    }
                } else {
                    // Для других типов ищем в feedback или parent
                    if (item.has("feedback") && !item.isNull("feedback")) {
                        JSONObject feedbackObj = item.getJSONObject("feedback");
                        if (feedbackObj.has("items")) {
                            JSONArray feedbackItems = feedbackObj.getJSONArray("items");
                            if (feedbackItems.length() > 0) {
                                JSONObject feedbackItemsObj = feedbackItems.getJSONObject(0);
                                if (feedbackItemsObj.has("from_id")) {
                                    fromId = feedbackItemsObj.getInt("from_id");
                                } else if (feedbackItemsObj.has("id")) {
                                    fromId = feedbackItemsObj.getInt("id");
                                }
                            }
                        }
                    }
                    
                    if (fromId == 0 && item.has("parent") && !item.isNull("parent")) {
                        JSONObject parentObj = item.getJSONObject("parent");
                        if (parentObj.has("from_id")) {
                            fromId = parentObj.getInt("from_id");
                        }
                    }
                }
                
                notification.setFromId(fromId);
                
                // Ищем профиль отправителя
                if (fromId != 0 && profiles != null) {
                    for (int j = 0; j < profiles.length(); j++) {
                        JSONObject profile = profiles.getJSONObject(j);
                        // Используем uid вместо id для профилей
                        int profileId = profile.has("uid") ? profile.getInt("uid") : profile.getInt("id");
                        if (profileId == Math.abs(fromId)) {
                            String name = profile.getString("first_name") + " " + profile.getString("last_name");
                            String photo = profile.optString("photo", "");
                            notification.setFromName(name);
                            notification.setFromPhoto(photo);
                            break;
                        }
                    }
                }
                
                // Если имя не найдено, используем заглушку
                if (notification.getFromName() == null || notification.getFromName().isEmpty()) {
                    notification.setFromName("Пользователь");
                }
                
                // Извлекаем данные поста/объекта для разных типов уведомлений
                try {
                    if ("mention".equals(type)) {
                        // Для mention данные поста находятся в feedback
                        if (item.has("feedback") && !item.isNull("feedback")) {
                            JSONObject feedbackObj = item.getJSONObject("feedback");
                            int postId = feedbackObj.optInt("id", 0);
                            int postOwnerId = feedbackObj.optInt("to_id", 0);
                            if (postOwnerId == 0) {
                                postOwnerId = feedbackObj.optInt("from_id", 0);
                            }
                            
                            notification.setPostId(postId);
                            notification.setPostOwnerId(postOwnerId);
                            
                            Logger.d(TAG, "mention notification - postId=" + postId + " postOwnerId=" + postOwnerId);
                        }
                    } else if (item.has("parent") && !item.isNull("parent")) {
                        JSONObject parentObj = item.getJSONObject("parent");
                        
                        // Для уведомлений связанных с постами
                        if ("comment_post".equals(type) || "comment_photo".equals(type) || 
                            "like_post".equals(type) || "copy_post".equals(type) || 
                            "wall".equals(type)) {
                            
                            int postId = parentObj.optInt("id", 0);
                            int postOwnerId = parentObj.optInt("owner_id", 0);
                            if (postOwnerId == 0) {
                                postOwnerId = parentObj.optInt("to_id", 0); // Альтернативное поле
                            }
                            if (postOwnerId == 0) {
                                postOwnerId = parentObj.optInt("from_id", 0); // Еще одно альтернативное поле
                            }
                            
                            notification.setPostId(postId);
                            notification.setPostOwnerId(postOwnerId);
                            
                            Logger.d(TAG, type + " notification - postId=" + postId + " postOwnerId=" + postOwnerId);
                        }
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error extracting post data: " + e.getMessage(), e);
                }
                
                // Специальная обработка для sent_gift
                if ("sent_gift".equals(type)) {
                    processSentGiftNotification(item, notification, profiles);
                }

                notifications.add(notification);
                Logger.d(TAG, "Added notification: " + type + " from " + notification.getFromName() + " text: " + text);
                
            } catch (Exception e) {
                Logger.e(TAG, "Error processing notification " + i + ": " + e.getMessage(), e);
            }
        }

        Logger.d(TAG, "Successfully parsed " + notifications.size() + " notifications");
        return notifications;
    }
}