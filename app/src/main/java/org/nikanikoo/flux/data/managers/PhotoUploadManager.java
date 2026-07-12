package org.nikanikoo.flux.data.managers;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.utils.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Manager for handling photo upload operations.
 * Extracted from OpenVKApi to follow single responsibility principle.
 */
public class PhotoUploadManager extends BaseManager<PhotoUploadManager> {
    private static final String TAG = "PhotoUploadManager";
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer

    private final OkHttpClient httpClient;
    private final ExecutorService executor;

    /**
     * Callback interface for photo upload operations.
     */
    public interface PhotoUploadCallback {
        void onSuccess(String attachment);
        void onError(String error);
    }

    /**
     * Internal callback for file upload operations.
     */
    private interface FileUploadCallback {
        void onSuccess(String server, String photo, String hash);
        void onError(String error);
    }

    /**
     * Private constructor for Singleton pattern.
     *
     * @param context Application context
     */
    private PhotoUploadManager(Context context) {
        super(context);
        
        // Создаем OkHttpClient с поддержкой SSL для самоподписанных сертификатов
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        // Configure secure SSL
        org.nikanikoo.flux.utils.SSLHelper.configureToIgnoreSSL(clientBuilder);
        this.httpClient = clientBuilder.build();
        
        this.executor = Executors.newFixedThreadPool(2);
    }

    /**
     * Get singleton instance of PhotoUploadManager.
     *
     * @param context Application context
     * @return PhotoUploadManager instance
     */
    public static PhotoUploadManager getInstance(Context context) {
        return BaseManager.getInstance(PhotoUploadManager.class, context);
    }

