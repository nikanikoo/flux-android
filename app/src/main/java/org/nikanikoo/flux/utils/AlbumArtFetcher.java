package org.nikanikoo.flux.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AlbumArtFetcher {
    private static final String TAG = "AlbumArtFetcher";

    private static final String LASTFM_API_KEY = "token";
    private static final String LASTFM_BASE_URL = "https://ws.audioscrobbler.com/2.0";

    private final ExecutorService executor;
    private final OkHttpClient httpClient;

    public interface AlbumArtCallback {
        void onSuccess(String imageUrl);
        void onError(String error);
    }

    public AlbumArtFetcher(Context context) {
        executor = Executors.newSingleThreadExecutor();
        httpClient = createUnsafeOkHttpClient();
    }

    private OkHttpClient createUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            final HostnameVerifier trustAllHostnames = (hostname, session) -> true;

            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(trustAllHostnames)
                    .build();

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            Logger.e(TAG, "Error creating unsafe SSL context", e);
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
    }

    public void loadAlbumArt(String artist, String title, ImageView imageView, int placeholderResId) {
        loadAlbumArt(artist, title, imageView, placeholderResId, null);
    }

    public void loadAlbumArt(String artist, String title, ImageView imageView, int placeholderResId, AlbumArtCallback callback) {
        if (imageView == null) {
            Logger.w(TAG, "ImageView is null");
            return;
        }

        if (artist == null || title == null || artist.isEmpty() || title.isEmpty()) {
            Logger.d(TAG, "Invalid artist or title, showing placeholder");
            imageView.setImageResource(placeholderResId);
            if (callback != null) callback.onError("Invalid artist or title");
            return;
        }

        executor.execute(() -> {
            try {
                String imageUrl = fetchImageUrl(artist, title);

                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Picasso.get()
                                .load(imageUrl)
                                .placeholder(placeholderResId)
                                .error(placeholderResId)
                                .into(imageView);
                        if (callback != null) callback.onSuccess(imageUrl);
                    } else {
                        imageView.setImageResource(placeholderResId);
                        if (callback != null) callback.onError("Album art not found");
                    }
                });
            } catch (Exception e) {
                Logger.e(TAG, "Error fetching album art URL", e);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    imageView.setImageResource(placeholderResId);
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    public void loadArtistImage(String artistName, ImageView imageView, int placeholderResId) {
        if (imageView == null) return;
        if (artistName == null || artistName.isEmpty()) {
            imageView.setImageResource(placeholderResId);
            return;
        }

        executor.execute(() -> {
            try {
                String imageUrl = fetchArtistImageUrl(artistName);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Picasso.get()
                                .load(imageUrl)
                                .placeholder(placeholderResId)
                                .error(placeholderResId)
                                .into(imageView);
                    } else {
                        imageView.setImageResource(placeholderResId);
                    }
                });
            } catch (Exception e) {
                Logger.e(TAG, "Error loading artist image", e);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> imageView.setImageResource(placeholderResId));
            }
        });
    }

    private String fetchArtistImageUrl(String artistName) {
        if (LASTFM_API_KEY == null || LASTFM_API_KEY.isEmpty() || LASTFM_API_KEY.contains("Replace") || LASTFM_API_KEY.contains("token")) {
            Logger.d(TAG, "Last.fm API key not configured");
            return null;
        }
        try {
            String artistEncoded = URLEncoder.encode(artistName, "UTF-8");
            String urlString = LASTFM_BASE_URL + "?method=artist.getInfo" +
                    "&api_key=" + LASTFM_API_KEY +
                    "&artist=" + artistEncoded +
                    "&format=json";

            Request request = new Request.Builder().url(urlString).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                ResponseBody body = response.body();
                if (body == null) return null;

                String jsonStr = body.string();
                JSONObject json = new JSONObject(jsonStr);

                if (json.has("artist")) {
                    JSONObject artistObj = json.getJSONObject("artist");
                    if (artistObj.has("image")) {
                        JSONArray images = artistObj.getJSONArray("image");
                        for (int i = images.length() - 1; i >= 0; i--) {
                            JSONObject image = images.getJSONObject(i);
                            String imageUrl = image.optString("#text", null);
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                return imageUrl;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error fetching artist info", e);
        }
        return null;
    }

    public void fetchAlbumArt(String artist, String title, AlbumArtCallback callback) {
        executor.execute(() -> {
            try {
                String imageUrl = fetchImageUrl(artist, title);

                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        callback.onSuccess(imageUrl);
                    } else {
                        callback.onError("Album art not found");
                    }
                });
            } catch (Exception e) {
                Logger.e(TAG, "Error fetching album art", e);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private String fetchImageUrl(String artist, String title) {
        if (LASTFM_API_KEY == null || LASTFM_API_KEY.isEmpty() || LASTFM_API_KEY.contains("Replace")) {
            Logger.d(TAG, "Last.fm API key not configured");
            return null;
        }

        String result = fetchTrackInfo(artist, title);
        if (result != null) {
            Logger.d(TAG, "Found album art via track.getInfo");
            return result;
        }

        result = fetchSimilarTracks(artist, title);
        if (result != null) {
            Logger.d(TAG, "Found album art via track.getSimilar");
            return result;
        }

        Logger.d(TAG, "No album art found for: " + artist + " - " + title);
        return null;
    }

    private String fetchTrackInfo(String artist, String title) {
        try {
            String artistEncoded = URLEncoder.encode(artist, "UTF-8");
            String titleEncoded = URLEncoder.encode(title, "UTF-8");

            String urlString = LASTFM_BASE_URL + "?method=track.getInfo" +
                    "&api_key=" + LASTFM_API_KEY +
                    "&artist=" + artistEncoded +
                    "&track=" + titleEncoded +
                    "&format=json";

            Request request = new Request.Builder().url(urlString).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                ResponseBody body = response.body();
                if (body == null) return null;

                JSONObject json = new JSONObject(body.string());

                if (json.has("error") || !json.has("track")) {
                    return null;
                }

                JSONObject track = json.getJSONObject("track");

                if (track.has("album") && !track.isNull("album")) {
                    JSONObject album = track.getJSONObject("album");
                    if (album.has("image")) {
                        JSONArray images = album.getJSONArray("image");
                        String imageUrl = findBestImageUrl(images);
                        if (imageUrl != null) return imageUrl;
                    }
                }

                if (track.has("artist") && !track.isNull("artist")) {
                    JSONObject artistObj = track.getJSONObject("artist");
                    if (artistObj.has("image")) {
                        JSONArray artistImages = artistObj.getJSONArray("image");
                        String imageUrl = findBestImageUrl(artistImages);
                        if (imageUrl != null) return imageUrl;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error fetching track info", e);
        }
        return null;
    }

    private String fetchSimilarTracks(String artist, String title) {
        try {
            String artistEncoded = URLEncoder.encode(artist, "UTF-8");
            String titleEncoded = URLEncoder.encode(title, "UTF-8");

            String urlString = LASTFM_BASE_URL + "?method=track.getSimilar" +
                    "&api_key=" + LASTFM_API_KEY +
                    "&artist=" + artistEncoded +
                    "&track=" + titleEncoded +
                    "&limit=10" +
                    "&format=json";

            Request request = new Request.Builder().url(urlString).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                ResponseBody body = response.body();
                if (body == null) return null;

                JSONObject json = new JSONObject(body.string());

                if (json.has("error") || !json.has("similartracks")) {
                    return null;
                }

                JSONObject similarTracks = json.getJSONObject("similartracks");
                if (similarTracks.has("track")) {
                    JSONArray tracks = similarTracks.getJSONArray("track");

                    for (int i = 0; i < tracks.length(); i++) {
                        try {
                            JSONObject similarTrack = tracks.getJSONObject(i);

                            float match = (float) similarTrack.optDouble("match", 0.0);

                            if (match < 0.5f) {
                                continue;
                            }

                            if (similarTrack.has("album") && !similarTrack.isNull("album")) {
                                JSONObject album = similarTrack.getJSONObject("album");
                                if (album.has("image")) {
                                    JSONArray images = album.getJSONArray("image");
                                    String imageUrl = findBestImageUrl(images);
                                    if (imageUrl != null) {
                                        Logger.d(TAG, "Loaded from similar: " + similarTrack.optString("artist", "") + " - " + similarTrack.optString("name", "") + " (match=" + match + ")");
                                        return imageUrl;
                                    }
                                }
                            }

                            if (similarTrack.has("artist") && !similarTrack.isNull("artist")) {
                                JSONObject artistObj = similarTrack.getJSONObject("artist");
                                if (artistObj.has("image")) {
                                    JSONArray artistImages = artistObj.getJSONArray("image");
                                    String imageUrl = findBestImageUrl(artistImages);
                                    if (imageUrl != null) {
                                        return imageUrl;
                                    }
                                }
                            }

                        } catch (Exception e) {
                            continue;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error fetching similar tracks", e);
        }
        return null;
    }

    private String fetchArtistInfo(String artist) {
        try {
            String artistEncoded = URLEncoder.encode(artist, "UTF-8");

            String urlString = LASTFM_BASE_URL + "?method=artist.getInfo" +
                    "&api_key=" + LASTFM_API_KEY +
                    "&artist=" + artistEncoded +
                    "&format=json";

            Request request = new Request.Builder().url(urlString).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                ResponseBody body = response.body();
                if (body == null) return null;

                JSONObject json = new JSONObject(body.string());

                if (json.has("error") || !json.has("artist")) {
                    return null;
                }

                JSONObject artistObj = json.getJSONObject("artist");
                if (artistObj.has("image")) {
                    JSONArray images = artistObj.getJSONArray("image");
                    return findBestImageUrl(images);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error fetching artist info", e);
        }
        return null;
    }

    private String findBestImageUrl(JSONArray images) {
        for (int i = images.length() - 1; i >= 0; i--) {
            try {
                JSONObject image = images.getJSONObject(i);
                String imageUrl = image.optString("#text", null);
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    return imageUrl;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return null;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
