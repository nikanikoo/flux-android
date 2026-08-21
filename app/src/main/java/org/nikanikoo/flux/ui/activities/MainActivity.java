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

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
import org.nikanikoo.flux.ui.fragments.menu.MenuDashboardFragment;
import org.nikanikoo.flux.ui.fragments.messages.ChatFragment;
import org.nikanikoo.flux.ui.fragments.messages.MessagesListFragment;
import org.nikanikoo.flux.ui.fragments.news.NewsFragment;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.LocaleManager;
import org.nikanikoo.flux.utils.ThemeManager;
import org.nikanikoo.flux.utils.ThemeTransitionHelper;
import org.nikanikoo.flux.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Главная Activity приложения.
 * Использует NavigationController для управления навигацией и MiniPlayerController для плеера.
 */
public class MainActivity extends AppCompatActivity implements NotificationBadgeListener {

    private static final String TAG = "MainActivity";

    private static int sCachedMessages = -1;
    private static int sCachedNotifications = -1;
    private static int sCachedFriends = -1;

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
    private int currentCustomColor = -1;
    private boolean currentBottomNavEnabled = false;
    private boolean isSyncingBottomNav = false;

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
        overridePendingTransition(0, 0);
        applyTheme();
        super.onCreate(savedInstanceState);
        
        if (!checkAuthentication()) {
            return;
        }
        
        setContentView(R.layout.activity_main);
        
        initializeManagers();
        currentBottomNavEnabled = isBottomNavigationEnabled();
        setupControllers();
        
