package org.nikanikoo.flux.data.models;

import java.util.ArrayList;
import java.util.List;

public class Message {
    private int id;
    private int userId;
    private int fromId;
    private String text;
    private long date;
    private boolean out;
    private boolean readState;
    private String userName;
    private String userPhoto;
    private boolean userVerified;
    private List<MessageAttachment> attachments = new ArrayList<>();
    private MessageReference reply;
    private List<MessageReference> forwardedMessages = new ArrayList<>();

    public Message(int id, int userId, int fromId, String text, long date, boolean out, boolean readState) {
        this.id = id;
        this.userId = userId;
        this.fromId = fromId;
        this.text = text;
        this.date = date;
        this.out = out;
        this.readState = readState;
    }

    // Getters
    public int getId() { return id; }
    public int getUserId() { return userId; }
    public int getFromId() { return fromId; }
    public String getText() { return text; }
    public long getDate() { return date; }
    public boolean isOut() { return out; }
    public boolean isReadState() { return readState; }
    public String getUserName() { return userName; }
    public String getUserPhoto() { return userPhoto; }
    public boolean isUserVerified() { return userVerified; }
    public List<MessageAttachment> getAttachments() { return attachments; }
    public MessageReference getReply() { return reply; }
    public List<MessageReference> getForwardedMessages() { return forwardedMessages; }

    // Setters
    public void setUserName(String userName) { this.userName = userName; }
    public void setUserPhoto(String userPhoto) { this.userPhoto = userPhoto; }
    public void setReadState(boolean readState) { this.readState = readState; }
    public void setUserVerified(boolean userVerified) { this.userVerified = userVerified; }
    public void setAttachments(List<MessageAttachment> attachments) {
        this.attachments = attachments != null ? attachments : new ArrayList<>();
    }
    public void setReply(MessageReference reply) { this.reply = reply; }
    public void setForwardedMessages(List<MessageReference> messages) {
        this.forwardedMessages = messages != null ? messages : new ArrayList<>();
    }
}
