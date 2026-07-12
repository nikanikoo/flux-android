package org.nikanikoo.flux.data.managers;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.utils.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileManager extends BaseManager<ProfileManager> {
    private static final String TAG = "ProfileManager";
    private static final String PREF_NAME = "profile_cache";
    private static final String KEY_PROFILE_DATA = "profile_data";
    private static final String KEY_LAST_UPDATE = "last_update";

    private final SharedPreferences prefs;
    private UserProfile cachedProfile;

    protected ProfileManager(Context context) {
        super(context);
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static ProfileManager getInstance(Context context) {
        return BaseManager.getInstance(ProfileManager.class, context);
    }

    public enum CounterType {
        FRIENDS("friends.get", true, false),      // Requires user_id for other users
        FOLLOWERS("users.getFollowers", true, false),
        GROUPS("groups.get", true, false),        // Requires user_id for other users
        PHOTOS("photos.getAll", false, true),
        VIDEOS("video.get", false, true),
        AUDIOS("audio.get", false, true);

        private final String apiMethod;
        private final boolean requiresUserId;
        private final boolean requiresOwnerId;

        CounterType(String apiMethod, boolean requiresUserId, boolean requiresOwnerId) {
            this.apiMethod = apiMethod;
            this.requiresUserId = requiresUserId;
            this.requiresOwnerId = requiresOwnerId;
        }

        public String getApiMethod() {
            return apiMethod;
        }

        public boolean requiresUserId() {
            return requiresUserId;
        }

        public boolean requiresOwnerId() {
            return requiresOwnerId;
        }
    }

    public interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(String error);
    }

    public void loadProfile(boolean forceRefresh, ProfileCallback callback) {
        loadProfile(forceRefresh, true, callback);
    }

    public void loadProfile(boolean forceRefresh, boolean loadCounters, ProfileCallback callback) {
        if (!forceRefresh && isCacheValid()) {
            UserProfile profile = getCachedProfile();
            if (profile != null) {
                cachedProfile = profile;
                callback.onSuccess(profile);
                return;
            }
        }

        Map<String, String> params = new HashMap<>();
        params.put("fields", "photo_50,photo_200,status,online,screen_name,sex,verified,has_photo,last_seen,music,movies,tv,books,city,interests,quotes,email,telegram,about,rating,reg_date,is_dead,nickname,blacklisted_by_me,blacklisted,can_post");

        api.callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray users = response.getJSONArray("response");
                    if (users.length() > 0) {
                        JSONObject userJson = users.getJSONObject(0);
                        UserProfile profile = UserProfile.fromJson(userJson);

                        saveToCache(profile);
                        cachedProfile = profile;

                        if (loadCounters) {
                            loadCounters(profile, profile.getId(), true, callback);
                        } else {
                            callback.onSuccess(profile);
                        }
                    } else {
                        callback.onError("Пользователь не найден");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing profile", e);
                    callback.onError("Не удалось загрузить профиль");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading profile: " + error);
                callback.onError("Не удалось загрузить профиль");
            }
        });
    }

    public void loadProfileById(int userId, ProfileCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("user_ids", String.valueOf(userId));
        params.put("fields", "photo_50,photo_200,status,online,screen_name,sex,verified,has_photo,last_seen,music,movies,tv,books,city,interests,quotes,email,telegram,about,rating,reg_date,is_dead,nickname,blacklisted_by_me,blacklisted,can_post");

        api.callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray users = response.getJSONArray("response");
                    if (users.length() > 0) {
                        JSONObject userJson = users.getJSONObject(0);
                        UserProfile profile = UserProfile.fromJson(userJson);

                        loadCounters(profile, userId, false, callback);
                    } else {
                        callback.onError("Пользователь не найден");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing profile by id", e);
                    callback.onError("Не удалось загрузить профиль");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading profile by id: " + error);
                callback.onError("Не удалось загрузить профиль");
            }
        });
    }

    /**
     * Unified method to load all profile counters.
     * Continues loading even if individual counters fail (error resilience).
     * 
     * @param profile The profile to populate with counter data
     * @param userId The user ID for which to load counters
     * @param isCurrentUser Whether this is the current user (affects parameter usage)
     * @param callback Callback to invoke when all counters are loaded
     */
    private void loadCounters(UserProfile profile, int userId, boolean isCurrentUser, ProfileCallback callback) {
        CounterType[] counterTypes = CounterType.values();
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalCounters = counterTypes.length;

        Logger.d(TAG, "Loading " + totalCounters + " counters for user " + userId + " (current: " + isCurrentUser + ")");

        for (CounterType counterType : counterTypes) {
            loadCounter(counterType, profile, userId, isCurrentUser, new CounterCallback() {
                @Override
                public void onComplete() {
                    int completed = completedCount.incrementAndGet();
                    Logger.d(TAG, "Counter " + counterType + " completed (" + completed + "/" + totalCounters + ")");
                    
                    if (completed == totalCounters) {
                        // All counters loaded (successfully or with errors)
                        Logger.d(TAG, "All counters loaded for user " + userId);
                        
                        if (isCurrentUser) {
                            saveToCache(profile);
                            cachedProfile = profile;
                        }
                        
                        callback.onSuccess(profile);
                    }
                }
            });
        }
    }

    private interface CounterCallback {
        void onComplete();
    }


    private void loadCounter(CounterType counterType, UserProfile profile, int userId, boolean isCurrentUser, CounterCallback callback) {
        Map<String, String> params = new HashMap<>();

        // FOLLOWERS always requires user_id parameter
        if (counterType == CounterType.FOLLOWERS) {
            params.put("user_id", String.valueOf(userId));
        } else if (counterType.requiresUserId() && !isCurrentUser) {
            params.put("user_id", String.valueOf(userId));
        }
        if (counterType.requiresOwnerId()) {
            params.put("owner_id", String.valueOf(userId));
        }

        api.callMethod(counterType.getApiMethod(), params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    int count = response.getJSONObject("response").optInt("count", 0);
                    setCounterValue(profile, counterType, count);
                    Logger.d(TAG, "Loaded " + counterType + ": " + count);
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing " + counterType + " counter", e);
                }
                callback.onComplete();
            }

            @Override
            public void onError(String error) {
                Logger.w(TAG, "Error loading " + counterType + " counter: " + error);
                callback.onComplete();
            }
        });
    }

    /**
     * Set the counter value on the profile based on counter type.
     * 
     * @param profile The profile to update
     * @param counterType The type of counter
     * @param count The count value
     */
    private void setCounterValue(UserProfile profile, CounterType counterType, int count) {
        switch (counterType) {
            case FRIENDS:
                profile.setFriendsCount(count);
                break;
            case FOLLOWERS:
                profile.setFollowersCount(count);
                break;
            case GROUPS:
                profile.setGroupsCount(count);
                break;
            case PHOTOS:
                profile.setPhotosCount(count);
                break;
            case VIDEOS:
                profile.setVideosCount(count);
                break;
            case AUDIOS:
                profile.setAudiosCount(count);
                break;
        }
    }

    public UserProfile getCachedProfileSync() {
        if (cachedProfile != null) {
            return cachedProfile;
        }
        return getCachedProfile();
    }

    public UserProfile getCachedProfileEvenIfExpired() {
        try {
            String jsonString = prefs.getString(KEY_PROFILE_DATA, null);
            if (jsonString != null) {
                JSONObject json = new JSONObject(jsonString);
                return UserProfile.fromJson(json);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error loading expired profile from cache", e);
        }
        return null;
    }

    private boolean isCacheValid() {
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastUpdate) < Constants.Cache.PROFILE_CACHE_DURATION_MS;
    }

    private void saveToCache(UserProfile profile) {
        try {
            String jsonString = profile.toJson().toString();
            prefs.edit()
                    .putString(KEY_PROFILE_DATA, jsonString)
                    .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Logger.e(TAG, "Error saving profile to cache", e);
        }
    }

    private UserProfile getCachedProfile() {
        try {
            String jsonString = prefs.getString(KEY_PROFILE_DATA, null);
            if (jsonString != null) {
                JSONObject json = new JSONObject(jsonString);
                return UserProfile.fromJson(json);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error loading profile from cache", e);
        }
        return null;
    }

    public void clearCache() {
        prefs.edit().clear().apply();
        cachedProfile = null;
    }

    public interface SaveProfileCallback {
        void onSuccess();
        void onError(String error);
    }

    public void saveProfileInfo(Map<String, String> params, SaveProfileCallback callback) {
        api.callMethod("account.saveProfileInfo", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.has("response") && response.optInt("response") == 1) {
                        callback.onSuccess();
                    } else if (response.has("response") && response.get("response") instanceof JSONObject) {
                        callback.onSuccess();
                    } else {
                        callback.onError("Не удалось сохранить изменения");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing saveProfileInfo response", e);
                    callback.onError("Ошибка при обработке ответа сервера");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}