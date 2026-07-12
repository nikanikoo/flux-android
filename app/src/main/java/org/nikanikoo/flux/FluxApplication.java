package org.nikanikoo.flux;

import android.app.Application;

import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.data.managers.PhotoUploadManager;
import org.nikanikoo.flux.data.models.Group;
import org.nikanikoo.flux.data.models.Notification;
import org.nikanikoo.flux.utils.CacheManager;
import org.nikanikoo.flux.utils.AsyncTaskHelper;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.SSLHelper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class FluxApplication extends Application {
    private static FluxApplication instance;

    public static FluxApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        instance = this;
        Logger.init(this);
        super.onCreate();

        Logger.d("FluxApplication", "Инициализация приложения");
        Notification.setAppContext(this);
        Group.setAppContext(this);
        CacheManager.setApplicationContext(this);
        clearOrphanedTempFiles();
        configurePicasso();
    }

    /**
     * Clean up orphaned temporary files that may have been left behind
     * if the process was killed between file creation and cleanup.
     */
    private void clearOrphanedTempFiles() {
        File cacheDir = getCacheDir();
        if (cacheDir == null || !cacheDir.exists()) return;

        int deletedCount = 0;
        try {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    // PhotoUploadManager temp files: upload_image*.jpg
                    // AudioPlayerService temp files: audio_*.tmp
                    if (name.startsWith("upload_image") || name.startsWith("audio_")) {
                        if (file.delete()) {
                            deletedCount++;
                            Logger.d("FluxApplication", "Deleted orphaned temp file: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("FluxApplication", "Error cleaning temp files", e);
        }

        if (deletedCount > 0) {
            Logger.d("FluxApplication", "Cleaned up " + deletedCount + " orphaned temp files");
        }
    }

    private void configurePicasso() {
        Logger.d("FluxApplication", "Настройка Picasso с поддержкой SSL и disk cache");

        File imageCacheDir = new File(getCacheDir(), "picasso_cache");
        int diskCacheSizeBytes = (int) ((long) Constants.Cache.IMAGE_CACHE_SIZE_MB * 1024 * 1024);
        Cache diskCache = new Cache(imageCacheDir, diskCacheSizeBytes);

        int memoryCacheSize = (int) (Runtime.getRuntime().maxMemory() / 8);

        ExecutorService executorService = Executors.newFixedThreadPool(8);

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(Constants.Api.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(Constants.Api.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(Constants.Api.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .cache(diskCache)
                .addInterceptor(CacheManager.getCacheInterceptor());

        SSLHelper.configureToIgnoreSSL(clientBuilder);

        OkHttpClient client = clientBuilder.build();

        Picasso picasso = new Picasso.Builder(this)
                .downloader(new OkHttp3Downloader(client))
                .executor(executorService)
                .memoryCache(new com.squareup.picasso.LruCache(memoryCacheSize))
                .indicatorsEnabled(false)
                .loggingEnabled(Constants.Debug.ENABLE_NETWORK_LOGGING)
                .build();

        Picasso.setSingletonInstance(picasso);
        Logger.d("FluxApplication", "Picasso успешно настроен с кэшем: " + (memoryCacheSize / 1024 / 1024) + "MB и 8 потоками");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Logger.d("FluxApplication", "Приложение завершается");

        AsyncTaskHelper.shutdown();
        Logger.d("FluxApplication", "AsyncTaskHelper остановлен");

        PhotoUploadManager.getInstance(this).shutdown();
        Logger.d("FluxApplication", "PhotoUploadManager остановлен");
    }
}
