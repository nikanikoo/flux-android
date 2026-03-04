package org.nikanikoo.flux.utils;

import android.text.Html;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nikanikoo.flux.Constants;

public class ValidationUtils {

    public static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && 
               android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isValidPassword(String password) {
        return !TextUtils.isEmpty(password) && password.length() >= Constants.Validation.MIN_PASSWORD_LENGTH;
    }

    public static boolean isValid2FACode(String code) {
        return !TextUtils.isEmpty(code) && code.matches("\\d{" + Constants.Validation.TWO_FA_CODE_LENGTH + "}");
    }

    public static boolean isValidUrl(String url) {
        return !TextUtils.isEmpty(url) && 
               (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static boolean isValidUserId(int userId) {
        return userId >= Constants.Validation.MIN_USER_ID;
    }

    public static boolean isValidPostOwnerId(int id) {
        return id != 0;
    }

    public static boolean isValidPostText(String text) {
        return !TextUtils.isEmpty(text) && text.trim().length() > 0 && text.length() <= Constants.Validation.MAX_POST_LENGTH;
    }

    public static boolean isValidMessageText(String text) {
        return !TextUtils.isEmpty(text) && text.trim().length() > 0 && text.length() <= Constants.Validation.MAX_MESSAGE_LENGTH;
    }

    public static String safeGetString(JSONObject json, String key, String defaultValue) {
        if (json == null || TextUtils.isEmpty(key)) {
            return defaultValue;
        }
        return json.optString(key, defaultValue);
    }

    public static int safeGetInt(JSONObject json, String key, int defaultValue) {
        if (json == null || TextUtils.isEmpty(key)) {
            return defaultValue;
        }
        return json.optInt(key, defaultValue);
    }

    public static long safeGetLong(JSONObject json, String key, long defaultValue) {
        if (json == null || TextUtils.isEmpty(key)) {
            return defaultValue;
        }
        return json.optLong(key, defaultValue);
    }

    public static boolean safeGetBoolean(JSONObject json, String key, boolean defaultValue) {
        if (json == null || TextUtils.isEmpty(key)) {
            return defaultValue;
        }
        return json.optBoolean(key, defaultValue);
    }

    public static String sanitizeUserInput(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }

        String decoded;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            decoded = Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            decoded = Html.fromHtml(input).toString();
        }

        return decoded.trim();
    }

    public static String SanitizeText(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        String text = input;

        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&amp;", "&");
        text = text.replace("&quot;", "\"");
        text = text.replace("&nbsp;", " ");
        
        return text;
    }

    public static boolean isValidFileSize(long sizeInBytes, long maxSizeInMB) {
        long maxSizeInBytes = maxSizeInMB * 1024 * 1024;
        return sizeInBytes > 0 && sizeInBytes <= maxSizeInBytes;
    }

    public static boolean isValidJson(String jsonString) {
        if (TextUtils.isEmpty(jsonString)) {
            return false;
        }
        try {
            new JSONObject(jsonString);
            return true;
        } catch (JSONException e) {
            try {
                new JSONArray(jsonString);
                return true;
            } catch (JSONException e2) {
                return false;
            }
        }
    }

    public static JSONArray safeGetJSONArray(JSONObject json, String key) {
        if (json == null || TextUtils.isEmpty(key)) {
            return new JSONArray();
        }
        return json.optJSONArray(key) != null ? json.optJSONArray(key) : new JSONArray();
    }

    public static JSONObject safeGetJSONObject(JSONObject json, String key) {
        if (json == null || TextUtils.isEmpty(key)) {
            return new JSONObject();
        }
        return json.optJSONObject(key) != null ? json.optJSONObject(key) : new JSONObject();
    }
}