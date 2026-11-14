package org.nikanikoo.flux.data.managers;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Conversation;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.data.models.Message;
import org.nikanikoo.flux.data.models.MessageAttachment;
import org.nikanikoo.flux.data.models.MessageReference;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.utils.ImageUtils;
import org.nikanikoo.flux.utils.AsyncTaskHelper;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.AttachmentProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessagesManager extends BaseManager<MessagesManager> {
    private static final String TAG = "MessagesManager";
    private static final long PUSH_SETTINGS_CACHE_TTL_MS = 30_000L;
    private final Map<Integer, CachedPushSettings> pushSettingsCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<PushSettingsCallback>> pendingPushSettings = new HashMap<>();
    private final Object pushSettingsLock = new Object();
    private final List<PushSettingsChangeListener> pushSettingsChangeListeners =
            Collections.synchronizedList(new ArrayList<>());

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

    public interface MessageActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface PinnedMessageCallback {
        void onSuccess(int messageId, String preview);
        void onError(String error);
    }

    public static final class PushSettings {
        private final long disabledUntil;
        private final boolean disabledForever;
        private final boolean soundEnabled;

        public PushSettings(long disabledUntil, boolean disabledForever, boolean soundEnabled) {
            this.disabledUntil = disabledUntil;
            this.disabledForever = disabledForever;
            this.soundEnabled = soundEnabled;
        }

        public long getDisabledUntil() { return disabledUntil; }
        public boolean isSoundEnabled() { return soundEnabled; }

        public boolean areNotificationsEnabled() {
            if (disabledForever || disabledUntil == -1) return false;
            if (disabledUntil > 0) {
                return disabledUntil <= System.currentTimeMillis() / 1000L;
            }
            return soundEnabled;
        }
    }

    public interface PushSettingsCallback {
        void onSuccess(PushSettings settings);
        void onError(String error);
    }

    public interface PushSettingsChangeListener {
        void onPushSettingsChanged(int peerId, PushSettings settings);
    }

    public interface SilenceModeCallback {
        void onSuccess();
        void onError(String error);
    }

    private static final class CachedPushSettings {
        final PushSettings settings;
        final long cachedAt;

        CachedPushSettings(PushSettings settings, long cachedAt) {
            this.settings = settings;
            this.cachedAt = cachedAt;
        }
    }

    public void getPushSettings(int peerId, PushSettingsCallback callback) {
        requestPushSettings(peerId, false, callback);
    }

    public void addPushSettingsChangeListener(PushSettingsChangeListener listener) {
        if (listener == null) return;
        synchronized (pushSettingsChangeListeners) {
            if (!pushSettingsChangeListeners.contains(listener)) {
                pushSettingsChangeListeners.add(listener);
            }
        }
    }

    public void removePushSettingsChangeListener(PushSettingsChangeListener listener) {
        if (listener == null) return;
        synchronized (pushSettingsChangeListeners) {
            pushSettingsChangeListeners.remove(listener);
        }
    }

    private void notifyPushSettingsChanged(int peerId, PushSettings settings) {
        List<PushSettingsChangeListener> listeners;
        synchronized (pushSettingsChangeListeners) {
            listeners = new ArrayList<>(pushSettingsChangeListeners);
        }
        for (PushSettingsChangeListener listener : listeners) {
            listener.onPushSettingsChanged(peerId, settings);
        }
    }

    public void refreshPushSettings(int peerId, PushSettingsCallback callback) {
        requestPushSettings(peerId, true, callback);
    }

    private void requestPushSettings(int peerId, boolean forceRefresh, PushSettingsCallback callback) {
        if (peerId == 0) {
            if (callback != null) callback.onError("Не указан чат");
            return;
        }

        final long requestStartedAt = System.currentTimeMillis();
        synchronized (pushSettingsLock) {
            CachedPushSettings cached = pushSettingsCache.get(peerId);
            if (!forceRefresh && cached != null
                    && System.currentTimeMillis() - cached.cachedAt < PUSH_SETTINGS_CACHE_TTL_MS) {
                if (callback != null) callback.onSuccess(cached.settings);
                return;
            }

            List<PushSettingsCallback> waiting = pendingPushSettings.get(peerId);
            if (waiting != null) {
                if (callback != null) waiting.add(callback);
                return;
            }
            waiting = new ArrayList<>();
            if (callback != null) waiting.add(callback);
            pendingPushSettings.put(peerId, waiting);
        }

        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        api.callMethod("account.getPushSettings", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject data = response.optJSONObject("response");
                if (data == null) {
                    finishPushSettingsRequest(peerId, null, "Не удалось прочитать настройки уведомлений");
                    return;
                }

                long disabledUntil = data.optLong("disabled_until", 0L);
                boolean soundEnabled = readBooleanSetting(data.opt("sound"), true);
                boolean disabledForever = readBooleanSetting(data.opt("disabled_forever"), false);
                PushSettings settings = new PushSettings(disabledUntil, disabledForever, soundEnabled);
                synchronized (pushSettingsLock) {
                    CachedPushSettings newer = pushSettingsCache.get(peerId);
                    if (newer != null && newer.cachedAt > requestStartedAt) {
                        settings = newer.settings;
                    } else {
                        pushSettingsCache.put(peerId,
                                new CachedPushSettings(settings, System.currentTimeMillis()));
                    }
                }
                notifyPushSettingsChanged(peerId, settings);
                finishPushSettingsRequest(peerId, settings, null);
            }

            @Override
            public void onError(String error) {
                finishPushSettingsRequest(peerId, null, error);
            }
        });
    }

    private void finishPushSettingsRequest(int peerId, PushSettings settings, String error) {
        List<PushSettingsCallback> callbacks;
        synchronized (pushSettingsLock) {
            callbacks = pendingPushSettings.remove(peerId);
        }
        if (callbacks == null) return;
        for (PushSettingsCallback callback : callbacks) {
            if (settings != null) callback.onSuccess(settings);
            else callback.onError(error != null ? error : "Не удалось загрузить настройки уведомлений");
        }
    }

    public void setSilenceMode(int peerId, int durationSeconds, SilenceModeCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("time", String.valueOf(durationSeconds));
        params.put("sound", durationSeconds == 0 ? "1" : "0");

        api.callMethod("account.setSilenceMode", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                long disabledUntil = durationSeconds == -1 ? -1L
                        : durationSeconds == 0 ? 0L
                        : System.currentTimeMillis() / 1000L + durationSeconds;
                PushSettings settings = new PushSettings(disabledUntil, durationSeconds == -1,
                        durationSeconds == 0);
                pushSettingsCache.put(peerId, new CachedPushSettings(settings, System.currentTimeMillis()));
                notifyPushSettingsChanged(peerId, settings);
                if (callback != null) callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public void updatePushSettingsFromLongPoll(int peerId, int sound, long disabledUntil) {
        if (peerId == 0) return;
        PushSettings settings = new PushSettings(disabledUntil, disabledUntil == -1, sound != 0);
        pushSettingsCache.put(peerId, new CachedPushSettings(settings, System.currentTimeMillis()));
        notifyPushSettingsChanged(peerId, settings);
    }

    private static boolean readBooleanSetting(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String stringValue = ((String) value).trim();
            if ("0".equals(stringValue) || "false".equalsIgnoreCase(stringValue)) return false;
            if ("1".equals(stringValue) || "true".equalsIgnoreCase(stringValue)) return true;
        }
        return fallback;
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
        getHistory(peerId, count, offset, 0, callback);
    }

    public void getHistory(int peerId, int count, int offset, int startMessageId, MessagesCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("count", String.valueOf(count));
        params.put("offset", String.valueOf(offset));
        if (startMessageId > 0) params.put("start_message_id", String.valueOf(startMessageId));
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

    public void getPinnedMessage(int peerId, PinnedMessageCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_ids", String.valueOf(peerId));
        params.put("extended", "1");

        api.callMethod("messages.getConversationsById", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                AsyncTaskHelper.executeAsync(() -> {
                    JSONObject responseData = response.optJSONObject("response");
                    JSONArray items = responseData != null ? responseData.optJSONArray("items") : null;
                    JSONObject item = items != null && items.length() > 0 ? items.optJSONObject(0) : null;
                    if (item == null) return new PinnedMessageData(0, "");

                    JSONObject conversation = item.optJSONObject("conversation");
                    if (conversation == null) conversation = item;
                    JSONObject chatSettings = conversation.optJSONObject("chat_settings");
                    JSONObject pinned = chatSettings != null ? chatSettings.optJSONObject("pinned_message") : null;
                    if (pinned == null) pinned = conversation.optJSONObject("pinned_message");
                    if (pinned == null) pinned = conversation.optJSONObject("current_pinned_message");
                    if (pinned == null) return new PinnedMessageData(0, "");

                    int messageId = pinned.optInt("id", pinned.optInt("mid",
                            pinned.optInt("conversation_message_id", pinned.optInt("local_id", 0))));
                    String preview = createConversationPreviewContent(pinned);
                    if (preview.isEmpty()) preview = pinned.optString("text", pinned.optString("body", ""));
                    return new PinnedMessageData(messageId, preview);
                }, new AsyncTaskHelper.AsyncCallback<PinnedMessageData>() {
                    @Override
                    public void onSuccess(PinnedMessageData data) {
                        callback.onSuccess(data.messageId, data.preview);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private static class PinnedMessageData {
        final int messageId;
        final String preview;

        PinnedMessageData(int messageId, String preview) {
            this.messageId = messageId;
            this.preview = preview;
        }
    }

    public void getMessageById(int messageId, int peerId, MessagesCallback callback) {
        Logger.d(TAG, "LongPoll: requesting message id=" + messageId + ", peerId=" + peerId);
        Map<String, String> params = new HashMap<>();
        params.put("message_ids", String.valueOf(messageId));
        params.put("extended", "1");
        params.put("fields", "photo_50,verified");

        api.callMethod("messages.getById", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                AsyncTaskHelper.executeAsync(() -> parseMessagesResponse(response, peerId),
                        new AsyncTaskHelper.AsyncCallback<List<Message>>() {
                            @Override
                            public void onSuccess(List<Message> messages) {
                                Logger.d(TAG, "LongPoll: getById returned " + messages.size() + " messages for id=" + messageId);
                                callback.onSuccess(messages);
                            }

                            @Override
                            public void onError(String error) {
                                Logger.e(TAG, "LongPoll: failed to parse getById for id=" + messageId + ": " + error);
                                callback.onError(error);
                            }
                        });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "LongPoll: getById API error for id=" + messageId + ": " + error);
                callback.onError(error);
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
            
                int id = item.optInt("id", item.optInt("mid", item.optInt("conversation_message_id", 0)));
                // Используем peerId из параметров, так как не все сообщения содержат peer_id
                int messagePeerId = item.optInt("peer_id", peerId);
                int fromId = item.optInt("from_id", item.optInt("user_id", item.optInt("uid", 0)));
                String text = item.optString("text", "");
                if (text.isEmpty()) text = item.optString("body", "");
                long date = item.optLong("date", 0);
                boolean out = item.optInt("out", 0) == 1;
                boolean readState = item.optInt("read_state", 0) == 1;

                Message message = new Message(id, messagePeerId, fromId, text, date, out, readState);
                JSONObject action = item.optJSONObject("action");
                String actionType = action != null
                        ? action.optString("type", "")
                        : item.optString("action", "");
                message.setActionType(actionType);
                String actionText = action != null
                        ? action.optString("text", item.optString("action_text", ""))
                        : item.optString("action_text", "");
                message.setActionText(actionText);
                message.setActionMid(action != null
                        ? action.optInt("member_id", item.optInt("action_mid", 0))
                        : item.optInt("action_mid", 0));
                message.setConversationMessageId(item.optInt("conversation_message_id", item.optInt("local_id", 0)));
                message.setAttachments(parseAttachments(item.optJSONArray("attachments")));
                message.setReply(parseReference(item.optJSONObject("reply_message"), profiles,
                        item.optInt("reply_to", 0)));
                message.setForwardedMessages(parseReferences(item.optJSONArray("fwd_messages"), profiles));

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

    private List<MessageAttachment> parseAttachments(JSONArray attachments) {
        List<MessageAttachment> parsed = new ArrayList<>();
        if (attachments == null) return parsed;

        for (int i = 0; i < attachments.length(); i++) {
            JSONObject attachment = attachments.optJSONObject(i);
            if (attachment == null) continue;

            String type = attachment.optString("type", "");
            if (type.isEmpty()) {
                for (String candidate : new String[]{"photo", "video", "audio", "doc", "wall", "link", "sticker", "gift", "poll"}) {
                    if (attachment.optJSONObject(candidate) != null) {
                        type = candidate;
                        break;
                    }
                }
            }
            if (type.isEmpty()) type = "attachment";
            JSONObject data = attachment.optJSONObject(type);
            if (data == null) data = attachment;

            String imageUrl = "";
            String title = "";
            String subtitle = "";
            org.nikanikoo.flux.data.models.Video parsedVideo = null;
            Audio parsedAudio = null;
            if ("photo".equals(type)) {
                imageUrl = ImageUtils.extractOptimalImageUrl(data);
            } else if ("video".equals(type)) {
                parsedVideo = org.nikanikoo.flux.data.managers.VideoManager.parseVideo(data);
                if (parsedVideo != null) {
                    imageUrl = parsedVideo.getImage();
                    title = parsedVideo.getTitle();
                    subtitle = parsedVideo.getFormattedDuration();
                }
            } else if ("audio".equals(type)) {
                parsedAudio = AttachmentProcessor.parseAudio(data);
                if (parsedAudio != null) {
                    title = parsedAudio.getTitle();
                    subtitle = parsedAudio.getArtist();
                } else {
                    title = data.optString("title", "Audio");
                    subtitle = data.optString("artist", "");
                }
            } else if ("doc".equals(type)) {
                title = data.optString("title", "Document");
                subtitle = data.optString("ext", "");
                JSONObject preview = data.optJSONObject("preview");
                if (preview != null) {
                    imageUrl = ImageUtils.extractOptimalImageUrl(preview.optJSONObject("photo"));
                }
            } else if ("wall".equals(type)) {
                title = data.optString("author_name", "Post");
                subtitle = data.optString("text", "");
                JSONArray postAttachments = data.optJSONArray("attachments");
                imageUrl = firstPhotoUrl(postAttachments);
                if (imageUrl.isEmpty()) {
                    JSONObject layout = data.optJSONObject("layout");
                    JSONObject firstTile = layout != null && layout.optJSONArray("tiles") != null
                            ? layout.optJSONArray("tiles").optJSONObject(0) : null;
                    JSONObject tilePhoto = firstTile == null ? null : firstTile.optJSONObject("photo");
                    imageUrl = ImageUtils.extractOptimalImageUrl(tilePhoto);
                }
            } else if ("gift".equals(type)) {
                title = data.optString("message", "");
                JSONObject giftData = data.optJSONObject("gift");
                if (giftData == null) giftData = data;
                imageUrl = giftData.optString("thumb_256", "");
                if (imageUrl.isEmpty()) imageUrl = giftData.optString("thumb_96", "");
                if (imageUrl.isEmpty()) imageUrl = giftData.optString("thumb_48", "");
            } else if ("link".equals(type)) {
                title = data.optString("title", "");
                if (title.isEmpty()) title = data.optString("caption", "");
                subtitle = data.optString("description", "");
                if (subtitle.isEmpty()) subtitle = data.optString("caption", "");
            } else if ("sticker".equals(type)) {
                imageUrl = data.optString("photo_128", "");
                if (imageUrl.isEmpty()) imageUrl = data.optString("photo_256", "");
                if (imageUrl.isEmpty()) imageUrl = data.optString("photo_64", "");
                if (imageUrl.isEmpty()) {
                    JSONArray images = data.optJSONArray("images");
                    if (images != null && images.length() > 0) {
                        JSONObject image = images.optJSONObject(0);
                        if (image != null) imageUrl = image.optString("url", "");
                    }
                }
                title = data.optString("emoji", "Sticker");
            } else {
                title = type;
            }
            String url = "doc".equals(type)
                    ? data.optString("url", data.optString("private_url", ""))
                    : "link".equals(type) ? data.optString("url", "") : "";
            int navigationOwnerId = "doc".equals(type) ? data.optInt("owner_id", 0) : 0;
            int navigationMediaId = "doc".equals(type) ? data.optInt("id", 0) : 0;
            String navigationAccessKey = "doc".equals(type) ? data.optString("access_key", null) : null;
            String targetUrl = "";
            String attachmentId = "";
            int attachmentOwnerId = data.optInt("owner_id", 0);
            int attachmentMediaId = data.optInt("id", 0);
            String attachmentAccessKey = data.optString("access_key", "");
            if ("wall".equals(type)) {
                int ownerId = data.optInt("owner_id", data.optInt("to_id", 0));
                int postId = data.optInt("id", 0);
                if (ownerId != 0 && postId != 0) targetUrl = "wall" + ownerId + "_" + postId;
                navigationOwnerId = ownerId;
                navigationMediaId = postId;
            }
            if ("video".equals(type) && parsedVideo != null) {
                attachmentOwnerId = parsedVideo.getOwnerId();
                attachmentMediaId = parsedVideo.getId();
                attachmentAccessKey = parsedVideo.getAccessKey();
            }
            if ("wall".equals(type)) {
                attachmentId = targetUrl;
            } else if (attachmentOwnerId != 0 && attachmentMediaId != 0
                    && ("photo".equals(type) || "video".equals(type) || "audio".equals(type) || "doc".equals(type))) {
                attachmentId = type + attachmentOwnerId + "_" + attachmentMediaId
                        + (!attachmentAccessKey.isEmpty() ? "_" + attachmentAccessKey : "");
            }
            MessageAttachment messageAttachment = new MessageAttachment(type, imageUrl, title, subtitle,
                    parsedVideo, url, targetUrl, navigationOwnerId, navigationMediaId,
                    navigationAccessKey, attachmentId);
            messageAttachment.setAudio(parsedAudio);
            parsed.add(messageAttachment);
        }
        return parsed;
    }

    private String firstPhotoUrl(JSONArray attachments) {
        if (attachments == null) return "";
        for (int i = 0; i < attachments.length(); i++) {
            JSONObject item = attachments.optJSONObject(i);
            if (item != null && "photo".equals(item.optString("type"))) {
                String url = ImageUtils.extractOptimalImageUrl(item.optJSONObject("photo"));
                if (!url.isEmpty()) return url;
            }
        }
        return "";
    }

    private List<MessageReference> parseReferences(JSONArray refs, JSONArray profiles) {
        List<MessageReference> result = new ArrayList<>();
        if (refs == null) return result;
        for (int i = 0; i < refs.length(); i++) {
            JSONObject ref = refs.optJSONObject(i);
            if (ref != null) result.add(parseReference(ref, profiles, 0));
        }
        return result;
    }

    private MessageReference parseReference(JSONObject ref, JSONArray profiles, int fallbackId) {
        if (ref == null) return fallbackId == 0 ? null : new MessageReference(fallbackId, "", "");
        int id = ref.optInt("id", ref.optInt("mid", ref.optInt("conversation_message_id", fallbackId)));
        int senderId = ref.optInt("from_id", ref.optInt("user_id", ref.optInt("uid", 0)));
        String sender = ref.optString("sender_name", ref.optString("author_name", ""));
        if (sender.isEmpty() && profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.optJSONObject(i);
                if (profile != null && profile.optInt("id", 0) == Math.abs(senderId)) {
                    sender = (profile.optString("first_name", "") + " " + profile.optString("last_name", "")).trim();
                    break;
                }
            }
        }
        String text = ref.optString("text", "");
        if (text.isEmpty()) text = ref.optString("body", "");
        return new MessageReference(id, sender, text);
    }

    // Отправка сообщения
    public void sendMessage(int peerId, String message, SendMessageCallback callback) {
        sendMessage(peerId, message, null, callback);
    }

    public void sendMessage(int peerId, String message, String attachment, SendMessageCallback callback) {
        sendMessage(peerId, message, attachment, 0, callback);
    }

    public void sendMessage(int peerId, String message, String attachment, int replyToMessageId,
                            SendMessageCallback callback) {
        sendMessage(peerId, message, attachment, replyToMessageId, 0, callback);
    }

    public void sendMessage(int peerId, String message, String attachment, int replyToMessageId,
                            int stickerId, SendMessageCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("message", message);
        params.put("random_id", String.valueOf(System.currentTimeMillis()));
        if (stickerId > 0) {
            params.put("sticker_id", String.valueOf(stickerId));
        }
        if (attachment != null && !attachment.isEmpty()) {
            params.put("attachment", attachment);
        }
        if (replyToMessageId > 0) {
            params.put("reply_to", String.valueOf(replyToMessageId));
        }

        api.callMethod("messages.send", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    int messageId = response.getInt("response");
                    callback.onSuccess(messageId);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing send message response", e);
                    callback.onError("Не удалось разобрать ответ при отправке: " + response);
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error sending message: " + error);
                callback.onError(error);
            }
        });
    }

    public void editMessage(int peerId, int messageId, String text, String attachments,
                            MessageActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("message_id", String.valueOf(messageId));
        params.put("message", text != null ? text : "");
        params.put("attachment", attachments != null ? attachments : "");
        params.put("keep_forward_messages", "1");
        api.callMethod("messages.edit", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void deleteMessage(int peerId, int messageId, boolean deleteForAll,
                              MessageActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("message_ids", String.valueOf(messageId));
        params.put("delete_for_all", deleteForAll ? "1" : "0");
        api.callMethod("messages.delete", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void forwardMessage(int peerId, int messageId, MessageActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("forward_messages", String.valueOf(messageId));
        params.put("random_id", String.valueOf(System.currentTimeMillis()));
        api.callMethod("messages.send", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Отправка статуса тайпинга
    public void sendTypingActivity(int peerId, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("type", "typing");

        api.callMethod("messages.setActivity", params, callback);
    }

    public void getConversationMembers(int peerId, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("extended", "1");
        api.callMethod("messages.getConversationMembers", params, callback);
    }

    public void addChatUser(int peerId, int userId, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("user_id", String.valueOf(userId));
        api.callMethod("messages.addChatUser", params, callback);
    }

    public void removeChatUser(int peerId, int userId, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("user_id", String.valueOf(userId));
        api.callMethod("messages.removeChatUser", params, callback);
    }

    public void editChat(int chatId, String title, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("chat_id", String.valueOf(chatId));
        params.put("title", title);
        api.callMethod("messages.editChat", params, callback);
    }

    public void setChatPhoto(int chatId, String file, String hash, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("chat_id", String.valueOf(chatId));
        params.put("file", file);
        params.put("hash", hash);
        api.callMethod("messages.setChatPhoto", params, callback);
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
                    JSONObject chatSettings = conversationObj.optJSONObject("chat_settings");
                    if (chatSettings != null) {
                        title = chatSettings.optString("title", "Групповой чат");
                        JSONObject chatPhoto = chatSettings.optJSONObject("photo");
                        if (chatPhoto != null) {
                            photo = chatPhoto.optString("photo_50", chatPhoto.optString("photo_200", ""));
                        } else {
                            photo = chatSettings.optString("photo_50", "");
                        }
                    }
                }

                String lastMessageText = "";
                long lastMessageDate = 0;
                if (lastMessage != null) {
                    lastMessageText = createConversationPreview(lastMessage, peerType == 2, profiles);
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

	/**
	* Создаёт превью для раздела сообщений
	* Если это обычный текст - пишется его содержимое, если вложение, то пишется что за вложение
	* message сообщение, для которого создаётся превью
	*/
    private String createConversationPreview(JSONObject message, boolean groupChat, JSONArray profiles) {
        String preview = createConversationPreviewContent(message);
        String senderName = groupChat
                ? message.optString("sender_name", message.optString("author_name", "")) : "";
        if (groupChat && senderName.isEmpty()) {
            senderName = findProfileName(profiles,
                    message.optInt("from_id", message.optInt("user_id", message.optInt("uid", 0))));
        }
        return addConversationSenderPrefix(preview, message.optInt("out", 0) == 1, groupChat, senderName);
    }

    private String createConversationPreviewContent(JSONObject message) {
        JSONObject action = message.optJSONObject("action");
        String actionType = action != null
                ? action.optString("type", "")
                : message.optString("action_type", message.optString("action", ""));
        if ("coins_transfer".equals(actionType) && message.optInt("out", 0) == 1) {
            int amount = action != null
                    ? action.optInt("member_id", message.optInt("action_mid", 0))
                    : message.optInt("action_mid", 0);
            if (amount > 0) return createOutgoingCoinsTransferPreview(amount);
        }

        String text = message.optString("text", "").trim();
        if (text.isEmpty()) text = message.optString("body", "").trim();
        JSONArray attachments = message.optJSONArray("attachments");
        List<String> attachmentTypes = new ArrayList<>();
        if (attachments != null) {
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.optJSONObject(i);
                if (attachment == null) continue;

                String type = attachment.optString("type", "");
                if (type.isEmpty()) {
                    for (String candidate : new String[]{"photo", "video", "audio", "doc", "wall", "sticker", "gift", "poll"}) {
                        if (attachment.optJSONObject(candidate) != null) {
                            type = candidate;
                            break;
                        }
                    }
                }
                attachmentTypes.add(type);
            }
        }
        return formatConversationPreview(text, attachmentTypes);
    }

    public String createConversationPreview(Message message) {
        if (message == null) return "";
        String preview;
        if ("coins_transfer".equals(message.getActionType()) && message.isOut() && message.getActionMid() > 0) {
            preview = createOutgoingCoinsTransferPreview(message.getActionMid());
        } else {
            List<String> attachmentTypes = new ArrayList<>();
            for (MessageAttachment attachment : message.getAttachments()) {
                if (attachment != null) attachmentTypes.add(attachment.getType());
            }
            preview = formatConversationPreview(message.getText(), attachmentTypes);
        }
        boolean groupChat = message.getUserId() > 2000000000;
        return addConversationSenderPrefix(preview, message.isOut(), groupChat, message.getUserName());
    }

    private String addConversationSenderPrefix(String preview, boolean outgoing, boolean groupChat, String senderName) {
        if (preview == null || preview.trim().isEmpty()) return preview == null ? "" : preview;
        if (groupChat) {
            if (senderName == null || senderName.trim().isEmpty()) return preview;
            return context.getString(R.string.conversation_preview_sender, senderName.trim(), preview);
        }
        // System previews such as coin transfers already include their own "Вы" wording.
        if (outgoing && !preview.startsWith("Вы ")) {
            return context.getString(R.string.conversation_preview_sender, context.getString(R.string.conversation_preview_you), preview);
        }
        return preview;
    }

    private String findProfileName(JSONArray profiles, int userId) {
        if (profiles == null || userId == 0) return "";
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile != null && profile.optInt("id", 0) == Math.abs(userId)) {
                return (profile.optString("first_name", "") + " " + profile.optString("last_name", "")).trim();
            }
        }
        return "";
    }

    private String createOutgoingCoinsTransferPreview(int amount) {
        String voteWord = context.getResources().getQuantityString(R.plurals.vote_count, amount);
        return context.getString(R.string.coins_transfer_outgoing, amount, voteWord);
    }

    private String formatConversationPreview(String text, List<String> attachmentTypes) {
        text = text == null ? "" : text.trim();
        if (attachmentTypes == null || attachmentTypes.isEmpty()) return text;

        Map<Integer, Integer> attachmentCounts = new LinkedHashMap<>();
        for (String type : attachmentTypes) {
            int labelRes;
            switch (type == null ? "" : type) {
                case "photo": labelRes = R.string.conversation_preview_photo; break;
                case "video": labelRes = R.string.conversation_preview_video; break;
                case "audio": labelRes = R.string.conversation_preview_audio; break;
                case "doc": labelRes = R.string.conversation_preview_document; break;
                case "wall": labelRes = R.string.conversation_preview_post; break;
                case "sticker": labelRes = R.string.conversation_preview_sticker; break;
                case "gift": labelRes = R.string.conversation_preview_gift; break;
                case "poll": labelRes = R.string.conversation_preview_poll; break;
                default: labelRes = R.string.conversation_preview_attachment; break;
            }
            Integer count = attachmentCounts.get(labelRes);
            attachmentCounts.put(labelRes, count == null ? 1 : count + 1);
        }

        if (attachmentCounts.isEmpty()) return text;

        List<String> labels = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : attachmentCounts.entrySet()) {
            String label = context.getString(entry.getKey());
            int count = entry.getValue();
            labels.add(count > 1
                    ? context.getString(R.string.conversation_preview_attachment_count, label, count)
                    : label);
        }
        String attachmentPreview = android.text.TextUtils.join(", ", labels);
        return text.isEmpty()
                ? attachmentPreview
                : context.getString(R.string.conversation_preview_with_text, attachmentPreview, text);
    }

    // Пометить сообщения как прочитанные
    public void markAsRead(int peerId, int startMessageId, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        if (startMessageId > 0) {
            params.put("start_message_id", String.valueOf(startMessageId));
        }

        api.callMethod("messages.markAsRead", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Marked messages as read for peer " + peerId
                        + " through message " + startMessageId);
                if (callback != null) callback.onSuccess(response);
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Could not mark messages as read for peer " + peerId + ": " + error);
                if (callback != null) callback.onError(error);
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
