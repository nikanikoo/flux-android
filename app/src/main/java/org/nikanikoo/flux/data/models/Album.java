package org.nikanikoo.flux.data.models;

import java.io.Serializable;

public class Album implements Serializable {

    private int id;
    private int ownerId;
    private String title;
    private String description;
    private int size;
    private long created;
    private long updated;

    private String thumbSrc;
    private int thumbId;
    private boolean canUpload = true;

    public Album() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getCreated() { return created; }
    public void setCreated(long created) { this.created = created; }

    public long getUpdated() { return updated; }
    public void setUpdated(long updated) { this.updated = updated; }

    public String getThumbSrc() { return thumbSrc; }
    public void setThumbSrc(String thumbSrc) { this.thumbSrc = thumbSrc; }

    public int getThumbId() { return thumbId; }
    public void setThumbId(int thumbId) { this.thumbId = thumbId; }

    public boolean canUpload() { return canUpload; }
    public void setCanUpload(boolean canUpload) { this.canUpload = canUpload; }
}
