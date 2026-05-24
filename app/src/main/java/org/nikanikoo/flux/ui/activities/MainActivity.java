package org.nikanikoo.flux.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.navigation.NavigationView;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.NotificationsManager;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.security.AccountManager;
import org.nikanikoo.flux.services.LongPollManager;
import org.nikanikoo.flux.services.LongPollService;
import org.nikanikoo.flux.services.MessageNotificationManager;
import org.nikanikoo.flux.ui.custom.CustomDrawerLayout;
import org.nikanikoo.flux.ui.custom.NotificationBadgeListener;
import org.nikanikoo.flux.ui.fragments.messages.ChatFragment;
import org.nikanikoo.flux.ui.fragments.messages.MessagesListFragment;
import org.nikanikoo.flux.ui.fragments.news.NewsFragment;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;
import org.nikanikoo.flux.utils.ValidationUtils;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Главная Activity приложения.
 * Использует NavigationController для управления навигацией и MiniPlayerController для плеера.
 */
public class MainActivity extends AppCompatActivity implements NotificationBadgeListener {

    private static final String TAG = "MainActivity";

    // Controllers
    private NavigationController navigationController;
    private MiniPlayerController miniPlayerController;

    // Managers
    private ProfileManager profileManager;
    private NotificationsManager notificationsManager;
    private LongPollManager longPollManager;
    private AccountManager accountManager;
    private LocaleManager localeManager;

    private int currentThemeMode = -1;
    private int currentThemeStyle = -1;
    private int currentContrastMode = -1;
    private boolean currentBottomNavEnabled;

    private final OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            Logger.d(TAG, "onBackPressed handled by callback, backStackCount=" +
                getSupportFragmentManager().getBackStackEntryCount());

