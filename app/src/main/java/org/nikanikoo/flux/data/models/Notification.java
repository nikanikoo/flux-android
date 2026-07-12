package org.nikanikoo.flux.data.models;

import android.content.Context;

import org.nikanikoo.flux.R;

public class Notification {
    private static Context appContext;
    private String id;
    private String type;
    private String date;
    private String text;
    private String feedback;
    private String parent;
    private boolean isRead;
    private boolean isArchived;
    private String fromName;
    private String fromPhoto;
    private int fromId;
    private int postOwnerId;
    private int postId;
    private boolean commentDataLoaded;

    public static void setAppContext(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public Notification(String id, String type, String date, String text, String feedback, String parent) {
        this.id = id;
        this.type = type;
        this.date = date;
        this.text = text;
        this.feedback = feedback;
        this.parent = parent;
        this.isRead = false;
        this.isArchived = id.startsWith("archived_");
        System.out.println("Notification constructor: Created notification " + id + " with isRead = false, isArchived = " + this.isArchived);
    }

    // Getters
    public String getId() { return id; }
    public String getType() { return type; }
    public String getDate() { return date; }
    public String getText() { return text; }
    public String getFeedback() { return feedback; }
    public String getParent() { return parent; }
    public boolean isRead() { return isRead; }
    public String getFromName() { return fromName; }
    public String getFromPhoto() { return fromPhoto; }
    public int getFromId() { return fromId; }
    public int getPostOwnerId() { return postOwnerId; }
    public int getPostId() { return postId; }
    public boolean isCommentDataLoaded() { return commentDataLoaded; }
    public boolean isArchived() { return isArchived; }

    // Setters
    public void setRead(boolean read) { 
        System.out.println("Notification.setRead: Setting " + this.id + " isRead from " + this.isRead + " to " + read);
        this.isRead = read; 
    }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public void setFromPhoto(String fromPhoto) { this.fromPhoto = fromPhoto; }
    public void setFromId(int fromId) { this.fromId = fromId; }
    public void setPostOwnerId(int postOwnerId) { this.postOwnerId = postOwnerId; }
    public void setPostId(int postId) { this.postId = postId; }
    public void setCommentDataLoaded(boolean commentDataLoaded) { this.commentDataLoaded = commentDataLoaded; }
    
    // Обновление данных комментария
    public void updateCommentData(String commentText, String authorName, String authorPhoto, int authorId) {
        System.out.println("Notification.updateCommentData: Before update - isRead: " + this.isRead + ", id: " + this.id + ", fromPhoto: " + this.fromPhoto);
        this.text = commentText;
        this.fromName = authorName;
        // Не перезаписываем фото, если новое фото null/пустое (сохраняем оригинальное из уведомления)
        if (authorPhoto != null && !authorPhoto.isEmpty()) {
            this.fromPhoto = authorPhoto;
        }
        // Не перезаписываем fromId если он уже был установлен и новый равен 0
        if (authorId != 0) {
            this.fromId = authorId;
        }
        this.commentDataLoaded = true;
        System.out.println("Notification.updateCommentData: After update - isRead: " + this.isRead + ", commentDataLoaded: " + this.commentDataLoaded + ", fromPhoto: " + this.fromPhoto);
    }

    // текст
    public String getReadableType() {

        switch (type) {
            case "like_post":
                return getString(R.string.notification_like_post);
            case "comment_post":
                return getString(R.string.notification_comment_post);
            case "comment_photo":
                return getString(R.string.notification_comment_photo);
            case "sent_gift":
                return getString(R.string.notification_sent_gift);
            case "wall":
                return getString(R.string.notification_wall);
            case "mention":
                return getString(R.string.notification_mention);
            case "copy_post":
                return getString(R.string.notification_copy_post);
            default:
                return getString(R.string.notification_default);
        }
    }

    // значки
    public int getTypeIcon() {
        switch (type) {
            case "like_post":
                return R.drawable.ic_favorite;
            case "comment_post":
                return R.drawable.ic_note_stack;
            case "comment_photo":
                return R.drawable.ic_photo;
            case "sent_gift":
                return R.drawable.ic_redeem;
            case "wall":
                return R.drawable.ic_newspaper;
            case "mention":
                return R.drawable.ic_chat_bubble;
            case "copy_post":
                return R.drawable.ic_newspaper;
            default:
                return R.drawable.ic_notifications;
        }
    }

    private String getString(int resId) {
        if (appContext != null) {
            return appContext.getString(resId);
        }
        return "";
    }
}