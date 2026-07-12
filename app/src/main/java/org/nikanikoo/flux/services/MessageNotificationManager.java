package org.nikanikoo.flux.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.custom.CircularImageTransformation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageNotificationManager {
    private static final String TAG = "MsgNotifMgr";
    private static final String CHANNEL_ID = "messages_channel";
    private static final String CHANNEL_NAME = "Сообщения";
    private static final String GROUP_KEY = "messages_group";
    private static MessageNotificationManager instance;
    
    private final Context context;
    private final NotificationManager notificationManager;
    private final OpenVKApi api;
    
    // Переменная для отслеживания текущего открытого чата
    private static int currentOpenChatPeerId = -1;
    
    // Метод для установки текущего открытого чата
    public static void setCurrentOpenChat(int peerId) {
        currentOpenChatPeerId = peerId;
        Log.d(TAG, "Current open chat set to: " + peerId);
    }
    
    // Метод для очистки текущего открытого чата
    public static void clearCurrentOpenChat() {
        Log.d(TAG, "Current open chat cleared (was: " + currentOpenChatPeerId + ")");
        currentOpenChatPeerId = -1;
    }
    
    // Метод для получения текущего открытого чата
    public static int getCurrentOpenChat() {
        return currentOpenChatPeerId;
    }

    private static final int MAX_USER_INFO_CACHE = 200;
    private static final long USER_INFO_TTL_MS = 30 * 60 * 1000; // 30 минут

    private final Map<Integer, CachedUserInfo> userInfoCache = Collections.synchronizedMap(
        new LinkedHashMap<Integer, CachedUserInfo>(MAX_USER_INFO_CACHE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, CachedUserInfo> eldest) {
                return size() > MAX_USER_INFO_CACHE ||
                       (System.currentTimeMillis() - eldest.getValue().timestamp) > USER_INFO_TTL_MS;
            }
        }
    );

    private static class CachedUserInfo {
        final UserInfo info;
        final long timestamp;

        CachedUserInfo(UserInfo info) {
            this.info = info;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private final java.util.Set<Target> activeTargets = Collections.synchronizedSet(new java.util.HashSet<>());
    private final Map<Integer, List<String>> pendingMessages = new ConcurrentHashMap<>();
    
    // Кеш для отслеживания уже обработанных сообщений (messageId -> timestamp)
    private final Map<Integer, Long> processedMessages = new ConcurrentHashMap<>();
    
    // Максимальный размер кеша обработанных сообщений
    private static final int MAX_PROCESSED_MESSAGES = 1000;
    
    // Класс для хранения информации о пользователе
    private static class UserInfo {
        String firstName;
        String lastName;
        String photoUrl;
        Bitmap photoBitmap;
        
        UserInfo(String firstName, String lastName, String photoUrl) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.photoUrl = photoUrl;
        }
        
        String getFullName() {
            return firstName + " " + lastName;
        }
    }
    
    private MessageNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.api = OpenVKApi.getInstance(context);
        createNotificationChannel();
    }
    
    public static synchronized MessageNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new MessageNotificationManager(context);
        }
        return instance;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о новых сообщениях");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.enableLights(true);
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    public void showMessageNotification(int messageId, int fromId, int peerId, String messageText, long timestamp) {
        Log.d(TAG, "showMessageNotification called - messageId: " + messageId + ", fromId: " + fromId + 
              ", peerId: " + peerId + ", timestamp: " + timestamp + ", text: " + messageText);
        
        // Проверяем, не обрабатывали ли мы уже это сообщение
        if (processedMessages.containsKey(messageId)) {
            Long existingTimestamp = processedMessages.get(messageId);
            if (existingTimestamp != null && existingTimestamp.equals(timestamp)) {
                Log.d(TAG, "Message " + messageId + " with timestamp " + timestamp + " already processed, skipping");
                return;
            }
        }
        
        // Добавляем сообщение в кеш обработанных
        processedMessages.put(messageId, timestamp);
        
        // Очищаем кеш если он стал слишком большим
        if (processedMessages.size() > MAX_PROCESSED_MESSAGES) {
            // Удаляем 100 самых старых записей
            java.util.List<java.util.Map.Entry<Integer, Long>> entries = 
                new java.util.ArrayList<>(processedMessages.entrySet());
            
            // Сортируем по timestamp (значению)
            java.util.Collections.sort(entries, new java.util.Comparator<java.util.Map.Entry<Integer, Long>>() {
                @Override
                public int compare(java.util.Map.Entry<Integer, Long> o1, java.util.Map.Entry<Integer, Long> o2) {
                    return o1.getValue().compareTo(o2.getValue());
                }
            });
            
            // Удаляем первые 100 (самые старые)
            int toRemove = Math.min(100, entries.size());
            for (int i = 0; i < toRemove; i++) {
                processedMessages.remove(entries.get(i).getKey());
            }
        }
        
        // Проверяем, не открыт ли чат с этим пользователем
        Log.d(TAG, "Current open chat peerId: " + currentOpenChatPeerId + ", message peerId: " + peerId);
        if (currentOpenChatPeerId == peerId) {
            Log.d(TAG, "Chat with peerId " + peerId + " is currently open, skipping notification");
            return;
        }
        
        Log.d(TAG, "Proceeding to show notification for peerId " + peerId);
        
        // Получаем информацию о пользователе и показываем уведомление
        getUserInfo(fromId, new UserInfoCallback() {
            @Override
            public void onUserInfoReceived(UserInfo userInfo) {
                if (userInfo != null) {
                    Log.d(TAG, "Got user info for " + fromId + ": " + userInfo.getFullName());
                    showNotificationWithUserInfo(fromId, peerId, userInfo, messageText);
                } else {
                    Log.d(TAG, "No user info for " + fromId + ", showing simple notification");
                    // Fallback - показываем уведомление без аватарки
                    showSimpleNotification(fromId, peerId, "Пользователь " + fromId, messageText);
                }
            }
        });
    }
    
    // Оставляем старый метод для совместимости
    public void showMessageNotification(int fromId, int peerId, String messageText) {
        // Генерируем фиктивный messageId и используем текущее время
        int fakeMessageId = (fromId + peerId + messageText.hashCode()) & 0x7FFFFFFF;
        showMessageNotification(fakeMessageId, fromId, peerId, messageText, System.currentTimeMillis());
    }
    
    private void getUserInfo(int userId, UserInfoCallback callback) {
        Log.d(TAG, "getUserInfo called for userId: " + userId);

        synchronized (userInfoCache) {
            CachedUserInfo cached = userInfoCache.get(userId);
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < USER_INFO_TTL_MS) {
                Log.d(TAG, "Found valid user info in cache for " + userId);
                callback.onUserInfoReceived(cached.info);
                return;
            } else if (cached != null) {
                // Запись устарела, удаляем
                userInfoCache.remove(userId);
                Log.d(TAG, "Expired user info removed from cache for " + userId);
            }
        }
        
        Log.d(TAG, "Requesting user info from API for " + userId);
        
        // Запрашиваем информацию через API
        Map<String, String> params = new HashMap<>();
        params.put("user_ids", String.valueOf(userId));
        params.put("fields", "photo_100");
        
        api.callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                Log.d(TAG, "API response for user " + userId + ": " + response.toString());
                try {
                    org.json.JSONArray users = response.getJSONArray("response");
                    if (users.length() > 0) {
                        org.json.JSONObject user = users.getJSONObject(0);
                        String firstName = user.getString("first_name");
                        String lastName = user.getString("last_name");
                        String photoUrl = user.optString("photo_100", "");
                        
                        Log.d(TAG, "Parsed user info - name: " + firstName + " " + lastName + ", photo: " + photoUrl);
                        
                        UserInfo userInfo = new UserInfo(firstName, lastName, photoUrl);
                        userInfoCache.put(userId, new CachedUserInfo(userInfo));
                        
                        // Загружаем аватарку
                        if (!photoUrl.isEmpty()) {
                            loadUserAvatar(userInfo, callback);
                        } else {
                            callback.onUserInfoReceived(userInfo);
                        }
                    } else {
                        Log.w(TAG, "No users in API response");
                        callback.onUserInfoReceived(null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing user info: " + e.getMessage());
                    // Логируем полный ответ для отладки
                    Log.e(TAG, "Full response: " + response.toString());
                    callback.onUserInfoReceived(null);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error getting user info: " + error);
                callback.onUserInfoReceived(null);
            }
        });
    }
    
    private void loadUserAvatar(UserInfo userInfo, UserInfoCallback callback) {
        if (userInfo.photoUrl.isEmpty()) {
            callback.onUserInfoReceived(userInfo);
            return;
        }
        
        try {
            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    activeTargets.remove(this);
                    userInfo.photoBitmap = bitmap;
                    callback.onUserInfoReceived(userInfo);
                }

                @Override
                public void onBitmapFailed(Exception e, android.graphics.drawable.Drawable errorDrawable) {
                    activeTargets.remove(this);
                    Log.e(TAG, "Failed to load avatar: " + e.getMessage());
                    callback.onUserInfoReceived(userInfo);
                }

                @Override
                public void onPrepareLoad(android.graphics.drawable.Drawable placeHolderDrawable) {
                    // Ничего не делаем
                }
            };
            
            activeTargets.add(target);
            
            Picasso.get()
                    .load(userInfo.photoUrl)
                    .resize(128, 128)
                    .centerCrop()
                    .transform(new CircularImageTransformation())
                    .into(target);
        } catch (Exception e) {
            Log.e(TAG, "Error loading avatar: " + e.getMessage());
            callback.onUserInfoReceived(userInfo);
        }
    }
    
    private void showNotificationWithUserInfo(int fromId, int peerId, UserInfo userInfo, String messageText) {
        Log.d(TAG, "showNotificationWithUserInfo called - fromId: " + fromId + ", peerId: " + peerId + 
              ", userInfo: " + userInfo.getFullName() + ", messageText: " + messageText);
        
        // Добавляем новое сообщение в очередь
        List<String> messages = pendingMessages.get(fromId);
        if (messages == null) {
            messages = new ArrayList<>();
            pendingMessages.put(fromId, messages);
        }
        messages.add(messageText);
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("open_chat", true);
        intent.putExtra("peer_id", peerId);
        intent.putExtra("from_id", fromId);
        intent.putExtra("peer_name", userInfo.getFullName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                fromId, // Используем fromId для уникальности
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : 
                    PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        // Используем системную иконку сообщений как fallback
        int iconRes;
        try {
            iconRes = R.drawable.ic_chat_bubble;
        } catch (Exception e) {
            Log.w(TAG, "ic_messages not found, using system icon");
            iconRes = android.R.drawable.ic_dialog_email;
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(userInfo.getFullName())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(GROUP_KEY)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
        
        // Добавляем аватарку если есть
        if (userInfo.photoBitmap != null) {
            builder.setLargeIcon(userInfo.photoBitmap);
        }
        
        // Если сообщений несколько, показываем их список
        if (messages.size() == 1) {
            String displayText = messageText.length() > 100 ? 
                    messageText.substring(0, 100) + "..." : messageText;
            builder.setContentText(displayText)
                   .setStyle(new NotificationCompat.BigTextStyle().bigText(messageText));
        } else {
            // Множественные сообщения - используем InboxStyle
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(userInfo.getFullName());
            inboxStyle.setSummaryText(messages.size() + " новых сообщений");
            
            // Показываем последние 5 сообщений
            int startIndex = Math.max(0, messages.size() - 5);
            for (int i = startIndex; i < messages.size(); i++) {
                String msg = messages.get(i);
                if (msg.length() > 50) {
                    msg = msg.substring(0, 50) + "...";
                }
                inboxStyle.addLine(msg);
            }
            
            builder.setStyle(inboxStyle)
                   .setContentText(messages.size() + " новых сообщений")
                   .setNumber(messages.size());
        }
        
        Log.d(TAG, "Building notification with user info, messages count: " + messages.size());
        
        if (notificationManager != null) {
            notificationManager.notify(fromId, builder.build());
            Log.d(TAG, "Notification sent with user info, ID: " + fromId);
        } else {
            Log.e(TAG, "NotificationManager is null!");
        }
    }
    
    private void showSimpleNotification(int fromId, int peerId, String senderName, String messageText) {
        Log.d(TAG, "showSimpleNotification called - fromId: " + fromId + ", peerId: " + peerId + 
              ", senderName: " + senderName + ", messageText: " + messageText);
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("open_chat", true);
        intent.putExtra("peer_id", peerId);
        intent.putExtra("from_id", fromId);
        intent.putExtra("peer_name", senderName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                fromId, // Используем fromId для уникальности
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : 
                    PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        String displayText = messageText.length() > 100 ? 
                messageText.substring(0, 100) + "..." : messageText;
        
        // Используем системную иконку сообщений как fallback
        int iconRes;
        try {
            iconRes = R.drawable.ic_chat_bubble;
        } catch (Exception e) {
            Log.w(TAG, "ic_messages not found, using system icon");
            iconRes = android.R.drawable.ic_dialog_email;
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(senderName)
                .setContentText(displayText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(GROUP_KEY)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
        
        Log.d(TAG, "Building notification with icon: " + iconRes);
        
        if (notificationManager != null) {
            notificationManager.notify(fromId, builder.build());
            Log.d(TAG, "Notification sent with ID: " + fromId);
        } else {
            Log.e(TAG, "NotificationManager is null!");
        }
    }
    
    public void cancelNotification(int fromId) {
        // Очищаем очередь сообщений для пользователя
        pendingMessages.remove(fromId);
        
        if (notificationManager != null) {
            notificationManager.cancel(fromId);
        }
    }
    
    public void cancelAllNotifications() {
        // Очищаем все очереди сообщений
        pendingMessages.clear();
        
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }
    
    // Интерфейс для колбэка получения информации о пользователе
    private interface UserInfoCallback {
        void onUserInfoReceived(UserInfo userInfo);
    }
    
    // Метод для очистки кеша (можно вызывать при логауте)
    public void clearCache() {
        userInfoCache.clear();
        pendingMessages.clear();
        processedMessages.clear();
        Log.d(TAG, "All caches cleared");
    }
}