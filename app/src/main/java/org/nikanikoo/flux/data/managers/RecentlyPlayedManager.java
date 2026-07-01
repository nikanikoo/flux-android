package org.nikanikoo.flux.data.managers;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nikanikoo.flux.data.models.AudioPlaylist;
import org.nikanikoo.flux.security.AccountManager;
import org.nikanikoo.flux.utils.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecentlyPlayedManager {
    private static final String TAG = "RecentlyPlayedManager";
    private static final String PREF_NAME = "recently_played_prefs";
    private static final String KEY_PREFIX = "recently_played_list_";
    private static final int MAX_ITEMS = 12;

    private static RecentlyPlayedManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    private RecentlyPlayedManager(Context context) {
        this.context = context.getApplicationContext();
        // Storing in a separate SharedPreferences file which is not in the cache directory
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized RecentlyPlayedManager getInstance(Context context) {
        if (instance == null) {
            instance = new RecentlyPlayedManager(context);
        }
        return instance;
    }

    public static class Item implements Serializable {
        public String type; // "playlist" or "artist"
        public int id; // playlist ID, 0 for artist
        public int ownerId; // playlist owner ID, 0 for artist
        public String title; // playlist title or artist name
        public String creatorName; // playlist creator name, empty for artist
        public String coverUrl; // cover image URL, empty for artist (fetched via Last.fm)
        public long timestamp;

        public Item() {}

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("id", id);
            json.put("ownerId", ownerId);
            json.put("title", title);
            json.put("creatorName", creatorName);
            json.put("coverUrl", coverUrl);
            json.put("timestamp", timestamp);
            return json;
        }

        public static Item fromJson(JSONObject json) throws JSONException {
            Item item = new Item();
            item.type = json.getString("type");
            item.id = json.optInt("id", 0);
            item.ownerId = json.optInt("ownerId", 0);
            item.title = json.getString("title");
            item.creatorName = json.optString("creatorName", "");
            item.coverUrl = json.optString("coverUrl", "");
            item.timestamp = json.optLong("timestamp", 0);
            return item;
        }
    }

    private String getCurrentUserId() {
        AccountManager.Account currentAccount = AccountManager.getInstance(context).getCurrentAccount();
        return currentAccount != null ? currentAccount.userId : "default";
    }

    private String getPrefKey() {
        return KEY_PREFIX + getCurrentUserId();
    }

    public synchronized List<Item> getItems() {
        String key = getPrefKey();
        String jsonStr = prefs.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(jsonStr);
            List<Item> items = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                items.add(Item.fromJson(array.getJSONObject(i)));
            }
            // Sort by timestamp descending (newest first)
            Collections.sort(items, (a, b) -> Long.compare(b.timestamp, a.timestamp));
            return items;
        } catch (Exception e) {
            Logger.e(TAG, "Error loading recently played items", e);
            return new ArrayList<>();
        }
    }

    private synchronized void saveItems(List<Item> items) {
        String key = getPrefKey();
        try {
            JSONArray array = new JSONArray();
            for (Item item : items) {
                array.put(item.toJson());
            }
            prefs.edit().putString(key, array.toString()).apply();
        } catch (Exception e) {
            Logger.e(TAG, "Error saving recently played items", e);
        }
    }

    public synchronized void addPlaylist(AudioPlaylist playlist) {
        if (playlist == null) return;

        List<Item> items = getItems();
        Item existing = null;
        for (Item item : items) {
            if ("playlist".equals(item.type) && item.id == playlist.getId() && item.ownerId == playlist.getOwnerId()) {
                existing = item;
                break;
            }
        }

        if (existing != null) {
            items.remove(existing);
        }

        Item item = new Item();
        item.type = "playlist";
        item.id = playlist.getId();
        item.ownerId = playlist.getOwnerId();
        item.title = playlist.getTitle();
        item.creatorName = playlist.getAuthorName();
        item.coverUrl = playlist.getPhotoUrl();
        item.timestamp = System.currentTimeMillis();

        items.add(0, item);

        if (items.size() > MAX_ITEMS) {
            items = items.subList(0, MAX_ITEMS);
        }

        saveItems(items);
        Logger.d(TAG, "Added playlist to recently played: " + playlist.getTitle());
    }

    public synchronized void addArtist(String artistName) {
        if (artistName == null || artistName.trim().isEmpty() || "Неизвестный исполнитель".equalsIgnoreCase(artistName)) {
            return;
        }

        List<Item> items = getItems();
        Item existing = null;
        for (Item item : items) {
            if ("artist".equals(item.type) && artistName.equalsIgnoreCase(item.title)) {
                existing = item;
                break;
            }
        }

        if (existing != null) {
            items.remove(existing);
        }

        Item item = new Item();
        item.type = "artist";
        item.id = 0;
        item.ownerId = 0;
        item.title = artistName;
        item.creatorName = "";
        item.coverUrl = "";
        item.timestamp = System.currentTimeMillis();

        items.add(0, item);

        if (items.size() > MAX_ITEMS) {
            items = items.subList(0, MAX_ITEMS);
        }

        saveItems(items);
        Logger.d(TAG, "Added artist to recently played: " + artistName);
    }
}
