package org.nikanikoo.flux.data.managers;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Friend;
import org.nikanikoo.flux.data.models.FriendRequest;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsManager extends BaseManager<FriendsManager> {
    private static final String TAG = "FriendsManager";

    private FriendsManager(Context context) {
        super(context);
    }

    public static synchronized FriendsManager getInstance(Context context) {
        return BaseManager.getInstance(FriendsManager.class, context);
    }

    public interface FriendsCallback {
        void onSuccess(List<Friend> friends);
        void onError(String error);
    }

    public interface FriendRequestsCallback {
        void onSuccess(List<FriendRequest> requests);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface AreFriendsCallback {
        void onSuccess(List<FriendStatus> statuses);
        void onError(String error);
    }

    public static class FriendStatus {
        private int userId;
        private int friendStatus;

        public FriendStatus(int userId, int friendStatus) {
            this.userId = userId;
            this.friendStatus = friendStatus;
        }

        public int getUserId() {
            return userId;
        }

        public int getFriendStatus() {
            return friendStatus;
        }

        public boolean isFriend() {
            return friendStatus == 3;
        }
    }

    // Получение списка друзей
    public void getFriends(int count, int offset, FriendsCallback callback) {
        Logger.d(TAG, "Начинаем запрос списка друзей...");
        
        // Ограничиваем количество для предотвращения OutOfMemory
        int safeCount = Math.min(count, Constants.Api.FRIENDS_PER_PAGE);
        
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(safeCount));
        params.put("offset", String.valueOf(offset));
        params.put("fields", "photo_50,photo_100,online,screen_name,status,verified");
        params.put("order", "name");

        Logger.d(TAG, "Параметры запроса: " + params.toString());

        api.callMethod("friends.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.d(TAG, "Сырой ответ: " + response.toString());
                    
                    // Проверяем структуру ответа
                    if (response.has("response")) {
                        JSONObject responseObj = response.getJSONObject("response");
                        
                        // Проверяем, есть ли items или это просто массив
                        if (responseObj.has("items")) {
                            JSONArray items = responseObj.getJSONArray("items");
                            Logger.d(TAG, "Найдено " + items.length() + " друзей в массиве items");
                            parseFriendsFromArray(items, callback);
                        } else if (responseObj.has("count")) {
                            // Возможно, структура: {"response": {"count": X, "items": [...]}}
                            int totalCount = responseObj.getInt("count");
                            Logger.d(TAG, "Общее количество друзей: " + totalCount);
                            
                            if (responseObj.has("items")) {
                                JSONArray items = responseObj.getJSONArray("items");
                                parseFriendsFromArray(items, callback);
                            } else {
                                callback.onError("Нет массива items в ответе");
                            }
                        } else {
                            // Возможно, response сам является массивом друзей
                            try {
                                JSONArray friendsArray = response.getJSONArray("response");
                                Logger.d(TAG, "Найдено " + friendsArray.length() + " друзей в прямом массиве");
                                parseFriendsFromArray(friendsArray, callback);
                            } catch (Exception e) {
                                Logger.e(TAG, "Response structure unknown: " + response.toString());
                                callback.onError("Неизвестная структура ответа API");
                            }
                        }
                    } else {
                        Logger.e(TAG, "No 'response' field in API response");
                        callback.onError("Некорректный ответ API");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing response: " + e.getMessage(), e);
                    callback.onError("Не удалось загрузить список друзей");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "API error: " + error);
                callback.onError("Не удалось загрузить список друзей");
            }
        });
    }
    
    private void parseFriendsFromArray(JSONArray friendsArray, FriendsCallback callback) {
        List<Friend> friends = new ArrayList<>();
        
        for (int i = 0; i < friendsArray.length(); i++) {
            try {
                JSONObject friendJson = friendsArray.getJSONObject(i);
                Friend friend = Friend.fromJson(friendJson);
                friends.add(friend);
                
                Logger.d(TAG, "Добавлен друг " + friend.getFullName() + " (ID: " + friend.getId() + ")");
            } catch (Exception e) {
                Logger.e(TAG, "Ошибка парсинга друга " + i + ": " + e.getMessage(), e);
            }
        }
        
        Logger.d(TAG, "Успешно обработано " + friends.size() + " друзей");
        callback.onSuccess(friends);
    }

    // Поиск друзей
    public void searchFriends(String query, int count, FriendsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("q", query);
        params.put("count", String.valueOf(count));
        params.put("fields", "photo_50,photo_100,online,screen_name,status,verified");

        api.callMethod("friends.search", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");

                    List<Friend> friends = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject friendJson = items.getJSONObject(i);
                        Friend friend = Friend.fromJson(friendJson);
                        friends.add(friend);
                    }

                    callback.onSuccess(friends);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing search results", e);
                    callback.onError("Не удалось выполнить поиск");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error searching friends: " + error);
                callback.onError("Не удалось выполнить поиск");
            }
        });
    }

    // Получение онлайн друзей
    public void getOnlineFriends(FriendsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("online", "1");
        params.put("fields", "photo_50,photo_100,online,screen_name,status,verified");
        params.put("order", "name");

        api.callMethod("friends.getOnline", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray items = response.getJSONArray("response");
                    List<Friend> friends = new ArrayList<>();
                    
                    for (int i = 0; i < items.length(); i++) {
                        int friendId = items.getInt(i);
                        // Для онлайн друзей нужно дополнительно получить информацию
                        // Пока создаем базовый объект
                        Friend friend = new Friend();
                        friend.setId(friendId);
                        friend.setOnline(true);
                        friends.add(friend);
                    }

                    callback.onSuccess(friends);
                } catch (Exception e) {
                    Logger.e(TAG, "Error getting online friends", e);
                    callback.onError("Не удалось получить список онлайн друзей");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error getting online friends: " + error);
                callback.onError("Не удалось получить список онлайн друзей");
            }
        });
    }

    // Получение заявок в друзья
    public void getFriendRequests(FriendRequestsCallback callback) {
        Logger.d(TAG, "Получение заявок в друзья...");
        
        Map<String, String> params = new HashMap<>();
        params.put("out", "0");
        params.put("count", "100");
        params.put("fields", "photo_50,status,deactivated");
        
        api.callMethod("friends.getRequests", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.d(TAG, "Ответ заявок в друзья: " + response.toString());
                    
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    
                    List<FriendRequest> requests = new ArrayList<>();
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        
                        // Фильтруем удаленных пользователей
                        if (item.has("deactivated") && "deleted".equals(item.optString("deactivated"))) {
                            Logger.d(TAG, "Пропускаем удаленного пользователя: " + item.toString());
                            continue;
                        }
                        
                        // Также проверяем, что имя не "DELETED"
                        String firstName = item.optString("first_name", "");
                        if ("DELETED".equals(firstName)) {
                            Logger.d(TAG, "Пропускаем пользователя с именем DELETED: " + item.toString());
                            continue;
                        }
                        
                        int userId = item.getInt("user_id");
                        String lastName = item.optString("last_name", "");
                        String name = (firstName + " " + lastName).trim();
                        String status = item.optString("status", "");
                        String avatarUrl = item.optString("photo_50", "");
                        
                        // Пропускаем пользователей с пустым именем
                        if (name.trim().isEmpty()) {
                            Logger.d(TAG, "Пропускаем пользователя с пустым именем: " + item.toString());
                            continue;
                        }
                        
                        FriendRequest request = new FriendRequest(userId, name, status, avatarUrl, System.currentTimeMillis());
                        requests.add(request);
                    }
                    
                    Logger.d(TAG, "Обработано " + requests.size() + " валидных заявок в друзья (отфильтрованы удаленные пользователи)");
                    callback.onSuccess(requests);
                    
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга заявок в друзья: " + e.getMessage(), e);
                    callback.onError("Не удалось загрузить заявки в друзья");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка получения заявок в друзья: " + error);
                callback.onError("Не удалось загрузить заявки в друзья");
            }
        });
    }

    // Принятие заявки в друзья
    public void acceptFriendRequest(int userId, ActionCallback callback) {
        Logger.d(TAG, "Принимаем заявку в друзья от пользователя " + userId);
        
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(userId));
        
        api.callMethod("friends.add", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Заявка в друзья успешно принята");
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка принятия заявки в друзья: " + error);
                callback.onError("Не удалось принять заявку в друзья");
            }
        });
    }

    // Отклонение заявки в друзья
    public void declineFriendRequest(int userId, ActionCallback callback) {
        Logger.d(TAG, "Отклоняем заявку в друзья от пользователя " + userId);
        
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(userId));
        
        // Используем friends.delete - согласно документации удаляет из друзей или отклоняет заявку
        api.callMethod("friends.delete", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.d(TAG, "Ответ API friends.delete: " + response.toString());
                    
                    // Проверяем успешность операции
                    if (response.has("response")) {
                        int result = response.getInt("response");
                        if (result == 1) {
                            Logger.d(TAG, "Заявка в друзья успешно отклонена");
                            callback.onSuccess();
                        } else {
                            Logger.w(TAG, "Неожиданный результат friends.delete: " + result);
                            callback.onError("Неожиданный результат операции");
                        }
                    } else {
                        Logger.w(TAG, "Нет поля response в ответе friends.delete");
                        callback.onError("Некорректный ответ сервера");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга ответа friends.delete", e);
                    callback.onError("Ошибка обработки ответа");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка отклонения заявки в друзья: " + error);
                
                // Проверяем специфичные ошибки
                if (error.contains("No friend or friend request found")) {
                    callback.onError("Заявка уже обработана или не найдена");
                } else if (error.contains("Access denied")) {
                    callback.onError("Нет доступа к этой операции");
                } else {
                    callback.onError("Не удалось отклонить заявку в друзья");
                }
            }
        });
    }

    public void areFriends(List<Integer> userIds, AreFriendsCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        StringBuilder idsBuilder = new StringBuilder();
        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) idsBuilder.append(",");
            idsBuilder.append(userIds.get(i));
        }

        Map<String, String> params = new HashMap<>();
        params.put("user_ids", idsBuilder.toString());

        api.callMethod("friends.areFriends", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.d(TAG, "friends.areFriends: " + response.toString());

                    if (response.has("response")) {
                        JSONArray responseArray = response.getJSONArray("response");
                        List<FriendStatus> statuses = new ArrayList<>();

                        for (int i = 0; i < responseArray.length(); i++) {
                            JSONObject item = responseArray.getJSONObject(i);
                            int userId = item.getInt("user_id");
                            int friendStatus = item.getInt("friend_status");
                            statuses.add(new FriendStatus(userId, friendStatus));
                        }

                        callback.onSuccess(statuses);
                    } else {
                        Logger.w(TAG, "Нет поля response в ответе friends.areFriends");
                        callback.onError("Некорректный ответ сервера");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга ответа friends.areFriends", e);
                    callback.onError("Ошибка обработки ответа");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка запроса friends.areFriends: " + error);
                callback.onError("Не удалось проверить статус дружбы");
            }
        });
    }

    // Создание диалога с другом
    public void startConversationWithFriend(int friendId, ActionCallback callback) {
        // Используем MessagesManager для создания диалога
        MessagesManager messagesManager = MessagesManager.getInstance(context);
        messagesManager.sendMessage(friendId, "", new MessagesManager.SendMessageCallback() {
            @Override
            public void onSuccess(int messageId) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error starting conversation: " + error);
                callback.onError("Не удалось начать диалог");
            }
        });
    }
}