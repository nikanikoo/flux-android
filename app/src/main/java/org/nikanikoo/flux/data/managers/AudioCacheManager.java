package org.nikanikoo.flux.data.managers;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.SSLHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioCacheManager extends BaseManager<AudioCacheManager> {
    private static final String TAG = "AudioCacheManager";
    private static final String PREF_NAME = "audio_cache";
    private static final String KEY_ITEMS = "items";
    public static final String ACTION_AUDIO_CACHE_CHANGED =
            "org.nikanikoo.flux.ACTION_AUDIO_CACHE_CHANGED";

    private static final String KEY_SAVE_ON_LISTENING = "save_on_listening";
    private static final String KEY_CACHE_LIMIT = "cache_limit";

    private final SharedPreferences prefs;
    private final File audioCacheDir;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();

    public interface DownloadCallback {
        void onSuccess(File file);
        void onError(String error);
    }

    public AudioCacheManager(Context context) {
        super(context);
        prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        audioCacheDir = new File(this.context.getFilesDir(), "audio");
        if (!audioCacheDir.exists()) {
            audioCacheDir.mkdirs();
        }

        File[] cacheFiles = this.context.getCacheDir().listFiles();
        if (cacheFiles != null) {
            for (File file : cacheFiles) {
                if (file.getName().startsWith("temp_") && file.getName().endsWith(".mp3")) {
                    file.delete();
                }
            }
        }
    }

    public static AudioCacheManager getInstance(Context context) {
        return BaseManager.getInstance(AudioCacheManager.class, context);
    }

    public boolean isDownloaded(Audio audio) {
        File file = getCachedFile(audio);
        return file != null && file.exists();
    }

    public File getCachedFile(Audio audio) {
        if (audio == null) {
            return null;
        }

        JSONObject item = getItem(getAudioKey(audio));
        if (item == null) {
            return null;
        }

        String fileName = item.optString("fileName", "");
        if (fileName.isEmpty()) {
            return null;
        }

        File file = new File(audioCacheDir, fileName);
        return file.exists() ? file : null;
    }

    public List<Audio> getDownloadedAudios() {
        List<Audio> audios = new ArrayList<>();
        JSONArray items = getItems();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }

            File file = new File(audioCacheDir, item.optString("fileName", ""));
            if (!file.exists()) {
                continue;
            }

            Audio audio = new Audio();
            audio.setUniqueId(item.optString("uniqueId", ""));
            audio.setId(item.optInt("id", 0));
            audio.setOwnerId(item.optInt("ownerId", 0));
            audio.setArtist(item.optString("artist", ""));
            audio.setTitle(item.optString("title", ""));
            audio.setDuration(item.optInt("duration", 0));
            audio.setUrl(item.optString("url", ""));
            audio.setManifest(item.optString("manifest", ""));
            audio.setCoverUrl(item.optString("coverUrl", ""));
            audio.setGenreId(item.optInt("genreId", 0));
            audio.setGenreStr(item.optString("genreStr", ""));
            audio.setLyrics_id(item.optInt("lyrics", 0));
            audio.setAdded(item.optBoolean("added", false));
            audio.setEditable(item.optBoolean("editable", false));
            audio.setSearchable(item.optBoolean("searchable", true));
            audio.setExplicit(item.optBoolean("explicit", false));
            audio.setWithdrawn(item.optBoolean("withdrawn", false));
            audio.setReady(item.optBoolean("ready", true));
            audios.add(audio);
        }
        return audios;
    }

    public void downloadAudio(Audio audio, DownloadCallback callback) {
        downloadExecutor.execute(() -> {
            try {
                File file = downloadAudioBlocking(audio);
                callback.onSuccess(file);
            } catch (Exception e) {
                Logger.e(TAG, "Audio download failed", e);
                callback.onError(e.getMessage() != null ? e.getMessage() : "Download failed");
            }
        });
    }

    public File downloadAudioBlocking(Audio audio) throws IOException {
        if (audio == null || audio.getUrl() == null || audio.getUrl().isEmpty()) {
            throw new IOException("Invalid audio URL");
        }

        File cachedFile = getCachedFile(audio);
        if (cachedFile != null) {
            return cachedFile;
        }

        String audioKey = getAudioKey(audio);
        String fileName = sha256(audioKey) + ".mp3";
        File targetFile = new File(audioCacheDir, fileName);
        File tempFile = new File(audioCacheDir, fileName + ".download");
        if (tempFile.exists()) {
            tempFile.delete();
        }

        okhttp3.OkHttpClient.Builder clientBuilder = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS);
        SSLHelper.configureToIgnoreSSL(clientBuilder);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(audio.getUrl())
                .build();

        long bytesWritten = 0;
        long expectedLength = -1;
        try (okhttp3.Response response = clientBuilder.build().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP error: " + response.code());
            }

            expectedLength = response.body().contentLength();
            try (java.io.InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesWritten += bytesRead;
                }
            }
        } catch (IOException e) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw e;
        }

        if (bytesWritten <= 0) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw new IOException("Downloaded audio is empty");
        }

        if (expectedLength >= 0 && bytesWritten != expectedLength) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw new IOException("Incomplete audio download");
        }

        if (targetFile.exists()) {
            targetFile.delete();
        }
        if (!tempFile.renameTo(targetFile)) {
            throw new IOException("Could not save audio file");
        }

        saveItem(audio, fileName, targetFile.length());
        evictCacheIfExceeded();
        notifyCacheChanged();
        return targetFile;
    }

    public boolean deleteAudio(Audio audio) {
        if (audio == null) {
            return false;
        }

        String audioKey = getAudioKey(audio);
        JSONArray items = getItems();
        JSONArray updatedItems = new JSONArray();
        boolean removed = false;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }

            if (audioKey.equals(item.optString("key", ""))) {
                File file = new File(audioCacheDir, item.optString("fileName", ""));
                if (file.exists()) {
                    file.delete();
                }
                removed = true;
            } else {
                updatedItems.put(item);
            }
        }

        prefs.edit().putString(KEY_ITEMS, updatedItems.toString()).apply();
        if (removed) {
            notifyCacheChanged();
        }
        return removed;
    }

    private void saveItem(Audio audio, String fileName, long size) {
        String audioKey = getAudioKey(audio);
        JSONArray items = getItems();
        JSONArray updatedItems = new JSONArray();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && !audioKey.equals(item.optString("key", ""))) {
                updatedItems.put(item);
            }
        }

        JSONObject item = new JSONObject();
        try {
            item.put("key", audioKey);
            item.put("fileName", fileName);
            item.put("size", size);
            item.put("savedAt", System.currentTimeMillis());
            item.put("uniqueId", audio.getUniqueId());
            item.put("id", audio.getId());
            item.put("ownerId", audio.getOwnerId());
            item.put("artist", audio.getArtist());
            item.put("title", audio.getTitle());
            item.put("duration", audio.getDuration());
            item.put("url", audio.getUrl());
            item.put("manifest", audio.getManifest());
            item.put("coverUrl", audio.getCoverUrl());
            item.put("genreId", audio.getGenreId());
            item.put("genreStr", audio.getGenreStr());
            item.put("lyrics_id", audio.getLyrics_id());
            item.put("added", audio.isAdded());
            item.put("editable", audio.isEditable());
            item.put("searchable", audio.isSearchable());
            item.put("explicit", audio.isExplicit());
            item.put("withdrawn", audio.isWithdrawn());
            item.put("ready", audio.isReady());
            updatedItems.put(item);
        } catch (Exception e) {
            Logger.e(TAG, "Could not save cached audio metadata", e);
        }

        prefs.edit().putString(KEY_ITEMS, updatedItems.toString()).apply();
    }

    private JSONObject getItem(String audioKey) {
        JSONArray items = getItems();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && audioKey.equals(item.optString("key", ""))) {
                return item;
            }
        }
        return null;
    }

    private JSONArray getItems() {
        try {
            return new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private String getAudioKey(Audio audio) {
        if (audio.getUniqueId() != null && !audio.getUniqueId().isEmpty()) {
            return audio.getUniqueId();
        }
        return audio.getOwnerId() + "_" + audio.getId() + "_" + audio.getUrl();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private void notifyCacheChanged() {
        android.content.Intent intent = new android.content.Intent(ACTION_AUDIO_CACHE_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public boolean isSaveOnListeningEnabled() {
        return prefs.getBoolean(KEY_SAVE_ON_LISTENING, false);
    }

    public void setSaveOnListeningEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SAVE_ON_LISTENING, enabled).apply();
    }

    public long getCacheLimit() {
        return prefs.getLong(KEY_CACHE_LIMIT, -1L);
    }

    public void setCacheLimit(long limitBytes) {
        prefs.edit().putLong(KEY_CACHE_LIMIT, limitBytes).apply();
        downloadExecutor.execute(this::evictCacheIfExceeded);
    }

    public long getCacheDirSize() {
        long size = 0;
        File[] files = audioCacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                }
            }
        }
        return size;
    }

    public void evictCacheIfExceeded() {
        long limit = getCacheLimit();
        if (limit <= 0) {
            return;
        }

        while (getCacheDirSize() > limit) {
            JSONArray items = getItems();
            if (items.length() == 0) {
                break;
            }

            JSONObject oldestItem = null;
            long oldestTime = Long.MAX_VALUE;
            int oldestIndex = -1;

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                long savedAt = item.optLong("savedAt", 0);
                if (savedAt < oldestTime) {
                    oldestTime = savedAt;
                    oldestItem = item;
                    oldestIndex = i;
                }
            }

            if (oldestItem != null) {
                String fileName = oldestItem.optString("fileName", "");
                if (!fileName.isEmpty()) {
                    File file = new File(audioCacheDir, fileName);
                    if (file.exists()) {
                        file.delete();
                    }
                }
                JSONArray updatedItems = new JSONArray();
                for (int i = 0; i < items.length(); i++) {
                    if (i != oldestIndex) {
                        JSONObject it = items.optJSONObject(i);
                        if (it != null) {
                            updatedItems.put(it);
                        }
                    }
                }
                prefs.edit().putString(KEY_ITEMS, updatedItems.toString()).apply();
            } else {
                break;
            }
        }
        notifyCacheChanged();
    }

    public File downloadAudioToTempBlocking(Audio audio) throws IOException {
        if (audio == null || audio.getUrl() == null || audio.getUrl().isEmpty()) {
            throw new IOException("Invalid audio URL");
        }

        String audioKey = getAudioKey(audio);
        String fileName = "temp_" + sha256(audioKey) + ".mp3";
        File tempFile = new File(context.getCacheDir(), fileName);
        if (tempFile.exists()) {
            return tempFile;
        }

        okhttp3.OkHttpClient.Builder clientBuilder = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS);
        SSLHelper.configureToIgnoreSSL(clientBuilder);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(audio.getUrl())
                .build();

        long bytesWritten = 0;
        long expectedLength = -1;
        File downloadingFile = new File(context.getCacheDir(), fileName + ".download");
        if (downloadingFile.exists()) {
            downloadingFile.delete();
        }

        try (okhttp3.Response response = clientBuilder.build().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP error: " + response.code());
            }

            expectedLength = response.body().contentLength();
            try (java.io.InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(downloadingFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesWritten += bytesRead;
                }
            }
        } catch (IOException e) {
            if (downloadingFile.exists()) {
                downloadingFile.delete();
            }
            throw e;
        }

        if (bytesWritten <= 0) {
            if (downloadingFile.exists()) {
                downloadingFile.delete();
            }
            throw new IOException("Downloaded audio is empty");
        }

        if (expectedLength >= 0 && bytesWritten != expectedLength) {
            if (downloadingFile.exists()) {
                downloadingFile.delete();
            }
            throw new IOException("Incomplete audio download");
        }

        if (tempFile.exists()) {
            tempFile.delete();
        }
        if (!downloadingFile.renameTo(tempFile)) {
            throw new IOException("Could not save temp audio file");
        }

        tempFile.deleteOnExit();
        return tempFile;
    }
}
