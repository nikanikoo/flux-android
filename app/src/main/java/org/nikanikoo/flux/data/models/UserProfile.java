package org.nikanikoo.flux.data.models;

import org.json.JSONObject;

public class UserProfile {
    private int id;
    private String firstName;
    private String lastName;
    private String screenName;
    private String photo50;
    private String photo200;
    private String profileStatus;
    private boolean profileOnline;
    private int friendsCount;
    private int followersCount;
    private int groupsCount;
    private int photosCount;
    private int videosCount;
    private int audiosCount;

    private int sex; // 1 - женский, 2 - мужской
    private boolean verified;
    private boolean hasPhoto;
    private long lastSeen;
    private String music;
    private String movies;
    private String tv;
    private String books;
    private String city;
    private String interests;
    private String quotes;
    private String email;
    private String telegram;
    private String about;
    private String rating;
    private long regDate;
    private boolean isDead;
    private String nickname;
    private boolean blacklistedByMe;
    private boolean blacklisted;
    private boolean canPost;
    
    public static UserProfile fromJson(JSONObject json) {
        UserProfile profile = new UserProfile();
        try {
            profile.id = json.optInt("id", 0);
            profile.firstName = json.optString("first_name", "");
            profile.lastName = json.optString("last_name", "");
            profile.screenName = json.optString("screen_name", "");
            profile.photo200 = json.optString("photo_200", "");
            profile.photo50 = json.optString("photo_50", "");
            profile.profileStatus = json.optString("status", "");
            profile.profileOnline = json.optInt("online", 0) == 1;
            
            // Новые поля
            profile.sex = json.optInt("sex", 0);
            // verified может быть int 1/0 или boolean
            if (json.has("verified")) {
                Object verifiedObj = json.opt("verified");
                if (verifiedObj instanceof Integer) {
                    profile.verified = (Integer) verifiedObj == 1;
                } else if (verifiedObj instanceof Boolean) {
                    profile.verified = (Boolean) verifiedObj;
                }
            }
            profile.hasPhoto = json.optBoolean("has_photo", false);
            profile.lastSeen = json.optLong("last_seen", 0);
            profile.music = json.optString("music", "");
            profile.movies = json.optString("movies", "");
            profile.tv = json.optString("tv", "");
            profile.books = json.optString("books", "");

            //Город может быть либо JSON, либо просто строкой (на старых инстансах)
            if (json.has("city")) {
                Object cityObj = json.opt("city");
                if (cityObj instanceof JSONObject) {
                    profile.city = ((JSONObject) cityObj).optString("title", cityObj.toString());
                } else {
                    profile.city = json.optString("city", "");
                }
            } else {
                profile.city = "";
            }

            profile.interests = json.optString("interests", "");
            profile.quotes = json.optString("quotes", "");
            profile.email = json.optString("email", "");
            profile.telegram = json.optString("telegram", "");
            profile.about = json.optString("about", "");
            profile.rating = json.optString("rating", "");
            profile.regDate = json.optLong("reg_date", 0);
            profile.isDead = json.optBoolean("is_dead", false);
            profile.nickname = json.optString("nickname", "");
            profile.blacklistedByMe = json.optBoolean("blacklisted_by_me", false);
            profile.blacklisted = json.optBoolean("blacklisted", false);
            
            // can_post может быть boolean или int, по умолчанию true для своей стены
            if (json.has("can_post")) {
                Object canPostValue = json.get("can_post");
                if (canPostValue instanceof Boolean) {
                    profile.canPost = (Boolean) canPostValue;
                } else if (canPostValue instanceof Integer) {
                    profile.canPost = ((Integer) canPostValue) == 1;
                } else {
                    profile.canPost = json.optInt("can_post", 1) == 1;
                }
                System.out.println("UserProfile.fromJson: can_post = " + profile.canPost + " (type: " + canPostValue.getClass().getSimpleName() + ")");
            } else {
                profile.canPost = true; // По умолчанию можно постить на своей стене
                System.out.println("UserProfile.fromJson: can_post field not found, defaulting to true");
            }
            
            // Счетчики
            if (json.has("counters")) {
                JSONObject counters = json.getJSONObject("counters");
                profile.friendsCount = counters.optInt("friends", 0);
                profile.followersCount = counters.optInt("followers", 0);
                profile.groupsCount = counters.optInt("groups", 0);
                profile.photosCount = counters.optInt("photos", 0);
                profile.videosCount = counters.optInt("videos", 0);
                profile.audiosCount = counters.optInt("audios", 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return profile;
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("first_name", firstName);
            json.put("last_name", lastName);
            json.put("screen_name", screenName);
            json.put("photo_200", photo200);
            json.put("photo_50", photo50);
            json.put("status", profileStatus);
            json.put("online", profileOnline ? 1 : 0);
            json.put("verified", verified);
            json.put("sex", sex);
            json.put("has_photo", hasPhoto);
            json.put("last_seen", lastSeen);
            json.put("music", music);
            json.put("movies", movies);
            json.put("tv", tv);
            json.put("books", books);
            json.put("city", city);
            json.put("interests", interests);
            json.put("quotes", quotes);
            json.put("email", email);
            json.put("telegram", telegram);
            json.put("about", about);
            json.put("rating", rating);
            json.put("reg_date", regDate);
            json.put("is_dead", isDead);
            json.put("nickname", nickname);
            json.put("blacklisted_by_me", blacklistedByMe);
            json.put("blacklisted", blacklisted);
            json.put("can_post", canPost);
            
            JSONObject counters = new JSONObject();
            counters.put("friends", friendsCount);
            counters.put("followers", followersCount);
            counters.put("groups", groupsCount);
            counters.put("photos", photosCount);
            counters.put("videos", videosCount);
            counters.put("audios", audiosCount);
            json.put("counters", counters);
            
            return json;
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    // Getters
    public int getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getFullName() { return firstName + " " + lastName; }
    public String getScreenName() { return screenName; }
    public String getPhoto200() { return photo200; }
    public String getPhoto50() { return photo50; }
    public String getProfileStatus() { return profileStatus; }
    public boolean isProfileOnline() { return profileOnline; }
    public int getFriendsCount() { return friendsCount; }
    public int getFollowersCount() { return followersCount; }
    public int getGroupsCount() { return groupsCount; }
    public int getPhotosCount() { return photosCount; }
    public int getVideosCount() { return videosCount; }
    public int getAudiosCount() { return audiosCount; }
    public int getSex() { return sex; }
    public boolean isVerified() { return verified; }
    public boolean hasPhoto() { return hasPhoto; }
    public long getLastSeen() { return lastSeen; }
    public String getMusic() { return music; }
    public String getMovies() { return movies; }
    public String getTv() { return tv; }
    public String getBooks() { return books; }
    public String getCity() { return city; }
    public String getInterests() { return interests; }
    public String getQuotes() { return quotes; }
    public String getEmail() { return email; }
    public String getTelegram() { return telegram; }
    public String getAbout() { return about; }
    public String getRating() { return rating; }
    public long getRegDate() { return regDate; }
    public boolean isDead() { return isDead; }
    public String getNickname() { return nickname; }
    public boolean isBlacklistedByMe() { return blacklistedByMe; }
    public boolean isBlacklisted() { return blacklisted; }
    public boolean canPost() { return canPost; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }
    public void setPhoto200(String photo200) { this.photo200 = photo200; }
    public void setPhoto50(String photo50) { this.photo50 = photo50; }
    public void setProfileStatus(String profileStatus) { this.profileStatus = profileStatus; }
    public void setProfileOnline(boolean profileOnline) { this.profileOnline = profileOnline; }
    public void setFriendsCount(int friendsCount) { this.friendsCount = friendsCount; }
    public void setFollowersCount(int followersCount) { this.followersCount = followersCount; }
    public void setGroupsCount(int groupsCount) { this.groupsCount = groupsCount; }
    public void setPhotosCount(int photosCount) { this.photosCount = photosCount; }
    public void setVideosCount(int videosCount) { this.videosCount = videosCount; }
    public void setAudiosCount(int audiosCount) { this.audiosCount = audiosCount; }
    public void setCanPost(boolean canPost) { this.canPost = canPost; }
}
