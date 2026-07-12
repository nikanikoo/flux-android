package org.nikanikoo.flux.data.models;

import java.io.Serializable;
import java.util.Locale;

public class Audio implements Serializable {
    private String uniqueId;
    private int id;
    private int ownerId;
    private String artist;
    private String title;
    private int duration;
    private String url;
    private String manifest;
    private String coverUrl;
    private int genreId;
    private String genreStr;
    private int lyrics_id;
    private boolean added;
    private boolean editable;
    private boolean searchable;
    private boolean explicit;
    private boolean withdrawn;
    private boolean ready;

    public Audio() {}

    // Getters
    public String getUniqueId() { return uniqueId; }
    public int getId() { return id; }
    public int getOwnerId() { return ownerId; }
    public String getArtist() { return artist; }
    public String getTitle() { return title; }
    public int getDuration() { return duration; }
    public String getUrl() { return url; }
    public String getManifest() { return manifest; }
    public String getCoverUrl() { return coverUrl; }
    public int getGenreId() { return genreId; }
    public String getGenreStr() { return genreStr; }
    public int getLyrics_id() { return lyrics_id; }
    public boolean isAdded() { return added; }
    public boolean isEditable() { return editable; }
    public boolean isSearchable() { return searchable; }
    public boolean isExplicit() { return explicit; }
    public boolean isWithdrawn() { return withdrawn; }
    public boolean isReady() { return ready; }

    // Setters
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }
    public void setId(int id) { this.id = id; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setTitle(String title) { this.title = title; }
    public void setDuration(int duration) { this.duration = duration; }
    public void setUrl(String url) { this.url = url; }
    public void setManifest(String manifest) { this.manifest = manifest; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setGenreId(int genreId) { this.genreId = genreId; }
    public void setGenreStr(String genreStr) { this.genreStr = genreStr; }
    public void setLyrics_id(int lyrics_id) { this.lyrics_id = lyrics_id; }
    public void setAdded(boolean added) { this.added = added; }
    public void setEditable(boolean editable) { this.editable = editable; }
    public void setSearchable(boolean searchable) { this.searchable = searchable; }
    public void setExplicit(boolean explicit) { this.explicit = explicit; }
    public void setWithdrawn(boolean withdrawn) { this.withdrawn = withdrawn; }
    public void setReady(boolean ready) { this.ready = ready; }

    public String getFullTitle() {
        return artist + " - " + title;
    }

    public String getFormattedDuration() {
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
