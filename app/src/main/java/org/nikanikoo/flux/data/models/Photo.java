package org.nikanikoo.flux.data.models;

import java.io.Serializable;

public class Photo implements Serializable {

    private int id;
    private int ownerId;
    private int albumId;
    private int width;
    private int height;
    private long date;
    private String text;

    private String photo75;
    private String photo130;
    private String photo604;
    private String photo807;
    private String photo1280;
    private String photo2560;

    public Photo() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public int getAlbumId() { return albumId; }
    public void setAlbumId(int albumId) { this.albumId = albumId; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getPhoto75() { return photo75; }
    public void setPhoto75(String photo75) { this.photo75 = photo75; }

    public String getPhoto130() { return photo130; }
    public void setPhoto130(String photo130) { this.photo130 = photo130; }

    public String getPhoto604() { return photo604; }
    public void setPhoto604(String photo604) { this.photo604 = photo604; }

    public String getPhoto807() { return photo807; }
    public void setPhoto807(String photo807) { this.photo807 = photo807; }

    public String getPhoto1280() { return photo1280; }
    public void setPhoto1280(String photo1280) { this.photo1280 = photo1280; }

    public String getPhoto2560() { return photo2560; }
    public void setPhoto2560(String photo2560) { this.photo2560 = photo2560; }

    public String getBestUrl() {
        if (photo2560 != null && !photo2560.isEmpty()) return photo2560;
        if (photo1280 != null && !photo1280.isEmpty()) return photo1280;
        if (photo807 != null && !photo807.isEmpty()) return photo807;
        if (photo604 != null && !photo604.isEmpty()) return photo604;
        if (photo130 != null && !photo130.isEmpty()) return photo130;
        return photo75 != null ? photo75 : "";
    }

    public String getThumbnailUrl() {
        if (photo604 != null && !photo604.isEmpty()) return photo604;
        if (photo130 != null && !photo130.isEmpty()) return photo130;
        return photo75 != null ? photo75 : "";
    }
}
