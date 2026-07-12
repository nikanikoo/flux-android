package org.nikanikoo.flux.data.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.utils.AsyncTaskHelper;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostsManager extends BaseManager<PostsManager> {
    private static final String TAG = "PostsManager";

    private PostsManager(Context context) {
        super(context);
    }

    public static PostsManager getInstance(Context context) {
        return BaseManager.getInstance(PostsManager.class, context);
    }

    public interface PostsCallback {
        void onSuccess(List<Post> posts);
        void onError(String error);
    }

    public interface FeedPostsCallback {
        void onSuccess(List<Post> posts, String nextFrom);
        void onError(String error);
    }
    
    public interface PostCallback {
        void onSuccess(Post post);
        void onError(String error);
    }

    public interface CreatePostCallback {
        void onSuccess(int postId);
        void onError(String error);
    }

    public interface UploadImageCallback {
        void onSuccess(String attachment);
        void onError(String error);
    }

    public interface UploadImagesCallback {
        void onSuccess(List<String> attachments);
        void onError(String error);
    }

    public interface RepostCallback {
        void onSuccess(int postId, int likeCount);
        void onError(String error);
    }

    public interface UserIdCallback {
        void onSuccess(int userId);
        void onError(String error);
    }

    public interface EditCallback {
        void onSuccess(int postId);
        void onError(String error);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface PinCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface LikeToggleCallback {
        void onSuccess(int newLikesCount, boolean isLiked);
        void onError(String error);
    }

    public void createPost(String message, CreatePostCallback callback) {
        createPost(0, message, false, false, false, callback); // 0 означает свою стену
    }

    public void createPost(int ownerId, String message, CreatePostCallback callback) {
        createPost(ownerId, message, false, false, false, callback);
    }

    public void createPost(int ownerId, String message, boolean fromGroup, boolean signed, CreatePostCallback callback) {
        createPost(ownerId, message, fromGroup, signed, false, callback);
    }

    public void createPost(int ownerId, String message, boolean fromGroup, boolean signed, boolean explicit, CreatePostCallback callback) {
        if (!ValidationUtils.isValidPostText(message) && ownerId == 0 && !fromGroup) {
            callback.onError("Текст поста не может быть пустым");
            return;
        }

        // Если ownerId не указан (0), получаем ID текущего пользователя
        if (ownerId == 0 && !fromGroup) {
            ProfileManager profileManager = ProfileManager.getInstance(context);
            profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    int userId = profile.getId();
                    Logger.d(TAG, "ID пользователя получен из профиля: " + userId);
                    createPostInternal(userId, message, fromGroup, signed, explicit, callback);
                }

                @Override
                public void onError(String error) {
                    Logger.w(TAG, "Не удалось получить ID пользователя из профиля, попытка доступа через API: " + error);
                    getCurrentUserId(new UserIdCallback() {
                        @Override
                        public void onSuccess(int userId) {
                            createPostInternal(userId, message, fromGroup, signed, explicit, callback);
                        }

                        @Override
                        public void onError(String error) {
                            callback.onError("Не удалось получить ID пользователя: " + error);
                        }
                    });
                }
            });
        } else {
            // Если ownerId указан, используем его напрямую
            createPostInternal(ownerId, message, fromGroup, signed, explicit, callback);
        }
    }

    private void createPostInternal(int ownerId, String message, boolean fromGroup, boolean signed, boolean explicit, CreatePostCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("message", message);

        if (fromGroup) {
            params.put("from_group", "1");
            if (signed) {
                params.put("signed", "1");
            }
        }

        if (explicit) {
            params.put("explicit", "1");
        }

        Logger.d(TAG, "Creating post on wall: owner_id=" + ownerId + ", from_group=" + fromGroup + ", signed=" + signed + ", explicit=" + explicit + ", message length=" + message.length());

        api.callMethod("wall.post", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());

                    if (response.has("response")) {
                        JSONObject responseObj = response.getJSONObject("response");
                        int postId = responseObj.getInt("post_id");

                        Logger.d(TAG, "Пост успешно создан: " + postId);
                        callback.onSuccess(postId);
                    } else if (response.has("error")) {
                        JSONObject errorObj = response.getJSONObject("error");
                        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка");
                        Logger.w(TAG, "API вернул ошибку: " + errorMsg);
                        callback.onError(errorMsg);
                    } else {
                        Logger.w(TAG, "Неожиданный формат ответа: " + response.toString());
                        callback.onError("Неожиданный формат ответа сервера");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга при создании поста", e);
                    callback.onError("Не удалось создать пост");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка API создания поста: " + error);
                callback.onError("Не удалось создать пост");
            }
        });
    }

    private void getCurrentUserId(UserIdCallback callback) {
        Map<String, String> params = new HashMap<>();
        
        api.callMethod("users.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());
                    
                    JSONArray users = response.getJSONArray("response");
                    if (users.length() > 0) {
                        JSONObject user = users.getJSONObject(0);
                        int userId = ValidationUtils.safeGetInt(user, "id", 0);
                        if (ValidationUtils.isValidUserId(userId)) {
                            Logger.d(TAG, "Current user ID: " + userId);
                            callback.onSuccess(userId);
                        } else {
                            callback.onError("Получен некорректный ID пользователя: " + userId);
                        }
                    } else {
                        callback.onError("Пользователь не найден");
                    }
                } catch (Exception e) {
                    try {
                        JSONObject responseObj = response.getJSONObject("response");
                        JSONArray users = responseObj.getJSONArray("response");
                        if (users.length() > 0) {
                            JSONObject user = users.getJSONObject(0);
                            int userId = ValidationUtils.safeGetInt(user, "id", 0);
                            if (ValidationUtils.isValidUserId(userId)) {
                                Logger.d(TAG, "Current user ID (alternative parsing): " + userId);
                                callback.onSuccess(userId);
                            } else {
                                callback.onError("Получен некорректный ID пользователя: " + userId);
                            }
                        } else {
                            callback.onError("Пользователь не найден");
                        }
                    } catch (Exception e2) {
                        Logger.e(TAG, "Ошибка парсинга ID пользователя", e);
                        Logger.e(TAG, "Ошибка альт парсинга", e2);
                        callback.onError("Не удалось получить информацию о пользователе");
                    }
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка получения ID пользователя: " + error);
                callback.onError("Не удалось получить информацию о пользователе");
            }
        });
    }

    public void loadNewsFeed(PostsCallback callback) {
        loadNewsFeed(0, callback);
    }

    public void loadNewsFeed(int offset, PostsCallback callback) {
        loadGlobalNewsFeed(offset, callback);
    }

    public void loadGlobalNewsFeed(String nextFrom, FeedPostsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(Constants.Api.POSTS_PER_PAGE));
        params.put("extended", "1");
        params.put("fields", "verified");

        if (nextFrom != null && !nextFrom.isEmpty()) {
            params.put("start_from", nextFrom);
        }

        Logger.d(TAG, "Загрузка глобальной новостной ленты с next_from: " + nextFrom);

        api.callMethod("newsfeed.getGlobal", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.apiResponse(TAG, response.toString());

                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");
                    
                    String newNextFrom = responseObj.optString("next_from", null);

                    Logger.d(TAG, "Items кол-во: " + items.length() + ", next_from: " + newNextFrom);

                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromNewsfeed(items, profiles, groups);
                    Logger.d(TAG, "Кол-во постов: " + posts.size());

                    return new FeedPostsResult(posts, newNextFrom);
                }, new AsyncTaskHelper.AsyncCallback<FeedPostsResult>() {
                    @Override
                    public void onSuccess(FeedPostsResult result) {
                        callback.onSuccess(result.posts, result.nextFrom);
                    }

                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Ошибка парсинга новостей: " + error);
                        callback.onError("Не удалось загрузить новости");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка глобал. ленты: " + error);
                callback.onError("Не удалось загрузить новости");
            }
        });
    }

    public void loadGlobalNewsFeed(int offset, PostsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(Constants.Api.POSTS_PER_PAGE));
        params.put("extended", "1");
        params.put("fields", "verified");

        if (offset > 0) {
            params.put("offset", String.valueOf(offset));
        }

        Logger.d(TAG, "Загрузка глобальной новостной ленты со смещением: " + offset);
        
        api.callMethod("newsfeed.getGlobal", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse posts in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.apiResponse(TAG, response.toString());
                    
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");

                    Logger.d(TAG, "Items кол-во: " + items.length());
                    
                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromNewsfeed(items, profiles, groups);
                    Logger.d(TAG, "Кол-во постов: " + posts.size());
                    
                    return posts;
                }, new AsyncTaskHelper.AsyncCallback<List<Post>>() {
                    @Override
                    public void onSuccess(List<Post> posts) {
                        callback.onSuccess(posts);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Ошибка парсинга новостей: " + error);
                        callback.onError("Не удалось загрузить новости");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка глобал. ленты: " + error);
                callback.onError("Не удалось загрузить новости");
            }
        });
    }
    
    public void loadSubscriptionNewsFeed(int offset, PostsCallback callback) {
        loadSubscriptionNewsFeedLegacy(offset, callback);
    }

    public void loadSubscriptionNewsFeed(String nextFrom, FeedPostsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(Constants.Api.POSTS_PER_PAGE));
        params.put("extended", "1");
        params.put("fields", "verified");

        if (nextFrom != null && !nextFrom.isEmpty()) {
            params.put("start_from", nextFrom);
        }

        api.callMethod("newsfeed.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse posts in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.apiResponse(TAG, response.toString());

                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");
                    
                    String newNextFrom = responseObj.optString("next_from", null);

                    Logger.d(TAG, "Subscription items count: " + items.length() + ", next_from: " + newNextFrom);

                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromNewsfeed(items, profiles, groups);
                    Logger.d(TAG, "Parsed subscription posts count: " + posts.size());

                    return new FeedPostsResult(posts, newNextFrom);
                }, new AsyncTaskHelper.AsyncCallback<FeedPostsResult>() {
                    @Override
                    public void onSuccess(FeedPostsResult result) {
                        callback.onSuccess(result.posts, result.nextFrom);
                    }

                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Subscription parse error: " + error);
                        callback.onError("Не удалось загрузить новости подписок");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Subscription newsfeed error: " + error);
                callback.onError("Не удалось загрузить новости подписок");
            }
        });
    }

    private void loadSubscriptionNewsFeedLegacy(int offset, PostsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(Constants.Api.POSTS_PER_PAGE));
        params.put("extended", "1");
        params.put("fields", "verified");

        if (offset > 0) {
            params.put("offset", String.valueOf(offset));
        }

        api.callMethod("newsfeed.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.apiResponse(TAG, response.toString());

                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");

                    Logger.d(TAG, "Subscription items count: " + items.length());

                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromNewsfeed(items, profiles, groups);
                    Logger.d(TAG, "Parsed subscription posts count: " + posts.size());

                    return posts;
                }, new AsyncTaskHelper.AsyncCallback<List<Post>>() {
                    @Override
                    public void onSuccess(List<Post> posts) {
                        callback.onSuccess(posts);
                    }

                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Subscription parse error: " + error);
                        callback.onError("Не удалось загрузить новости подписок");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Subscription newsfeed error: " + error);
                callback.onError("Не удалось загрузить новости подписок");
            }
        });
    }

    public void loadUserPosts(int userId, PostsCallback callback) {
        loadUserPosts(userId, 0, callback);
    }

    // Загрузка постов пользователя с пагинацией
    public void loadUserPosts(int userId, int offset, PostsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(userId));
        params.put("count", String.valueOf(Constants.Api.POSTS_PER_PAGE));
        params.put("extended", "1"); // Получаем расширенную информацию о профилях
        params.put("fields", "verified"); // Дополнительно запрашиваем verified

        if (offset > 0) {
            params.put("offset", String.valueOf(offset));
        }

        Logger.d(TAG, "Loading user posts for userId: " + userId + " with offset: " + offset);

        final int finalUserId = userId;
        api.callMethod("wall.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse posts in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");

                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromWall(items, profiles, groups, finalUserId);
                    Logger.d(TAG, "Loaded " + posts.size() + " user posts");
                    
                    return posts;
                }, new AsyncTaskHelper.AsyncCallback<List<Post>>() {
                    @Override
                    public void onSuccess(List<Post> posts) {
                        callback.onSuccess(posts);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Ошибка парсинга постов: " + error);
                        callback.onError("Не удалось загрузить посты");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка загрузки постов пользователя: " + error);
                callback.onError("Не удалось загрузить посты");
            }
        });
    }

    // Загрузка постов со стены (для пользователей и групп)
    public void loadWallPosts(int ownerId, int count, int offset, PostsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("count", String.valueOf(count));
        params.put("offset", String.valueOf(offset));
        params.put("extended", "1"); // Получаем расширенную информацию о профилях
        params.put("fields", "verified"); // Дополнительно запрашиваем verified

        Logger.d(TAG, "Loading wall posts for owner_id: " + ownerId + ", count: " + count + ", offset: " + offset);

        final int finalOwnerId = ownerId;
        api.callMethod("wall.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse posts in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.apiResponse(TAG, response.toString());
                    
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");

                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromWall(items, profiles, groups, finalOwnerId);
                    return posts;
                }, new AsyncTaskHelper.AsyncCallback<List<Post>>() {
                    @Override
                    public void onSuccess(List<Post> posts) {
                        callback.onSuccess(posts);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Parse error in wall posts: " + error);
                        callback.onError("Не удалось загрузить посты");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Wall posts API error: " + error);
                callback.onError("Не удалось загрузить посты");
            }
        });
    }

    // Альтернативный метод загрузки новостей через wall.get
    public void loadPublicPosts(PostsCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", "-1"); // Публичные посты
        params.put("count", "10");
        params.put("extended", "1"); // Получаем расширенную информацию о профилях
        params.put("fields", "verified"); // Дополнительно запрашиваем verified

        Logger.d(TAG, "Loading public posts as fallback...");
        
        api.callMethod("wall.get", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse posts in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.apiResponse(TAG, response.toString());
                    
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");

                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromWall(items, profiles, groups, -1);
                    return posts;
                }, new AsyncTaskHelper.AsyncCallback<List<Post>>() {
                    @Override
                    public void onSuccess(List<Post> posts) {
                        callback.onSuccess(posts);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "Parse error in public posts: " + error);
                        callback.onError("Не удалось загрузить публичные посты");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Public posts API error: " + error);
                callback.onError("Не удалось загрузить публичные посты");
            }
        });
    }

    public void createPostWithImages(String message, List<android.net.Uri> imageUris, CreatePostCallback callback) {
        createPostWithImages(0, message, imageUris, false, false, false, callback); // 0 означает свою стену
    }

    public void createPostWithImages(int ownerId, String message, List<android.net.Uri> imageUris, CreatePostCallback callback) {
        createPostWithImages(ownerId, message, imageUris, false, false, false, callback);
    }

    public void createPostWithImages(int ownerId, String message, List<android.net.Uri> imageUris, boolean fromGroup, boolean signed, CreatePostCallback callback) {
        createPostWithImages(ownerId, message, imageUris, fromGroup, signed, false, callback);
    }

    public void createPostWithImages(int ownerId, String message, List<android.net.Uri> imageUris, boolean fromGroup, boolean signed, boolean explicit, CreatePostCallback callback) {
        if (imageUris.isEmpty()) {
            createPost(ownerId, message, fromGroup, signed, explicit, callback);
            return;
        }

        uploadImages(imageUris, new UploadImagesCallback() {
            @Override
            public void onSuccess(List<String> attachments) {
                createPostWithAttachments(ownerId, message, attachments, fromGroup, signed, explicit, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError("Ошибка загрузки изображений: " + error);
            }
        });
    }


    private void uploadImages(List<android.net.Uri> imageUris, UploadImagesCallback callback) {
        List<String> attachments = new ArrayList<>();
        uploadImageRecursive(imageUris, 0, attachments, callback);
    }

    private void uploadImageRecursive(List<android.net.Uri> imageUris, int index, List<String> attachments, UploadImagesCallback callback) {
        if (index >= imageUris.size()) {
            callback.onSuccess(attachments);
            return;
        }

        uploadSingleImage(imageUris.get(index), new UploadImageCallback() {
            @Override
            public void onSuccess(String attachment) {
                attachments.add(attachment);
                uploadImageRecursive(imageUris, index + 1, attachments, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void uploadSingleImage(android.net.Uri imageUri, UploadImageCallback callback) {
        Logger.d(TAG, "Загрузка изображения: " + imageUri.toString());
        
        PhotoUploadManager photoUploadManager = PhotoUploadManager.getInstance(context);
        photoUploadManager.uploadWallPhoto(imageUri, new PhotoUploadManager.PhotoUploadCallback() {
            @Override
            public void onSuccess(String attachment) {
                Logger.d(TAG, "Изображение успешно загружено: " + attachment);
                callback.onSuccess(attachment);
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка загрузки изображения: " + error);
                callback.onError(error);
            }
        });
    }
    
    private void createPostWithAttachments(int ownerId, String message, List<String> attachments, boolean fromGroup, boolean signed, boolean explicit, CreatePostCallback callback) {
        // Если ownerId не указан (0), получаем ID текущего пользователя
        if (ownerId == 0 && !fromGroup) {
            ProfileManager profileManager = ProfileManager.getInstance(context);
            profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    int userId = profile.getId();
                    createPostWithAttachmentsInternal(userId, message, attachments, fromGroup, signed, explicit, callback);
                }

                @Override
                public void onError(String error) {
                    callback.onError("Не удалось получить ID пользователя: " + error);
                }
            });
        } else {
            // Если ownerId указан, используем его напрямую
            createPostWithAttachmentsInternal(ownerId, message, attachments, fromGroup, signed, explicit, callback);
        }
    }

    private void createPostWithAttachmentsInternal(int ownerId, String message, List<String> attachments, boolean fromGroup, boolean signed, boolean explicit, CreatePostCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("message", message);

        if (fromGroup) {
            params.put("from_group", "1");
            if (signed) {
                params.put("signed", "1");
            }
        }

        if (explicit) {
            params.put("explicit", "1");
        }

        StringBuilder attachmentsStr = new StringBuilder();
        for (int i = 0; i < attachments.size(); i++) {
            if (i > 0) attachmentsStr.append(",");
            attachmentsStr.append(attachments.get(i));
        }
        params.put("attachments", attachmentsStr.toString());

        Logger.d(TAG, "Создание поста с вложениями на стене owner_id=" + ownerId + ", from_group=" + fromGroup + ", signed=" + signed + ", explicit=" + explicit + ": " + attachmentsStr.toString());

        api.callMethod("wall.post", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (response.has("response")) {
                        JSONObject responseObj = response.getJSONObject("response");
                        int postId = responseObj.getInt("post_id");
                        callback.onSuccess(postId);
                    } else if (response.has("error")) {
                        JSONObject errorObj = response.getJSONObject("error");
                        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка");
                        callback.onError(errorMsg);
                    } else {
                        callback.onError("Неожиданный формат ответа сервера");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing post creation response", e);
                    callback.onError("Не удалось создать пост");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error creating post with attachments: " + error);
                callback.onError("Не удалось создать пост");
            }
        });
    }

    public void toggleLike(Post post, LikeToggleCallback callback) {
        if (post.getPostId() == 0 || post.getOwnerId() == 0) {
            callback.onError("Недостаточно данных для лайка поста");
            return;
        }

        LikesManager likesManager = LikesManager.getInstance(context);
        likesManager.toggleLike("post", post.getOwnerId(), post.getPostId(), post.isLiked(), 
            new LikesManager.LikeCallback() {
                @Override
                public void onSuccess(int likesCount) {
                    callback.onSuccess(likesCount, !post.isLiked());
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
    }

    public void toggleLikeOptimistic(Post post, boolean originalLikedState, LikeToggleCallback callback) {
        if (post.getPostId() == 0 || post.getOwnerId() == 0) {
            callback.onError("Недостаточно данных для лайка поста");
            return;
        }

        LikesManager likesManager = LikesManager.getInstance(context);
        likesManager.toggleLike("post", post.getOwnerId(), post.getPostId(), originalLikedState, 
            new LikesManager.LikeCallback() {
                @Override
                public void onSuccess(int likesCount) {
                    callback.onSuccess(likesCount, !originalLikedState);
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
    }

    public void checkLikeStatus(Post post, LikesManager.LikeStatusCallback callback) {
        if (post.getPostId() == 0 || post.getOwnerId() == 0) {
            callback.onError("Недостаточно данных для проверки лайка");
            return;
        }

        LikesManager likesManager = LikesManager.getInstance(context);
        likesManager.isLiked("post", post.getOwnerId(), post.getPostId(), 
            new LikesManager.LikeStatusCallback() {
                @Override
                public void onSuccess(boolean isLiked) {
                    callback.onSuccess(isLiked);
                }

                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
    }

    public void getPostById(int ownerId, int postId, PostCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("posts", ownerId + "_" + postId);
        params.put("extended", "1");
        params.put("fields", "verified");

        Logger.d(TAG, "PostsManager: Загрузка поста " + ownerId + "_" + postId);
        
        final int finalOwnerId = ownerId;
        api.callMethod("wall.getById", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Parse post in background thread to avoid UI blocking
                AsyncTaskHelper.executeAsync(() -> {
                    Logger.apiResponse(TAG, response.toString());
                    
                    JSONObject responseObj = response.getJSONObject("response");
                    JSONArray items = responseObj.getJSONArray("items");
                    JSONArray profiles = responseObj.optJSONArray("profiles");
                    JSONArray groups = responseObj.optJSONArray("groups");
                    
                    if (items.length() == 0) {
                        throw new Exception("Пост не найден");
                    }
                    
                    List<Post> posts = org.nikanikoo.flux.utils.PostParser.parsePostsFromWall(items, profiles, groups, finalOwnerId);
                    if (posts.isEmpty()) {
                        throw new Exception("Не удалось распарсить пост");
                    }
                    
                    Post post = posts.get(0);
                    Logger.d(TAG, "PostsManager: Загружен пост - Author: " + post.getAuthorName());
                    
                    return post;
                }, new AsyncTaskHelper.AsyncCallback<Post>() {
                    @Override
                    public void onSuccess(Post post) {
                        callback.onSuccess(post);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Logger.e(TAG, "PostsManager: Ошибка парсинга поста: " + error);
                        callback.onError("Не удалось загрузить пост");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Logger.e(TAG, "PostsManager: ошибка загрузки поста: " + error);
                callback.onError("Не удалось загрузить пост");
            }
        });
    }

    public void repostPost(String object, String message, RepostCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("object", object);
        if (message != null && !message.isEmpty()) {
            params.put("message", message);
        }

        Logger.d(TAG, "Репост поста: object=" + object + ", message=" + message);

        api.callMethod("wall.repost", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());

                    if (response.has("response")) {
                        JSONObject responseObj = response.getJSONObject("response");
                        int postId = responseObj.optInt("post_id", 0);
                        int likesCount = responseObj.optInt("likes_count", 0);

                        Logger.d(TAG, "Репост выполнен: post_id=" + postId + ", likes_count=" + likesCount);
                        callback.onSuccess(postId, likesCount);
                    } else if (response.has("error")) {
                        JSONObject errorObj = response.getJSONObject("error");
                        String errorMsg = errorObj.optString("error_msg", "Неизвестная ошибка");
                        Logger.w(TAG, "API вернул ошибку при репосте: " + errorMsg);
                        callback.onError(errorMsg);
                    } else {
                        Logger.w(TAG, "Неожиданный формат ответа при репосте: " + response.toString());
                        callback.onError("Неожиданный формат ответа сервера");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Ошибка парсинга при репосте", e);
                    callback.onError("Не удалось выполнить репост");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Ошибка API репоста: " + error);
                callback.onError("Не удалось выполнить репост");
            }
        });
    }

    /**
     * Внутренний класс для хранения результатов загрузки ленты с next_from
     */
    private static class FeedPostsResult {
        final List<Post> posts;
        final String nextFrom;

        FeedPostsResult(List<Post> posts, String nextFrom) {
            this.posts = posts;
            this.nextFrom = nextFrom;
        }
    }

    public void pinPost(int ownerId, int postId, PinCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("post_id", String.valueOf(postId));

        Logger.d(TAG, "Pinning post: owner_id=" + ownerId + ", post_id=" + postId);

        api.callMethod("wall.pin", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());
                    if (response.has("response") && (response.optInt("response") == 1 || response.optBoolean("response"))) {
                        callback.onSuccess();
                    } else if (response.has("error")) {
                        callback.onError(response.getJSONObject("error").optString("error_msg", "Ошибка API"));
                    } else {
                        callback.onError("Неожиданный формат ответа");
                    }
                } catch (Exception e) {
                    callback.onError("Ошибка парсинга ответа");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void unpinPost(int ownerId, int postId, PinCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("post_id", String.valueOf(postId));

        Logger.d(TAG, "Unpinning post: owner_id=" + ownerId + ", post_id=" + postId);

        api.callMethod("wall.unpin", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());
                    if (response.has("response") && (response.optInt("response") == 1 || response.optBoolean("response"))) {
                        callback.onSuccess();
                    } else if (response.has("error")) {
                        callback.onError(response.getJSONObject("error").optString("error_msg", "Ошибка API"));
                    } else {
                        callback.onError("Неожиданный формат ответа");
                    }
                } catch (Exception e) {
                    callback.onError("Ошибка парсинга ответа");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void deletePost(int ownerId, int postId, DeleteCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("post_id", String.valueOf(postId));

        Logger.d(TAG, "Deleting post: owner_id=" + ownerId + ", post_id=" + postId);

        api.callMethod("wall.delete", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());
                    if (response.has("response") && (response.optInt("response") == 1 || response.optBoolean("response"))) {
                        callback.onSuccess();
                    } else if (response.has("error")) {
                        callback.onError(response.getJSONObject("error").optString("error_msg", "Ошибка API"));
                    } else {
                        callback.onError("Неожиданный формат ответа");
                    }
                } catch (Exception e) {
                    callback.onError("Ошибка парсинга ответа");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void editPost(int ownerId, int postId, String message, EditCallback callback) {
        Map<String, String> params = new HashMap<>();
        params.put("owner_id", String.valueOf(ownerId));
        params.put("post_id", String.valueOf(postId));
        params.put("message", message);

        Logger.d(TAG, "Editing post: owner_id=" + ownerId + ", post_id=" + postId);

        api.callMethod("wall.edit", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());
                    if (response.has("response")) {
                        Object respObj = response.get("response");
                        if (respObj instanceof Integer) {
                            callback.onSuccess((Integer) respObj);
                        } else if (respObj instanceof JSONObject) {
                            callback.onSuccess(ValidationUtils.safeGetInt((JSONObject) respObj, "post_id", postId));
                        } else {
                            callback.onSuccess(postId);
                        }
                    } else if (response.has("error")) {
                        callback.onError(response.getJSONObject("error").optString("error_msg", "Ошибка API"));
                    } else {
                        callback.onError("Неожиданный формат ответа");
                    }
                } catch (Exception e) {
                    callback.onError("Ошибка парсинга ответа");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
