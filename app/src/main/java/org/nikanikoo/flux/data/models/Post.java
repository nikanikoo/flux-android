package org.nikanikoo.flux.data.models;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class Post implements Serializable {
    private String authorName;
    private String timestamp;
    private String content;
    private int likeCount;
    private int commentCount;
    private String imageUrl;
    private List<String> imageUrls;
    private List<String> imageMaxResUrls;
    private List<Audio> audioAttachments;
    private List<Video> videoAttachments;
    private List<Poll> pollAttachments;
    private int authorId;
    private String authorAvatarUrl;
    private String unsupportedElementsText;
    private int postId;
    private int ownerId;
    private boolean isLiked;
    private boolean isRepost;
    private Post originalPost;
    private String repostText;
    private boolean authorVerified;
    private boolean isGroup;
    private boolean isPinned;
    private boolean canEdit;
    private boolean canDelete;
    private boolean canPin;
    private String ownerName;
    private int authorSex;
    private boolean ownerVerified;
    private boolean isOwnerGroup;
    private boolean explicit;
    private boolean isNsfwRevealed;
    private String platform;
    private String copyrightName;
    private String copyrightLink;

    public Post(String authorName, String timestamp, String content, int likeCount, int commentCount) {
        this.authorName = authorName;
        this.timestamp = timestamp;
        this.content = content;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.isLiked = false;
        this.imageUrls = new ArrayList<>();
        this.imageMaxResUrls = new ArrayList<>();
        this.audioAttachments = new ArrayList<>();
        this.videoAttachments = new ArrayList<>();
        this.pollAttachments = new ArrayList<>();
        this.isRepost = false;
    }

    // Getters
    public String getAuthorName() { return authorName; }
    public String getTimestamp() { return timestamp; }
    public String getContent() { return content; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public String getImageUrl() { return imageUrl; }
    public List<String> getImageUrls() { return imageUrls; }
    public List<String> getImageMaxResUrls() { return imageMaxResUrls; }
    public List<Audio> getAudioAttachments() { return audioAttachments; }
    public List<Video> getVideoAttachments() { return videoAttachments; }
    public List<Poll> getPollAttachments() { return pollAttachments; }
    public int getAuthorId() { return authorId; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public String getUnsupportedElementsText() { return unsupportedElementsText; }
    public int getPostId() { return postId; }
    public int getOwnerId() { return ownerId; }
    public boolean isLiked() { return isLiked; }
    public boolean isRepost() { return isRepost; }
    public Post getOriginalPost() { return originalPost; }
    public String getRepostText() { return repostText; }
    public boolean isAuthorVerified() { return authorVerified; }
    public boolean isGroup() { return isGroup; }
    public boolean isPinned() { return isPinned; }
    public boolean canEdit() { return canEdit; }
    public boolean canDelete() { return canDelete; }
    public boolean canPin() { return canPin; }

    // Setters
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setContent(String content) { this.content = content; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public void setImageUrl(String imageUrl) { 
        this.imageUrl = imageUrl;
        if (imageUrl != null && !imageUrl.isEmpty() && imageUrls.isEmpty()) {
            imageUrls.add(imageUrl);
        }
    }
    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
        if (imageUrls != null && !imageUrls.isEmpty()) {
            this.imageUrl = imageUrls.get(0);
        }
    }
    public void setImageMaxResUrls(List<String> imageMaxResUrls) {
        this.imageMaxResUrls = imageMaxResUrls != null ? imageMaxResUrls : new ArrayList<>();
    }
    public void addImageUrl(String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            imageUrls.add(imageUrl);
            if (this.imageUrl == null || this.imageUrl.isEmpty()) {
                this.imageUrl = imageUrl;
            }
        }
    }
    public void setAudioAttachments(List<Audio> audioAttachments) { 
        this.audioAttachments = audioAttachments != null ? audioAttachments : new ArrayList<>();
    }
    public void addAudioAttachment(Audio audio) {
        if (audio != null) {
            if (audioAttachments == null) {
                audioAttachments = new ArrayList<>();
            }
            audioAttachments.add(audio);
        }
    }
    public void setVideoAttachments(List<Video> videoAttachments) { 
        this.videoAttachments = videoAttachments != null ? videoAttachments : new ArrayList<>();
    }
    public void addVideoAttachment(Video video) {
        if (video != null) {
            if (videoAttachments == null) {
                videoAttachments = new ArrayList<>();
            }
            videoAttachments.add(video);
        }
    }
    public void setPollAttachments(List<Poll> pollAttachments) {
        this.pollAttachments = pollAttachments != null ? pollAttachments : new ArrayList<>();
    }
    public void addPollAttachment(Poll poll) {
        if (poll != null) {
            if (pollAttachments == null) {
                pollAttachments = new ArrayList<>();
            }
            pollAttachments.add(poll);
        }
    }
    public void setAuthorId(int authorId) { this.authorId = authorId; }
    public void setAuthorAvatarUrl(String authorAvatarUrl) { this.authorAvatarUrl = authorAvatarUrl; }
    public void setUnsupportedElementsText(String unsupportedElementsText) { this.unsupportedElementsText = unsupportedElementsText; }
    public void setPostId(int postId) { this.postId = postId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
    public void setLiked(boolean liked) { this.isLiked = liked; }
    public void setRepost(boolean repost) { this.isRepost = repost; }
    public void setOriginalPost(Post originalPost) { this.originalPost = originalPost; }
    public void setRepostText(String repostText) { this.repostText = repostText; }
    public void setAuthorVerified(boolean authorVerified) { this.authorVerified = authorVerified; }
    public void setGroup(boolean group) { this.isGroup = group; }
    public void setPinned(boolean pinned) { this.isPinned = pinned; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }
    public void setCanPin(boolean canPin) { this.canPin = canPin; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    
    public int getAuthorSex() { return authorSex; }
    public void setAuthorSex(int authorSex) { this.authorSex = authorSex; }

    public boolean isOwnerVerified() { return ownerVerified; }
    public void setOwnerVerified(boolean ownerVerified) { this.ownerVerified = ownerVerified; }

    public boolean isOwnerGroup() { return isOwnerGroup; }
    public void setOwnerGroup(boolean isOwnerGroup) { this.isOwnerGroup = isOwnerGroup; }

    public boolean isExplicit() { return explicit; }
    public void setExplicit(boolean explicit) { this.explicit = explicit; }

    public boolean isNsfwRevealed() { return isNsfwRevealed; }
    public void setNsfwRevealed(boolean nsfwRevealed) { this.isNsfwRevealed = nsfwRevealed; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getCopyrightName() { return copyrightName; }
    public void setCopyrightName(String copyrightName) { this.copyrightName = copyrightName; }

    public String getCopyrightLink() { return copyrightLink; }
    public void setCopyrightLink(String copyrightLink) { this.copyrightLink = copyrightLink; }
}