        if (ThemeTransitionHelper.wasDrawerOpen()) {
            CustomDrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
            if (drawerLayout != null) {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START, false);
            }
        }
        
        if (ThemeTransitionHelper.isTransitioning()) {
            ThemeTransitionHelper.animateThemeChange(this);
        }
        
        Logger.checkAndShowCrashReport(this);
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
                if (backStackCount == 0) {
                    navigationController.updateToolbarForCurrentItem(navigationController.getCurrentFragmentId());
                } else {
                    androidx.fragment.app.FragmentManager.BackStackEntry topEntry =
                            getSupportFragmentManager().getBackStackEntryAt(backStackCount - 1);
                    if (topEntry != null && topEntry.getName() != null) {
                        String name = topEntry.getName();
                        if ("settings".equals(name) || "appearance_settings".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_settings));
                        } else if ("profile".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_profile));
                        } else if ("notifications".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_notifications));
                        } else if ("news".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_news));
                        } else if ("messages".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_messages));
                        } else if ("friends".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_friends));
                        } else if ("groups".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_groups));
                        } else if ("photos".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_photos));
                        } else if ("videos".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_videos));
                        } else if ("music".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_music));
                        } else if ("notes".equals(name)) {
                            setToolbarTitle(getString(R.string.nav_notes));
                        } else if ("menu_dashboard".equals(name)) {
                            setToolbarTitle(getString(R.string.navigation_menu_title));
                        }
                    }
                }
            }
        });
        
        if (savedInstanceState == null) {
            setupInitialFragment();
        } else {
            int savedFragmentId = savedInstanceState.getInt("current_fragment_id", -1);
            if (savedFragmentId != -1 && navigationController != null) {
                navigationController.setCurrentFragmentId(savedFragmentId);
                navigationController.updateToolbarForCurrentItem(savedFragmentId);
                syncBottomNavigationSelection(savedFragmentId);
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
        currentCustomColor = themeManager.getCustomColor();
        
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
        if (drawerLayout != null) {
            drawerLayout.setStatusBarBackground(null);
            drawerLayout.setStatusBarBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        NavigationView navigationView = findViewById(R.id.drawer_view);
        View navigationRailView = findViewById(R.id.navigation_rail);
        
        boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
        if (navigationRailView != null) {
            navigationRailView.setVisibility(isTablet ? View.VISIBLE : View.GONE);
        }
        
        navigationController = new NavigationController(this, drawerLayout, navigationView, navigationRailView, toolbar);
        
        // Apply navigation mode (bottom bar or drawer) BEFORE other setup
        updateNavigationMode();
        
        // Bottom navigation selection listener
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                if (isSyncingBottomNav) {
                    return true;
                }
                if (navigationController != null) {
                    navigationController.navigateToDrawerItem(item.getItemId());
                }
                return true;
            });
        }
        
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
            String startSectionTag = getSharedPreferences(MenuDashboardFragment.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(org.nikanikoo.flux.ui.fragments.settings.AppearanceSettingsFragment.KEY_START_SECTION,
                            org.nikanikoo.flux.ui.fragments.settings.AppearanceSettingsFragment.DEFAULT_START_SECTION);
            if (!isBottomNavigationEnabled() && "drawer_menu_dashboard".equals(startSectionTag)) {
                startSectionTag = org.nikanikoo.flux.ui.fragments.settings.AppearanceSettingsFragment.DEFAULT_START_SECTION;
            }
            int drawerId = MenuDashboardFragment.getDrawerIdForTag(startSectionTag);
            navigationController.navigateToDrawerItem(drawerId);
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
                updateMessagesListIfVisible(peerId, text, timestamp, isOut);
                onNotificationBadgeUpdate();
            }

            @Override
            public void onMessageRead(int peerId, int localId) {
                updateChatIfVisible(peerId);
                updateMessagesListOnRead(peerId);
                onNotificationBadgeUpdate();
            }

            @Override
            public void onMessageEdit(int messageId, int peerId, String newText) {
                updateChatIfVisible(peerId);
                updateMessagesListOnEdit(peerId, newText);
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
            currentCustomColor != themeManager.getCustomColor() ||
            currentBottomNavEnabled != isBottomNavigationEnabled()) {
            
            Logger.d(TAG, "Theme or navigation mode changed, recreating MainActivity");
            recreate();
            return;
        }
        
        if (longPollManager != null) {
            longPollManager.start();
        }
        applyCachedBadges();
        if (isBottomNavigationEnabled()) {
            // Перестраиваем панель (разделы/подписи могли измениться в настройках)
            refreshBottomNavigation();
        } else {
            updateAllBadges();
        }
        
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
    
    // Bottom nav
    
    public NavigationController getNavigationController() {
        return navigationController;
    }
    
    public boolean isBottomNavigationEnabled() {
        return getSharedPreferences(MenuDashboardFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(MenuDashboardFragment.KEY_BOTTOM_NAV_ENABLED, false);
    }
    
    public boolean areBottomNavLabelsEnabled() {
        return getSharedPreferences(MenuDashboardFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(MenuDashboardFragment.KEY_BOTTOM_NAV_LABELS, true);
    }

    public void updateNavigationMode() {
        boolean isBottomNav = isBottomNavigationEnabled();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        View navigationRailView = findViewById(R.id.navigation_rail);
        CustomDrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        
        boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
        
        if (bottomNav != null) {
            bottomNav.setVisibility(isBottomNav ? View.VISIBLE : View.GONE);
        }
        
        if (navigationRailView != null) {
            navigationRailView.setVisibility((isTablet && !isBottomNav) ? View.VISIBLE : View.GONE);
        }
        
        if (drawerLayout != null) {
            drawerLayout.setDrawerLockMode(isBottomNav
                    ? androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                    : androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED);
        }
        
        if (isBottomNav) {
            refreshBottomNavigation();
        }
        
        if (navigationController != null) {
            navigationController.updateDrawerToggleForBackStack(getSupportFragmentManager().getBackStackEntryCount());
        }
    }

    public void refreshBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;
        
        boolean showLabels = areBottomNavLabelsEnabled();
        bottomNav.setLabelVisibilityMode(showLabels
                ? BottomNavigationView.LABEL_VISIBILITY_LABELED
                : BottomNavigationView.LABEL_VISIBILITY_UNLABELED);
        
        String savedItems = getSharedPreferences(MenuDashboardFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(MenuDashboardFragment.KEY_BOTTOM_NAV_ITEMS, MenuDashboardFragment.DEFAULT_BOTTOM_NAV_ITEMS);
        
        List<String> items = new ArrayList<>();
        if (savedItems != null && !savedItems.isEmpty()) {
            for (String tag : savedItems.split(",")) {
                String cleanTag = tag.trim();
                if (!cleanTag.isEmpty() && items.size() < MenuDashboardFragment.MAX_BOTTOM_NAV_ITEMS && !cleanTag.equals("drawer_menu_dashboard")) {
                    items.add(cleanTag);
                }
            }
        }
        if (items.isEmpty()) {
            items.addAll(Arrays.asList(MenuDashboardFragment.DEFAULT_BOTTOM_NAV_ITEMS.split(",")));
        }
        
        items.add("drawer_menu_dashboard");
        
        android.view.Menu menu = bottomNav.getMenu();
        menu.clear();
        
        int order = 0;
        for (String tag : items) {
            int itemId = getDrawerIdForTag(tag);
            android.view.MenuItem menuItem = menu.add(android.view.Menu.NONE, itemId, order++, getString(getNameResForTag(tag)));
            menuItem.setIcon(getIconForTag(tag));
        }
        
        int currentId = navigationController != null ? navigationController.getCurrentFragmentId() : -1;
        isSyncingBottomNav = true;
        if (bottomNav.getMenu().findItem(currentId) != null) {
            bottomNav.setSelectedItemId(currentId);
        } else if (bottomNav.getMenu().findItem(R.id.drawer_menu_dashboard) != null) {
            bottomNav.setSelectedItemId(R.id.drawer_menu_dashboard);
        }
        isSyncingBottomNav = false;
        
        updateAllBadges();
    }

    public void syncBottomNavigationSelection(int id) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            if (bottomNav.getMenu().findItem(id) != null) {
                if (bottomNav.getSelectedItemId() != id) {
                    isSyncingBottomNav = true;
                    bottomNav.setSelectedItemId(id);
                    isSyncingBottomNav = false;
                }
            }
        }
    }
    
    private void updateBottomBadge(int itemId, int count) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;
        
        if (count > 0) {
            BadgeDrawable badge = bottomNav.getOrCreateBadge(itemId);
            badge.setNumber(count);
            badge.setVisible(true);
        } else {
            if (bottomNav.getBadge(itemId) != null) {
                bottomNav.removeBadge(itemId);
            }
        }
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
            case "drawer_settings":
                return R.string.nav_settings;
            case "drawer_menu_dashboard":
                return R.string.navigation_menu_title;
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
                sCachedMessages = messages;
                sCachedNotifications = notifications;
                sCachedFriends = friends;
                runOnUiThread(() -> {
                    NavigationView navigationView = findViewById(R.id.drawer_view);
                    View navigationRailView = findViewById(R.id.navigation_rail);
                    
                    if (navigationView != null) {
                        updateDrawerBadge(navigationView, R.id.drawer_messages, messages, getString(R.string.messages_title));
                        updateDrawerBadge(navigationView, R.id.drawer_friends, friends, getString(R.string.friends_title));
                    }
                    
                    if (navigationRailView != null) {
                        updateRailBadge(navigationRailView, R.id.drawer_messages, messages);
                        updateRailBadge(navigationRailView, R.id.drawer_friends, friends);
                    }
                    
                    if (isBottomNavigationEnabled()) {
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

    private void applyCachedBadges() {
        if (sCachedMessages == -1 && sCachedNotifications == -1 && sCachedFriends == -1) {
            return;
        }
        NavigationView navigationView = findViewById(R.id.drawer_view);
        View navigationRailView = findViewById(R.id.navigation_rail);
        
        int messages = Math.max(0, sCachedMessages);
        int friends = Math.max(0, sCachedFriends);

        if (navigationView != null) {
            updateDrawerBadge(navigationView, R.id.drawer_messages, messages, getString(R.string.messages_title));
            updateDrawerBadge(navigationView, R.id.drawer_friends, friends, getString(R.string.friends_title));
        }
        
        if (navigationRailView != null) {
            updateRailBadge(navigationRailView, R.id.drawer_messages, messages);
            updateRailBadge(navigationRailView, R.id.drawer_friends, friends);
        }
        
        if (isBottomNavigationEnabled()) {
            updateBottomBadge(R.id.drawer_messages, messages);
            updateBottomBadge(R.id.drawer_friends, friends);
        }
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
    
    private void updateMessagesListIfVisible(int peerId, String text, long timestamp, boolean isOut) {
        androidx.fragment.app.Fragment currentFragment = 
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof MessagesListFragment) {
            ((MessagesListFragment) currentFragment).onNewMessageReceived(peerId, text, timestamp, isOut);
        }
    }

    private void updateMessagesListOnRead(int peerId) {
        androidx.fragment.app.Fragment currentFragment = 
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof MessagesListFragment) {
            ((MessagesListFragment) currentFragment).onMessageReadLocally(peerId);
        }
    }

    private void updateMessagesListOnEdit(int peerId, String newText) {
        androidx.fragment.app.Fragment currentFragment = 
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof MessagesListFragment) {
            ((MessagesListFragment) currentFragment).onMessageEditLocally(peerId, newText);
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
