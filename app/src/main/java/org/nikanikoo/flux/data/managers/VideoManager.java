package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Video;
import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.utils.TimeUtils;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public interface VideoCommentsCallback {
        void onSuccess(List<Comment> comments, int totalCount);
        void onError(String error);
    }

    public interface CommentActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getComments(int ownerId, int videoId, int offset, int count, VideoCommentsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("video_id", String.valueOf(videoId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        api.callMethod("video.getComments", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject result = response.getJSONObject("response");
                    JSONArray items = result.optJSONArray("items");
                    List<Comment> comments = new ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) continue;
                            int authorId = item.optInt("from_id", item.optInt("uid", 0));
                            long date = item.optLong("date", 0L);
                            JSONObject likes = item.optJSONObject("likes");
                            Comment comment = new Comment(item.optInt("id", item.optInt("cid")),
                                    authorId, authorId == 0 ? "" : "ID " + Math.abs(authorId),
                                    item.optString("text", item.optString("message", "")), date);
                            comment.setLikesCount(likes != null ? likes.optInt("count", 0) : 0);
                            comment.setLiked(likes != null && likes.optInt("user_likes", 0) == 1);
                            comment.setGroup(authorId < 0);
                            comment.setTimestamp(TimeUtils.formatTimeAgo(date));
                            comments.add(comment);
                        }
                    }
                    int totalCount = result.optInt("count", comments.size());
                    enrichCommentAuthors(comments, totalCount, callback);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing video comments", e);
                    callback.onError("Failed to process video comments");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void enrichCommentAuthors(List<Comment> comments, int totalCount, VideoCommentsCallback callback) {
        Set<Integer> userIds = new LinkedHashSet<>();
        for (Comment comment : comments) {
            if (comment.getFromId() > 0) userIds.add(comment.getFromId());
        }
        if (userIds.isEmpty()) {
            callback.onSuccess(comments, totalCount);
            return;
        }

        StringBuilder ids = new StringBuilder();
        for (Integer id : userIds) {
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
        }
        Map<String, String> params = new HashMap<>();
        params.put("user_ids", ids.toString());
        params.put("fields", "photo_50,verified");
        api.callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray users = response.optJSONArray("response");
                    if (users != null) {
                        Map<Integer, JSONObject> byId = new HashMap<>();
                        for (int i = 0; i < users.length(); i++) {
                            JSONObject user = users.optJSONObject(i);
                            if (user != null) byId.put(user.optInt("id"), user);
                        }
                        for (Comment comment : comments) {
                            JSONObject user = byId.get(comment.getFromId());
                            if (user == null) continue;
                            String name = (user.optString("first_name", "") + " " +
                                    user.optString("last_name", "")).trim();
                            if (!name.isEmpty()) comment.setAuthorName(name);
                            comment.setAuthorAvatarUrl(user.optString("photo_50", ""));
                            comment.setAuthorVerified(user.optInt("verified", 0) == 1);
                        }
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error loading video comment authors", e);
                }
                callback.onSuccess(comments, totalCount);
            }

            @Override
            public void onError(String error) {
                callback.onSuccess(comments, totalCount);
            }
        });
    }

    public void addComment(int ownerId, int videoId, String message, int replyToCommentId,
                           CommentActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("video_id", String.valueOf(videoId));
        params.put("message", message);
        if (replyToCommentId > 0) params.put("reply_to_cid", String.valueOf(replyToCommentId));
        api.callMethod("video.addComment", params, new OpenVKApi.ApiCallback() {
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

    public void deleteComment(int ownerId, int videoId, int commentId, CommentActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("video_id", String.valueOf(videoId));
        params.put("comment_id", String.valueOf(commentId));
        api.callMethod("video.deleteComment", params, new OpenVKApi.ApiCallback() {
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
            video.setAccessKey(json.optString("access_key", null));
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
                Object userLikes = likes.opt("user_likes");
                video.setUserLikes(userLikes instanceof Boolean
                        ? (Boolean) userLikes : likes.optInt("user_likes", 0) == 1);
            }
            
            return video;
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing video", e);
            return null;
        }
    }
}
