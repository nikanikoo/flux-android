package org.nikanikoo.flux.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LongPollManager {
    private static final String TAG = "LongPollManager";
    private static LongPollManager instance;
    
    private final OpenVKApi api;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private String server;
    private String key;
    private long ts;

    private int fastResponseCount = 0;

    private final List<OnMessageEventListener> messageEventListeners = 
            java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile OnTypingEventListener typingEventListener;
    private volatile OnOnlineEventListener onlineEventListener;

    private final Map<Integer, Long> processedEvents = new ConcurrentHashMap<>();
    private static final int MAX_PROCESSED_EVENTS = 1000;
    
    private LongPollManager(Context context) {
        this.api = OpenVKApi.getInstance(context);
        
        // Создаем OkHttpClient с поддержкой SSL для самоподписанных сертификатов
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .readTimeout(90, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS);
        
        // Configure secure SSL
        org.nikanikoo.flux.utils.SSLHelper.configureToIgnoreSSL(clientBuilder);
        
        this.client = clientBuilder.build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LongPollManager");
            t.setDaemon(true);
            return t;
        });
    }
    
    public static synchronized LongPollManager getInstance(Context context) {
        if (instance == null) {
            instance = new LongPollManager(context);
        }
        return instance;
    }

    public interface OnMessageEventListener {
        void onNewMessage(int messageId, int peerId, long timestamp, String text, int fromId, boolean isOut);
        void onMessageRead(int peerId, int localId);
        void onMessageEdit(int messageId, int peerId, String newText);
    }
    
    public interface OnTypingEventListener {
        void onUserTyping(int peerId, int userId);
    }
    
    public interface OnOnlineEventListener {
        void onUserOnline(int userId, boolean isOnline);
    }

    public void setMessageEventListener(OnMessageEventListener listener) {
        synchronized (messageEventListeners) {
            messageEventListeners.clear();
            if (listener != null) {
                messageEventListeners.add(listener);
            }
        }
    }

    public void addMessageEventListener(OnMessageEventListener listener) {
        if (listener != null) {
            synchronized (messageEventListeners) {
                if (!messageEventListeners.contains(listener)) {
                    messageEventListeners.add(listener);
                }
            }
        }
    }
    
    public void removeMessageEventListener(OnMessageEventListener listener) {
        if (listener != null) {
            synchronized (messageEventListeners) {
                messageEventListeners.remove(listener);
            }
        }
    }
    
    public void setOnlineEventListener(OnOnlineEventListener listener) {
        this.onlineEventListener = listener;
    }

    public void setTypingEventListener(OnTypingEventListener listener) {
        this.typingEventListener = listener;
    }

    public void clearAllListeners() {
        synchronized (messageEventListeners) {
            messageEventListeners.clear();
        }
        typingEventListener = null;
        onlineEventListener = null;
    }

    public void clearProcessedEventsCache() {
        processedEvents.clear();
        Logger.d(TAG, "Processed events cache cleared");
    }

    public void shutdown() {
        Logger.d(TAG, "Остановка LongPollManager");
        stop();
        clearAllListeners();
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        processedEvents.clear();
    }

    public void start() {
        
        if (isRunning.get()) {
            Logger.d(TAG, "LongPoll уже запущен");
            return;
        }
        
        Logger.d(TAG, "Запуск LongPoll");
        isRunning.set(true);
        getLongPollServer();
    }

    public void stop() {
        Logger.d(TAG, "Остановка LongPoll");
        isRunning.set(false);
    }

    private void getLongPollServer() {
        executor.execute(() -> {
            try {
                String token = api.getToken();
                if (token == null) {
                    Logger.e(TAG, "Отсутствует токен");
                    scheduleRetry(10000);
                    return;
                }

                RequestBody body = new FormBody.Builder()
                        .add("access_token", token)
                        .add("need_pts", "1")
                        .add("lp_version", "3")
                        .add("group_id", "0")
                        .build();
                
                Request request = new Request.Builder()
                        .url(api.getBaseUrl() + "/method/messages.getLongPollServer")
                        .post(body)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.body() != null) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Ответ LongPoll: " + responseBody);
                        
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("response")) {
                            JSONObject responseObj = json.getJSONObject("response");
                            server = responseObj.getString("server");
                            if (server != null && !server.contains("://")) {
                                String baseUrl = api.getBaseUrl();
                                String scheme = baseUrl.startsWith("https://") ? "https://" : "http://";
                                server = scheme + server;
                            }
                            key = responseObj.getString("key");
                            ts = responseObj.optLong("ts", 0);
                            
                            Log.d(TAG, "LongPoll server obtained: " + server);
                            Log.d(TAG, "LongPoll key: " + key);
                            Log.d(TAG, "LongPoll initial ts: " + ts);
                            Log.d(TAG, "Full server response: " + responseObj.toString());

                            long currentTime = System.currentTimeMillis() / 1000;
                            long timeDiff = currentTime - ts;
                            Log.d(TAG, "Current unix time: " + currentTime);
                            Log.d(TAG, "TS difference: " + timeDiff + " seconds (" + (timeDiff/60) + " minutes)");

                            fastResponseCount = 0;
                            
                            if (ts == 0) {
                                ts = System.currentTimeMillis() / 1000;
                                Log.d(TAG, "Using current timestamp as fallback: " + ts);
                            }

                            startLongPollLoop();
                        } else if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String errorMsg = error.optString("error_msg", "Неизвестная ошибка");
                            Logger.e(TAG, "LongPoll ошибка: " + errorMsg);
                            scheduleRetry(10000);
                        }
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error getting LongPoll server", e);
                    scheduleRetry(10000);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error in getLongPollServer", e);
                scheduleRetry(10000);
            }
        });
    }

    private void startLongPollLoop() {
        Log.d(TAG, "Starting LongPoll loop thread");
        
        executor.execute(() -> {
            Log.d(TAG, "LongPoll loop thread started");
            
            // Флаг для первого запроса
            boolean isFirstRequest = true;
            
            while (isRunning.get()) {
                try {
                    Log.d(TAG, "LongPoll loop iteration, isFirstRequest=" + isFirstRequest + ", isRunning=" + isRunning.get());
                    
                    if (isFirstRequest) {
                        Log.d(TAG, "Performing first request");
                        performFirstLongPollRequest();
                        isFirstRequest = false;
                        Log.d(TAG, "First request completed");
                    } else {
                        Log.d(TAG, "Performing regular request");
                        performLongPollRequest();
                        Log.d(TAG, "Regular request completed");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error in LongPoll loop", e);
                    
                    if (isRunning.get()) {
                        Log.d(TAG, "Pausing before retry...");
                        // Пауза перед повторной попыткой
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ie) {
                            Log.d(TAG, "Thread interrupted during sleep");
                            Thread.currentThread().interrupt();
                            break;
                        }
                        
                        Log.d(TAG, "Getting new LongPoll server after error");
                        // Получаем новый сервер
                        getLongPollServer();
                        break;
                    }
                }
            }
            
            Log.d(TAG, "LongPoll loop thread finished");
        });
    }
    
    // Первый запрос для получения актуального ts
    private void performFirstLongPollRequest() throws Exception {
        Log.d(TAG, "Performing first LongPoll request to sync ts");
        
        // Попробуем обычный запрос, но с коротким таймаутом
        String url = server + "?key=" + key + "&act=a_check&wait=1&version=3&ts=" + ts;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        Log.d(TAG, "First LongPoll request: " + url);
        long requestStartTime = System.currentTimeMillis();
        
        try (Response response = client.newCall(request).execute()) {
            long requestDuration = System.currentTimeMillis() - requestStartTime;
            
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("First LongPoll request failed: " + response.code());
            }
            
            String responseBody = response.body().string();
            Log.d(TAG, "First LongPoll response (duration=" + requestDuration + "ms): " + responseBody);
            
            // Проверяем на пустой массив []
            if ("[]".equals(responseBody.trim())) {
                Log.d(TAG, "First request returned empty array - this is normal, ts is synchronized");
                return;
            }
            
            JSONObject json = new JSONObject(responseBody);
            
            if (json.has("failed")) {
                int failCode = json.getInt("failed");
                Log.w(TAG, "First request failed with code: " + failCode);
                
                if (failCode == 1 && json.has("ts")) {
                    // Получаем актуальный ts
                    long newTs = json.getLong("ts");
                    Log.d(TAG, "Got actual ts from failed response: " + newTs + " (was " + ts + ")");
                    ts = newTs;
                } else if (failCode == 2 || failCode == 3) {
                    Log.w(TAG, "Need new key/server, will restart");
                    throw new Exception("Need new LongPoll key");
                } else {
                    Log.w(TAG, "Unknown fail code: " + failCode);
                }
            } else if (json.has("ts")) {
                // Обновляем ts из успешного ответа
                long newTs = json.getLong("ts");
                Log.d(TAG, "Got actual ts from successful response: " + newTs + " (was " + ts + ")");
                ts = newTs;
                
                // Обрабатываем события если есть
                if (json.has("updates")) {
                    JSONArray updates = json.getJSONArray("updates");
                    if (updates.length() > 0) {
                        Log.d(TAG, "Processing " + updates.length() + " updates from first request");
                        processUpdates(updates);
                    } else {
                        Log.d(TAG, "No updates in first response");
                    }
                }
            } else {
                Log.w(TAG, "First response doesn't contain ts field: " + responseBody);
            }
            
            Log.d(TAG, "First request completed, synchronized ts=" + ts);
        }
    }
    
    // Выполнение LongPoll запроса согласно инструкции
    private void performLongPollRequest() throws Exception {
        // Проверяем, не слишком ли старый ts (больше 1 дня назад)
        long currentTime = System.currentTimeMillis() / 1000;
        long timeDiff = currentTime - ts;
        
        if (Math.abs(timeDiff) > 86400) { // 24 часа в любую сторону
            Log.w(TAG, "TS is too far from current time (diff: " + timeDiff + " seconds), using current time");
            ts = currentTime;
        } else if (Math.abs(timeDiff) > 60) { // Больше 1 минуты
            Log.w(TAG, "TS difference is significant (diff: " + timeDiff + " seconds), might cause issues");
        }
        
        // GET запрос как указано в инструкции: %server%?key=%наш ключ%&act=a_check&wait=%время ожидания%&version=3&ts=%ts%
        // Убираем mode=2 если есть проблемы с быстрыми ответами
        String url;
        if (fastResponseCount > 2) {
            // Без mode параметра
            url = server + "?key=" + key + "&act=a_check&wait=45&version=3&ts=" + ts;
            Log.d(TAG, "Using simplified URL due to fast responses");
        } else {
            // С mode=2 для получения дополнительных полей (как в VK API)
            url = server + "?key=" + key + "&act=a_check&wait=45&version=3&ts=" + ts + "&mode=2";
        }
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        Log.d(TAG, "LongPoll request: " + url + " (ts=" + ts + ", current=" + currentTime + ", diff=" + timeDiff + "s)");
        
        long requestStartTime = System.currentTimeMillis();
        
        try (Response response = client.newCall(request).execute()) {
            long requestDuration = System.currentTimeMillis() - requestStartTime;
            
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("LongPoll request failed: " + response.code());
            }
            
            String responseBody = response.body().string();
            Log.d(TAG, "LongPoll response (ts=" + ts + ", duration=" + requestDuration + "ms): " + responseBody);
            
            // Если запрос выполнился слишком быстро (менее 1 секунды), это подозрительно
            if (requestDuration < 1000) {
                fastResponseCount++;
                Log.w(TAG, "Request completed too quickly (" + requestDuration + "ms), count: " + fastResponseCount);
                
                // Если это происходит много раз подряд, получаем новый сервер
                if (fastResponseCount >= 5) {
                    Log.w(TAG, "Too many fast responses, getting new server");
                    throw new Exception("Too many fast responses, need new server");
                }
            } else {
                // Сбрасываем счетчик при нормальном ответе
                fastResponseCount = 0;
            }
            
            // Проверяем на пустой массив []
            if ("[]".equals(responseBody.trim())) {
                Log.d(TAG, "Empty response array, continuing with same ts=" + ts);
                
                // При быстрых ответах обновляем ts на текущее время
                if (requestDuration < 1000) {
                    long newTs = currentTime;
                    Log.w(TAG, "Fast empty response, updating ts from " + ts + " to " + newTs);
                    ts = newTs;
                }
                
                // Если запрос был очень быстрым, добавляем небольшую паузу
                if (requestDuration < 1000) {
                    Log.d(TAG, "Adding delay due to fast response");
                    try {
                        Thread.sleep(Math.min(2000, fastResponseCount * 500)); // Увеличиваем паузу
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                return;
            }
            
            JSONObject json = new JSONObject(responseBody);
            
            if (json.has("failed")) {
                int failCode = json.getInt("failed");
                Log.w(TAG, "LongPoll failed with code: " + failCode);
                
                if (failCode == 1) {
                    // Обновляем ts
                    long newTs = json.optLong("ts", ts);
                    Log.d(TAG, "Failed code 1: Updating ts from " + ts + " to " + newTs);
                    ts = newTs;
                } else if (failCode == 2 || failCode == 3) {
                    // Нужно получить новый ключ
                    Log.w(TAG, "Failed code " + failCode + ": Need new LongPoll key, restarting...");
                    throw new Exception("Need new LongPoll key");
                }
                return;
            }
            
            // Обновляем ts
            if (json.has("ts")) {
                long newTs = json.getLong("ts");
                Log.d(TAG, "Success: Updating ts from " + ts + " to " + newTs);
                ts = newTs;
            } else {
                Log.w(TAG, "Warning: Response doesn't contain ts field");
            }
            
            // Обрабатываем события
            if (json.has("updates")) {
                JSONArray updates = json.getJSONArray("updates");
                Log.d(TAG, "Processing " + updates.length() + " updates");
                processUpdates(updates);
            } else {
                Log.d(TAG, "No updates in response");
            }
            
            // Если ts был 0 и мы получили новый ts, логируем это
            if (ts > 0) {
                Log.d(TAG, "LongPoll is now synchronized with ts=" + ts);
            }
        }
    }
    
    // Обработка событий
    private void processUpdates(JSONArray updates) {
        for (int i = 0; i < updates.length(); i++) {
            try {
                JSONArray update = updates.getJSONArray(i);
                int eventType = update.getInt(0);
                
                Log.d(TAG, "Processing event type: " + eventType);
                
                switch (eventType) {
                    case 4: // Новое сообщение
                        processNewMessage(update);
                        break;
                    case 6: // Прочитано входящее сообщение
                    case 7: // Прочитано исходящее сообщение
                        processMessageRead(update);
                        break;
                    case 5: // Редактирование сообщения
                        processMessageEdit(update);
                        break;
                    case 61: // Пользователь печатает
                        processUserTyping(update);
                        break;
                    case 8: // Пользователь онлайн
                    case 9: // Пользователь оффлайн
                        processUserOnline(update, eventType == 8);
                        break;
                }
                
            } catch (Exception e) {
                Logger.e(TAG, "Error processing update " + i, e);
            }
        }
    }
    
    // Обработка нового сообщения
    private void processNewMessage(JSONArray update) {
        try {
            int messageId = update.getInt(1);
            int flags = update.getInt(2);
            int peerId = update.getInt(3);
            long timestamp = update.getLong(4);
            String text = update.getString(5);
            
            // Проверяем дедупликацию на уровне LongPoll
            if (processedEvents.containsKey(messageId)) {
                Long existingTimestamp = processedEvents.get(messageId);
                if (existingTimestamp != null && existingTimestamp.equals(timestamp)) {
                    Log.d(TAG, "Event for message " + messageId + " with timestamp " + timestamp + " already processed in LongPoll, skipping");
                    return;
                }
            }
            
            // Добавляем в кеш обработанных событий
            processedEvents.put(messageId, timestamp);

            // Очищаем кеш если он стал слишком большим
            if (processedEvents.size() > MAX_PROCESSED_EVENTS) {
                java.util.List<java.util.Map.Entry<Integer, Long>> entries =
                    new java.util.ArrayList<>(processedEvents.entrySet());
                java.util.Collections.sort(entries, new java.util.Comparator<java.util.Map.Entry<Integer, Long>>() {
                    @Override
                    public int compare(java.util.Map.Entry<Integer, Long> o1, java.util.Map.Entry<Integer, Long> o2) {
                        return o1.getValue().compareTo(o2.getValue());
                    }
                });
                int toRemove = Math.min(50, entries.size());
                for (int i = 0; i < toRemove; i++) {
                    processedEvents.remove(entries.get(i).getKey());
                }
            }
            
            // Дополнительные поля - могут быть массивом или объектом
            int fromId = peerId; // По умолчанию from_id = peer_id
            
            // Пытаемся получить from_id из позиции 9 (если есть)
            if (update.length() > 9) {
                try {
                    fromId = update.getInt(9);
                } catch (Exception e) {
                    Log.w(TAG, "Could not get from_id from position 9, using peer_id");
                }
            }
            
            // Если from_id не получен, пытаемся из позиции 6 (если это объект)
            if (fromId == peerId && update.length() > 6) {
                try {
                    Object extraField = update.get(6);
                    if (extraField instanceof JSONObject) {
                        JSONObject extra = (JSONObject) extraField;
                        fromId = extra.optInt("from", peerId);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Position 6 is not JSONObject, using peer_id as from_id");
                }
            }
            
            // Определяем, исходящее ли сообщение
            boolean isOut = (flags & 2) != 0;
            
            Log.d(TAG, "New message parsed:");
            Log.d(TAG, "  ID: " + messageId);
            Log.d(TAG, "  Flags: " + flags);
            Log.d(TAG, "  Peer ID: " + peerId);
            Log.d(TAG, "  From ID: " + fromId);
            Log.d(TAG, "  Text: " + text);
            Log.d(TAG, "  Timestamp: " + timestamp);
            Log.d(TAG, "  Is outgoing: " + isOut);
            
            if (!messageEventListeners.isEmpty()) {
                // Создаем final копии для lambda
                final int finalMessageId = messageId;
                final int finalPeerId = peerId;
                final long finalTimestamp = timestamp;
                final String finalText = text;
                final int finalFromId = fromId;
                final boolean finalIsOut = isOut;
                
                mainHandler.post(() -> {
                    for (OnMessageEventListener listener : messageEventListeners) {
                        listener.onNewMessage(finalMessageId, finalPeerId, finalTimestamp, finalText, finalFromId, finalIsOut);
                    }
                });
            }
            
        } catch (Exception e) {
            Logger.e(TAG, "Error processing new message", e);
            Logger.e(TAG, "Update array: " + update.toString());
        }
    }
    
    // Обработка прочтения сообщения
    private void processMessageRead(JSONArray update) {
        try {
            int peerId = update.getInt(1);
            int localId = update.getInt(2);
            
            Log.d(TAG, "Message read: peer " + peerId + ", local " + localId);
            
            if (!messageEventListeners.isEmpty()) {
                final int finalPeerId = peerId;
                final int finalLocalId = localId;
                
                mainHandler.post(() -> {
                    for (OnMessageEventListener listener : messageEventListeners) {
                        listener.onMessageRead(finalPeerId, finalLocalId);
                    }
                });
            }
            
        } catch (Exception e) {
            Logger.e(TAG, "Error processing message read", e);
        }
    }
    
    // Обработка редактирования сообщения
    private void processMessageEdit(JSONArray update) {
        try {
            int messageId = update.getInt(1);
            int peerId = update.getInt(3);
            String newText = update.getString(5);
            
            Log.d(TAG, "Message edited: " + messageId);
            
            if (!messageEventListeners.isEmpty()) {
                final int finalMessageId = messageId;
                final int finalPeerId = peerId;
                final String finalNewText = newText;
                
                mainHandler.post(() -> {
                    for (OnMessageEventListener listener : messageEventListeners) {
                        listener.onMessageEdit(finalMessageId, finalPeerId, finalNewText);
                    }
                });
            }
            
        } catch (Exception e) {
            Logger.e(TAG, "Error processing message edit", e);
        }
    }
    
    // Обработка печатания
    private void processUserTyping(JSONArray update) {
        try {
            int peerId = update.getInt(1);
            int userId = peerId; // В личных сообщениях peerId и userId совпадают
            
            Log.d(TAG, "User typing: " + userId + " in " + peerId);
            
            if (typingEventListener != null) {
                final int finalPeerId = peerId;
                final int finalUserId = userId;
                
                mainHandler.post(() -> 
                    typingEventListener.onUserTyping(finalPeerId, finalUserId)
                );
            }
            
        } catch (Exception e) {
            Logger.e(TAG, "Error processing user typing", e);
        }
    }
    
    // Обработка онлайн статуса
    private void processUserOnline(JSONArray update, boolean isOnline) {
        try {
            int userId = Math.abs(update.getInt(1));
            
            Log.d(TAG, "User " + (isOnline ? "online" : "offline") + ": " + userId);
            
            if (onlineEventListener != null) {
                final int finalUserId = userId;
                final boolean finalIsOnline = isOnline;
                
                mainHandler.post(() -> 
                    onlineEventListener.onUserOnline(finalUserId, finalIsOnline)
                );
            }
            
        } catch (Exception e) {
            Logger.e(TAG, "Error processing user online", e);
        }
    }
    
    // Планирование повторной попытки
    private void scheduleRetry(long delayMs) {
        if (isRunning.get()) {
            mainHandler.postDelayed(this::getLongPollServer, delayMs);
        }
    }
    
    // Проверка состояния
    public boolean isRunning() {
        return isRunning.get();
    }
    
    // Принудительный сброс состояния для тестирования
    public void forceReset() {
        Log.d(TAG, "forceReset() called");
        isRunning.set(false);
        server = null;
        key = null;
        ts = 0;
        fastResponseCount = 0;
        Log.d(TAG, "LongPoll state reset");
    }
    
    // Метод для тестирования - получение информации о сервере
    public void testGetLongPollServer(Context context) {
        executor.execute(() -> {
            try {
                String token = api.getToken();
                if (token == null) {
                    Logger.e(TAG, "TEST: No access token available");
                    return;
                }
                
                Log.d(TAG, "TEST: Getting LongPoll server info...");
                
                // Пробуем разные варианты параметров
                String[] variants = {
                    "access_token=" + token,
                    "access_token=" + token + "&need_pts=1",
                    "access_token=" + token + "&need_pts=1&lp_version=3",
                    "access_token=" + token + "&need_pts=1&lp_version=3&group_id=0"
                };
                
                for (int i = 0; i < variants.length; i++) {
                    Log.d(TAG, "TEST: Trying variant " + (i+1) + ": " + variants[i]);
                    
                    RequestBody body = new FormBody.Builder()
                            .add("access_token", token)
                            .build();
                    
                    if (i >= 1) body = new FormBody.Builder().add("access_token", token).add("need_pts", "1").build();
                    if (i >= 2) body = new FormBody.Builder().add("access_token", token).add("need_pts", "1").add("lp_version", "3").build();
                    if (i >= 3) body = new FormBody.Builder().add("access_token", token).add("need_pts", "1").add("lp_version", "3").add("group_id", "0").build();
                    
                    Request request = new Request.Builder()
                            .url(api.getBaseUrl() + "/method/messages.getLongPollServer")
                            .post(body)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    
                    try (Response response = client.newCall(request).execute()) {
                        if (response.body() != null) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "TEST: Variant " + (i+1) + " response: " + responseBody);
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "TEST: Variant " + (i+1) + " error: " + e.getMessage());
                    }
                    
                    Thread.sleep(1000); // Пауза между запросами
                }
                
            } catch (Exception e) {
                Logger.e(TAG, "TEST: Error in testGetLongPollServer", e);
            }
        });
    }
}