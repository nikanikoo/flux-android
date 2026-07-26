package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Album;
import org.nikanikoo.flux.data.models.Photo;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhotosManager extends BaseManager<PhotosManager> {

    private static final String TAG = "PhotosManager";

    private PhotosManager(Context context) {
        super(context);
    }

    public static PhotosManager getInstance(Context context) {
        return BaseManager.getInstance(PhotosManager.class, context);
    }

    public interface AlbumsCallback {
        void onSuccess(List<Album> albums, int totalCount);
        void onError(String error);
    }

    public interface PhotosCallback {
        void onSuccess(List<Photo> photos, int totalCount);
        void onError(String error);
    }

    public interface CreateAlbumCallback {
        void onSuccess(Album album);
        void onError(String error);
    }

    public interface EditAlbumCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getAlbums(int ownerId, int offset, int count, AlbumsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("need_covers", "1");
        params.put("need_system", "1");
        params.put("photo_sizes", "0");

        api.callMethod("photos.getAlbums", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Album> albums = parseAlbums(items);
                    Logger.d(TAG, "Loaded " + albums.size() + " albums, total: " + totalCount);
                    callback.onSuccess(albums, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing albums", e);
                    callback.onError("Ошибка обработки данных");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading albums: " + error);
                callback.onError(error);
            }
        });
    }

    public void getPhotos(int ownerId, String albumId, int offset, int count, PhotosCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("album_id", albumId);
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("photo_sizes", "1");
        params.put("extended", "0");

        api.callMethod("photos.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.optJSONArray("items");
                    List<Photo> photos = parsePhotos(items != null ? items : new JSONArray());
                    Logger.d(TAG, "Loaded " + photos.size() + " photos from album " + albumId);
                    callback.onSuccess(photos, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing photos", e);
                    callback.onError("Ошибка обработки данных");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading photos: " + error);
                callback.onError(error);
            }
        });
    }

    public void createAlbum(String title, String description, int groupId, CreateAlbumCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("title", title);
        params.put("description", description);
        if (groupId != 0) {
            params.put("group_id", String.valueOf(groupId));
        }

        api.callMethod("photos.createAlbum", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    Album album = parseAlbum(responseObj);
                    Logger.d(TAG, "Created album: " + album.getTitle());
                    callback.onSuccess(album);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing created album", e);
                    callback.onError("Ошибка обработки данных");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error creating album: " + error);
                callback.onError(error);
            }
        });
    }

    public void editAlbum(int albumId, int ownerId, String title, String description, EditAlbumCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("album_id", String.valueOf(albumId));
        params.put("owner_id", String.valueOf(ownerId));
        if (title != null) {
            params.put("title", title);
        }
        if (description != null) {
            params.put("description", description);
        }

        api.callMethod("photos.editAlbum", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Album edited successfully");
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error editing album: " + error);
                callback.onError(error);
            }
        });
    }

    private List<Album> parseAlbums(JSONArray items) throws Exception {
        List<Album> albums = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            Album album = parseAlbum(items.getJSONObject(i));
            if (album != null) albums.add(album);
        }
        return albums;
    }

    public static Album parseAlbum(JSONObject json) {
        try {
            Album album = new Album();
            album.setId(json.optInt("id", 0));
            album.setOwnerId(json.optInt("owner_id", 0));
            album.setTitle(json.optString("title", ""));
            album.setDescription(json.optString("description", ""));
            album.setSize(json.optInt("size", 0));
            album.setCreated(json.optLong("created", 0));
            album.setUpdated(json.optLong("updated", 0));
            album.setThumbId(json.optInt("thumb_id", 0));

            if (json.has("thumb_src") && !json.optString("thumb_src", "").isEmpty()) {
                album.setThumbSrc(json.optString("thumb_src", ""));
            } else if (json.has("sizes")) {
                JSONArray sizes = json.getJSONArray("sizes");
                String bestUrl = "";
                for (int i = 0; i < sizes.length(); i++) {
                    JSONObject sz = sizes.getJSONObject(i);
                    bestUrl = sz.optString("url", bestUrl);
                }
                album.setThumbSrc(bestUrl);
            } else if (json.has("photo_604")) {
                album.setThumbSrc(json.optString("photo_604", ""));
            } else if (json.has("photo_130")) {
                album.setThumbSrc(json.optString("photo_130", ""));
            }

            return album;
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing album", e);
            return null;
        }
    }

    private List<Photo> parsePhotos(JSONArray items) throws Exception {
        List<Photo> photos = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            Photo photo = parsePhoto(items.getJSONObject(i));
            if (photo != null) photos.add(photo);
        }
        return photos;
    }

    public static Photo parsePhoto(JSONObject json) {
        try {
            Photo photo = new Photo();
            photo.setId(json.optInt("id", 0));
            photo.setOwnerId(json.optInt("owner_id", 0));
            photo.setAlbumId(json.optInt("album_id", 0));
            photo.setWidth(json.optInt("width", 0));
            photo.setHeight(json.optInt("height", 0));
            photo.setDate(json.optLong("date", 0));
            photo.setText(json.optString("text", ""));

            photo.setPhoto75(json.optString("photo_75", ""));
            photo.setPhoto130(json.optString("photo_130", ""));
            photo.setPhoto604(json.optString("photo_604", ""));
            photo.setPhoto807(json.optString("photo_807", ""));
            photo.setPhoto1280(json.optString("photo_1280", ""));
            photo.setPhoto2560(json.optString("photo_2560", ""));

            if (photo.getPhoto604().isEmpty()) photo.setPhoto604(json.optString("src_big", ""));
            if (photo.getPhoto130().isEmpty()) photo.setPhoto130(json.optString("src", ""));
            if (photo.getPhoto75().isEmpty()) photo.setPhoto75(json.optString("src_small", ""));

            if (json.has("sizes")) {
                JSONArray sizes = json.getJSONArray("sizes");
                for (int i = 0; i < sizes.length(); i++) {
                    JSONObject sz = sizes.getJSONObject(i);
                    String type = sz.optString("type", "");
                    String url = sz.optString("url", "");
                    if (url.isEmpty()) continue;
                    switch (type) {
                        case "s": if (photo.getPhoto75().isEmpty()) photo.setPhoto75(url); break;
                        case "m": if (photo.getPhoto130().isEmpty()) photo.setPhoto130(url); break;
                        case "x": if (photo.getPhoto604().isEmpty()) photo.setPhoto604(url); break;
                        case "y": if (photo.getPhoto807().isEmpty()) photo.setPhoto807(url); break;
                        case "z": if (photo.getPhoto1280().isEmpty()) photo.setPhoto1280(url); break;
                        case "w": if (photo.getPhoto2560().isEmpty()) photo.setPhoto2560(url); break;
                        default:
                            if (photo.getPhoto604().isEmpty() && !url.isEmpty()) {
                                photo.setPhoto604(url);
                            }
                            break;
                    }
                }
            }

            return photo;
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing photo", e);
            return null;
        }
    }

}
