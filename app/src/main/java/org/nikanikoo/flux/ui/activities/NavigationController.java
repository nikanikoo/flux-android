package org.nikanikoo.flux.ui.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.navigation.NavigationView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.security.AccountManager;
import org.nikanikoo.flux.ui.custom.CustomDrawerLayout;
import org.nikanikoo.flux.ui.fragments.friends.FriendsListFragment;
import org.nikanikoo.flux.ui.fragments.groups.GroupsListFragment;
import org.nikanikoo.flux.ui.fragments.media.MusicListFragment;
import org.nikanikoo.flux.ui.fragments.media.VideoListFragment;
import org.nikanikoo.flux.ui.fragments.messages.MessagesListFragment;
import org.nikanikoo.flux.ui.fragments.news.NewsFragment;
import org.nikanikoo.flux.ui.fragments.notifications.NotificationsFragment;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.ui.fragments.settings.SettingsFragment;
import org.nikanikoo.flux.utils.Logger;
import org.nikanikoo.flux.utils.ThemeManager;
import org.nikanikoo.flux.utils.ThemeTransitionHelper;

import java.util.List;

/**
 * Controller для управления навигацией и боковым меню (Drawer).
 * Инкапсулирует логику навигации между фрагментами и управления DrawerLayout.
 */
public class NavigationController implements NavigationView.OnNavigationItemSelectedListener {
    
    private static final String TAG = "NavigationController";
    
    // Activity and Views
    private final MainActivity activity;
    private final CustomDrawerLayout drawerLayout;
    private final NavigationView navigationView;
    private final View navigationRailView;
    
    // Header views
    private TextView drawerName;
    private TextView drawerUsername;
    private TextView drawerInstance;
    private ImageView drawerVerifiedBadge;
    private ImageView drawerAvatar;
    private ImageView accountsExpandIcon;
    private LinearLayout accountsListContainer;
    private LinearLayout otherAccountsList;
    private View addAccountButton;
    private View currentAccountHeader;
    
    // State
    private boolean isAccountsListExpanded = false;
    private int currentFragmentId = -1;
    private ActionBarDrawerToggle drawerToggle;
    
    // Managers
    private final AccountManager accountManager;
    
    public NavigationController(MainActivity activity, CustomDrawerLayout drawerLayout, 
                                NavigationView navigationView, 
                                View navigationRailView, 
                                Toolbar toolbar) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.navigationView = navigationView;
        this.navigationRailView = navigationRailView;
        this.accountManager = AccountManager.getInstance(activity);
        
        initDrawer(toolbar);
        initHeaderViews();
        initNavigationRail();

