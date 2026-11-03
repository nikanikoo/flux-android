package org.nikanikoo.flux.data.models;

public class MessageAttachment {
    private final String type;
    private final String imageUrl;
    private final String title;
    private final String subtitle;
    private final Video video;
    private final String url;
    private final String targetUrl;
    private final int ownerId;
    private final int id;
    private final String accessKey;
    private final String attachmentId;

    public MessageAttachment(String type, String imageUrl, String title, String subtitle) {
        this(type, imageUrl, title, subtitle, null);
    }

    public MessageAttachment(String type, String imageUrl, String title, String subtitle, Video video) {
        this(type, imageUrl, title, subtitle, video, "", "", 0, 0, null);
    }

    public MessageAttachment(String type, String imageUrl, String title, String subtitle,
                            Video video, String url, String targetUrl) {
        this(type, imageUrl, title, subtitle, video, url, targetUrl, 0, 0, null);
    }

    public MessageAttachment(String type, String imageUrl, String title, String subtitle,
                            Video video, String url, String targetUrl,
                            int ownerId, int id, String accessKey) {
        this(type, imageUrl, title, subtitle, video, url, targetUrl, ownerId, id, accessKey, "");
    }

    public MessageAttachment(String type, String imageUrl, String title, String subtitle,
                            Video video, String url, String targetUrl,
                            int ownerId, int id, String accessKey, String attachmentId) {
        this.type = type;
        this.imageUrl = imageUrl;
        this.title = title;
        this.subtitle = subtitle;
        this.video = video;
        this.url = url;
        this.targetUrl = targetUrl;
        this.ownerId = ownerId;
        this.id = id;
        this.accessKey = accessKey;
        this.attachmentId = attachmentId != null ? attachmentId : "";
    }

    public String getType() { return type; }
    public String getImageUrl() { return imageUrl; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public Video getVideo() { return video; }
    public String getUrl() { return url; }
    public String getTargetUrl() { return targetUrl; }
    public int getOwnerId() { return ownerId; }
    public int getId() { return id; }
    public String getAccessKey() { return accessKey; }
    public String getAttachmentId() { return attachmentId; }
    public boolean isGifDocument() {
        if (!"doc".equals(type)) return false;
        return "gif".equalsIgnoreCase(subtitle)
                || (url != null && url.matches("(?i).*\\.gif(?:[?#].*)?$"));
    }
}
