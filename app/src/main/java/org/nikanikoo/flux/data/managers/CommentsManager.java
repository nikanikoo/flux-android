package org.nikanikoo.flux.data.managers;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Comment;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.utils.AsyncTaskHelper;
import org.nikanikoo.flux.utils.AttachmentProcessor;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsManager extends BaseManager<CommentsManager> {

    private CommentsManager(Context context) {
        super(context);
    }

    public static CommentsManager getInstance(Context context) {
        return BaseManager.getInstance(CommentsManager.class, context);
    }

    public interface CommentsCallback {
        void onSuccess(List<Comment> comments);
        void onError(String error);
    }

    public interface CreateCommentCallback {
        void onSuccess(Comment comment);
        void onError(String error);
    }

    public interface LikeCommentCallback {
        void onSuccess(int newLikesCount, boolean isLiked);
        void onError(String error);
    }

    public void loadComments(int ownerId, int postId, CommentsCallback callback) {
        Map<String, String> params = new HashMap<>();

        params.put("owner_id", String.valueOf(ownerId));
        params.put("post_id", String.valueOf(postId));
        params.put("count", String.valueOf(Constants.Api.POSTS_PER_PAGE));
        params.put("extended", "1");
        params.put("fields", "verified,photo_50");

        Logger.d("CommentsManager", "Загрузка комментариев из " + ownerId + "_" + postId);
        
        api.callMethod("wall.getComments", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.d("CommentsManager", "Ответ комментариев получен, начинаем парсинг в фоне");
                    
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");

                    List<Comment> comments = parseComments(items, profiles, groups);
                    Logger.d("CommentsManager", "Получено " + comments.size() + " комментариев");
                    
                    return comments;
                }, new AsyncTaskHelper.AsyncCallback<List<Comment>>() {
                    @Override
                    public void onSuccess(List<Comment> comments) {
                        callback.onSuccess(comments);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e("CommentsManager", "Ошибка парсинга комментариев: " + error);
                        callback.onError("Не удалось загрузить комментарии");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e("CommentsManager", "API ошибка: " + error);
                callback.onError("Не удалось загрузить комментарии");
            }
        });
    }

    public void createComment(int ownerId, int postId, String message, Uri imageUri, CreateCommentCallback callback) {
        if (imageUri != null) {
            PhotoUploadManager photoUploadManager = PhotoUploadManager.getInstance(context);
            photoUploadManager.uploadWallPhoto(imageUri, new PhotoUploadManager.PhotoUploadCallback() {
                @Override
                public void onSuccess(String attachment) {
                    createCommentInternal(ownerId, postId, message, attachment, callback);
                }
                
                @Override
                public void onError(String error) {
                    callback.onError("Ошибка загрузки изображения: " + error);
                }
            });
        } else {
            createCommentInternal(ownerId, postId, message, null, callback);
        }
    }

    public void createComment(int ownerId, int postId, String message, CreateCommentCallback callback) {
        createComment(ownerId, postId, message, null, callback);
    }

    private void createCommentInternal(int ownerId, int postId, String message, String attachment, CreateCommentCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("post_id", String.valueOf(postId));
        params.put("message", message);
        
        if (attachment != null) {
            params.put("attachments", attachment);
        }

        Logger.d("CommentsManager", "Создание комментария в " + ownerId + "_" + postId);
        
        api.callMethod("wall.createComment", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.d("CommentsManager", "Ответ создания комментария получен");
                    
                    JSONObject responseObj = response.getJSONObject("response");
                    int commentId = responseObj.getInt("comment_id");

                    ProfileManager profileManager = ProfileManager.getInstance(context);
                    profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
                        @Override
                        public void onSuccess(UserProfile profile) {
                            Comment comment = new Comment(commentId, profile.getId(),
                                    profile.getFirstName() + " " + profile.getLastName(),
                                    message, System.currentTimeMillis() / 1000);
                            comment.setTimestamp(TimeUtils.formatTimeAgo(comment.getDate()));
                            comment.setAuthorAvatarUrl(profile.getPhoto50());

                            if (attachment != null) {
                                comment.setImageUrl("");
                            }
                            
                            Logger.d("CommentsManager", "Комментарий успешно создан по ID: " + commentId);
                            callback.onSuccess(comment);
                        }

                        @Override
                        public void onError(String error) {
                            Comment comment = new Comment(commentId, 0, "Вы", message, System.currentTimeMillis() / 1000);
                            comment.setTimestamp(TimeUtils.formatTimeAgo(comment.getDate()));
                            
                            if (attachment != null) {
                                comment.setImageUrl("");
                            }
                            
                            callback.onSuccess(comment);
                        }
                    });

                } catch (Exception e) {
                    Logger.e("CommentsManager", "Ошибка парсинга ответа", e);
                    callback.onError("Не удалось создать комментарий");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e("CommentsManager", "Ошибка API создания комментария: " + error);
                callback.onError("Не удалось создать комментарий");
            }
        });
    }

    public void toggleCommentLikeWithOriginalState(Comment comment, int ownerId, int postId, boolean originalLikedState, LikeCommentCallback callback) {
        Logger.d("CommentsManager", "Переключение лайка комментария: commentId=" + comment.getId() + 
                          " ownerId=" + ownerId + " originalLiked=" + originalLikedState);
        
        LikesManager likesManager = LikesManager.getInstance(context);
        likesManager.toggleLike("comment", ownerId, comment.getId(), originalLikedState, new LikesManager.LikeCallback() {
            @Override
            public void onSuccess(int likesCount) {
                Logger.d("CommentsManager", "Лайк успешно переключен, новое количество: " + likesCount);
                callback.onSuccess(likesCount, !originalLikedState);
            }

            @Override
            public void onError(String error) {
                Logger.e("CommentsManager", "Ошибка переключения лайка: " + error);
                callback.onError("Не удалось изменить лайк");
            }
        });
    }

    public void deleteComment(int commentId, OpenVKApi.ApiCallback callback) {
        deleteComment(0, commentId, callback);
    }

    public void editComment(int commentId, String message, String attachments, OpenVKApi.ApiCallback callback) {
        editComment(0, commentId, message, attachments, callback);
    }

    public void editComment(int commentId, String message, OpenVKApi.ApiCallback callback) {
        editComment(0, commentId, message, null, callback);
    }

    public void deleteComment(int ownerId, int commentId, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        if (ownerId != 0) {
            params.put("owner_id", String.valueOf(ownerId));
        }
        params.put("comment_id", String.valueOf(commentId));

        Logger.d("CommentsManager", "Удаление комментария: " + commentId + " (owner: " + ownerId + ")");
        api.callMethod("wall.deleteComment", params, callback);
    }

    public void editComment(int ownerId, int commentId, String message, String attachments, OpenVKApi.ApiCallback callback) {
        Map<String, String> params = new HashMap<>();
        if (ownerId != 0) {
            params.put("owner_id", String.valueOf(ownerId));
        }
        params.put("comment_id", String.valueOf(commentId));
        params.put("message", message);
        
        if (attachments != null) {
            params.put("attachments", attachments);
        }

        Logger.d("CommentsManager", "Редактирование комментария: " + commentId + " (owner: " + ownerId + ") с вложениями: " + attachments);
        api.callMethod("wall.editComment", params, callback);
    }

    private List<Comment> parseComments(JSONArray items, JSONArray profiles, JSONArray groups) {
        List<Comment> comments = new ArrayList<>();
        Map<Integer, String> profileNames = new HashMap<>();
        Map<Integer, String> profileAvatars = new HashMap<>();
        Map<Integer, Boolean> profileVerified = new HashMap<>();
        
        Map<Integer, String> groupNames = new HashMap<>();
        Map<Integer, String> groupAvatars = new HashMap<>();
        Map<Integer, Boolean> groupVerified = new HashMap<>();

        if (profiles != null) {
            for (int i = 0; i < profiles.length(); i++) {
                try {
                    JSONObject profile = profiles.getJSONObject(i);
                    int id = profile.getInt("id");
                    String name = profile.getString("first_name") + " " + profile.getString("last_name");
                    String avatar = profile.optString("photo_50", "");
                    boolean verified = false;
                    if (profile.has("verified")) {
                        Object verifiedObj = profile.opt("verified");
                        if (verifiedObj instanceof Integer) {
                            verified = (Integer) verifiedObj == 1;
                        } else if (verifiedObj instanceof Boolean) {
                            verified = (Boolean) verifiedObj;
                        }
                    }
                    profileNames.put(id, name);
                    profileAvatars.put(id, avatar);
                    profileVerified.put(id, verified);
                } catch (Exception e) {
                    Logger.e("CommentsManager", "Ошибка парсинга профиля", e);
                }
            }
        }
        
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                try {
                    JSONObject group = groups.getJSONObject(i);
                    int id = group.getInt("id");
                    String name = group.optString("name", "Группа " + id);
                    String avatar = group.optString("photo_50", "");
                    boolean verified = false;
                    if (group.has("verified")) {
                        Object verifiedObj = group.opt("verified");
                        if (verifiedObj instanceof Integer) {
                            verified = (Integer) verifiedObj == 1;
                        } else if (verifiedObj instanceof Boolean) {
                            verified = (Boolean) verifiedObj;
                        }
                    }
                    groupNames.put(id, name);
                    groupAvatars.put(id, avatar);
                    groupVerified.put(id, verified);
                    Logger.d("CommentsManager", "Добавлена группа: " + name + " (ID: " + id + ") аватар: " + avatar);
                } catch (Exception e) {
                    Logger.e("CommentsManager", "Ошибка парсинга группы", e);
                }
            }
        }

        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                
                int id = item.getInt("id");
                int fromId = item.getInt("from_id");
                String text = item.optString("text", "");
                long date = item.optLong("date", 0);

                JSONObject likesObj = item.optJSONObject("likes");
                int likesCount = 0;
                boolean userLikes = false;
                if (likesObj != null) {
                    likesCount = likesObj.optInt("count", 0);
                    userLikes = likesObj.optInt("user_likes", 0) == 1;
                }
                
                String authorName;
                String authorAvatar = "";
                boolean authorVerified = false;
                boolean isGroup = false;
                
                if (fromId < 0) {
                    int groupId = -fromId;
                    if (groupNames.containsKey(groupId)) {
                        authorName = groupNames.get(groupId);
                        authorAvatar = groupAvatars.get(groupId);
                        authorVerified = groupVerified.containsKey(groupId) ? groupVerified.get(groupId) : false;
                        isGroup = true;
                    } else {
                        authorName = "Группа " + groupId;
                        isGroup = true;
                    }
                } else if (groupNames.containsKey(fromId)) {
                    authorName = groupNames.get(fromId);
                    authorAvatar = groupAvatars.get(fromId);
                    authorVerified = groupVerified.containsKey(fromId) ? groupVerified.get(fromId) : false;
                    isGroup = true;
                } else if (fromId > 0 && profileNames.containsKey(fromId)) {
                    authorName = profileNames.get(fromId);
                    authorAvatar = profileAvatars.get(fromId);
                    authorVerified = profileVerified.containsKey(fromId) ? profileVerified.get(fromId) : false;
                } else if (fromId > 0) {
                    authorName = "Пользователь " + fromId;
                } else {
                    authorName = "Пользователь 0";
                }
 
                Comment comment = new Comment(id, fromId, authorName, text, date);
                comment.setAuthorAvatarUrl(authorAvatar);
                comment.setAuthorVerified(authorVerified);
                comment.setGroup(isGroup);
                comment.setTimestamp(TimeUtils.formatTimeAgo(date));
                comment.setLikesCount(likesCount);
                comment.setLiked(userLikes);

                String imageUrl = "";
                String unsupportedElements = "";
                if (item.has("attachments")) {
                    JSONArray attachments = item.getJSONArray("attachments");
                    AttachmentProcessor.AttachmentResult result = AttachmentProcessor.processAttachments(attachments);

                    if (!result.getImageUrls().isEmpty()) {
                        imageUrl = result.getImageUrls().get(0);
                    }
                    
                    comment.setAudioAttachments(result.getAudioAttachments());
                    comment.setVideoAttachments(result.getVideoAttachments());
                    unsupportedElements = result.getUnsupportedElementsText();
                }
                
                comment.setImageUrl(imageUrl);
                comment.setUnsupportedElementsText(unsupportedElements);
                
                comments.add(comment);
                
                Logger.d("CommentsManager", "Добавлен комментарий от " + authorName + " (ID: " + fromId + 
                         ") лайки=" + likesCount + " изображение=" + imageUrl);
                
            } catch (Exception e) {
                Logger.e("CommentsManager", "Ошибка парсинга комментария " + i, e);
            }
        }

        return comments;
    }
}