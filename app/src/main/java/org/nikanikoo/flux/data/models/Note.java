package org.nikanikoo.flux.data.models;

import java.io.Serializable;

public class Note implements Serializable {
    private int id;
    private int ownerId;
    private String title;
    private String text;
    private long date;
    private int comments;
    private int readComments;
    private String viewUrl;

    public Note() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public int getComments() { return comments; }
    public void setComments(int comments) { this.comments = comments; }

    public int getReadComments() { return readComments; }
    public void setReadComments(int readComments) { this.readComments = readComments; }

    public String getViewUrl() { return viewUrl; }
    public void setViewUrl(String viewUrl) { this.viewUrl = viewUrl; }
}
