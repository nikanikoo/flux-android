package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Note;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesManager extends BaseManager<NotesManager> {
    private static final String TAG = "NotesManager";

    private NotesManager(Context context) {
        super(context);
    }

    public static NotesManager getInstance(Context context) {
        return BaseManager.getInstance(NotesManager.class, context);
    }

    public interface NotesCallback {
        void onSuccess(List<Note> notes, int count);
        void onError(String error);
    }

    public interface NoteCallback {
        void onSuccess(Note note);
        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface CreateCallback {
        void onSuccess(int noteId);
        void onError(String error);
    }

    public void getNotes(int userId, int offset, int count, NotesCallback callback) {
        Map<String, String> params = new HashMap<>();
        if (userId != 0) {
            params.put("user_id", String.valueOf(userId));
        } else {
            org.nikanikoo.flux.security.AccountManager.Account currentAccount = 
                org.nikanikoo.flux.security.AccountManager.getInstance(context).getCurrentAccount();
            if (currentAccount != null && currentAccount.userId != null) {
                params.put("user_id", currentAccount.userId);
            }
        }
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("sort", "1");

        api.callMethod("notes.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.optJSONObject("response");
                    if (responseObj == null) {
                        Object respObj = response.get("response");
                        List<Note> notesList = new ArrayList<>();
                        int totalCount = 0;
                        
                        if (respObj instanceof JSONObject) {
                            JSONObject obj = (JSONObject) respObj;
                            totalCount = obj.optInt("count", 0);
                            JSONArray items = obj.optJSONArray("items");
                            if (items != null) {
                                for (int i = 0; i < items.length(); i++) {
                                    notesList.add(parseNote(items.getJSONObject(i)));
                                }
                            }
                        } else if (respObj instanceof JSONArray) {
                            JSONArray items = (JSONArray) respObj;
                            int startIndex = 0;
                            if (items.length() > 0 && items.get(0) instanceof Integer) {
                                totalCount = items.getInt(0);
                                startIndex = 1;
                            } else {
                                totalCount = items.length();
                            }
                            for (int i = startIndex; i < items.length(); i++) {
                                notesList.add(parseNote(items.getJSONObject(i)));
                            }
                        }
                        
                        callback.onSuccess(notesList, totalCount);
                    } else {
                        int totalCount = responseObj.optInt("count", 0);
                        JSONArray items = responseObj.optJSONArray("items");
                        List<Note> notesList = new ArrayList<>();
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                notesList.add(parseNote(items.getJSONObject(i)));
                            }
                        }
                        callback.onSuccess(notesList, totalCount);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing notes", e);
                    callback.onError("Error parsing notes");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void addNote(String title, String text, CreateCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("title", title);
        params.put("text", text);

        api.callMethod("notes.add", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    int noteId = response.getInt("response");
                    callback.onSuccess(noteId);
                } catch (Exception e) {
                    try {
                        JSONObject resp = response.getJSONObject("response");
                        int noteId = resp.getInt("note_id");
                        callback.onSuccess(noteId);
                    } catch (Exception ex) {
                        Logger.e(TAG, "Error adding note", ex);
                        callback.onError("Error adding note");
                    }
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void deleteNote(int noteId, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("note_id", String.valueOf(noteId));

        api.callMethod("notes.delete", params, new OpenVKApi.ApiCallback() {
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

    public void deleteComment(int commentId, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("comment_id", String.valueOf(commentId));

        api.callMethod("wall.deleteComment", params, new OpenVKApi.ApiCallback() {
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

    public void editComment(int commentId, String message, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("comment_id", String.valueOf(commentId));
        params.put("message", message);

        api.callMethod("wall.editComment", params, new OpenVKApi.ApiCallback() {
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

    private Note parseNote(JSONObject obj) {
        Note note = new Note();
        note.setId(obj.optInt("id", obj.optInt("nid", 0))); // OpenVK might use nid or id
        note.setOwnerId(obj.optInt("owner_id", obj.optInt("user_id", 0)));
        note.setTitle(obj.optString("title"));
        note.setText(obj.optString("text"));
        note.setDate(obj.optLong("date"));
        note.setComments(obj.optInt("comments"));
        note.setReadComments(obj.optInt("read_comments"));
        note.setViewUrl(obj.optString("view_url"));
        return note;
    }

    public void editNote(int noteId, String title, String text, ActionCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("note_id", String.valueOf(noteId));
        params.put("title", title);
        params.put("text", text);

        api.callMethod("notes.edit", params, new OpenVKApi.ApiCallback() {
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

    public void createComment(int ownerId, int noteId, String message, android.net.Uri imageUri, CommentsManager.CreateCommentCallback callback) {
        if (imageUri != null) {
            PhotoUploadManager photoUploadManager = PhotoUploadManager.getInstance(context);
            photoUploadManager.uploadWallPhoto(imageUri, new PhotoUploadManager.PhotoUploadCallback() {
                @Override
                public void onSuccess(String attachment) {
                    createCommentInternal(ownerId, noteId, message, attachment, callback);
                }
                
                @Override
                public void onError(String error) {
                    callback.onError("Ошибка загрузки изображения: " + error);
                }
            });
        } else {
            createCommentInternal(ownerId, noteId, message, null, callback);
        }
    }

    private void createCommentInternal(int ownerId, int noteId, String message, String attachment, CommentsManager.CreateCommentCallback callback) {
        Map<String, String> params = new HashMap<>();
        if (ownerId != 0) {
            params.put("owner_id", String.valueOf(ownerId));
        } else {
            org.nikanikoo.flux.security.AccountManager.Account currentAccount = 
                org.nikanikoo.flux.security.AccountManager.getInstance(context).getCurrentAccount();
            if (currentAccount != null && currentAccount.userId != null) {
                params.put("owner_id", currentAccount.userId);
            }
        }
        params.put("note_id", String.valueOf(noteId));
        params.put("message", message);
        
        if (attachment != null) {
            params.put("attachments", attachment);
        }

        api.callMethod("notes.createComment", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                int commentId = response.optInt("response", 0);
                org.nikanikoo.flux.data.models.Comment comment = new org.nikanikoo.flux.data.models.Comment(
                    commentId, 0, "Вы", message, System.currentTimeMillis() / 1000
                );
                comment.setTimestamp("только что");
                if (attachment != null) comment.setImageUrl(""); // Hack for now to show something
                callback.onSuccess(comment);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getComments(int ownerId, int noteId, int offset, int count, CommentsManager.CommentsCallback callback) {
        Map<String, String> params = new HashMap<>();
        if (ownerId != 0) {
            params.put("owner_id", String.valueOf(ownerId));
        } else {
            org.nikanikoo.flux.security.AccountManager.Account currentAccount = 
                org.nikanikoo.flux.security.AccountManager.getInstance(context).getCurrentAccount();
            if (currentAccount != null && currentAccount.userId != null) {
                params.put("owner_id", currentAccount.userId);
            }
        }
        params.put("note_id", String.valueOf(noteId));
        params.put("offset", String.valueOf(offset));
        params.put("count", String.valueOf(count));
        params.put("extended", "1");
        params.put("fields", "photo_50,verified");

        api.callMethod("notes.getComments", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    List<org.nikanikoo.flux.data.models.Comment> commentsList = new ArrayList<>();
                    JSONObject respObj = response.optJSONObject("response");
                    
                    Map<Integer, String> names = new HashMap<>();
                    Map<Integer, String> avatars = new HashMap<>();
                    Map<Integer, Boolean> verifiedMap = new HashMap<>();
                    
                    if (respObj != null) {
                        JSONArray profiles = respObj.optJSONArray("profiles");
                        if (profiles != null) {
                            for (int i = 0; i < profiles.length(); i++) {
                                JSONObject profile = profiles.optJSONObject(i);
                                if (profile != null) {
                                    int id = profile.optInt("id", profile.optInt("uid", 0));
                                    String name = profile.optString("first_name", "") + " " + profile.optString("last_name", "");
                                    names.put(id, name.trim());
                                    
                                    String avatar = profile.optString("photo_50", profile.optString("photo_100", profile.optString("photo", "")));
                                    avatars.put(id, avatar);
                                    
                                    boolean verified = false;
                                    if (profile.has("verified")) {
                                        Object verifiedObj = profile.opt("verified");
                                        if (verifiedObj instanceof Integer) {
                                            verified = (Integer) verifiedObj == 1;
                                        } else if (verifiedObj instanceof Boolean) {
                                            verified = (Boolean) verifiedObj;
                                        }
                                    }
                                    verifiedMap.put(id, verified);
                                }
                            }
                        }
                        JSONArray groups = respObj.optJSONArray("groups");
                        if (groups != null) {
                            for (int i = 0; i < groups.length(); i++) {
                                JSONObject group = groups.optJSONObject(i);
                                if (group != null) {
                                    int id = group.optInt("id", group.optInt("gid", 0));
                                    names.put(-id, group.optString("name", "Группа " + id));
                                    
                                    String avatar = group.optString("photo_50", group.optString("photo_100", group.optString("photo", "")));
                                    avatars.put(-id, avatar);
                                    
                                    boolean verified = false;
                                    if (group.has("verified")) {
                                        Object verifiedObj = group.opt("verified");
                                        if (verifiedObj instanceof Integer) {
                                            verified = (Integer) verifiedObj == 1;
                                        } else if (verifiedObj instanceof Boolean) {
                                            verified = (Boolean) verifiedObj;
                                        }
                                    }
                                    verifiedMap.put(-id, verified);
                                }
                            }
                        }
                    }

                    if (respObj == null) {
                        Object ro = response.get("response");
                        if (ro instanceof JSONArray) {
                            JSONArray items = (JSONArray) ro;
                            int start = (items.length() > 0 && items.get(0) instanceof Integer) ? 1 : 0;
                            for(int i=start; i<items.length(); i++) {
                                commentsList.add(parseNoteComment(items.getJSONObject(i), names, avatars, verifiedMap));
                            }
                        }
                    } else {
                        JSONArray items = respObj.optJSONArray("items");
                        if (items == null) items = respObj.optJSONArray("comments");
                        if (items != null) {
                            for(int i=0; i<items.length(); i++) {
                                commentsList.add(parseNoteComment(items.getJSONObject(i), names, avatars, verifiedMap));
                            }
                        }
                    }
                    fetchMissingProfiles(commentsList, names, avatars, verifiedMap, callback);
                } catch (Exception e) {
                    callback.onError("Error parsing comments");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void fetchMissingProfiles(
            List<org.nikanikoo.flux.data.models.Comment> comments,
            Map<Integer, String> names,
            Map<Integer, String> avatars,
            Map<Integer, Boolean> verifiedMap,
            CommentsManager.CommentsCallback callback) {
        
        List<Integer> missingUserIds = new ArrayList<>();
        List<Integer> missingGroupIds = new ArrayList<>();
        
        for (org.nikanikoo.flux.data.models.Comment comment : comments) {
            int fromId = comment.getFromId();
            if (fromId == 0) continue;
            
            if (fromId > 0) {
                if (!names.containsKey(fromId)) {
                    missingUserIds.add(fromId);
                }
            } else {
                if (!names.containsKey(fromId)) {
                    missingGroupIds.add(-fromId);
                }
            }
        }
        
        if (missingUserIds.isEmpty() && missingGroupIds.isEmpty()) {
            updateCommentsData(comments, names, avatars, verifiedMap);
            callback.onSuccess(comments);
            return;
        }
        
        int totalCalls = (missingUserIds.isEmpty() ? 0 : 1) + (missingGroupIds.isEmpty() ? 0 : 1);
        final int[] completedCalls = {0};
        
        Runnable checkCompletion = () -> {
            completedCalls[0]++;
            if (completedCalls[0] == totalCalls) {
                updateCommentsData(comments, names, avatars, verifiedMap);
                callback.onSuccess(comments);
            }
        };
        
        if (!missingUserIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < missingUserIds.size(); i++) {
                sb.append(missingUserIds.get(i));
                if (i < missingUserIds.size() - 1) sb.append(",");
            }
            Map<String, String> params = new HashMap<>();
            params.put("user_ids", sb.toString());
            params.put("fields", "photo_50,verified");
            
            api.callMethod("users.get", params, new OpenVKApi.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        JSONArray users = response.optJSONArray("response");
                        if (users == null) {
                            JSONObject respObj = response.optJSONObject("response");
                            if (respObj != null) {
                                users = respObj.optJSONArray("items");
                            }
                        }
                        if (users != null) {
                            for (int i = 0; i < users.length(); i++) {
                                JSONObject userObj = users.optJSONObject(i);
                                if (userObj != null) {
                                    int id = userObj.optInt("id", userObj.optInt("uid", 0));
                                    String name = userObj.optString("first_name", "") + " " + userObj.optString("last_name", "");
                                    names.put(id, name.trim());
                                    
                                    String avatar = userObj.optString("photo_50", userObj.optString("photo_100", userObj.optString("photo", "")));
                                    avatars.put(id, avatar);
                                    
                                    boolean verified = false;
                                    if (userObj.has("verified")) {
                                        Object verifiedObj = userObj.opt("verified");
                                        if (verifiedObj instanceof Integer) {
                                            verified = (Integer) verifiedObj == 1;
                                        } else if (verifiedObj instanceof Boolean) {
                                            verified = (Boolean) verifiedObj;
                                        }
                                    }
                                    verifiedMap.put(id, verified);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "Error fetching user profiles for comments", e);
                    }
                    checkCompletion.run();
                }
                
                @Override
                public void onError(String error) {
                    Logger.e(TAG, "Error calling users.get: " + error);
                    checkCompletion.run();
                }
            });
        }
        
        if (!missingGroupIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < missingGroupIds.size(); i++) {
                sb.append(missingGroupIds.get(i));
                if (i < missingGroupIds.size() - 1) sb.append(",");
            }
            Map<String, String> params = new HashMap<>();
            params.put("group_ids", sb.toString());
            params.put("fields", "photo_50,verified");
            
            api.callMethod("groups.getById", params, new OpenVKApi.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        JSONArray groups = response.optJSONArray("response");
                        if (groups == null) {
                            JSONObject respObj = response.optJSONObject("response");
                            if (respObj != null) {
                                groups = respObj.optJSONArray("items");
                            }
                        }
                        if (groups != null) {
                            for (int i = 0; i < groups.length(); i++) {
                                JSONObject groupObj = groups.optJSONObject(i);
                                if (groupObj != null) {
                                    int id = groupObj.optInt("id", groupObj.optInt("gid", 0));
                                    names.put(-id, groupObj.optString("name", "Группа " + id));
                                    
                                    String avatar = groupObj.optString("photo_50", groupObj.optString("photo_100", groupObj.optString("photo", "")));
                                    avatars.put(-id, avatar);
                                    
                                    boolean verified = false;
                                    if (groupObj.has("verified")) {
                                        Object verifiedObj = groupObj.opt("verified");
                                        if (verifiedObj instanceof Integer) {
                                            verified = (Integer) verifiedObj == 1;
                                        } else if (verifiedObj instanceof Boolean) {
                                            verified = (Boolean) verifiedObj;
                                        }
                                    }
                                    verifiedMap.put(-id, verified);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "Error fetching group profiles for comments", e);
                    }
                    checkCompletion.run();
                }
                
                @Override
                public void onError(String error) {
                    Logger.e(TAG, "Error calling groups.getById: " + error);
                    checkCompletion.run();
                }
            });
        }
    }

    private void updateCommentsData(
            List<org.nikanikoo.flux.data.models.Comment> comments,
            Map<Integer, String> names,
            Map<Integer, String> avatars,
            Map<Integer, Boolean> verifiedMap) {
        for (org.nikanikoo.flux.data.models.Comment comment : comments) {
            int fromId = comment.getFromId();
            if (names.containsKey(fromId)) {
                comment.setAuthorName(names.get(fromId));
            }
            if (avatars.containsKey(fromId)) {
                comment.setAuthorAvatarUrl(avatars.get(fromId));
            }
            if (verifiedMap.containsKey(fromId)) {
                comment.setAuthorVerified(verifiedMap.get(fromId));
            }
        }
    }

    private org.nikanikoo.flux.data.models.Comment parseNoteComment(
            JSONObject item,
            Map<Integer, String> names,
            Map<Integer, String> avatars,
            Map<Integer, Boolean> verifiedMap) {
        int id = item.optInt("id", item.optInt("cid", 0));
        int fromId = item.optInt("uid", item.optInt("from_id", item.optInt("user_id", 0)));
        String text = item.optString("message", item.optString("text", ""));
        long date = item.optLong("date", 0);
        
        String authorName = names.containsKey(fromId) ? names.get(fromId) : "Пользователь " + fromId;
        String authorAvatar = avatars.containsKey(fromId) ? avatars.get(fromId) : "";
        boolean verified = verifiedMap.containsKey(fromId) ? verifiedMap.get(fromId) : false;
        
        org.nikanikoo.flux.data.models.Comment comment = new org.nikanikoo.flux.data.models.Comment(id, fromId, authorName, text, date);
        comment.setAuthorAvatarUrl(authorAvatar);
        comment.setAuthorVerified(verified);
        comment.setGroup(fromId < 0);
        comment.setTimestamp(org.nikanikoo.flux.utils.TimeUtils.formatTimeAgo(date));
        
        // Parse likes
        JSONObject likesObj = item.optJSONObject("likes");
        int likesCount = 0;
        boolean userLikes = false;
        if (likesObj != null) {
            likesCount = likesObj.optInt("count", 0);
            userLikes = likesObj.optInt("user_likes", 0) == 1;
        }
        comment.setLikesCount(likesCount);
        comment.setLiked(userLikes);

        // Parse attachments
        String imageUrl = "";
        String unsupportedElements = "";
        if (item.has("attachments")) {
            try {
                JSONArray attachments = item.getJSONArray("attachments");
                org.nikanikoo.flux.utils.AttachmentProcessor.AttachmentResult result = 
                    org.nikanikoo.flux.utils.AttachmentProcessor.processAttachments(attachments);

                if (!result.getImageUrls().isEmpty()) {
                    imageUrl = result.getImageUrls().get(0);
                }
                
                comment.setAudioAttachments(result.getAudioAttachments());
                comment.setVideoAttachments(result.getVideoAttachments());
                unsupportedElements = result.getUnsupportedElementsText();
            } catch (Exception e) {
                Logger.e(TAG, "Error parsing attachments", e);
            }
        }
        
        comment.setImageUrl(imageUrl);
        comment.setUnsupportedElementsText(unsupportedElements);

        return comment;
    }
}
