package org.nikanikoo.flux.ui.fragments.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;

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
            "drawer_news,drawer_messages,drawer_friends,drawer_notification";
    public static final int MAX_BOTTOM_NAV_ITEMS = 5;

    private ShapeableImageView profileAvatar;
    private TextView profileName;
    private TextView profileUsername;
    private View profileCard;

    private TextView sectionsTitle;
    private TextView emptyHint;

    private static class ToggleableNavItem {
        final String tag;
        final int nameResId;
        final int cardResId;

        ToggleableNavItem(String tag, int nameResId, int cardResId) {
            this.tag = tag;
            this.nameResId = nameResId;
            this.cardResId = cardResId;
        }
    }

    private final List<ToggleableNavItem> navItems = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupNavItemsList();
    }

    private void setupNavItemsList() {
        navItems.clear();
        navItems.add(new ToggleableNavItem("drawer_news", R.string.nav_news, R.id.card_news));
        navItems.add(new ToggleableNavItem("drawer_notification", R.string.nav_notifications, R.id.card_notifications));
        navItems.add(new ToggleableNavItem("drawer_friends", R.string.nav_friends, R.id.card_friends));
        navItems.add(new ToggleableNavItem("drawer_photos", R.string.nav_photos, R.id.card_photos));
        navItems.add(new ToggleableNavItem("drawer_videos", R.string.nav_videos, R.id.card_videos));
        navItems.add(new ToggleableNavItem("drawer_audio", R.string.nav_music, R.id.card_music));
        navItems.add(new ToggleableNavItem("drawer_messages", R.string.nav_messages, R.id.card_messages));
        navItems.add(new ToggleableNavItem("drawer_groups", R.string.nav_groups, R.id.card_groups));
        navItems.add(new ToggleableNavItem("drawer_notes", R.string.nav_notes, R.id.card_notes));
        navItems.add(new ToggleableNavItem("drawer_settings", R.string.nav_settings, R.id.card_settings));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_dashboard, container, false);

        initViews(view);
        setupProfileCard();
        setupNavigationClicks(view);
        updateCardsVisibility();
        loadUserProfile();

        return view;
    }

    private void initViews(View view) {
        profileAvatar = view.findViewById(R.id.menu_profile_avatar);
        profileName = view.findViewById(R.id.menu_profile_name);
        profileUsername = view.findViewById(R.id.menu_profile_username);
        profileCard = view.findViewById(R.id.menu_profile_card);
        sectionsTitle = view.findViewById(R.id.menu_sections_title);
        emptyHint = view.findViewById(R.id.menu_empty_hint);
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

    private void setupNavigationClicks(View view) {
        for (ToggleableNavItem item : navItems) {
            View card = view.findViewById(item.cardResId);
            if (card != null) {
                card.setOnClickListener(v -> navigateToDrawerItem(item.tag));
            }
        }
    }

    private void navigateToDrawerItem(String tag) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null && mainActivity.getNavigationController() != null) {
            mainActivity.getNavigationController().navigateToDrawerItem(getDrawerIdForTag(tag));
        }
    }

    private static int getDrawerIdForTag(String tag) {
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
            default:
                return R.id.drawer_news;
        }
    }

    private void updateCardsVisibility() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> activeTags = new HashSet<>(Arrays.asList(
                prefs.getString(KEY_BOTTOM_NAV_ITEMS, DEFAULT_BOTTOM_NAV_ITEMS).split(",")));

        int visibleCount = 0;
        View view = getView();
        if (view == null) return;

        for (ToggleableNavItem item : navItems) {
            View card = view.findViewById(item.cardResId);
            if (card == null) continue;
            boolean onBottomBar = activeTags.contains(item.tag);
            card.setVisibility(onBottomBar ? View.GONE : View.VISIBLE);
            if (!onBottomBar) {
                visibleCount++;
            }
        }

        if (sectionsTitle != null) {
            sectionsTitle.setVisibility(visibleCount > 0 ? View.VISIBLE : View.GONE);
        }
        if (emptyHint != null) {
            emptyHint.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
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
}