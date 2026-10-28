package org.nikanikoo.flux.data.models;

import java.io.Serializable;
import java.util.Locale;

public class Video implements Serializable {
    private int id;
    private int ownerId;
    private String title;
    private String description;
    private int duration;
    private String image;
    private String firstFrame;
    private long date;
    private long addingDate;
    private int views;
    private int localViews;
    private int comments;
    private String player;
    private String platform;
    private String accessKey;
    private boolean canEdit;
    private boolean canAdd;
    private boolean isPrivate;
    private int processing;
    private boolean isFavorite;
    private boolean canComment;
    private boolean canRepost;
    private boolean userLikes;
    private boolean repeat;
    private int likes;
    private int width;
    private int height;

    public Video() {}

    // Getters
    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getDuration() { return duration; }
    public String getImage() { return image; }
    public String getFirstFrame() { return firstFrame; }
    public long getDate() { return date; }
    public long getAddingDate() { return addingDate; }
    public int getViews() { return views; }
    public int getLocalViews() { return localViews; }
    public int getComments() { return comments; }
    public String getPlayer() { return player; }
    public String getPlatform() { return platform; }
    public String getAccessKey() { return accessKey; }
    public boolean isCanEdit() { return canEdit; }
    public boolean isCanAdd() { return canAdd; }
    public boolean isPrivate() { return isPrivate; }
    public int getProcessing() { return processing; }
    public boolean isFavorite() { return isFavorite; }
    public boolean isCanComment() { return canComment; }
    public boolean isCanRepost() { return canRepost; }
    public boolean isUserLikes() { return userLikes; }
    public boolean isRepeat() { return repeat; }
    public int getLikes() { return likes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setDuration(int duration) { this.duration = duration; }
    public void setImage(String image) { this.image = image; }
    public void setFirstFrame(String firstFrame) { this.firstFrame = firstFrame; }
    public void setDate(long date) { this.date = date; }
    public void setAddingDate(long addingDate) { this.addingDate = addingDate; }
    public void setViews(int views) { this.views = views; }
    public void setLocalViews(int localViews) { this.localViews = localViews; }
    public void setComments(int comments) { this.comments = comments; }
    public void setPlayer(String player) { this.player = player; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public void setCanAdd(boolean canAdd) { this.canAdd = canAdd; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    public void setProcessing(int processing) { this.processing = processing; }
    public void setFavorite(boolean favorite) { this.isFavorite = favorite; }
    public void setCanComment(boolean canComment) { this.canComment = canComment; }
    public void setCanRepost(boolean canRepost) { this.canRepost = canRepost; }
    public void setUserLikes(boolean userLikes) { this.userLikes = userLikes; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }
    public void setLikes(int likes) { this.likes = likes; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }

    public String getFormattedDuration() {
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public String getUniqueId() {
        return ownerId + "_" + id;
    }
}
