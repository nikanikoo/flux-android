package org.nikanikoo.flux.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class JsonUtils {
    private static final String TAG = "JsonUtils";

    public static class ProfileInfo {
        public final int id;
        public final String name;
        public final String avatarUrl;
        public final boolean verified;
        public final int sex;

        public ProfileInfo(int id, String name, String avatarUrl, boolean verified) {
            this(id, name, avatarUrl, verified, 0);
        }

        public ProfileInfo(int id, String name, String avatarUrl, boolean verified, int sex) {
            this.id = id;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.verified = verified;
            this.sex = sex;
        }

        @Override
        public String toString() {
            return "ProfileInfo{id=" + id + ", name='" + name + "', avatarUrl='" + avatarUrl + "', verified=" + verified + ", sex=" + sex + "}";
        }
    }

    public static class GroupInfo {
        public final int id;
        public final String name;
        public final String avatarUrl;
        public final boolean verified;

        public GroupInfo(int id, String name, String avatarUrl, boolean verified) {
            this.id = id;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.verified = verified;
        }

        @Override
        public String toString() {
            return "GroupInfo{id=" + id + ", name='" + name + "', avatarUrl='" + avatarUrl + "', verified=" + verified + "}";
        }
    }

    public static Map<Integer, ProfileInfo> parseProfiles(JSONArray profiles) {
        Map<Integer, ProfileInfo> profileMap = new HashMap<>();
        
        if (profiles == null) {
            return profileMap;
        }

        for (int i = 0; i < profiles.length(); i++) {
            try {
                JSONObject profile = profiles.getJSONObject(i);
                
                int id = ValidationUtils.safeGetInt(profile, "id", 0);
                if (id == 0) {
                    Logger.w(TAG, "Skipping profile with invalid id at index " + i);
                    continue;
                }

                String firstName = ValidationUtils.safeGetString(profile, "first_name", "");
                String lastName = ValidationUtils.safeGetString(profile, "last_name", "");
                String name = (firstName + " " + lastName).trim();
                
                if (name.isEmpty()) {
                    Logger.w(TAG, "Skipping profile with empty name at index " + i + ", id=" + id);
                    continue;
                }

                String avatarUrl = profile.optString("photo_50", "");

                boolean verified = false;
                if (profile.has("verified")) {
                    Object verifiedObj = profile.opt("verified");
                    if (verifiedObj instanceof Integer) {
                        verified = (Integer) verifiedObj == 1;
                    } else if (verifiedObj instanceof Boolean) {
                        verified = (Boolean) verifiedObj;
                    }
                }

                int sex = profile.optInt("sex", 0);
                profileMap.put(id, new ProfileInfo(id, name, avatarUrl, verified, sex));
                
            } catch (JSONException e) {
                Logger.e(TAG, "Error parsing profile at index " + i, e);
            } catch (Exception e) {
                Logger.e(TAG, "Unexpected error parsing profile at index " + i, e);
            }
        }

        return profileMap;
    }

    public static Map<Integer, GroupInfo> parseGroups(JSONArray groups) {
        Map<Integer, GroupInfo> groupMap = new HashMap<>();
        
        if (groups == null) {
            return groupMap;
        }

        for (int i = 0; i < groups.length(); i++) {
            try {
                JSONObject group = groups.getJSONObject(i);
                
                int id = ValidationUtils.safeGetInt(group, "id", 0);
                if (id == 0) {
                    Logger.w(TAG, "Skipping group with invalid id at index " + i);
                    continue;
                }

                String name = ValidationUtils.safeGetString(group, "name", "");
                if (name.isEmpty()) {
                    Logger.w(TAG, "Skipping group with empty name at index " + i + ", id=" + id);
                    continue;
                }

                String avatarUrl = group.optString("photo_50", "");

                boolean verified = false;
                if (group.has("verified")) {
                    Object verifiedObj = group.opt("verified");
                    if (verifiedObj instanceof Integer) {
                        verified = (Integer) verifiedObj == 1;
                    } else if (verifiedObj instanceof Boolean) {
                        verified = (Boolean) verifiedObj;
                    }
                }

                groupMap.put(id, new GroupInfo(id, name, avatarUrl, verified));
                
            } catch (JSONException e) {
                Logger.e(TAG, "Error parsing group at index " + i, e);
            } catch (Exception e) {
                Logger.e(TAG, "Unexpected error parsing group at index " + i, e);
            }
        }

        return groupMap;
    }
}