    /**
     * Upload a photo for wall post.
     *
     * @param imageUri URI of the image to upload
     * @param callback Callback for result
     */
    public void uploadWallPhoto(Uri imageUri, PhotoUploadCallback callback) {
        Logger.d(TAG, "Starting wall photo upload for URI: " + imageUri);

        getUploadServer("photos.getWallUploadServer", new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    String uploadUrl = responseObj.getString("upload_url");

                    Logger.d(TAG, "Got upload URL: " + uploadUrl);

                    uploadPhotoFile(imageUri, uploadUrl, new FileUploadCallback() {
                        @Override
                        public void onSuccess(String server, String photo, String hash) {
                            Map<String, String> params = new HashMap<>();
                            params.put("server", server);
                            params.put("photo", photo);
                            params.put("hash", hash);

                            savePhoto("photos.saveWallPhoto", params, callback);
                        }

                        @Override
                        public void onError(String error) {
                            Logger.e(TAG, "File upload error: " + error);
                            callback.onError("Ошибка загрузки файла: " + error);
                        }
                    });

                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing upload URL", e);
                    callback.onError("Не удалось получить адрес для загрузки");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error getting upload server: " + error);
                callback.onError("Не удалось получить адрес для загрузки");
            }
        });
    }

    public void uploadOwnerPhoto(Uri imageUri, PhotoUploadCallback callback) {
        Logger.d(TAG, "Starting owner photo upload for URI: " + imageUri);

        getUploadServer("photos.getOwnerPhotoUploadServer", new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONObject responseObj = response.getJSONObject("response");
                    String uploadUrl = responseObj.getString("upload_url");

                    uploadPhotoFile(imageUri, uploadUrl, new FileUploadCallback() {
                        @Override
                        public void onSuccess(String server, String photo, String hash) {
                            Map<String, String> params = new HashMap<>();
                            params.put("server", server);
                            params.put("photo", photo);
                            params.put("hash", hash);

                            savePhoto("photos.saveOwnerPhoto", params, callback);
                        }

                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    });
                } catch (Exception e) {
                    callback.onError("Ошибка получения адреса сервера");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Get upload server URL from API.
     *
     * @param method   API method to call (e.g., "photos.getWallUploadServer")
     * @param callback Callback for result
     */
    private void getUploadServer(String method, OpenVKApi.ApiCallback callback) {
        Logger.d(TAG, "Getting upload server for method: " + method);
        Map<String, String> params = new HashMap<>();
        api.callMethod(method, params, callback);
    }

    /**
     * Upload photo file to the upload server.
     *
     * @param imageUri  URI of the image to upload
     * @param uploadUrl Upload server URL
     * @param callback  Callback for result
     */
    private void uploadPhotoFile(Uri imageUri, String uploadUrl, FileUploadCallback callback) {
        executor.execute(() -> {
            File tempFile = null;
            try {
                Logger.d(TAG, "Creating temp file from URI: " + imageUri);
                tempFile = createTempFileFromUri(imageUri);
                
                if (tempFile == null) {
                    Logger.e(TAG, "Failed to create temp file");
                    callback.onError("Не удалось создать временный файл");
                    return;
                }

                final File finalTempFile = tempFile;
                Logger.d(TAG, "Temp file created: " + tempFile.getName() + ", size: " + tempFile.length() + " bytes");

                RequestBody fileBody = RequestBody.create(
                        MediaType.parse("image/*"),
                        tempFile
                );

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("photo", tempFile.getName(), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(requestBody)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = null;
                    if (response.body() != null) {
                        responseBody = response.body().string();
                        Logger.apiResponse(TAG, responseBody);
                    }

                    if (responseBody == null) {
                        Logger.e(TAG, "Empty server response");
                        callback.onError("Пустой ответ сервера");
                        return;
                    }

                    JSONObject json = new JSONObject(responseBody);
                    String server = json.optString("server", "");
                    String photo = json.optString("photo", "");
                    String hash = json.optString("hash", "");

                    if (!server.isEmpty() && !photo.isEmpty() && !hash.isEmpty()) {
                        Logger.d(TAG, "Photo file uploaded successfully");
                        callback.onSuccess(server, photo, hash);
                    } else {
                        Logger.e(TAG, "Incomplete server response: " + responseBody);
                        callback.onError("Неполный ответ сервера: " + responseBody);
                    }
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error uploading photo file", e);
                callback.onError(e.getMessage());
            } finally {
                // Ensure temp file is deleted even on errors
                if (tempFile != null && tempFile.exists()) {
                    try {
                        if (tempFile.delete()) {
                            Logger.d(TAG, "Temp file deleted: " + tempFile.getName());
                        } else {
                            Logger.w(TAG, "Failed to delete temp file: " + tempFile.getName());
                        }
                    } catch (Exception e) {
                        Logger.w(TAG, "Exception deleting temp file: " + tempFile.getName(), e);
                    }
                }
            }
        });
    }

    /**
     * Save uploaded photo using API.
     *
     * @param method   API method to call (e.g., "photos.saveWallPhoto")
     * @param params   Parameters (server, photo, hash)
     * @param callback Callback for result
     */
    private void savePhoto(String method, Map<String, String> params, PhotoUploadCallback callback) {
        Logger.d(TAG, "Saving photo with method: " + method);

        api.callMethod(method, params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    Logger.apiResponse(TAG, response.toString());

                    if (response.has("response")) {
                        Object responseData = response.get("response");

                        if (responseData instanceof JSONArray) {
                            JSONArray photoArray = (JSONArray) responseData;
                            if (photoArray.length() > 0) {
                                JSONObject photoObj = photoArray.getJSONObject(0);
                                int ownerId = photoObj.getInt("owner_id");
                                int photoId = photoObj.getInt("id");
                                String attachment = "photo" + ownerId + "_" + photoId;

                                Logger.d(TAG, "Photo saved successfully: " + attachment);
                                callback.onSuccess(attachment);
                                return;
                            }
                        } else if (responseData instanceof JSONObject) {
                            JSONObject responseObj = (JSONObject) responseData;
                            
                            if (responseObj.has("photo_src")) {
                                String photoSrc = responseObj.getString("photo_src");
                                Logger.d(TAG, "Owner photo saved successfully: " + photoSrc);
                                callback.onSuccess(photoSrc);
                                return;
                            }
                            
                            if (responseObj.has("response")) {
                                JSONArray photoArray = responseObj.getJSONArray("response");
                                if (photoArray.length() > 0) {
                                    JSONObject photoObj = photoArray.getJSONObject(0);
                                    int ownerId = photoObj.getInt("owner_id");
                                    int photoId = photoObj.getInt("id");
                                    String attachment = "photo" + ownerId + "_" + photoId;

                                    Logger.d(TAG, "Photo saved successfully: " + attachment);
                                    callback.onSuccess(attachment);
                                    return;
                                }
                            }
                        }
                    }

                    Logger.e(TAG, "Unexpected response format: " + response.toString());
                    callback.onError("Неожиданный формат ответа saveWallPhoto: " + response.toString());
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing save photo response", e);
                    callback.onError("Не удалось сохранить фото");
                }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error saving photo: " + error);
                callback.onError("Не удалось сохранить фото");
            }
        });
    }

    /**
     * Create a temporary file from URI.
     *
     * @param uri URI of the image
     * @return Temporary file or null on error
     */
    private File createTempFileFromUri(Uri uri) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File tempFile = null;

        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Logger.w(TAG, "Failed to open input stream for URI: " + uri);
                return null;
            }

            // Создаем директорию для временных файлов если её нет
            File cacheDir = context.getCacheDir();
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    Logger.e(TAG, "Failed to create cache directory");
                    return null;
                }
            }

            tempFile = File.createTempFile("upload_image", ".jpg", cacheDir);
            Logger.d(TAG, "Temp file path: " + tempFile.getAbsolutePath());
            
            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            // Принудительно сбрасываем буфер на диск
            outputStream.flush();

            Logger.d(TAG, "Temp file created successfully: " + tempFile.getName() + ", size: " + tempFile.length());
            return tempFile;
        } catch (Exception e) {
            Logger.e(TAG, "Error creating temp file from URI: " + uri, e);

            // Cleanup on error
            if (tempFile != null && tempFile.exists()) {
                try {
                    if (tempFile.delete()) {
                        Logger.d(TAG, "Cleaned up temp file after error");
                    }
                } catch (Exception deleteException) {
                    Logger.w(TAG, "Failed to delete temp file after error", deleteException);
                }
            }
            return null;
        } finally {
            // Always close streams
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    Logger.w(TAG, "Failed to close output stream", e);
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Logger.w(TAG, "Failed to close input stream", e);
                }
            }
        }
    }

    /**
     * Shutdown the executor service to prevent memory leaks.
     * Should be called when application is terminating.
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            Logger.d(TAG, "PhotoUploadManager executor shutdown");
        }
    }
}