        org.nikanikoo.flux.data.managers.ProfileManager profileManager =
                org.nikanikoo.flux.data.managers.ProfileManager.getInstance(activity);
        UserProfile cachedProfile = profileManager.getCachedProfileSync();
        if (cachedProfile != null) {
            updateUserInfo(cachedProfile);
        }
    }
    
    private void initNavigationRail() {
        if (navigationRailView != null) {
            View menuButton = navigationRailView.findViewById(R.id.rail_menu_button);
            if (menuButton != null) {
                menuButton.setOnClickListener(v -> openDrawer());
            }

            LinearLayout itemsContainer = navigationRailView.findViewById(R.id.navigation_rail_items);
            if (itemsContainer != null) {
                android.view.LayoutInflater inflater = android.view.LayoutInflater.from(activity);
                android.view.Menu menu = navigationView.getMenu();
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem item = menu.getItem(i);
                    if (item.isVisible()) {
                        View itemView = inflater.inflate(R.layout.item_custom_rail, itemsContainer, false);
                        ImageView iconView = itemView.findViewById(R.id.rail_item_icon);
                        if (iconView != null && item.getIcon() != null) {
                            iconView.setImageDrawable(item.getIcon());
                        }
                        
                        itemView.setOnClickListener(v -> {
                            onNavigationItemSelected(item);
                        });
                        
                        itemView.setTag(item.getItemId());
                        
                        itemsContainer.addView(itemView);
                    }
                }
            }
        }
    }
    
    /**
     * Инициализация DrawerLayout и ActionBarDrawerToggle
     */
    private void initDrawer(Toolbar toolbar) {
        Logger.d(TAG, "initDrawer: setting up ActionBarDrawerToggle");
        
        drawerToggle = new ActionBarDrawerToggle(
                activity, drawerLayout, toolbar,
                R.string.open_drawer,
                R.string.close_drawer);
        
        drawerLayout.addDrawerListener(drawerToggle);
        
        boolean isTablet = navigationRailView != null && navigationRailView.getVisibility() == View.VISIBLE;
        drawerToggle.setDrawerIndicatorEnabled(!isTablet);
        
        drawerToggle.syncState();
        
        Logger.d(TAG, "initDrawer: drawerIndicatorEnabled=" + drawerToggle.isDrawerIndicatorEnabled());
        
        // Важно: устанавливаем слушатель на toolbar для обработки кликов
        toolbar.setNavigationOnClickListener(v -> {
            Logger.d(TAG, "Toolbar navigation clicked");
            int backStackCount = activity.getSupportFragmentManager().getBackStackEntryCount();
            Logger.d(TAG, "Back stack count: " + backStackCount);
            
            if (backStackCount > 0) {
                Logger.d(TAG, "Popping back stack");
                activity.getSupportFragmentManager().popBackStack();
            } else {
                Logger.d(TAG, "Opening drawer");
                openDrawer();
            }
        });
        
        navigationView.setNavigationItemSelectedListener(this);
    }
    
    /**
     * Инициализация View в header'е NavigationView
     */
    private void initHeaderViews() {
        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) {
            Logger.e(TAG, "NavigationView header is null");
            return;
        }
        
        drawerName = headerView.findViewById(R.id.drawer_name);
        drawerUsername = headerView.findViewById(R.id.drawer_username);
        drawerInstance = headerView.findViewById(R.id.drawer_instance);
        drawerVerifiedBadge = headerView.findViewById(R.id.drawer_verified_badge);
        drawerAvatar = headerView.findViewById(R.id.drawer_avatar);
        accountsExpandIcon = headerView.findViewById(R.id.accounts_expand_icon);
        accountsListContainer = headerView.findViewById(R.id.accounts_list_container);
        otherAccountsList = headerView.findViewById(R.id.other_accounts_list);
        addAccountButton = headerView.findViewById(R.id.add_account_button);
        currentAccountHeader = headerView.findViewById(R.id.current_account_header);
        
        // Обработчик раскрытия списка аккаунтов
        if (currentAccountHeader != null) {
            currentAccountHeader.setOnClickListener(v -> toggleAccountsList());
        }
        
        // Обработчик добавления аккаунта
        if (addAccountButton != null) {
            addAccountButton.setOnClickListener(v -> openAddAccount());
        }
        
        // Обработчик клика на аватар - открывает профиль
        if (drawerAvatar != null) {
            drawerAvatar.setOnClickListener(v -> {
                Logger.d(TAG, "Avatar clicked, navigating to profile");
                closeDrawer();
                navigateToFragmentWithBackStack(ProfileFragment.newInstance("", ""), "profile");
                activity.setToolbarTitle(activity.getString(R.string.nav_profile));
            });
        }

        // Также добавляем клик на имя пользователя
        if (drawerName != null) {
            drawerName.setOnClickListener(v -> {
                Logger.d(TAG, "Name clicked, navigating to profile");
                closeDrawer();
                navigateToFragmentWithBackStack(ProfileFragment.newInstance("", ""), "profile");
                activity.setToolbarTitle(activity.getString(R.string.nav_profile));
            });
        }

        ImageView themeToggleBtn = headerView.findViewById(R.id.theme_toggle_btn);
        if (themeToggleBtn != null) {
            ThemeManager themeManager = ThemeManager.getInstance(activity);
            boolean isDark = themeManager.isDarkMode();
            themeToggleBtn.setImageResource(isDark ? R.drawable.ic_sunny : R.drawable.ic_bedtime);

            themeToggleBtn.setOnClickListener(v -> {
                int currentMode = themeManager.getThemeMode();
                int newMode = (currentMode == ThemeManager.THEME_DARK || currentMode == ThemeManager.THEME_AMOLED)
                        ? ThemeManager.THEME_LIGHT
                        : ThemeManager.THEME_DARK;

                int[] location = new int[2];
                v.getLocationInWindow(location);
                int x = location[0] + v.getWidth() / 2;
                int y = location[1] + v.getHeight() / 2;

                Bitmap screenshot = ThemeTransitionHelper.takeScreenshot(activity);
                ThemeTransitionHelper.setTransitionData(screenshot, x, y);
                themeManager.setThemeMode(newMode);
                activity.recreate();
                activity.overridePendingTransition(0, 0);
            });
        }
    }
    
    /**
     * Обновить информацию о текущем пользователе в Drawer
     */
    public void updateUserInfo(UserProfile profile) {
        if (profile == null) {
            return;
        }
        
        if (drawerName != null) {
            drawerName.setText(profile.getFullName());
        }
        
        if (drawerUsername != null) {
            String screenName = profile.getScreenName();
            if (screenName != null && !screenName.isEmpty()) {
                drawerUsername.setText("@" + screenName);
            } else {
                drawerUsername.setText("@id" + profile.getId());
            }
        }
        
        if (drawerInstance != null) {
            AccountManager.Account currentAccount = accountManager.getCurrentAccount();
            if (currentAccount != null && currentAccount.instance != null) {
                String instance = currentAccount.instance;
                if (instance.startsWith("https://")) {
                    instance = instance.substring(8);
                } else if (instance.startsWith("http://")) {
                    instance = instance.substring(7);
                }
                drawerInstance.setText(instance);
            } else {
                drawerInstance.setText("");
            }
        }
        
        if (drawerVerifiedBadge != null) {
            drawerVerifiedBadge.setVisibility(
                    profile.isVerified() ? View.VISIBLE : View.GONE);
        }
        
        if (drawerAvatar != null && profile.getPhoto200() != null) {
            Picasso.get()
                    .load(profile.getPhoto200())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(drawerAvatar);
        }
    }
    
    /**
     * Переключить видимость списка аккаунтов
     */
    private void toggleAccountsList() {
        isAccountsListExpanded = !isAccountsListExpanded;
        
        if (accountsListContainer != null) {
            accountsListContainer.setVisibility(
                    isAccountsListExpanded ? View.VISIBLE : View.GONE);
        }
        
        if (accountsExpandIcon != null) {
            accountsExpandIcon.setRotation(isAccountsListExpanded ? 180 : 0);
        }
        
        if (isAccountsListExpanded) {
            refreshAccountsList();
        }
    }
    
    /**
     * Обновить список аккаунтов
     */
    private void refreshAccountsList() {
        if (otherAccountsList == null) {
            return;
        }
        
        otherAccountsList.removeAllViews();
        
        String currentAccountId = accountManager.getCurrentAccountId();
        List<AccountManager.Account> accounts = accountManager.getAccounts();
        for (AccountManager.Account account : accounts) {
            if (!account.id.equals(currentAccountId)) {
                addAccountToList(account);
            }
        }
    }
    
    /**
     * Добавить аккаунт в список
     */
    private void addAccountToList(AccountManager.Account account) {
        if (otherAccountsList == null || account == null) {
            return;
        }
        
        // Используем item_account.xml layout
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(activity);
        android.view.View accountView = inflater.inflate(R.layout.item_account, otherAccountsList, false);
        
        // Находим Views
        ImageView avatarView = accountView.findViewById(R.id.account_avatar);
        TextView nameView = accountView.findViewById(R.id.account_name);
        TextView instanceView = accountView.findViewById(R.id.account_instance);
        ImageView verifiedBadge = accountView.findViewById(R.id.account_verified_badge);
        
        // Устанавливаем данные
        if (nameView != null) {
            nameView.setText(account.fullName);
        }
        
        if (instanceView != null && account.instance != null) {
            String instance = account.instance;
            if (instance.startsWith("https://")) {
                instance = instance.substring(8);
            } else if (instance.startsWith("http://")) {
                instance = instance.substring(7);
            }
            instanceView.setText(instance);
        }
        
        if (verifiedBadge != null) {
            verifiedBadge.setVisibility(account.isVerified ? View.VISIBLE : View.GONE);
        }
        
        // Загружаем аватар
        if (avatarView != null && account.photoUrl != null && !account.photoUrl.isEmpty()) {
            Picasso.get()
                    .load(account.photoUrl)
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(avatarView);
        }
        
        // Обработчик клика
        accountView.setOnClickListener(v -> {
            android.util.Log.d(TAG, "Switching to account: " + account.fullName);
            switchToAccount(account);
        });
        
        otherAccountsList.addView(accountView);
    }
    
    /**
     * Переключиться на аккаунт
     */
    private void switchToAccount(AccountManager.Account account) {
        // Сохраняем токен нового аккаунта
        try {
            org.nikanikoo.flux.security.TokenManager tokenManager =
                new org.nikanikoo.flux.security.TokenManager(activity);
            tokenManager.saveToken(account.token);
            tokenManager.saveInstance(account.instance);
        } catch (org.nikanikoo.flux.security.TokenManager.EncryptionException e) {
            android.util.Log.e(TAG, "Error saving token", e);
            return;
        }
        
        // Переключаем аккаунт
        accountManager.switchToAccount(account.id);
        
        // Очищаем кэш
        org.nikanikoo.flux.data.managers.ProfileManager.getInstance(activity).clearCache();
        org.nikanikoo.flux.ui.fragments.news.NewsFragment.clearCache();
        
        // Сбрасываем OpenVKApi
        org.nikanikoo.flux.data.managers.api.OpenVKApi.resetInstance();
        
        // Перезапускаем MainActivity
        android.content.Intent intent = new android.content.Intent(activity, MainActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
    
    /**
     * Открыть экран добавления аккаунта
     */
    private void openAddAccount() {
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.putExtra("add_account", true);
        activity.startActivity(intent);
        closeDrawer();
    }
    
    /**
     * Закрыть Drawer
     */
    public void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Открыть Drawer
     */
    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }
    
    /**
     * Проверяет, открыт ли Drawer
     */
    public boolean isDrawerOpen() {
        return drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START);
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        // Предотвращаем повторное открытие текущего фрагмента
        if (id == currentFragmentId) {
            closeDrawer();
            return true;
        }
        
        Fragment fragment = null;
        String tag = null;
        
        if (id == R.id.drawer_news) {
            fragment = new NewsFragment();
            tag = "news";
            activity.setToolbarTitle(activity.getString(R.string.nav_news));
        } else if (id == R.id.drawer_messages) {
            fragment = new MessagesListFragment();
            tag = "messages";
            activity.setToolbarTitle(activity.getString(R.string.nav_messages));
        } else if (id == R.id.drawer_friends) {
            fragment = new FriendsListFragment();
            tag = "friends";
            activity.setToolbarTitle(activity.getString(R.string.nav_friends));
        } else if (id == R.id.drawer_groups) {
            fragment = new GroupsListFragment();
            tag = "groups";
            activity.setToolbarTitle(activity.getString(R.string.nav_groups));
        } else if (id == R.id.drawer_audio) {
            fragment = new MusicListFragment();
            tag = "music";
            activity.setToolbarTitle(activity.getString(R.string.nav_music));
        } else if (id == R.id.drawer_videos) {
            fragment = new VideoListFragment();
            tag = "videos";
            activity.setToolbarTitle(activity.getString(R.string.nav_videos));
        } else if (id == R.id.drawer_notification) {
            fragment = new NotificationsFragment();
            tag = "notifications";
            activity.setToolbarTitle(activity.getString(R.string.nav_notifications));
        } else if (id == R.id.drawer_notes) {
            fragment = new org.nikanikoo.flux.ui.fragments.notes.NotesFragment();
            tag = "notes";
            activity.setToolbarTitle(activity.getString(R.string.nav_notes));
        } else if (id == R.id.drawer_settings) {
            fragment = new SettingsFragment();
            tag = "settings";
            activity.setToolbarTitle(activity.getString(R.string.nav_settings));
        }
        
        if (fragment != null) {
            navigateToFragment(fragment, tag);
            setCurrentFragmentId(id);
        }
        
        closeDrawer();
        return true;
    }
    
    /**
     * Перейти к фрагменту
     */
    public void navigateToFragment(Fragment fragment, String tag) {
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        
        // Очищаем back stack при навигации из drawer
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }
    
    /**
     * Перейти к фрагменту с добавлением в back stack
     */
    public void navigateToFragmentWithBackStack(Fragment fragment, String tag) {
        Logger.d(TAG, "navigateToFragmentWithBackStack: tag=" + tag);
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .addToBackStack(tag)
                .commit();
    }

    public void restoreToolbarTitle(int id) {
        if (id == R.id.drawer_news) {
            activity.setToolbarTitle(activity.getString(R.string.nav_news));
        } else if (id == R.id.drawer_messages) {
            activity.setToolbarTitle(activity.getString(R.string.nav_messages));
        } else if (id == R.id.drawer_friends) {
            activity.setToolbarTitle(activity.getString(R.string.nav_friends));
        } else if (id == R.id.drawer_groups) {
            activity.setToolbarTitle(activity.getString(R.string.nav_groups));
        } else if (id == R.id.drawer_audio) {
            activity.setToolbarTitle(activity.getString(R.string.nav_music));
        } else if (id == R.id.drawer_videos) {
            activity.setToolbarTitle(activity.getString(R.string.nav_videos));
        } else if (id == R.id.drawer_notification) {
            activity.setToolbarTitle(activity.getString(R.string.nav_notifications));
        } else if (id == R.id.drawer_notes) {
            activity.setToolbarTitle(activity.getString(R.string.nav_notes));
        } else if (id == R.id.drawer_settings) {
            activity.setToolbarTitle(activity.getString(R.string.nav_settings));
        }
    }
    
    /**
     * Установить ID текущего фрагмента
     */
    public void setCurrentFragmentId(int id) {
        this.currentFragmentId = id;
        if (navigationView != null) {
            navigationView.setCheckedItem(id);
        }
        if (navigationRailView != null) {
            LinearLayout itemsContainer = navigationRailView.findViewById(R.id.navigation_rail_items);
            if (itemsContainer != null) {
                for (int i = 0; i < itemsContainer.getChildCount(); i++) {
                    View child = itemsContainer.getChildAt(i);
                    Object tag = child.getTag();
                    if (tag instanceof Integer) {
                        int itemId = (Integer) tag;
                        child.setSelected(itemId == id);
                    }
                }
            }
        }
    }
    
    /**
     * Получить ID текущего фрагмента
     */
    public int getCurrentFragmentId() {
        return currentFragmentId;
    }
    
    /**
     * Обработать нажатие системной кнопки Back
     */
    public boolean handleBackPress() {
        if (isDrawerOpen()) {
            closeDrawer();
            return true;
        }
        return false;
    }
    
    /**
     * Синхронизировать состояние drawer toggle
     */
    public void syncDrawerToggleState() {
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }
    
    /**
     * Обновить состояние drawer toggle в зависимости от back stack
     */
    public void updateDrawerToggleForBackStack(int backStackCount) {
        if (drawerToggle == null) {
            Logger.w(TAG, "updateDrawerToggleForBackStack: drawerToggle is NULL");
            return;
        }
        
        Logger.d(TAG, "updateDrawerToggleForBackStack: backStackCount=" + backStackCount + 
                ", current drawerIndicatorEnabled=" + drawerToggle.isDrawerIndicatorEnabled());
        
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (backStackCount > 0) {
            drawerToggle.setDrawerIndicatorEnabled(false);
            drawerToggle.setHomeAsUpIndicator(R.drawable.ic_arrow_back);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            if (toolbar != null) {
                toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
                toolbar.setNavigationOnClickListener(v -> {
                    Logger.d(TAG, "Back button clicked via toolbar listener");
                    activity.getSupportFragmentManager().popBackStack();
                });
            }
            drawerToggle.setToolbarNavigationClickListener(v -> {
                Logger.d(TAG, "Back button clicked via drawerToggle listener");
                activity.getSupportFragmentManager().popBackStack();
            });
            Logger.d(TAG, "Set drawerIndicatorEnabled=false, displayHomeAsUpEnabled=true");
        } else {
            boolean isTablet = navigationRailView != null && navigationRailView.getVisibility() == View.VISIBLE;
            drawerToggle.setDrawerIndicatorEnabled(false);
            if (!isTablet) {
                drawerToggle.setHomeAsUpIndicator(R.drawable.ic_sort);
            }
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(!isTablet);
            }
            drawerToggle.setToolbarNavigationClickListener(null);
            
            drawerToggle.syncState();
            
            if (toolbar != null) {
                if (isTablet) {
                    toolbar.setNavigationIcon(null);
                } else {
                    toolbar.setNavigationIcon(R.drawable.ic_sort);
                }
                toolbar.setNavigationOnClickListener(v -> {
                    Logger.d(TAG, "Toolbar navigation clicked");
                    int bc = activity.getSupportFragmentManager().getBackStackEntryCount();
                    if (bc > 0) {
                        activity.getSupportFragmentManager().popBackStack();
                    } else {
                        openDrawer();
                    }
                });
            }
            Logger.d(TAG, "Set drawerIndicatorEnabled=false, displayHomeAsUpEnabled=" + (!isTablet) + ", homeAsUpIndicator=ic_sort");
        }
        drawerToggle.syncState();
    }
    
    /**
     * Обработать нажатие на элемент меню options
     * @return true если событие обработано
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        Logger.d(TAG, "NavigationController.onOptionsItemSelected: itemId=" + item.getItemId());
        
        // Если drawer indicator отключен (показана стрелка), не обрабатываем через drawer toggle
        if (drawerToggle != null && drawerToggle.isDrawerIndicatorEnabled()) {
            if (drawerToggle.onOptionsItemSelected(item)) {
                Logger.d(TAG, "Handled by drawer toggle");
                return true;
            }
        }
        return false;
    }
}
