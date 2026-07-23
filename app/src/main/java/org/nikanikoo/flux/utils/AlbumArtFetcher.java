package org.nikanikoo.flux.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.nikanikoo.flux.R;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.json.JSONArray;
import org.json.JSONObject;

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
    private static final String ITUNES_SEARCH_URL = "https://itunes.apple.com/search";

    private static final String NO_ART = "";

    private static final LruCache<String, String> urlCache = new LruCache<>(200);

    private final ExecutorService executor;
    private final OkHttpClient httpClient;

    public interface AlbumArtCallback {
        void onSuccess(String imageUrl);
        void onError(String error);
    }

    public AlbumArtFetcher(Context context) {
        executor = Executors.newFixedThreadPool(3);
        httpClient = createUnsafeOkHttpClient();
    }

    private OkHttpClient createUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
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
            Logger.e(TAG, "Error creating SSL context", e);
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
        if (imageView == null) return;

        if (artist == null || title == null || artist.isEmpty() || title.isEmpty()) {
            if (placeholderResId > 0) imageView.setImageResource(placeholderResId);
            else imageView.setImageDrawable(null);
            if (callback != null) callback.onError("Invalid artist or title");
            return;
        }

        String key = artist + "\u0000" + title;

        imageView.setTag(R.id.tag_album_art_key, key);

        String cached = urlCache.get(key);
        if (cached != null) {
            if (!cached.isEmpty()) {
                RequestCreator req = Picasso.get().load(cached);
                if (placeholderResId > 0) req = req.placeholder(placeholderResId).error(placeholderResId);
                req.into(imageView);
                if (callback != null) callback.onSuccess(cached);
            } else {
                if (placeholderResId > 0) imageView.setImageResource(placeholderResId);
                else imageView.setImageDrawable(null);
                if (callback != null) callback.onError("No art (cached)");
            }
            return;
        }

        imageView.setImageDrawable(null);

        executor.execute(() -> {
            String imageUrl = fetchFromItunes(artist, title);

            urlCache.put(key, imageUrl != null ? imageUrl : NO_ART);

            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                Object currentTag = imageView.getTag(R.id.tag_album_art_key);
                if (!key.equals(currentTag)) return;

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    RequestCreator req = Picasso.get().load(imageUrl);
                    if (placeholderResId > 0) req = req.placeholder(placeholderResId).error(placeholderResId);
                    req.into(imageView);
                    if (callback != null) callback.onSuccess(imageUrl);
                } else {
                    if (placeholderResId > 0) imageView.setImageResource(placeholderResId);
                    else imageView.setImageDrawable(null);
                    if (callback != null) callback.onError("Album art not found");
                }
            });
        });
    }

    public void fetchAlbumArt(String artist, String title, AlbumArtCallback callback) {
        if (artist == null || title == null || artist.isEmpty() || title.isEmpty()) {
            if (callback != null) callback.onError("Invalid artist or title");
            return;
        }

        String key = artist + "\u0000" + title;
        String cached = urlCache.get(key);
        if (cached != null) {
            if (!cached.isEmpty()) callback.onSuccess(cached);
            else callback.onError("No art (cached)");
            return;
        }

        executor.execute(() -> {
            String imageUrl = fetchFromItunes(artist, title);
            urlCache.put(key, imageUrl != null ? imageUrl : NO_ART);

            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                if (imageUrl != null && !imageUrl.isEmpty()) callback.onSuccess(imageUrl);
                else callback.onError("Album art not found");
            });
        });
    }

    public String fetchAlbumArtSync(String artist, String title) {
        if (artist == null || title == null || artist.isEmpty() || title.isEmpty()) {
            return null;
        }

        String key = artist + "\u0000" + title;
        String cached = urlCache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String imageUrl = fetchFromItunes(artist, title);
        urlCache.put(key, imageUrl != null ? imageUrl : NO_ART);
        return imageUrl;
    }

    private String fetchFromItunes(String artist, String title) {
        try {
            String term = URLEncoder.encode(artist + " " + title, "UTF-8");
            String url = ITUNES_SEARCH_URL + "?term=" + term + "&entity=song&limit=5&media=music";

            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;

                ResponseBody body = response.body();
                if (body == null) return null;

                JSONObject json = new JSONObject(body.string());
                JSONArray results = json.optJSONArray("results");
                if (results == null || results.length() == 0) return null;

                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String artUrl = item.optString("artworkUrl100", null);
                    if (artUrl != null && !artUrl.isEmpty()) {
                        // Upgrade to 600x600 for better quality
                        return artUrl.replace("100x100bb", "600x600bb");
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error fetching artwork from iTunes", e);
        }
        return null;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
