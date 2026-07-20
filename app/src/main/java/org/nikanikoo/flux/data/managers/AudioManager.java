package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioManager extends BaseManager<AudioManager> {
    private static final String TAG = "AudioManager";

    public AudioManager(Context context) {
        super(context);
    }

    public static AudioManager getInstance(Context context) {
        return BaseManager.getInstance(AudioManager.class, context);
    }

    public interface AudioCallback {
        void onSuccess(List<Audio> audios, int totalCount);
        void onError(String error);
    }

    public interface AudioActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getAudio(int ownerId, int offset, int count, AudioCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));

        api.callMethod("audio.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Audio> audios = parseAudios(items);
                    callback.onSuccess(audios, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга аудио", e);
                    callback.onError("Ошибка парсинга аудио");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void searchAudio(String query, int offset, int count, AudioCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("q", query);
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));

        api.callMethod("audio.search", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Audio> audios = parseAudios(items);
                    callback.onSuccess(audios, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка поиска аудио", e);
                    callback.onError("Ошибка поиска аудио");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void addAudio(int audioId, int ownerId, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("audio_id", String.valueOf(audioId));
        params.put("owner_id", String.valueOf(ownerId));

        api.callMethod("audio.add", params, new OpenVKApi.ApiCallback() {
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

    public void deleteAudio(int audioId, int ownerId, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("audio_id", String.valueOf(audioId));
        params.put("owner_id", String.valueOf(ownerId));

        api.callMethod("audio.delete", params, new OpenVKApi.ApiCallback() {
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

    private List<Audio> parseAudios(JSONArray items) {
        if (items == null || items.length() == 0) {
            return Collections.emptyList();
        }

        List<Audio> audios = new ArrayList<>(items.length());

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }

            Audio audio = parseAudio(item);
            if (audio != null) {
                audios.add(audio);
            } else {
                Logger.w(TAG, "Ошибка парсинга аудио, аудио было пропущено: " + item);
            }
        }

        return audios;
    }


    private Audio parseAudio(JSONObject json) {
        try {
            Audio audio = new Audio();
            audio.setUniqueId(json.optString("unique_id", ""));
            audio.setId(json.optInt("id", 0));
            audio.setOwnerId(json.optInt("owner_id", 0));
            audio.setArtist(json.optString("artist", "Неизвестный исполнитель"));
            audio.setTitle(json.optString("title", "Без названия"));
            audio.setDuration(json.optInt("duration", 0));
            audio.setUrl(json.optString("url", ""));
            audio.setCoverUrl(json.optString("cover_url", ""));
            audio.setManifest(json.optString("manifest", ""));
            audio.setGenreId(json.optInt("genre_id", 0));
            audio.setGenreStr(json.optString("genre_str", ""));
            audio.setLyrics_id(json.optInt("lyrics", 0));
            audio.setAdded(json.optBoolean("added", false));
            audio.setEditable(json.optBoolean("editable", false));
            audio.setSearchable(json.optBoolean("searchable", true));
            audio.setExplicit(json.optBoolean("explicit", false));
            audio.setWithdrawn(json.optBoolean("withdrawn", false));
            audio.setReady(json.optBoolean("ready", true));
            return audio;
        } catch (Exception e) {
            Logger.e(TAG, "Ошибка парсинга аудио", e);
            return null;
        }
    }
}
