package org.nikanikoo.flux;

public final class Constants {
    // todo использовать везде константы из этого класса, вместо "магических чисел" и строк

    private Constants() {}

    public static final class Api {
        public static final String DEFAULT_INSTANCE = "https://api.openvk.org";
        public static final String DEFAULT_CLIENT_NAME = "openvk_flux_android";
        public static final String API_VERSION = "5.131";
        public static final String USER_AGENT = "OpenVK Flux";
        public static final String APP_NAME = "OpenVK Flux";
        public static final String APP_VERSION = "1.1";
        
        // Таймауты (в секундах)
        public static final int CONNECT_TIMEOUT = 15;
        public static final int READ_TIMEOUT = 30;
        public static final int WRITE_TIMEOUT = 30;
        public static final int LONGPOLL_TIMEOUT = 90;
        
        // Лимиты запросов
        public static final int MAX_RETRY_ATTEMPTS = 3;
        public static final long RETRY_DELAY_MS = 1000;
        public static final long MIN_REQUEST_INTERVAL_MS = 100;
        public static final int POSTS_PER_PAGE = 15;
        public static final int MESSAGES_PER_PAGE = 20;
        public static final int FRIENDS_PER_PAGE = 50;
    }
    
    // UI
    public static final class UI {
        // Размеры изображений
        public static final int AVATAR_SIZE = 120;
        public static final int POST_IMAGE_WIDTH = 800;
        public static final int POST_IMAGE_HEIGHT = 600;
        public static final int THUMBNAIL_SIZE = 200;
        
        // Пагинация
        public static final int ENDLESS_SCROLL_THRESHOLD = 5;
        public static final long ENDLESS_SCROLL_DELAY_MS = 1000;
        public static final int MAX_PAGES = 100;
        
        // Анимации
        public static final int ANIMATION_DURATION_SHORT = 200;
        public static final int ANIMATION_DURATION_MEDIUM = 300;
        public static final int ANIMATION_DURATION_LONG = 500;
    }
    
    // Кеширование
    public static final class Cache {
        public static final long PROFILE_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 минут
        public static final int MAX_PROCESSED_EVENTS = 1000;
        public static final int MAX_POST_CACHE_SIZE = 500;
        public static final long IMAGE_CACHE_SIZE_MB = 50;
        public static final long HTTP_CACHE_SIZE_MB = 10; // 10 MB for OkHttp
    }
    
    // Уведомления
    public static final class Notifications {
    }
    
    // Файлы и хранилище
    public static final class Storage {
        public static final String PREFS_NAME = "openvk_prefs";
        public static final String KEY_TOKEN = "access_token";
        public static final String KEY_INSTANCE = "instance_url";
        public static final String KEY_ENCRYPTION_FAILED = "encryption_failed";
        public static final long MAX_FILE_SIZE_MB = 10;
    }
    
    // Валидация
    public static final class Validation {
        public static final int MIN_PASSWORD_LENGTH = 6;
        public static final int TWO_FA_CODE_LENGTH = 6;
        public static final int MAX_POST_LENGTH = 4096;
        public static final int MAX_MESSAGE_LENGTH = 4096;
        public static final int MIN_USER_ID = 1;
    }
    
    // Отладка
    public static final class Debug {
        public static final boolean ENABLE_LOGGING = BuildConfig.DEBUG;
        public static final boolean ENABLE_NETWORK_LOGGING = BuildConfig.DEBUG;
        public static final boolean ENABLE_CRASH_REPORTING = !BuildConfig.DEBUG;
    }
    
    // Интенты и экстра данные
    public static final class Intents {
        public static final String EXTRA_OPEN_CHAT = "open_chat";
        public static final String EXTRA_PEER_ID = "peer_id";
        public static final String EXTRA_PEER_NAME = "peer_name";
        public static final String EXTRA_FROM_ID = "from_id";
        public static final String EXTRA_OPEN_COMMENTS = "open_comments";
        public static final String EXTRA_POST = "post";
    }

    public static final class Links {
        public static final String GITHUB_REPO = "https://github.com/nikanikoo/flux-android";
        public static final String GITHUB_RELEASES_API = "https://raw.githubusercontent.com/nikanikoo/flux-android/gh-pages/update.json";
    }
}