package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Video;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoManager extends BaseManager<VideoManager> {
    private static final String TAG = "VideoManager";

    private VideoManager(Context context) {
        super(context);
    }

    public static VideoManager getInstance(Context context) {
        return BaseManager.getInstance(VideoManager.class, context);
    }

    public interface VideoCallback {
        void onSuccess(List<Video> videos, int totalCount);
        void onError(String error);
    }

    public interface VideoActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getVideos(int ownerId, int offset, int count, VideoCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));

        api.callMethod("video.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Video> videos = parseVideos(items);
                    
                    Logger.d(TAG, "Loaded " + videos.size() + " videos, total: " + totalCount);
                    callback.onSuccess(videos, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing videos", e);
                    callback.onError("Ошибка обработки данных");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading videos: " + error);
                callback.onError(error);
            }
        });
    }

    public void searchVideos(String query, int offset, int count, VideoCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("q", query);
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));

        api.callMethod("video.search", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int totalCount = responseObj.optInt("count", 0);
                    JSONArray items = responseObj.getJSONArray("items");
                    List<Video> videos = parseVideos(items);
                    
                    Logger.d(TAG, "Found " + videos.size() + " videos for query: " + query);
                    callback.onSuccess(videos, totalCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing search results", e);
                    callback.onError("Ошибка обработки данных");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error searching videos: " + error);
                callback.onError(error);
            }
        });
    }

    private List<Video> parseVideos(JSONArray items) throws Exception {
        List<Video> videos = new ArrayList<>();
        
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            Video video = parseVideo(item);
            if (video != null) {
                videos.add(video);
            }
        }
        
        return videos;
    }

    public static Video parseVideo(JSONObject json) {
        try {
            Video video = new Video();
            
            video.setId(json.optInt("id", 0));
            video.setOwnerId(json.optInt("owner_id", 0));
            video.setTitle(json.optString("title", ""));
            video.setDescription(json.optString("description", ""));
            video.setDuration(json.optInt("duration", 0));
            if (json.has("image")) {
                if (json.optJSONArray("image") != null) {
                    JSONArray images = json.getJSONArray("image");
                    if (images.length() > 0) {
                        video.setImage(images.getJSONObject(images.length() - 1).optString("url", ""));
                    }
                } else {
                    video.setImage(json.optString("image", ""));
                }
            } else if (json.has("photo_800")) {
                video.setImage(json.optString("photo_800", ""));
            } else if (json.has("photo_320")) {
                video.setImage(json.optString("photo_320", ""));
            } else if (json.has("photo_130")) {
                video.setImage(json.optString("photo_130", ""));
            }

            if (json.has("first_frame")) {
                if (json.optJSONArray("first_frame") != null) {
                    JSONArray frames = json.getJSONArray("first_frame");
                    if (frames.length() > 0) {
                        video.setFirstFrame(frames.getJSONObject(frames.length() - 1).optString("url", ""));
                    }
                } else {
                    video.setFirstFrame(json.optString("first_frame", ""));
                }
            }
            video.setDate(json.optLong("date", 0));
            video.setAddingDate(json.optLong("adding_date", 0));
            video.setViews(json.optInt("views", 0));
            video.setLocalViews(json.optInt("local_views", 0));
            video.setComments(json.optInt("comments", 0));
            video.setPlayer(json.optString("player", ""));
            video.setPlatform(json.optString("platform", ""));
            video.setCanEdit(json.optInt("can_edit", 0) == 1);
            video.setCanAdd(json.optInt("can_add", 0) == 1);
            video.setPrivate(json.optInt("is_private", 0) == 1);
            video.setProcessing(json.optInt("processing", 0));
            video.setFavorite(json.optInt("is_favorite", 0) == 1);
            video.setCanComment(json.optInt("can_comment", 0) == 1);
            video.setCanRepost(json.optInt("can_repost", 0) == 1);
            video.setUserLikes(json.optInt("user_likes", 0) == 1);
            video.setRepeat(json.optInt("repeat", 0) == 1);
            video.setWidth(json.optInt("width", 0));
            video.setHeight(json.optInt("height", 0));
            
            if (json.has("likes")) {
                JSONObject likes = json.getJSONObject("likes");
                video.setLikes(likes.optInt("count", 0));
            }
            
            return video;
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing video", e);
            return null;
        }
    }
}
