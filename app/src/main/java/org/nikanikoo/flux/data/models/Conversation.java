package org.nikanikoo.flux.data.models;

public class Conversation {
    private int id;
    private int peerId;
    private String title;
    private String lastMessage;
    private long lastMessageDate;
    private int unreadCount;
    private String peerPhoto;
    private boolean isOnline;
    private boolean peerVerified;
    private Boolean notificationsEnabled;

    public Conversation(int id, int peerId, String title, String lastMessage,
                       long lastMessageDate, int unreadCount) {
        this.id = id;
        this.peerId = peerId;
        this.title = title;
        this.lastMessage = lastMessage;
        this.lastMessageDate = lastMessageDate;
        this.unreadCount = unreadCount;
    }

    // Getters
    public int getId() { return id; }
    public int getPeerId() { return peerId; }
    public String getTitle() { return title; }
    public String getLastMessage() { return lastMessage; }
    public long getLastMessageDate() { return lastMessageDate; }
    public int getUnreadCount() { return unreadCount; }
    public String getPeerPhoto() { return peerPhoto; }
    public boolean isOnline() { return isOnline; }
    public boolean isPeerVerified() { return peerVerified; }
    public Boolean areNotificationsEnabled() { return notificationsEnabled; }

    // Setters
    public void setPeerPhoto(String peerPhoto) { this.peerPhoto = peerPhoto; }
    public void setOnline(boolean online) { this.isOnline = online; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public void setLastMessageDate(long lastMessageDate) { this.lastMessageDate = lastMessageDate; }
    public void setPeerVerified(boolean peerVerified) { this.peerVerified = peerVerified; }
    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }
}
