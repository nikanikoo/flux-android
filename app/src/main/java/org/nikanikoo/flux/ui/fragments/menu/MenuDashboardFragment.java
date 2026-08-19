package org.nikanikoo.flux.ui.fragments.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;
import org.nikanikoo.flux.utils.ThemeManager;
import org.nikanikoo.flux.utils.ThemeTransitionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MenuDashboardFragment extends Fragment {

    public static final String PREFS_NAME = "navigation_prefs";
    public static final String KEY_BOTTOM_NAV_ENABLED = "bottom_nav_enabled";
    public static final String KEY_BOTTOM_NAV_LABELS = "bottom_nav_show_labels";
    public static final String KEY_BOTTOM_NAV_ITEMS = "bottom_nav_items";
    public static final String DEFAULT_BOTTOM_NAV_ITEMS =
            "drawer_news,drawer_messages,drawer_friends";
    public static final int MAX_BOTTOM_NAV_ITEMS = 5;

    private ShapeableImageView profileAvatar;
    private TextView profileName;
    private TextView profileUsername;
    private View profileCard;

    private TextView sectionsTitle;
    private TextView emptyHint;
    private RecyclerView sectionsRecycler;
    private MenuDashboardAdapter adapter;

    public static class NavItem {
        public final String tag;
        public final int nameResId;
        public final int iconResId;

        public NavItem(String tag, int nameResId, int iconResId) {
            this.tag = tag;
            this.nameResId = nameResId;
            this.iconResId = iconResId;
        }
    }

    private final List<NavItem> allNavItems = new ArrayList<>();
    private final List<NavItem> displayedNavItems = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setupAllNavItems();
    }

    private void setupAllNavItems() {
        allNavItems.clear();
        allNavItems.add(new NavItem("drawer_news", R.string.nav_news, R.drawable.ic_newspaper));
        allNavItems.add(new NavItem("drawer_messages", R.string.nav_messages, R.drawable.ic_chat_bubble));
        allNavItems.add(new NavItem("drawer_friends", R.string.nav_friends, R.drawable.ic_contacts));
        allNavItems.add(new NavItem("drawer_groups", R.string.nav_groups, R.drawable.ic_group));
        allNavItems.add(new NavItem("drawer_photos", R.string.nav_photos, R.drawable.ic_photo));
        allNavItems.add(new NavItem("drawer_videos", R.string.nav_videos, R.drawable.ic_video_library));
        allNavItems.add(new NavItem("drawer_audio", R.string.nav_music, R.drawable.ic_library_music));
        allNavItems.add(new NavItem("drawer_notes", R.string.nav_notes, R.drawable.ic_note_stack));
        allNavItems.add(new NavItem("drawer_settings", R.string.nav_settings, R.drawable.ic_settings));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_dashboard, container, false);

        initViews(view);
        setupProfileCard();
        setupRecyclerView();
        updateCardsVisibility();
        loadUserProfile();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        ThemeManager themeManager = ThemeManager.getInstance(requireContext());
        boolean isDark = themeManager.isDarkMode();
        MenuItem themeItem = menu.add(Menu.NONE, R.id.action_theme_toggle, 0, R.string.appearance_theme_mode);
        themeItem.setIcon(isDark ? R.drawable.ic_sunny : R.drawable.ic_bedtime);
        themeItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_theme_toggle) {
            toggleThemeWithAnimation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleThemeWithAnimation() {
        if (getActivity() == null) return;
        ThemeManager themeManager = ThemeManager.getInstance(requireContext());
        int currentMode = themeManager.getThemeMode();
        int newMode = (currentMode == ThemeManager.THEME_DARK || currentMode == ThemeManager.THEME_AMOLED)
                ? ThemeManager.THEME_LIGHT
                : ThemeManager.THEME_DARK;

        Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
        int x = toolbar != null ? toolbar.getWidth() - 60 : 500;
        int y = toolbar != null ? toolbar.getHeight() / 2 : 100;

        if (toolbar != null) {
            View actionItemView = toolbar.findViewById(R.id.action_theme_toggle);
            if (actionItemView != null) {
                int[] location = new int[2];
                actionItemView.getLocationInWindow(location);
                x = location[0] + actionItemView.getWidth() / 2;
                y = location[1] + actionItemView.getHeight() / 2;
            }
        }

        Bitmap screenshot = ThemeTransitionHelper.takeScreenshot(getActivity());
        ThemeTransitionHelper.setTransitionData(screenshot, x, y);
        themeManager.setThemeMode(newMode);
        getActivity().recreate();
        getActivity().overridePendingTransition(0, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCardsVisibility();
    }

    private void initViews(View view) {
        profileAvatar = view.findViewById(R.id.menu_profile_avatar);
        profileName = view.findViewById(R.id.menu_profile_name);
        profileUsername = view.findViewById(R.id.menu_profile_username);
        profileCard = view.findViewById(R.id.menu_profile_card);
        sectionsTitle = view.findViewById(R.id.menu_sections_title);
        emptyHint = view.findViewById(R.id.menu_empty_hint);
        sectionsRecycler = view.findViewById(R.id.menu_sections_recycler);
    }

    private void setupProfileCard() {
        profileCard.setOnClickListener(v -> {
            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity != null && mainActivity.getNavigationController() != null) {
                mainActivity.getNavigationController().navigateToFragmentWithBackStack(
                        ProfileFragment.newInstance("", ""), "profile"
                );
                mainActivity.setToolbarTitle(getString(R.string.nav_profile));
            }
        });
    }

    private void setupRecyclerView() {
        sectionsRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new MenuDashboardAdapter(displayedNavItems, item -> navigateToDrawerItem(item.tag));
        sectionsRecycler.setAdapter(adapter);
    }

    private void navigateToDrawerItem(String tag) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null && mainActivity.getNavigationController() != null) {
            mainActivity.getNavigationController().navigateToDrawerItem(getDrawerIdForTag(tag));
        }
    }

    public static int getDrawerIdForTag(String tag) {
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
            default:
                return R.id.drawer_news;
        }
    }

    private void updateCardsVisibility() {
        if (!isAdded()) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedItems = prefs.getString(KEY_BOTTOM_NAV_ITEMS, DEFAULT_BOTTOM_NAV_ITEMS);

        Set<String> activeTags = new HashSet<>();
        if (savedItems != null && !savedItems.isEmpty()) {
            activeTags.addAll(Arrays.asList(savedItems.split(",")));
        }

        displayedNavItems.clear();
        for (NavItem item : allNavItems) {
            if (!activeTags.contains(item.tag)) {
                displayedNavItems.add(item);
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        boolean hasItems = !displayedNavItems.isEmpty();
        if (sectionsTitle != null) {
            sectionsTitle.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        }
        if (emptyHint != null) {
            emptyHint.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        }
    }

    private void loadUserProfile() {
        ProfileManager.getInstance(requireContext()).loadProfile(false, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateProfileUI(profile));
                }
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void updateProfileUI(UserProfile profile) {
        if (profile == null) return;

        if (profileName != null) {
            profileName.setText(profile.getFullName());
        }

        if (profileUsername != null) {
            String screenName = profile.getScreenName();
            if (screenName != null && !screenName.isEmpty()) {
                profileUsername.setText("@" + screenName);
            } else {
                profileUsername.setText("@id" + profile.getId());
            }
        }

        if (profileAvatar != null && profile.getPhoto200() != null) {
            Picasso.get()
                    .load(profile.getPhoto200())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(profileAvatar);
        }
    }

    private static class MenuDashboardAdapter extends RecyclerView.Adapter<MenuDashboardAdapter.ViewHolder> {
        private final List<NavItem> items;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(NavItem item);
        }

        MenuDashboardAdapter(List<NavItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_menu_dashboard_section, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NavItem item = items.get(position);
            holder.titleView.setText(item.nameResId);
            holder.iconView.setImageResource(item.iconResId);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView titleView;
            final ImageView iconView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleView = itemView.findViewById(R.id.section_title);
                iconView = itemView.findViewById(R.id.section_icon);
            }
        }
    }
}