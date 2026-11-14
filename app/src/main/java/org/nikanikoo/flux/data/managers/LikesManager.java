package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.data.models.UserProfile;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for handling like operations (add, delete, check status, toggle).
 * Extracted from OpenVKApi to follow single responsibility principle.
 */
public class LikesManager extends BaseManager<LikesManager> {
    private static final String TAG = "LikesManager";

    /**
     * Callback interface for like operations that return likes count.
     */
    public interface LikeCallback {
        void onSuccess(int likesCount);
        void onError(String error);
    }

    /**
     * Callback interface for checking like status.
     */
    public interface LikeStatusCallback {
        void onSuccess(boolean isLiked);
        void onError(String error);
    }

    public interface LikedUsersCallback {
        void onSuccess(List<UserProfile> users, int totalCount);
        void onError(String error);
    }

    /**
     * Private constructor for Singleton pattern.
     *
     * @param context Application context
     */
    private LikesManager(Context context) {
        super(context);
    }

    /**
     * Get singleton instance of LikesManager.
     *
     * @param context Application context
     * @return LikesManager instance
     */
    public static LikesManager getInstance(Context context) {
        return BaseManager.getInstance(LikesManager.class, context);
    }

    /**
     * Add a like to an item (post, comment, photo, etc.).
     *
     * @param type     Type of item ("post", "comment", "photo", etc.)
     * @param ownerId  Owner ID of the item
     * @param itemId   Item ID
     * @param callback Callback for result
     */
    public void addLike(String type, int ownerId, int itemId, LikeCallback callback) {
        Logger.d(TAG, "Adding like: type=" + type + ", ownerId=" + ownerId + ", itemId=" + itemId);

        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        params.put("owner_id", String.valueOf(ownerId));
        params.put("item_id", String.valueOf(itemId));

        api.callMethod("likes.add", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int likesCount = responseObj.getInt("likes");
                    Logger.d(TAG, "Like added successfully. New count: " + likesCount);
                    callback.onSuccess(likesCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing like response", e);
                    callback.onError("Не удалось добавить лайк");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error adding like: " + error);
                callback.onError("Не удалось добавить лайк");
            }
        });
    }

    /**
     * Remove a like from an item (post, comment, photo, etc.).
     *
     * @param type     Type of item ("post", "comment", "photo", etc.)
     * @param ownerId  Owner ID of the item
     * @param itemId   Item ID
     * @param callback Callback for result
     */
    public void deleteLike(String type, int ownerId, int itemId, LikeCallback callback) {
        Logger.d(TAG, "Deleting like: type=" + type + ", ownerId=" + ownerId + ", itemId=" + itemId);

        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        params.put("owner_id", String.valueOf(ownerId));
        params.put("item_id", String.valueOf(itemId));

        api.callMethod("likes.delete", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    int likesCount = responseObj.getInt("likes");
                    Logger.d(TAG, "Like deleted successfully. New count: " + likesCount);
                    callback.onSuccess(likesCount);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing delete like response", e);
                    callback.onError("Не удалось удалить лайк");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error deleting like: " + error);
                callback.onError("Не удалось удалить лайк");
            }
        });
    }

    /**
     * Check if an item is liked by the current user.
     *
     * @param type     Type of item ("post", "comment", "photo", etc.)
     * @param ownerId  Owner ID of the item
     * @param itemId   Item ID
     * @param callback Callback for result
     */
    public void isLiked(String type, int ownerId, int itemId, LikeStatusCallback callback) {
        Logger.d(TAG, "Checking like status: type=" + type + ", ownerId=" + ownerId + ", itemId=" + itemId);

        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        params.put("owner_id", String.valueOf(ownerId));
        params.put("item_id", String.valueOf(itemId));

        api.callMethod("likes.isLiked", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    boolean liked = responseObj.getInt("liked") == 1;
                    Logger.d(TAG, "Like status checked: " + liked);
                    callback.onSuccess(liked);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing like status response", e);
                    callback.onError("Не удалось проверить статус лайка");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error checking like status: " + error);
                callback.onError("Не удалось проверить статус лайка");
            }
        });
    }

    public void getLikedUsers(String type, int ownerId, int itemId, LikedUsersCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        params.put("owner_id", String.valueOf(ownerId));
        params.put("item_id", String.valueOf(itemId));
        params.put("extended", "1");
        params.put("count", "100");

        api.callMethod("likes.getList", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject result = response.getJSONObject("response");
                    JSONArray items = result.optJSONArray("items");
                    List<UserProfile> users = new ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item != null) users.add(UserProfile.fromJson(item));
                        }
                    }
                    callback.onSuccess(users, result.optInt("count", users.size()));
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing liked users", e);
                    callback.onError("Не удалось обработать список лайков");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading liked users: " + error);
                callback.onError(error);
            }
        });
    }

    /**
     * Toggle like state (add if not liked, delete if liked).
     *
     * @param type         Type of item ("post", "comment", "photo", etc.)
     * @param ownerId      Owner ID of the item
     * @param itemId       Item ID
     * @param currentState Current like state (true if liked, false if not)
     * @param callback     Callback for result
     */
    public void toggleLike(String type, int ownerId, int itemId, boolean currentState, LikeCallback callback) {
        Logger.d(TAG, "Toggling like: type=" + type + ", ownerId=" + ownerId + 
                      ", itemId=" + itemId + ", currentState=" + currentState);

        if (currentState) {
            // Currently liked, so delete the like
            deleteLike(type, ownerId, itemId, callback);
        } else {
            // Not liked, so add a like
            addLike(type, ownerId, itemId, callback);
        }
    }
}
