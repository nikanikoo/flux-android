package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.data.models.AudioPlaylist;

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

    public interface PlaylistsCallback {
        void onSuccess(List<AudioPlaylist> playlists, int totalCount);
        void onError(String error);
    }

    public interface PlaylistCallback {
        void onSuccess(AudioPlaylist playlist);
        void onError(String error);
    }

    public interface LyricsCallback {
        void onSuccess(String lyricsText);
        void onError(String error);
    }

    public interface CreatePlaylistCallback {
        void onSuccess(int playlistId);
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

    public void getPlaylists(int ownerId, int offset, int count, PlaylistsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));

        api.callMethod("audio.getPlaylists", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<AudioPlaylist> playlists = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject playlistJson = items.optJSONObject(i);
                        if (playlistJson != null) {
                            playlists.add(AudioPlaylist.fromJson(playlistJson));
                        }
                    }
                    callback.onSuccess(playlists, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга плейлистов", e);
                    callback.onError("Ошибка парсинга плейлистов");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getPlaylistById(int ownerId, int playlistId, PlaylistCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("playlist_id", String.valueOf(playlistId));

        api.callMethod("audio.getPlaylistById", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    AudioPlaylist playlist = AudioPlaylist.fromJson(responseObj);
                    callback.onSuccess(playlist);
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга плейлиста", e);
                    callback.onError("Ошибка парсинга плейлиста");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void searchPlaylists(String query, int offset, int limit, int order, int fromMe, PlaylistsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("q", query);
        params.put("offset", String.valueOf(offset));
        params.put("limit", String.valueOf(limit));
        params.put("order", String.valueOf(order));
        params.put("from_me", String.valueOf(fromMe));

        api.callMethod("audio.searchAlbums", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<AudioPlaylist> playlists = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject playlistJson = items.optJSONObject(i);
                        if (playlistJson != null) {
                            playlists.add(AudioPlaylist.fromJson(playlistJson));
                        }
                    }
                    callback.onSuccess(playlists, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга поиска альбомов", e);
                    callback.onError("Ошибка парсинга поиска альбомов");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getPopular(int genreId, int offset, int count, AudioCallback callback) {
        Map<String, String> params = new HashMap<>();
        if (genreId > 0) {
            params.put("genre_id", String.valueOf(genreId));
        }
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));

        api.callMethod("audio.getPopular", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Audio> audios = parseAudios(items);
                    callback.onSuccess(audios, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга популярной музыки", e);
                    callback.onError("Ошибка парсинга популярной музыки");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getFeed(int genreId, int offset, int count, AudioCallback callback) {
        Map<String, String> params = new HashMap<>();
        if (genreId > 0) {
            params.put("genre_id", String.valueOf(genreId));
        }
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));

        api.callMethod("audio.getFeed", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Audio> audios = parseAudios(items);
                    callback.onSuccess(audios, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга ленты музыки", e);
                    callback.onError("Ошибка парсинга ленты музыки");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void searchAudioWithSort(String query, int offset, int count, int sort, AudioCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("q", query);
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("sort", String.valueOf(sort));

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
                    Logger.e(TAG, "Ошибка парсинга поиска аудио с сортировкой", e);
                    callback.onError("Ошибка парсинга поиска аудио с сортировкой");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getPlaylistAudios(int ownerId, int playlistId, String accessKey, int offset, int count, AudioCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("album_id", String.valueOf(playlistId));
        if (accessKey != null && !accessKey.isEmpty()) {
            params.put("access_key", accessKey);
        }
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
                    Logger.e(TAG, "Ошибка парсинга аудио плейлиста", e);
                    callback.onError("Ошибка парсинга аудио плейлиста");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getLyrics(int lyricsId, LyricsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("lyrics_id", String.valueOf(lyricsId));

        api.callMethod("audio.getLyrics", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.optJSONObject("response");
                    String text = responseObj != null ? responseObj.optString("text", "") : "";
                    callback.onSuccess(text);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing lyrics", e);
                    callback.onError("Ошибка обработки текста песни");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void createPlaylist(String title, String description, int groupId, CreatePlaylistCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("title", title);
        if (description != null && !description.isEmpty()) {
            params.put("description", description);
        }
        if (groupId > 0) {
            params.put("group_id", String.valueOf(groupId));
        }

        api.callMethod("audio.addAlbum", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    int playlistId = 0;
                    if (response.has("response")) {
                        Object respObj = response.get("response");
                        if (respObj instanceof Number) {
                            playlistId = ((Number) respObj).intValue();
                        } else if (respObj instanceof JSONObject) {
                            playlistId = ((JSONObject) respObj).optInt("album_id", ((JSONObject) respObj).optInt("id", 0));
                        }
                    }
                    callback.onSuccess(playlistId);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing create playlist response", e);
                    callback.onError("Ошибка обработки ответа");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void editPlaylist(int playlistId, String title, String description, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("album_id", String.valueOf(playlistId));
        if (title != null) params.put("title", title);
        if (description != null) params.put("description", description);

        api.callMethod("audio.editAlbum", params, new OpenVKApi.ApiCallback() {
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

    public void deletePlaylist(int playlistId, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("album_id", String.valueOf(playlistId));

        api.callMethod("audio.deleteAlbum", params, new OpenVKApi.ApiCallback() {
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

    public void addTracksToPlaylist(int playlistId, String audioIds, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("album_id", String.valueOf(playlistId));
        params.put("audio_ids", audioIds);

        api.callMethod("audio.moveToAlbum", params, new OpenVKApi.ApiCallback() {
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

    public void removeTracksFromPlaylist(int playlistId, String audioIds, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("album_id", String.valueOf(playlistId));
        params.put("audio_ids", audioIds);

        api.callMethod("audio.removeFromAlbum", params, new OpenVKApi.ApiCallback() {
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

    public void editAudio(int ownerId, int audioId, String artist, String title, String lyrics, int genreId, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("audio_id", String.valueOf(audioId));
        if (artist != null) params.put("artist", artist);
        if (title != null) params.put("title", title);
        if (lyrics != null) params.put("text", lyrics);
        if (genreId > 0) params.put("genre_id", String.valueOf(genreId));

        api.callMethod("audio.edit", params, new OpenVKApi.ApiCallback() {
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

    public void sendListenBeacon(int audioId, int groupId, AudioActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("aid", String.valueOf(audioId));
        if (groupId > 0) {
            params.put("gid", String.valueOf(groupId));
        }

        api.callMethod("audio.beacon", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (callback != null) callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
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
