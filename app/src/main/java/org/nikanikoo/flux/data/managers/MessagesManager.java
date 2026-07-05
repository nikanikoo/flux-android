package org.nikanikoo.flux.data.managers;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.data.models.Message;
import org.nikanikoo.flux.utils.AsyncTaskHelper;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessagesManager extends BaseManager<MessagesManager> {
    private static final String TAG = "MessagesManager";

    private MessagesManager(Context context) {
        super(context);
    }

    public static synchronized MessagesManager getInstance(Context context) {
        return BaseManager.getInstance(MessagesManager.class, context);
    }

    public interface ConversationsCallback {
        void onSuccess(List<Conversation> conversations);
        void onError(String error);
    }

    public interface MessagesCallback {
        void onSuccess(List<Message> messages);
        void onError(String error);
    }

    public interface SendMessageCallback {
        void onSuccess(int messageId);
        void onError(String error);
    }
    
    public interface UserInfoCallback {
        void onSuccess(String userName, String userPhoto);
        void onError(String error);
    }

    // Получение списка диалогов
    public void getConversations(int count, int offset, ConversationsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(count));
        params.put("offset", String.valueOf(offset));
        params.put("extended", "1");
        params.put("fields", "photo_50,online,verified");

        api.callMethod("messages.getConversations", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse conversations in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    return parseConversationsResponse(response);
                }, new AsyncTaskHelper.AsyncCallback<List<Conversation>>() {
                    @Override
                    public void onSuccess(List<Conversation> conversations) {
                        callback.onSuccess(conversations);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Ошибка парсинга диалогов: " + error);
                        callback.onError("Не удалось загрузить диалоги");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка получения диалогов: " + error);
                callback.onError(error);
            }
        });
    }

    // Получение истории сообщений
    public void getHistory(int peerId, int count, int offset, MessagesCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("count", String.valueOf(count));
        params.put("offset", String.valueOf(offset));
        params.put("extended", "1");
        params.put("fields", "photo_50,verified");

        api.callMethod("messages.getHistory", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse messages in background thread to avoid UI blocking
                final int finalPeerId = peerId;
                AsyncTaskHelper.executeAsync(() -> {
                    return parseMessagesResponse(response, finalPeerId);
                }, new AsyncTaskHelper.AsyncCallback<List<Message>>() {
                    @Override
                    public void onSuccess(List<Message> messages) {
                        callback.onSuccess(messages);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Ошибка парсинга сообщений: " + error);
                        callback.onError("Не удалось загрузить сообщения");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading messages: " + error);
                callback.onError("Не удалось загрузить сообщения");
            }
        });
    }
    
    /**
     * Parse messages response in background thread
     */
    private List<Message> parseMessagesResponse(JSONObject response, int peerId) throws Exception {
        Logger.d(TAG, "Ответ getHistory: " + response.toString());
        
        JSONObject responseObj = response.getJSONObject("response");
        JSONArray items = responseObj.getJSONArray("items");
        JSONArray profiles = responseObj.optJSONArray("profiles");

        Logger.d(TAG, "Найдено " + items.length() + " сообщений");

        List<Message> messages = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                
                Logger.d(TAG, "Обрабатываем сообщение " + i + ": " + item.toString());
            
                int id = item.getInt("id");
                // Используем peerId из параметров, так как не все сообщения содержат peer_id
                int messagePeerId = item.optInt("peer_id", peerId);
                int fromId = item.getInt("from_id");
                String text = item.optString("text", "");
                long date = item.getLong("date");
                boolean out = item.optInt("out", 0) == 1;
                boolean readState = item.optInt("read_state", 0) == 1;

                Message message = new Message(id, messagePeerId, fromId, text, date, out, readState);

                // Найдем информацию о пользователе
                if (profiles != null) {
                    for (int j = 0; j < profiles.length(); j++) {
                        JSONObject profile = profiles.getJSONObject(j);
                        if (profile.getInt("id") == Math.abs(fromId)) {
                            String userName = profile.getString("first_name") + " " + profile.getString("last_name");
                            String userPhoto = profile.optString("photo_50", "");
                            message.setUserName(userName);
                            message.setUserPhoto(userPhoto);
                            break;
                        }
                    }
                }

                messages.add(message);
                Logger.d(TAG, "Добавлено сообщение от " + fromId + ": " + text.substring(0, Math.min(30, text.length())));
                
            } catch (Exception e) {
                Logger.e(TAG, "Ошибка обработки сообщения " + i + ": " + e.getMessage(), e);
            }
        }

        Logger.d(TAG, "Успешно обработано " + messages.size() + " сообщений");
        return messages;
    }

    // Отправка сообщения
    public void sendMessage(int peerId, String message, SendMessageCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("message", message);
        params.put("random_id", String.valueOf(System.currentTimeMillis()));

        api.callMethod("messages.send", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    int messageId = response.getInt("response");
                    callback.onSuccess(messageId);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing send message response", e);
                    callback.onError("Не удалось отправить сообщение");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error sending message: " + error);
                callback.onError("Не удалось отправить сообщение");
            }
        });
    }

    // Отправка статуса тайпинга
    public void sendTypingActivity(int peerId, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("user_id", String.valueOf(peerId));
        params.put("type", "typing");

        api.callMethod("messages.setActivity", params, callback);
    }

    /**
     * Parse conversations response in background thread
     */
    private List<Conversation> parseConversationsResponse(JSONObject response) throws Exception {
        Logger.d(TAG, "Сырой ответ: " + response.toString());
        
        JSONObject responseObj = response.getJSONObject("response");
        JSONArray items = responseObj.getJSONArray("items");
        JSONArray profiles = responseObj.optJSONArray("profiles");
        JSONArray groups = responseObj.optJSONArray("groups");

        Logger.d(TAG, "Найдено " + items.length() + " диалогов");

        List<Conversation> conversations = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                JSONObject conversationObj = item.getJSONObject("conversation");
                JSONObject lastMessage = item.optJSONObject("last_message");

                JSONObject peer = conversationObj.getJSONObject("peer");
                int peerId = peer.getInt("id");
                String peerTypeStr = peer.getString("type");
                int localId = peer.getInt("local_id");
                
                // Преобразуем строковый тип в числовой
                int peerType = 0; // по умолчанию пользователь
                if ("user".equals(peerTypeStr)) {
                    peerType = 0;
                } else if ("chat".equals(peerTypeStr)) {
                    peerType = 2; // групповой чат
                } else if ("group".equals(peerTypeStr)) {
                    peerType = 1; // группа
                }

                Logger.d(TAG, "Обрабатываем диалог " + i + " - peerId: " + peerId + ", тип: " + peerTypeStr + " (" + peerType + ")");

                String title = "Неизвестный пользователь";
                String photo = "";
                boolean isOnline = false;
                boolean isVerified = false;

                if (peerType == 0) { // Пользователь
                    if (profiles != null) {
                        for (int j = 0; j < profiles.length(); j++) {
                            JSONObject profile = profiles.getJSONObject(j);
                            if (profile.getInt("id") == peerId) {
                                title = profile.getString("first_name") + " " + profile.getString("last_name");
                                photo = profile.optString("photo_50", "");
                                isOnline = profile.optInt("online", 0) == 1;
                                // verified может быть int 1/0 или boolean
                                if (profile.has("verified")) {
                                    Object verifiedObj = profile.opt("verified");
                                    if (verifiedObj instanceof Integer) {
                                        isVerified = (Integer) verifiedObj == 1;
                                    } else if (verifiedObj instanceof Boolean) {
                                        isVerified = (Boolean) verifiedObj;
                                    }
                                }
                                break;
                            }
                        }
                    }
                } else if (peerType == 1) { // Группа
                    if (groups != null) {
                        for (int j = 0; j < groups.length(); j++) {
                            JSONObject group = groups.getJSONObject(j);
                            if (group.getInt("id") == Math.abs(peerId)) {
                                title = group.getString("name");
                                photo = group.optString("photo_50", "");
                                // verified может быть int 1/0 или boolean
                                if (group.has("verified")) {
                                    Object verifiedObj = group.opt("verified");
                                    if (verifiedObj instanceof Integer) {
                                        isVerified = (Integer) verifiedObj == 1;
                                    } else if (verifiedObj instanceof Boolean) {
                                        isVerified = (Boolean) verifiedObj;
                                    }
                                }
                                break;
                            }
                        }
                    }
                } else if (peerType == 2) { // Групповой чат
                    // Для групповых чатов title может быть в самом объекте conversation
                    title = conversationObj.optString("chat_settings", "Групповой чат");
                    if (conversationObj.has("chat_settings")) {
                        JSONObject chatSettings = conversationObj.optJSONObject("chat_settings");
                        if (chatSettings != null) {
                            title = chatSettings.optString("title", "Групповой чат");
                            photo = chatSettings.optString("photo", "");
                        }
                    }
                }

                String lastMessageText = "";
                long lastMessageDate = 0;
                if (lastMessage != null) {
                    lastMessageText = lastMessage.optString("text", "");
                    lastMessageDate = lastMessage.optLong("date", 0);
                }

                int unreadCount = conversationObj.optInt("unread_count", 0);
                if (lastMessage != null && lastMessage.optInt("out", 0) == 1) {
                    unreadCount = 0;
                }

                Conversation conversation = new Conversation(
                        i, peerId, title, lastMessageText,
                        lastMessageDate, unreadCount
                );
                conversation.setPeerPhoto(photo);
                conversation.setOnline(isOnline);
                conversation.setPeerVerified(isVerified);

                conversations.add(conversation);
                Logger.d(TAG, "Добавлен диалог: " + title);
                
            } catch (Exception e) {
                Logger.e(TAG, "Ошибка обработки диалога " + i + ": " + e.getMessage(), e);
            }
        }

        Logger.d(TAG, "Успешно обработано " + conversations.size() + " диалогов");
        return conversations;
    }

    // Пометить сообщения как прочитанные
    public void markAsRead(int peerId) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));

        api.callMethod("messages.markAsRead", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Успешно помечено как прочитанное
            }

            @Override
            public void onError(String error) {
                // Ошибка пометки как прочитанное
            }
        });
    }
    
    // Получение информации о пользователе
    public void getUserInfo(int userId, UserInfoCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("user_ids", String.valueOf(userId));
        params.put("fields", "photo_50");

        api.callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray users = response.getJSONArray("response");
                    if (users.length() > 0) {
                        JSONObject user = users.getJSONObject(0);
                        String firstName = user.getString("first_name");
                        String lastName = user.getString("last_name");
                        String userName = firstName + " " + lastName;
                        String userPhoto = user.optString("photo_50", "");
                        
                        callback.onSuccess(userName, userPhoto);
                    } else {
                        callback.onError("Пользователь не найден");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing user info", e);
                    callback.onError("Не удалось получить информацию о пользователе");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error getting user info: " + error);
                callback.onError("Не удалось получить информацию о пользователе");
            }
        });
    }
}