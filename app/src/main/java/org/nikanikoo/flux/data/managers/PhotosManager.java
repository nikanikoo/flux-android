package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Album;
import org.nikanikoo.flux.data.models.Photo;
import org.nikanikoo.flux.utils.Logger;

import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.utils.TimeUtils;

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

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface PhotoCommentsCallback {
        void onSuccess(List<Comment> comments, int totalCount);
        void onError(String error);
    }

    public interface CreateCommentCallback {
        void onSuccess(int commentId);
        void onError(String error);
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

    public void deleteAlbum(int albumId, int groupId, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("album_id", String.valueOf(albumId));
        if (groupId != 0) {
            params.put("group_id", String.valueOf(groupId));
        }

        api.callMethod("photos.deleteAlbum", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Album deleted successfully");
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error deleting album: " + error);
                callback.onError(error);
            }
        });
    }

    public void deletePhoto(int ownerId, int photoId, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("photo_id", String.valueOf(photoId));

        api.callMethod("photos.delete", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Photo deleted successfully");
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error deleting photo: " + error);
                callback.onError(error);
            }
        });
    }

    public void editPhoto(int ownerId, int photoId, String caption, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("photo_id", String.valueOf(photoId));
        params.put("caption", caption != null ? caption : "");

        api.callMethod("photos.edit", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Photo edited successfully");
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error editing photo: " + error);
                callback.onError(error);
            }
        });
    }

    public void getAllPhotos(int ownerId, int offset, int count, PhotosCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("photo_sizes", "1");
        params.put("extended", "0");

        api.callMethod("photos.getAll", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.optJSONArray("items");
                    List<Photo> photos = parsePhotos(items != null ? items : new JSONArray());
                    Logger.d(TAG, "Loaded " + photos.size() + " photos (getAll)");
                    callback.onSuccess(photos, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing getAll photos", e);
                    callback.onError("Ошибка обработки данных");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading getAll photos: " + error);
                callback.onError(error);
            }
        });
    }

    public void getPhotoComments(int ownerId, int photoId, int offset, int count, PhotoCommentsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("photo_id", String.valueOf(photoId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("extended", "1");
        params.put("need_likes", "1");
        params.put("fields", "photo_50,photo_100,photo_200,verified");

        api.callMethod("photos.getComments", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.optJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");

                    parsePhotoCommentsAsync(items != null ? items : new JSONArray(), profiles, groups, totalCount, callback);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing photo comments", e);
                    callback.onError("Ошибка обработки данных");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading photo comments: " + error);
                callback.onError(error);
            }
        });
    }

    public void createPhotoComment(int ownerId, int photoId, String message, CreateCommentCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("photo_id", String.valueOf(photoId));
        params.put("message", message);

        api.callMethod("photos.createComment", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    int commentId = 0;
                    if (response.has("response")) {
                        Object respObj = response.get("response");
                        if (respObj instanceof Number) {
                            commentId = ((Number) respObj).intValue();
                        } else if (respObj instanceof JSONObject) {
                            commentId = ((JSONObject) respObj).optInt("comment_id", 0);
                        }
                    }
                    callback.onSuccess(commentId);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing create photo comment", e);
                    callback.onError("Ошибка обработки ответа");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error creating photo comment: " + error);
                callback.onError(error);
            }
        });
    }

    private void parsePhotoCommentsAsync(JSONArray items, JSONArray profiles, JSONArray groups, int totalCount, PhotoCommentsCallback callback) {
        Map<Integer, String> namesMap = new HashMap<>();
        Map<Integer, String> avatarsMap = new HashMap<>();
        Map<Integer, Boolean> verifiedMap = new HashMap<>();

        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                try {
                    JSONObject p = profiles.getJSONObject(i);
                    int id = p.optInt("id", 0);
                    String name = (p.optString("first_name", "") + " " + p.optString("last_name", "")).trim();
                    namesMap.put(id, name);
                    String avatar = p.optString("photo_50", p.optString("photo_100", p.optString("photo_200", p.optString("photo_max", ""))));
                    if (!avatar.isEmpty()) {
                        avatarsMap.put(id, avatar);
                    }
                    verifiedMap.put(id, p.optInt("verified", 0) == 1);
                } catch (Exception ignored) { }
            }
        }
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                try {
                    JSONObject g = groups.getJSONObject(i);
                    int id = -g.optInt("id", 0);
                    namesMap.put(id, g.optString("name", ""));
                    String avatar = g.optString("photo_50", g.optString("photo_100", g.optString("photo_200", "")));
                    if (!avatar.isEmpty()) {
                        avatarsMap.put(id, avatar);
                    }
                    verifiedMap.put(id, g.optInt("verified", 0) == 1);
                } catch (Exception ignored) { }
            }
        }

        List<Integer> missingUserIds = new ArrayList<>();
        List<Integer> missingGroupIds = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.optJSONObject(i);
            if (obj != null) {
                int fromId = obj.optInt("from_id", obj.optInt("owner_id", 0));
                if (fromId > 0 && (!avatarsMap.containsKey(fromId) || avatarsMap.get(fromId) == null || avatarsMap.get(fromId).isEmpty())) {
                    if (!missingUserIds.contains(fromId)) {
                        missingUserIds.add(fromId);
                    }
                } else if (fromId < 0 && (!avatarsMap.containsKey(fromId) || avatarsMap.get(fromId) == null || avatarsMap.get(fromId).isEmpty())) {
                    int gid = -fromId;
                    if (!missingGroupIds.contains(gid)) {
                        missingGroupIds.add(gid);
                    }
                }
            }
        }

        if (missingUserIds.isEmpty() && missingGroupIds.isEmpty()) {
            List<Comment> comments = buildCommentsList(items, namesMap, avatarsMap, verifiedMap);
            callback.onSuccess(comments, totalCount);
            return;
        }

        final int[] pendingRequests = new int[]{ (missingUserIds.isEmpty() ? 0 : 1) + (missingGroupIds.isEmpty() ? 0 : 1) };
        Runnable checkDone = () -> {
            pendingRequests[0]--;
            if (pendingRequests[0] <= 0) {
                List<Comment> comments = buildCommentsList(items, namesMap, avatarsMap, verifiedMap);
                callback.onSuccess(comments, totalCount);
            }
        };

        if (!missingUserIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < missingUserIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(missingUserIds.get(i));
            }
            Map<String, String> userParams = new HashMap<>();
            userParams.put("user_ids", sb.toString());
            userParams.put("fields", "photo_50,photo_100,photo_200,verified");
            api.callMethod("users.get", userParams, new OpenVKApi.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        JSONArray users = response.optJSONArray("response");
                        if (users != null) {
                            for (int i = 0; i < users.length(); i++) {
                                JSONObject u = users.getJSONObject(i);
                                int id = u.optInt("id", 0);
                                String name = (u.optString("first_name", "") + " " + u.optString("last_name", "")).trim();
                                if (!name.isEmpty()) namesMap.put(id, name);
                                String avatar = u.optString("photo_50", u.optString("photo_100", u.optString("photo_200", "")));
                                if (!avatar.isEmpty()) avatarsMap.put(id, avatar);
                                verifiedMap.put(id, u.optInt("verified", 0) == 1);
                            }
                        }
                    } catch (Exception ignored) { }
                    checkDone.run();
                }

                @Override
                public void onError(String error) {
                    checkDone.run();
                }
            });
        }

        if (!missingGroupIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < missingGroupIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(missingGroupIds.get(i));
            }
            Map<String, String> groupParams = new HashMap<>();
            groupParams.put("group_ids", sb.toString());
            groupParams.put("fields", "photo_50,photo_100,photo_200,verified");
            api.callMethod("groups.getById", groupParams, new OpenVKApi.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        JSONArray grps = response.optJSONArray("response");
                        if (grps != null) {
                            for (int i = 0; i < grps.length(); i++) {
                                JSONObject g = grps.getJSONObject(i);
                                int id = -g.optInt("id", 0);
                                String name = g.optString("name", "");
                                if (!name.isEmpty()) namesMap.put(id, name);
                                String avatar = g.optString("photo_50", g.optString("photo_100", g.optString("photo_200", "")));
                                if (!avatar.isEmpty()) avatarsMap.put(id, avatar);
                                verifiedMap.put(id, g.optInt("verified", 0) == 1);
                            }
                        }
                    } catch (Exception ignored) { }
                    checkDone.run();
                }

                @Override
                public void onError(String error) {
                    checkDone.run();
                }
            });
        }
    }

    private static List<Comment> buildCommentsList(JSONArray items, Map<Integer, String> namesMap, Map<Integer, String> avatarsMap, Map<Integer, Boolean> verifiedMap) {
        List<Comment> comments = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject obj = items.getJSONObject(i);
                int id = obj.optInt("id", 0);
                int fromId = obj.optInt("from_id", obj.optInt("owner_id", 0));
                String text = obj.optString("text", obj.optString("message", ""));
                long date = obj.optLong("date", obj.optLong("created", 0));

                String name = namesMap.get(fromId);
                if (name == null || name.isEmpty()) {
                    name = "ID " + fromId;
                }
                Comment comment = new Comment(id, fromId, name, text, date);
                comment.setGroup(fromId < 0);
                comment.setAuthorAvatarUrl(avatarsMap.get(fromId));
                Boolean isVer = verifiedMap.get(fromId);
                if (isVer != null && isVer) {
                    comment.setAuthorVerified(true);
                }
                comment.setTimestamp(TimeUtils.formatTimeAgo(date));

                if (obj.has("likes")) {
                    JSONObject likesObj = obj.getJSONObject("likes");
                    comment.setLikesCount(likesObj.optInt("count", 0));
                    comment.setLiked(likesObj.optInt("user_likes", 0) == 1);
                }

                comments.add(comment);
            } catch (Exception ignored) { }
        }

        return comments;
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
            if (json.has("can_upload")) {
                album.setCanUpload(json.optInt("can_upload", 0) == 1);
            }

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
            String text = json.optString("text", "");
            if (text.isEmpty()) text = json.optString("description", "");
            if (text.isEmpty()) text = json.optString("caption", "");
            if (text.isEmpty()) text = json.optString("title", "");
            photo.setText(text);

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
