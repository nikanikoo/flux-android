package org.nikanikoo.flux.data.models;

public class Sticker {
    private final int id;
    private final String packName;
    private final String imageUrl;
    private final String emoji;

    public Sticker(int id, String packName, String imageUrl, String emoji) {
        this.id = id;
        this.packName = packName;
        this.imageUrl = imageUrl;
        this.emoji = emoji;
    }

    public int getId() { return id; }
    public String getPackName() { return packName; }
    public String getImageUrl() { return imageUrl; }
    public String getEmoji() { return emoji; }
}
