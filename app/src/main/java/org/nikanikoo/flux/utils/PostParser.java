package org.nikanikoo.flux.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.models.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostParser {
    private static final String TAG = "PostParser";

    public static class AuthorInfo {
        public final int id;
        public final String name;
        public final String avatarUrl;
        public final boolean isGroup;
        public final boolean verified;

        public AuthorInfo(int id, String name, String avatarUrl, boolean isGroup, boolean verified) {
            this.id = id;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.isGroup = isGroup;
            this.verified = verified;
        }
    }

    public static List<Post> parsePostsFromNewsfeed(
            JSONArray items,
            JSONArray profiles,
            JSONArray groups
    ) {
        List<Post> posts = new ArrayList<>();
        
        if (items == null) {
            return posts;
        }

        Map<Integer, JsonUtils.ProfileInfo> profileMap = JsonUtils.parseProfiles(profiles);
        Map<Integer, JsonUtils.GroupInfo> groupMap = JsonUtils.parseGroups(groups);

        try {
            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    Post post = parsePost(item, profileMap, groupMap);
                    if (post != null) {
                        posts.add(post);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing post at index " + i, e);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing newsfeed posts", e);
        }

        return posts;
    }

    public static List<Post> parsePostsFromWall(
            JSONArray items,
            JSONArray profiles,
            JSONArray groups,
            int ownerId
    ) {
        List<Post> posts = new ArrayList<>();
        
        if (items == null) {
            return posts;
        }

        Map<Integer, JsonUtils.ProfileInfo> profileMap = JsonUtils.parseProfiles(profiles);
        Map<Integer, JsonUtils.GroupInfo> groupMap = JsonUtils.parseGroups(groups);

        try {
            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    Post post = parsePost(item, profileMap, groupMap);
                    if (post != null) {
                        posts.add(post);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing wall post at index " + i, e);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing wall posts", e);
        }

        return posts;
    }

    private static Post parsePost(
            JSONObject postData,
            Map<Integer, JsonUtils.ProfileInfo> profileMap,
            Map<Integer, JsonUtils.GroupInfo> groupMap
    ) {
        if (postData == null) {
            return null;
        }

        try {
            JSONObject actualPostData = postData;
            String type = postData.optString("type", "");
            if ("post".equals(type) && postData.has("post")) {
                actualPostData = postData.getJSONObject("post");
            }

            int postId = ValidationUtils.safeGetInt(actualPostData, "id", 0);
            int fromId = ValidationUtils.safeGetInt(actualPostData, "from_id", 0);
            int ownerId = ValidationUtils.safeGetInt(actualPostData, "owner_id", 0);
            int sourceId = ValidationUtils.safeGetInt(actualPostData, "source_id", 0);
            long timestamp = ValidationUtils.safeGetLong(actualPostData, "date", 0);
            String text = ValidationUtils.safeGetString(actualPostData, "text", "");

            JSONObject likes = ValidationUtils.safeGetJSONObject(actualPostData, "likes");
            int likeCount = ValidationUtils.safeGetInt(likes, "count", 0);
            int userLikes = ValidationUtils.safeGetInt(likes, "user_likes", 0);
            boolean isLiked = userLikes == 1;
            
            JSONObject comments = ValidationUtils.safeGetJSONObject(actualPostData, "comments");
            int commentCount = ValidationUtils.safeGetInt(comments, "count", 0);

            int authorSourceId = sourceId != 0 ? sourceId : (fromId != 0 ? fromId : ownerId);
            AuthorInfo authorInfo = extractAuthorInfo(authorSourceId, profileMap, groupMap);

            String timeAgo = TimeUtils.formatTimeAgo(timestamp);

            int authorSex = 0;
            if (authorSourceId > 0) {
                JsonUtils.ProfileInfo authorProfile = profileMap.get(authorSourceId);
                if (authorProfile != null) {
                    authorSex = authorProfile.sex;
                }
            }

            AuthorInfo ownerInfo = extractAuthorInfo(ownerId, profileMap, groupMap);

            Post post = new Post(authorInfo.name, timeAgo, text, likeCount, commentCount);
            post.setPostId(postId);
            post.setOwnerId(ownerId);
            post.setAuthorId(authorInfo.id);
            post.setAuthorAvatarUrl(authorInfo.avatarUrl);
            post.setAuthorVerified(authorInfo.verified);
            post.setGroup(authorInfo.isGroup);
            post.setLiked(isLiked);
            
            post.setOwnerName(ownerInfo.name);
            post.setOwnerVerified(ownerInfo.verified);
            post.setOwnerGroup(ownerInfo.isGroup);
            post.setAuthorSex(authorSex);

            boolean isPinned = actualPostData.optInt("is_pinned", 0) == 1 || 
                              actualPostData.optBoolean("is_pinned", false) ||
                              actualPostData.optInt("pinned", 0) == 1 ||
                              actualPostData.optBoolean("pinned", false);
            post.setPinned(isPinned);
            
            boolean isExplicit = actualPostData.optInt("is_explicit", 0) == 1 || 
                               actualPostData.optBoolean("is_explicit", false) ||
                               actualPostData.optInt("explicit", 0) == 1 ||
                               actualPostData.optBoolean("explicit", false);
            post.setExplicit(isExplicit);

            boolean canEdit = actualPostData.optInt("can_edit", 0) == 1 ||
                    actualPostData.optBoolean("can_edit", false) ||
                    actualPostData.optInt("can_edit", 0) == 1 ||
                    actualPostData.optBoolean("can_edit", false);
            post.setCanEdit(canEdit);

            boolean canDelete = actualPostData.optInt("can_delete", 0) == 1 ||
                    actualPostData.optBoolean("can_delete", false) ||
                    actualPostData.optInt("can_delete", 0) == 1 ||
                    actualPostData.optBoolean("can_delete", false);
            post.setCanDelete(canDelete);

            boolean canPin = actualPostData.optInt("can_pin", 0) == 1 ||
                    actualPostData.optBoolean("can_pin", false) ||
                    actualPostData.optInt("can_pin", 0) == 1 ||
                    actualPostData.optBoolean("can_pin", false);
            post.setCanPin(canPin);

            JSONObject postSource = actualPostData.optJSONObject("post_source");
            if (postSource != null) {
                String platform = postSource.optString("platform", null);
                if (platform != null && !platform.isEmpty()) {
                    post.setPlatform(platform);
                }
            }

            JSONObject copyright = actualPostData.optJSONObject("copyright");
            if (copyright != null) {
                String link = copyright.optString("link", null);
                String name = copyright.optString("name", null);
                if (link != null && !link.isEmpty()) {
                    post.setCopyrightLink(link);
                    post.setCopyrightName(name);
                }
            }

            JSONArray attachments = ValidationUtils.safeGetJSONArray(actualPostData, "attachments");
            AttachmentProcessor.AttachmentResult attachmentResult = AttachmentProcessor.processAttachments(attachments);
            
            post.setImageUrls(attachmentResult.getImageUrls());
            post.setImageMaxResUrls(attachmentResult.getImageMaxResUrls());
            post.setAudioAttachments(attachmentResult.getAudioAttachments());
            post.setVideoAttachments(attachmentResult.getVideoAttachments());
            post.setPollAttachments(attachmentResult.getPollAttachments());
            post.setUnsupportedElementsText(attachmentResult.getUnsupportedElementsText());

            JSONArray copyHistory = ValidationUtils.safeGetJSONArray(actualPostData, "copy_history");
            if (copyHistory.length() > 0) {
                post.setRepost(true);
                post.setRepostText(text);

                try {
                    JSONObject originalPostData = copyHistory.getJSONObject(0);
                    Post originalPost = parseOriginalPost(originalPostData, profileMap, groupMap);
                    post.setOriginalPost(originalPost);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing original post", e);
                }
            }

            return post;
            
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing post", e);
            return null;
        }
    }

    private static Post parseOriginalPost(
            JSONObject originalPostData,
            Map<Integer, JsonUtils.ProfileInfo> profileMap,
            Map<Integer, JsonUtils.GroupInfo> groupMap
    ) {
        if (originalPostData == null) {
            return null;
        }

        try {
            int postId = ValidationUtils.safeGetInt(originalPostData, "id", 0);
            int fromId = ValidationUtils.safeGetInt(originalPostData, "from_id", 0);
            int ownerId = ValidationUtils.safeGetInt(originalPostData, "owner_id", 0);
            long timestamp = ValidationUtils.safeGetLong(originalPostData, "date", 0);
            String text = ValidationUtils.safeGetString(originalPostData, "text", "");

            JSONObject likes = ValidationUtils.safeGetJSONObject(originalPostData, "likes");
            int likeCount = ValidationUtils.safeGetInt(likes, "count", 0);
            int userLikes = ValidationUtils.safeGetInt(likes, "user_likes", 0);
            boolean isLiked = userLikes == 1;
            
            JSONObject comments = ValidationUtils.safeGetJSONObject(originalPostData, "comments");
            int commentCount = ValidationUtils.safeGetInt(comments, "count", 0);

            int sourceId = fromId != 0 ? fromId : ownerId;
            AuthorInfo authorInfo = extractAuthorInfo(sourceId, profileMap, groupMap);

            String timeAgo = TimeUtils.formatTimeAgo(timestamp);

            int authorSex = 0;
            if (sourceId > 0) {
                JsonUtils.ProfileInfo authorProfile = profileMap.get(sourceId);
                if (authorProfile != null) {
                    authorSex = authorProfile.sex;
                }
            }

            AuthorInfo ownerInfo = extractAuthorInfo(ownerId, profileMap, groupMap);

            Post post = new Post(authorInfo.name, timeAgo, text, likeCount, commentCount);
            post.setPostId(postId);
            post.setOwnerId(ownerId);
            post.setAuthorId(authorInfo.id);
            post.setAuthorAvatarUrl(authorInfo.avatarUrl);
            post.setAuthorVerified(authorInfo.verified);
            post.setGroup(authorInfo.isGroup);
            post.setLiked(isLiked);
            
            post.setOwnerName(ownerInfo.name);
            post.setOwnerVerified(ownerInfo.verified);
            post.setOwnerGroup(ownerInfo.isGroup);
            post.setAuthorSex(authorSex);

            boolean isExplicit = originalPostData.optInt("is_explicit", 0) == 1 || 
                               originalPostData.optBoolean("is_explicit", false) ||
                               originalPostData.optInt("explicit", 0) == 1 ||
                               originalPostData.optBoolean("explicit", false);
            post.setExplicit(isExplicit);

            JSONObject postSource = originalPostData.optJSONObject("post_source");
            if (postSource != null) {
                String platform = postSource.optString("platform", null);
                if (platform != null && !platform.isEmpty()) {
                    post.setPlatform(platform);
                }
            }

            JSONObject copyright = originalPostData.optJSONObject("copyright");
            if (copyright != null) {
                String link = copyright.optString("link", null);
                String name = copyright.optString("name", null);
                if (link != null && !link.isEmpty()) {
                    post.setCopyrightLink(link);
                    post.setCopyrightName(name);
                }
            }

            JSONArray attachments = ValidationUtils.safeGetJSONArray(originalPostData, "attachments");
            AttachmentProcessor.AttachmentResult attachmentResult = AttachmentProcessor.processAttachments(attachments);
            
            post.setImageUrls(attachmentResult.getImageUrls());
            post.setImageMaxResUrls(attachmentResult.getImageMaxResUrls());
            post.setAudioAttachments(attachmentResult.getAudioAttachments());
            post.setVideoAttachments(attachmentResult.getVideoAttachments());
            post.setPollAttachments(attachmentResult.getPollAttachments());
            post.setUnsupportedElementsText(attachmentResult.getUnsupportedElementsText());

            return post;
            
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing original post", e);
            return null;
        }
    }

    private static AuthorInfo extractAuthorInfo(
            int sourceId,
            Map<Integer, JsonUtils.ProfileInfo> profileMap,
            Map<Integer, JsonUtils.GroupInfo> groupMap
    ) {
        if (sourceId > 0) {
            // User
            JsonUtils.ProfileInfo profile = profileMap.get(sourceId);
            if (profile != null) {
                return new AuthorInfo(sourceId, profile.name, profile.avatarUrl, false, profile.verified);
            } else {
                return new AuthorInfo(sourceId, "Unknown User", "", false, false);
            }
        } else if (sourceId < 0) {
            // Group
            int groupId = Math.abs(sourceId);
            JsonUtils.GroupInfo group = groupMap.get(groupId);
            if (group != null) {
                return new AuthorInfo(groupId, group.name, group.avatarUrl, true, group.verified);
            } else {
                return new AuthorInfo(groupId, "Unknown Group", "", true, false);
            }
        } else {
            return new AuthorInfo(0, "Unknown", "", false, false);
        }
    }
}
