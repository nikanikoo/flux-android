package org.nikanikoo.flux.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.security.AccountManager;
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
    private final Context appContext;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private String server;
    private String key;
    private long ts;
    private long pts;
    private static final int LONG_POLL_MODE = 2 + 8 + 32 + 64 + 128 + 256;

    private int fastResponseCount = 0;

    private final List<OnMessageEventListener> messageEventListeners = 
            java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile OnTypingEventListener typingEventListener;
    private volatile OnOnlineEventListener onlineEventListener;

    private final Map<Integer, Long> processedEvents = new ConcurrentHashMap<>();
    private static final int MAX_PROCESSED_EVENTS = 1000;
    
    private LongPollManager(Context context) {
        this.appContext = context.getApplicationContext();
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
        void onMessageRead(int peerId, int localId, boolean outgoingMessagesRead);
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
                        Log.d(TAG, "LongPoll server response received");
                        
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
                            if (!responseObj.has("ts")) {
                                throw new IllegalStateException("LongPoll server response has no ts cursor");
                            }
                            ts = responseObj.getLong("ts");
                            pts = responseObj.optLong("pts", 0);
                            
                            Log.d(TAG, "LongPoll server obtained: " + server);
                            Log.d(TAG, "LongPoll cursors initialized: ts=" + ts + ", pts=" + pts);

                            fastResponseCount = 0;

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
        
        String url = server + "?key=" + key + "&act=a_check&wait=1&version=3&ts=" + ts
                + "&pts=" + pts + "&mode=" + LONG_POLL_MODE;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        Log.d(TAG, "First LongPoll request: ts=" + ts + ", pts=" + pts + ", mode=" + LONG_POLL_MODE);
        long requestStartTime = System.currentTimeMillis();
        
        try (Response response = client.newCall(request).execute()) {
            long requestDuration = System.currentTimeMillis() - requestStartTime;
            
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("First LongPoll request failed: " + response.code());
            }
            
            String responseBody = response.body().string();
            Log.d(TAG, "First LongPoll response received in " + requestDuration + "ms");
            
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
                    ts = json.getLong("ts");
                    pts = json.optLong("pts", pts);
                    Log.d(TAG, "First request cursor updated: ts=" + ts + ", pts=" + pts);
                } else if (failCode == 2 || failCode == 3) {
                    Log.w(TAG, "Need new key/server, will restart");
                    throw new Exception("Need new LongPoll key");
                } else {
                    Log.w(TAG, "Unknown fail code: " + failCode);
                }
            } else if (json.has("ts")) {
                // Обновляем ts из успешного ответа
                ts = json.getLong("ts");
                pts = json.optLong("pts", pts);
                
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
                throw new Exception("First LongPoll response has no ts cursor");
            }
            
            Log.d(TAG, "First request synchronized: ts=" + ts + ", pts=" + pts);
        }
    }
    
    // Выполнение LongPoll запроса согласно инструкции
    private void performLongPollRequest() throws Exception {
        // ts и pts — серверные курсоры, это не Unix-время. Web-клиент также передаёт pts и mode=490.
        String url = server + "?key=" + key + "&act=a_check&wait=45&version=3&ts=" + ts
                + "&pts=" + pts + "&mode=" + LONG_POLL_MODE;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        Log.d(TAG, "LongPoll request: ts=" + ts + ", pts=" + pts + ", mode=" + LONG_POLL_MODE);
        
        long requestStartTime = System.currentTimeMillis();
        
        try (Response response = client.newCall(request).execute()) {
            long requestDuration = System.currentTimeMillis() - requestStartTime;
            
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("LongPoll request failed: " + response.code());
            }
            
            String responseBody = response.body().string();
            Log.d(TAG, "LongPoll response received in " + requestDuration + "ms");
            
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
                Log.d(TAG, "Empty response array, keeping ts=" + ts + ", pts=" + pts);
                
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
                    ts = json.optLong("ts", ts);
                    pts = json.optLong("pts", pts);
                    Log.d(TAG, "LongPoll cursor refreshed: ts=" + ts + ", pts=" + pts);
                } else if (failCode == 2 || failCode == 3) {
                    // Нужно получить новый ключ
                    Log.w(TAG, "Failed code " + failCode + ": Need new LongPoll key, restarting...");
                    throw new Exception("Need new LongPoll key");
                }
                return;
            }
            
            // Обновляем ts
            if (json.has("ts")) {
                ts = json.getLong("ts");
            } else {
                Log.w(TAG, "Warning: Response doesn't contain ts field");
            }
            pts = json.optLong("pts", pts);
            
            // Обрабатываем события
            if (json.has("updates")) {
                JSONArray updates = json.getJSONArray("updates");
                Log.d(TAG, "Processing " + updates.length() + " updates (ts=" + ts + ", pts=" + pts + ")");
                processUpdates(updates);
            } else {
                Log.d(TAG, "No updates in response");
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
                        processMessageRead(update, false);
                        break;
                    case 7: // Прочитано исходящее сообщение
                        processMessageRead(update, true);
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
            // OpenVK adds the actual conversation peer at index 9; index 3 can be the sender for chats.
            int conversationPeerId = update.optInt(9, 0);
            if (conversationPeerId > 2000000000) {
                peerId = conversationPeerId;
            }
            long timestamp = update.getLong(4);
            String text = update.getString(5);
            Object textOrExtra = update.opt(6);
            if (textOrExtra instanceof String && !((String) textOrExtra).isEmpty()) {
                // Web client reads message text from index 6 (index 5 is the subject).
                text = (String) textOrExtra;
            } else if (textOrExtra instanceof JSONObject) {
                text = ((JSONObject) textOrExtra).optString("text", text);
            }
            
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
            JSONObject extra = update.optJSONObject(6);
            JSONObject attachments = update.optJSONObject(7);
            int currentUserId = getCurrentUserId();
            int defaultFromId = (flags & 2) != 0 && currentUserId > 0 ? currentUserId : peerId;
            int fromId = attachments != null
                    ? attachments.optInt("from", extra != null ? extra.optInt("from", defaultFromId) : defaultFromId)
                    : (extra != null ? extra.optInt("from", defaultFromId) : defaultFromId);
            if (fromId <= 0) fromId = defaultFromId;

            // OpenVK может присылать флаг 256 вместо стандартного флага исходящего сообщения.
            boolean isOut = (flags & 2) != 0 || (currentUserId > 0 && fromId == currentUserId);
            
            Log.d(TAG, "New message parsed:");
            Log.d(TAG, "  ID: " + messageId);
            Log.d(TAG, "  Flags: " + flags);
            Log.d(TAG, "  Peer ID: " + peerId);
            Log.d(TAG, "  From ID: " + fromId);
            Log.d(TAG, "  Text length: " + text.length());
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

    private int getCurrentUserId() {
        AccountManager.Account account = AccountManager.getInstance(appContext).getCurrentAccount();
        if (account == null || account.userId == null) return 0;
        try {
            return Integer.parseInt(account.userId);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
    
    // Обработка прочтения сообщения
    private void processMessageRead(JSONArray update, boolean outgoingMessagesRead) {
        try {
            int peerId = update.getInt(1);
            int localId = update.getInt(2);
            
            Log.d(TAG, "Message read: peer=" + peerId + ", localId=" + localId
                    + ", outgoingMessagesRead=" + outgoingMessagesRead);
            
            if (!messageEventListeners.isEmpty()) {
                final int finalPeerId = peerId;
                final int finalLocalId = localId;
                final boolean finalOutgoingMessagesRead = outgoingMessagesRead;
                
                mainHandler.post(() -> {
                    for (OnMessageEventListener listener : messageEventListeners) {
                        listener.onMessageRead(finalPeerId, finalLocalId, finalOutgoingMessagesRead);
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
                            response.body().close();
                            Log.d(TAG, "TEST: Variant " + (i+1) + " response received");
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
