package org.nikanikoo.flux.data.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;

public class AudioPlaylist implements Serializable {
    private int id;
    private int ownerId;
    private String title;
    private String description;
    private String photoUrl;
    private String accessKey;
    private int tracksCount;
    private String authorName;

    public AudioPlaylist() {}

    public static AudioPlaylist fromJson(JSONObject json) {
        AudioPlaylist playlist = new AudioPlaylist();
        if (json == null) return playlist;

        try {
            playlist.id = json.optInt("id", 0);
            playlist.ownerId = json.optInt("owner_id", 0);
            playlist.title = json.optString("title", "Без названия");
            playlist.description = json.optString("description", "");
            playlist.accessKey = json.optString("access_key", "");
            playlist.tracksCount = json.optInt("count", json.optInt("size", 0));

            // Parsing photo/thumbs
            if (json.has("cover_url")) {
                playlist.photoUrl = json.optString("cover_url", "");
            } else if (json.has("photo")) {
                JSONObject photoObj = json.optJSONObject("photo");
                if (photoObj != null) {
                    playlist.photoUrl = photoObj.optString("photo_300", 
                            photoObj.optString("photo_600", 
                            photoObj.optString("photo_135", "")));
                } else {
                    playlist.photoUrl = json.optString("photo", "");
                }
            } else if (json.has("thumbs")) {
                JSONArray thumbs = json.optJSONArray("thumbs");
                if (thumbs != null && thumbs.length() > 0) {
                    JSONObject firstThumb = thumbs.optJSONObject(0);
                    if (firstThumb != null) {
                        playlist.photoUrl = firstThumb.optString("photo_300", 
                                firstThumb.optString("photo_600", 
                                firstThumb.optString("photo_135", "")));
                    }
                }
            }

            // Parsing author name
            if (json.has("owner_name")) {
                playlist.authorName = json.optString("owner_name", "");
            } else if (json.has("author_name")) {
                playlist.authorName = json.optString("author_name", "");
            } else if (json.has("author")) {
                JSONObject authorObj = json.optJSONObject("author");
                if (authorObj != null) {
                    playlist.authorName = authorObj.optString("name", "");
                } else {
                    playlist.authorName = json.optString("author", "");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return playlist;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public int getTracksCount() { return tracksCount; }
    public void setTracksCount(int tracksCount) { this.tracksCount = tracksCount; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
}
