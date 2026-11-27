package org.nikanikoo.flux.data.models;

import java.util.ArrayList;
import java.util.List;

public class Comment {
    private int id;
    private int fromId;
    private String authorName;
    private String authorAvatarUrl;
    private String text;
    private String timestamp;
    private long date;
    private int likesCount;
    private boolean isLiked;
    private String imageUrl;
    private List<Audio> audioAttachments;
    private List<Video> videoAttachments;
    private List<String> stickerImageUrls = new ArrayList<>();
    private String unsupportedElementsText;
    private boolean authorVerified;
    private boolean isGroup;

    public Comment(int id, int fromId, String authorName, String text, long date) {
        this.id = id;
        this.fromId = fromId;
        this.authorName = authorName;
        this.text = text;
        this.date = date;
        this.likesCount = 0;
        this.isLiked = false;
        this.audioAttachments = new ArrayList<>();
        this.videoAttachments = new ArrayList<>();
    }

    // Getters
    public int getId() { return id; }
    public int getFromId() { return fromId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public String getText() { return text; }
    public String getTimestamp() { return timestamp; }
    public long getDate() { return date; }
    public int getLikesCount() { return likesCount; }
    public boolean isLiked() { return isLiked; }
    public String getImageUrl() { return imageUrl; }
    public List<Audio> getAudioAttachments() { return audioAttachments; }
    public List<Video> getVideoAttachments() { return videoAttachments; }
    public List<String> getStickerImageUrls() { return stickerImageUrls; }
    public String getUnsupportedElementsText() { return unsupportedElementsText; }
    public boolean isAuthorVerified() { return authorVerified; }
    public boolean isGroup() { return isGroup; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setFromId(int fromId) { this.fromId = fromId; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public void setAuthorAvatarUrl(String authorAvatarUrl) { this.authorAvatarUrl = authorAvatarUrl; }
    public void setText(String text) { this.text = text; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setDate(long date) { this.date = date; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }
    public void setLiked(boolean liked) { this.isLiked = liked; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
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
    public void setStickerImageUrls(List<String> stickerImageUrls) {
        this.stickerImageUrls = stickerImageUrls != null ? stickerImageUrls : new ArrayList<>();
    }
    public void addVideoAttachment(Video video) {
        if (video != null) {
            if (videoAttachments == null) {
                videoAttachments = new ArrayList<>();
            }
            videoAttachments.add(video);
        }
    }
    public void setUnsupportedElementsText(String unsupportedElementsText) { this.unsupportedElementsText = unsupportedElementsText; }
    public void setAuthorVerified(boolean authorVerified) { this.authorVerified = authorVerified; }
    public void setGroup(boolean group) { this.isGroup = group; }
}