            if (navigationController != null && navigationController.handleBackPress()) {
                Logger.d(TAG, "Drawer was open, closed it");
                return;
            }

            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                Logger.d(TAG, "Popping back stack");
                getSupportFragmentManager().popBackStack();
            } else {
                Logger.d(TAG, "Finishing activity");
                finish();
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        localeManager = LocaleManager.getInstance(newBase);
        Context context = localeManager.updateContext(newBase);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        
        if (!checkAuthentication()) {
            return;
        }
        
        setContentView(R.layout.activity_main);
        
        Logger.checkAndShowCrashReport(this);
        
        initializeManagers();
        currentBottomNavEnabled = isBottomNavigationEnabled();
        setupControllers(); // Setup controllers BEFORE toolbar (navigationController needed)
        setupToolbar();
        setupLongPoll();
        loadUserProfile();
        
        handleNotificationIntent(getIntent());
        
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
            Logger.d(TAG, "BackStack changed, count=" + backStackCount);
            
            for (int i = 0; i < backStackCount; i++) {
                Logger.d(TAG, "  BackStack[" + i + "]: " + getSupportFragmentManager().getBackStackEntryAt(i).getName());
            }
            
            if (navigationController != null) {
                navigationController.updateDrawerToggleForBackStack(backStackCount);
            }
        });
        
        if (savedInstanceState == null) {
            setupInitialFragment();
        } else {
            int savedFragmentId = savedInstanceState.getInt("current_fragment_id", -1);
            if (savedFragmentId != -1 && navigationController != null) {
                navigationController.setCurrentFragmentId(savedFragmentId);
            }
            if (navigationController != null) {
                navigationController.updateDrawerToggleForBackStack(getSupportFragmentManager().getBackStackEntryCount());
            }
        }
        
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        
        ThemeManager.applySystemBarsAppearance(this);
        
        org.nikanikoo.flux.utils.UpdateChecker.checkForUpdates(this);
        
        Logger.d(TAG, "onCreate completed");
    }
    
    /**
     * Применение темы до создания View
     */
    private void applyTheme() {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applySavedTheme();
        themeManager.applyThemeToActivity(this);
        
        currentThemeMode = themeManager.getThemeMode();
        currentThemeStyle = themeManager.getThemeStyle();
        currentContrastMode = themeManager.getContrastMode();
        
        if (themeManager.getThemeStyle() == ThemeManager.STYLE_MATERIAL_YOU && 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
    }
    
    /**
     * Проверка аутентификации пользователя
     */
    private boolean checkAuthentication() {
        OpenVKApi.resetInstance();
        OpenVKApi api = OpenVKApi.getInstance(this);
        
        if (api.getToken() == null) {
            Logger.d(TAG, "Токен отсутствует, переход в авторизацию");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }
        
        return true;
    }
    
    /**
     * Инициализация менеджеров
     */
    private void initializeManagers() {
        profileManager = ProfileManager.getInstance(this);
        notificationsManager = NotificationsManager.getInstance(this);
        longPollManager = LongPollManager.getInstance(this);
        accountManager = AccountManager.getInstance(this);
    }
    
    /**
     * Настройка Toolbar
     */
    private void setupToolbar() {
        // Toolbar уже настроен в NavigationController
    }
    
    /**
     * Настройка контроллеров
     */
    private void setupControllers() {
        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Navigation Controller
        CustomDrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.drawer_view);
        View navigationRailView = findViewById(R.id.navigation_rail);
        
        boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
        if (navigationRailView != null) {
            navigationRailView.setVisibility(isTablet ? View.VISIBLE : View.GONE);
        }
        
        navigationController = new NavigationController(this, drawerLayout, navigationView, navigationRailView, toolbar);
        updateNavigationMode();
        
        // Mini Player Controller
        miniPlayerController = new MiniPlayerController(this);
        miniPlayerController.initViews(findViewById(R.id.main_mini_player));
        miniPlayerController.setOnPlayerStateChangeListener(
                new MiniPlayerController.OnPlayerStateChangeListener() {
            @Override
            public void onPlayerConnected() {
                Logger.d(TAG, "Player connected");
            }
            
            @Override
            public void onPlayerDisconnected() {
                Logger.d(TAG, "Player disconnected");
            }
            
            @Override
            public void onTrackChanged(org.nikanikoo.flux.data.models.Audio audio) {
                Logger.d(TAG, "Track changed: " + audio.getFullTitle());
            }
        });
        
        // Setup accounts in navigation
        setupAccountSwitcher();
    }
    
    /**
     * Настройка переключателя аккаунтов
     */
    private void setupAccountSwitcher() {
        // Account switching logic is now in NavigationController
    }
    
    /**
     * Начальный фрагмент
     */
    private void setupInitialFragment() {
        String openFragment = getIntent().getStringExtra("open_fragment");
        
        if ("appearance_settings".equals(openFragment)) {
            navigationController.navigateToFragment(
                    new org.nikanikoo.flux.ui.fragments.settings.AppearanceSettingsFragment(),
                    "appearance_settings");
            navigationController.setCurrentFragmentId(R.id.drawer_settings);
        } else if ("settings".equals(openFragment)) {
            navigationController.navigateToFragment(
                    new org.nikanikoo.flux.ui.fragments.settings.SettingsFragment(),
                    "settings");
            navigationController.setCurrentFragmentId(R.id.drawer_settings);
        } else {
            navigationController.navigateToFragment(new NewsFragment(), "news");
            navigationController.setCurrentFragmentId(R.id.drawer_news);
        }
    }
    
    /**
     * Настройка LongPoll
     */
    private void setupLongPoll() {
        longPollManager.addMessageEventListener(new LongPollManager.OnMessageEventListener() {
            @Override
            public void onNewMessage(int messageId, int peerId, long timestamp, 
                                     String text, int fromId, boolean isOut) {
                if (!isOut) {
                    MessageNotificationManager.getInstance(MainActivity.this)
                            .showMessageNotification(messageId, fromId, peerId, text, timestamp);
                }
                updateMessagesListIfVisible();
                onNotificationBadgeUpdate();
            }

            @Override
            public void onMessageRead(int peerId, int localId) {
                updateChatIfVisible(peerId);
                onNotificationBadgeUpdate();
            }

            @Override
            public void onMessageEdit(int messageId, int peerId, String newText) {
                updateChatIfVisible(peerId);
            }
        });
        
        longPollManager.setOnlineEventListener((userId, isOnline) -> {
            updateUserOnlineStatus(userId, isOnline);
        });

        LongPollService.start(this);
    }
    
    /**
     * Загрузка профиля пользователя для Drawer
     */
    private void loadUserProfile() {
        android.util.Log.d(TAG, "Loading user profile for drawer...");
        
        // Получаем текущий аккаунт из AccountManager
        AccountManager accountManager = AccountManager.getInstance(this);
        AccountManager.Account currentAccount = accountManager.getCurrentAccount();
        
        if (currentAccount != null) {
            android.util.Log.d(TAG, "Current account: " + currentAccount.fullName + " (id: " + currentAccount.userId + ")");
        } else {
            android.util.Log.w(TAG, "No current account found!");
        }
        
        profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                runOnUiThread(() -> {
                    android.util.Log.d(TAG, "Profile loaded: " + profile.getFullName() + " (id: " + profile.getId() + ")");
                    if (navigationController != null) {
                        navigationController.updateUserInfo(profile);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading profile: " + error);
            }
        });
    }
    
    /**
     * Обработка Intent от уведомлений
     */
    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;
        
        if (intent.getBooleanExtra("open_chat", false)) {
            int peerId = intent.getIntExtra("peer_id", 0);
            String peerName = intent.getStringExtra("peer_name");
            int fromId = intent.getIntExtra("from_id", peerId);
            
            if (ValidationUtils.isValidUserId(peerId) && peerName != null) {
                Intent chatIntent = new Intent(this, ChatActivity.class);
                chatIntent.putExtra(ChatActivity.EXTRA_PEER_ID, peerId);
                chatIntent.putExtra(ChatActivity.EXTRA_PEER_NAME, peerName);
                chatIntent.putExtra(ChatActivity.EXTRA_FROM_ID, fromId);
                startActivity(chatIntent);
                
                MessageNotificationManager.getInstance(this).cancelNotification(fromId);
            }
        } else if (intent.getBooleanExtra("open_comments", false)) {
            Post post = (Post) intent.getSerializableExtra("post");
            if (post != null) {
                Intent commentsIntent = new Intent(this, CommentsActivity.class);
                commentsIntent.putExtra(CommentsActivity.EXTRA_POST, post);
                startActivity(commentsIntent);
            }
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationIntent(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        ThemeManager themeManager = ThemeManager.getInstance(this);
        if (currentThemeMode != themeManager.getThemeMode() ||
            currentThemeStyle != themeManager.getThemeStyle() ||
            currentContrastMode != themeManager.getContrastMode() ||
            currentBottomNavEnabled != isBottomNavigationEnabled()) {
            
            Logger.d(TAG, "Theme or navigation mode changed, recreating MainActivity");
            recreate();
            return;
        }
        
        if (longPollManager != null) {
            longPollManager.start();
        }
        updateAllBadges();
        
        if (miniPlayerController != null) {
            miniPlayerController.bindService();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (navigationController != null) {
            outState.putInt("current_fragment_id", navigationController.getCurrentFragmentId());
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            cleanupResources();
        }
    }
    
    private void cleanupResources() {
        if (longPollManager != null) {
            longPollManager.clearAllListeners();
        }
        if (miniPlayerController != null) {
            miniPlayerController.unbindService();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Logger.d(TAG, "onOptionsItemSelected: itemId=" + item.getItemId() + ", homeId=" + android.R.id.home);
        
        // Если это home button и есть back stack - сначала обрабатываем навигацию
        if (item.getItemId() == android.R.id.home) {
            int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
            Logger.d(TAG, "Home button pressed, backStackCount=" + backStackCount);
            
            if (backStackCount > 0) {
                Logger.d(TAG, "Popping back stack");
                getSupportFragmentManager().popBackStack();
                return true;
            }
        }
        
        // Затем проверяем drawer toggle (только если нет back stack)
        if (navigationController != null && navigationController.onOptionsItemSelected(item)) {
            Logger.d(TAG, "Handled by drawer toggle");
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    public NavigationController getNavigationController() {
        return navigationController;
    }

    public boolean isBottomNavigationEnabled() {
        SharedPreferences prefs = getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("bottom_nav_enabled", false);
    }

    public void updateNavigationMode() {
        boolean isBottomNav = isBottomNavigationEnabled();
        LinearLayout bottomNavContainer = findViewById(R.id.custom_bottom_navigation);
        View navigationRailView = findViewById(R.id.navigation_rail);
        org.nikanikoo.flux.ui.custom.CustomDrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        
        boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
        
        if (bottomNavContainer != null) {
            bottomNavContainer.setVisibility(isBottomNav ? View.VISIBLE : View.GONE);
        }
        
        if (navigationRailView != null) {
            navigationRailView.setVisibility((isTablet && !isBottomNav) ? View.VISIBLE : View.GONE);
        }
        
        if (drawerLayout != null) {
            if (isBottomNav) {
                drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            } else {
                drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED);
            }
        }
        
        if (isBottomNav) {
            refreshBottomNavigation();
        }
        
        if (navigationController != null) {
            navigationController.updateDrawerToggleForBackStack(getSupportFragmentManager().getBackStackEntryCount());
        }
    }

    public void refreshBottomNavigation() {
        LinearLayout bottomNavContainer = findViewById(R.id.custom_bottom_navigation);
        if (bottomNavContainer == null) return;
        
        bottomNavContainer.removeAllViews();
        
        SharedPreferences prefs = getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE);
        boolean showLabels = prefs.getBoolean("bottom_nav_show_labels", true);
        String savedItems = prefs.getString("bottom_nav_items", "drawer_news,drawer_messages,drawer_friends,drawer_notification");
        
        List<String> items = new ArrayList<>();
        if (!savedItems.isEmpty()) {
            items.addAll(Arrays.asList(savedItems.split(",")));
        }
        
        items.add("drawer_menu_dashboard");
        
        LayoutInflater inflater = LayoutInflater.from(this);
        int currentId = navigationController != null ? navigationController.getCurrentFragmentId() : -1;
        
        for (String tag : items) {
            View itemView = inflater.inflate(R.layout.item_custom_bottom_nav, bottomNavContainer, false);
            int itemId = getDrawerIdForTag(tag);
            itemView.setTag(itemId);
            
            ImageView iconView = itemView.findViewById(R.id.nav_item_icon);
            TextView labelView = itemView.findViewById(R.id.nav_item_label);
            
            if (iconView != null) {
                iconView.setImageResource(getIconForTag(tag));
            }
            
            if (labelView != null) {
                if (showLabels && !"drawer_menu_dashboard".equals(tag)) {
                    labelView.setVisibility(View.VISIBLE);
                    labelView.setText(getNameResForTag(tag));
                } else if ("drawer_menu_dashboard".equals(tag)) {
                    labelView.setVisibility(showLabels ? View.VISIBLE : View.GONE);
                    labelView.setText("Меню");
                } else {
                    labelView.setVisibility(View.GONE);
                }
            }
            
            itemView.setOnClickListener(v -> {
                if (navigationController != null) {
                    navigationController.navigateToDrawerItem(itemId);
                }
            });
            
            bottomNavContainer.addView(itemView);
        }
        
        updateBottomNavigationSelection(currentId);
        updateAllBadges();
    }

    public void updateBottomNavigationSelection(int selectedId) {
        LinearLayout bottomNav = findViewById(R.id.custom_bottom_navigation);
        if (bottomNav == null) return;

        int activeColor = getColorFromAttr(this, androidx.appcompat.R.attr.colorPrimary);
        int inactiveColor = getColorFromAttr(this, androidx.appcompat.R.attr.colorControlNormal);

        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            View child = bottomNav.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof Integer) {
                int itemId = (Integer) tag;
                boolean isSelected = (itemId == selectedId);

                ImageView iconView = child.findViewById(R.id.nav_item_icon);
                TextView labelView = child.findViewById(R.id.nav_item_label);

                if (iconView != null) {
                    iconView.setImageTintList(android.content.res.ColorStateList.valueOf(
                            isSelected ? activeColor : inactiveColor
                    ));
                }
                if (labelView != null) {
                    labelView.setTextColor(isSelected ? activeColor : inactiveColor);
                    labelView.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
            }
        }
    }

    private void updateBottomBadge(int itemId, int count) {
        LinearLayout bottomNav = findViewById(R.id.custom_bottom_navigation);
        if (bottomNav != null) {
            for (int i = 0; i < bottomNav.getChildCount(); i++) {
                View child = bottomNav.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof Integer && (Integer) tag == itemId) {
                    TextView badgeView = child.findViewById(R.id.nav_item_badge);
                    if (badgeView != null) {
                        if (count > 0) {
                            badgeView.setVisibility(View.VISIBLE);
                            badgeView.setText(count > 99 ? "99+" : String.valueOf(count));
                        } else {
                            badgeView.setVisibility(View.GONE);
                        }
                    }
                    break;
                }
            }
        }
    }

    private int getColorFromAttr(Context context, int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private int getIconForTag(String tag) {
        switch (tag) {
            case "drawer_news":
                return R.drawable.ic_newspaper;
            case "drawer_messages":
                return R.drawable.ic_chat_bubble;
            case "drawer_friends":
                return R.drawable.ic_contacts;
            case "drawer_groups":
                return R.drawable.ic_group;
            case "drawer_photos":
                return R.drawable.ic_photo;
            case "drawer_videos":
                return R.drawable.ic_video_library;
            case "drawer_audio":
                return R.drawable.ic_library_music;
            case "drawer_notes":
                return R.drawable.ic_note_stack;
            case "drawer_notification":
                return R.drawable.ic_notifications;
            case "drawer_settings":
                return R.drawable.ic_settings;
            case "drawer_menu_dashboard":
                return R.drawable.ic_menu;
            default:
                return R.drawable.ic_newspaper;
        }
    }

    private int getNameResForTag(String tag) {
        switch (tag) {
            case "drawer_news":
                return R.string.nav_news;
            case "drawer_messages":
                return R.string.nav_messages;
            case "drawer_friends":
                return R.string.nav_friends;
            case "drawer_groups":
                return R.string.nav_groups;
            case "drawer_photos":
                return R.string.nav_photos;
            case "drawer_videos":
                return R.string.nav_videos;
            case "drawer_audio":
                return R.string.nav_music;
            case "drawer_notes":
                return R.string.nav_notes;
            case "drawer_notification":
                return R.string.nav_notifications;
            case "drawer_settings":
                return R.string.nav_settings;
            default:
                return R.string.nav_news;
        }
    }

    private int getDrawerIdForTag(String tag) {
        switch (tag) {
            case "drawer_news":
                return R.id.drawer_news;
            case "drawer_messages":
                return R.id.drawer_messages;
            case "drawer_friends":
                return R.id.drawer_friends;
            case "drawer_groups":
                return R.id.drawer_groups;
            case "drawer_photos":
                return R.id.drawer_photos;
            case "drawer_videos":
                return R.id.drawer_videos;
            case "drawer_audio":
                return R.id.drawer_audio;
            case "drawer_notes":
                return R.id.drawer_notes;
            case "drawer_notification":
                return R.id.drawer_notification;
            case "drawer_settings":
                return R.id.drawer_settings;
            case "drawer_menu_dashboard":
                return R.id.drawer_menu_dashboard;
            default:
                return R.id.drawer_news;
        }
    }

    /**
     * Установить заголовок Toolbar
     */
    public void setToolbarTitle(String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setOnClickListener(null);
                toolbar.setClickable(false);
                View arrow = toolbar.findViewById(R.id.news_toolbar_arrow);
                if (arrow != null) {
                    arrow.setVisibility(View.GONE);
                }
            }
        }
    }
    
    public void setToolbarTitleClickable(String title, View.OnClickListener clickListener) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setOnClickListener(clickListener);
                toolbar.setClickable(true);
            }
        }
    }
    
    /**
     * Показать диалог выбора темы
     */
    public void showThemeDialog() {
        ThemeManager themeManager = ThemeManager.getInstance(this);
        int currentTheme = themeManager.getThemeMode();
        
        String[] themes = {getString(R.string.appearance_theme_light), getString(R.string.appearance_theme_dark), getString(R.string.appearance_theme_system)};
        
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.appearance_select_theme))
                .setSingleChoiceItems(themes, currentTheme, (dialog, which) -> {
                    themeManager.setThemeMode(which);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
    
    /**
     * Обновить все бейджи уведомлений
     */
    public void updateAllBadges() {
        OpenVKApi api = OpenVKApi.getInstance(this);
        api.getCounters(new OpenVKApi.CountersCallback() {
            @Override
            public void onSuccess(int messages, int notifications, int friends) {
                runOnUiThread(() -> {
                    NavigationView navigationView = findViewById(R.id.drawer_view);
                    View navigationRailView = findViewById(R.id.navigation_rail);
                    
                    if (navigationView != null) {
                        updateDrawerBadge(navigationView, R.id.drawer_notification, notifications, getString(R.string.notifications_title));
                        updateDrawerBadge(navigationView, R.id.drawer_messages, messages, getString(R.string.messages_title));
                        updateDrawerBadge(navigationView, R.id.drawer_friends, friends, getString(R.string.friends_title));
                    }
                    
                    if (navigationRailView != null) {
                        updateRailBadge(navigationRailView, R.id.drawer_notification, notifications);
                        updateRailBadge(navigationRailView, R.id.drawer_messages, messages);
                        updateRailBadge(navigationRailView, R.id.drawer_friends, friends);
                    }

                    if (isBottomNavigationEnabled()) {
                        updateBottomBadge(R.id.drawer_notification, notifications);
                        updateBottomBadge(R.id.drawer_messages, messages);
                        updateBottomBadge(R.id.drawer_friends, friends);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error updating counters: " + error);
            }
        });
    }
    
    private void updateDrawerBadge(NavigationView navigationView, int itemId, int count, String defaultTitle) {
        MenuItem item = navigationView.getMenu().findItem(itemId);
        if (item != null) {
            item.setTitle(count > 0 ? defaultTitle + " (" + count + ")" : defaultTitle);
        }
    }
    
    private void updateRailBadge(View navigationRailView, int itemId, int count) {
        if (navigationRailView != null) {
            android.widget.LinearLayout itemsContainer = navigationRailView.findViewById(R.id.navigation_rail_items);
            if (itemsContainer != null) {
                for (int i = 0; i < itemsContainer.getChildCount(); i++) {
                    View child = itemsContainer.getChildAt(i);
                    Object tag = child.getTag();
                    if (tag instanceof Integer && (Integer) tag == itemId) {
                        android.widget.TextView badgeView = child.findViewById(R.id.rail_item_badge);
                        if (badgeView != null) {
                            if (count > 0) {
                                badgeView.setVisibility(View.VISIBLE);
                                badgeView.setText(count > 99 ? "99+" : String.valueOf(count));
                            } else {
                                badgeView.setVisibility(View.GONE);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }
    
    @Override
    public void onNotificationBadgeUpdate() {
        updateAllBadges();
    }
    
    // ==================== Helper Methods ====================
    
    private void updateMessagesListIfVisible() {
        androidx.fragment.app.Fragment currentFragment = 
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof MessagesListFragment) {
            ((MessagesListFragment) currentFragment).refreshConversations();
        }
    }
    
    private void updateChatIfVisible(int peerId) {
        androidx.fragment.app.Fragment currentFragment = 
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof ChatFragment) {
            ((ChatFragment) currentFragment).refreshMessagesIfSamePeer(peerId);
        }
    }
    
    private void updateUserOnlineStatus(int userId, boolean isOnline) {
        androidx.fragment.app.Fragment currentFragment = 
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof MessagesListFragment) {
            ((MessagesListFragment) currentFragment).updateUserOnlineStatus(userId, isOnline);
        } else if (currentFragment instanceof org.nikanikoo.flux.ui.fragments.friends.FriendsListFragment) {
            ((org.nikanikoo.flux.ui.fragments.friends.FriendsListFragment) currentFragment)
                    .updateUserOnlineStatus(userId, isOnline);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.handleSaveCrashResult(this, requestCode, resultCode, data);
    }
}
