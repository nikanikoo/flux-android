package org.nikanikoo.flux.data.models;

public class StickerPack {
    private final int id;
    private final String name;
    private final String description;
    private final int price;
    private final boolean purchased;
    private final boolean available;
    private final String previewUrl;

    public StickerPack(int id, String name, String description, int price,
                       boolean purchased, boolean available, String previewUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.purchased = purchased;
        this.available = available;
        this.previewUrl = previewUrl;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getPrice() { return price; }
    public boolean isPurchased() { return purchased; }
    public boolean isAvailable() { return available; }
    public String getPreviewUrl() { return previewUrl; }
}
