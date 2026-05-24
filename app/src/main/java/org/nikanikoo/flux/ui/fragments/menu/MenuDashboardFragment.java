package org.nikanikoo.flux.ui.fragments.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.activities.NavigationController;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MenuDashboardFragment extends Fragment {

    private ShapeableImageView profileAvatar;
    private TextView profileName;
    private TextView profileUsername;
    private View profileCard;

    private static class ToggleableNavItem {
        final String tag;
        final int nameResId;
        final int iconResId;
        final int drawerId;

        ToggleableNavItem(String tag, int nameResId, int iconResId, int drawerId) {
            this.tag = tag;
            this.nameResId = nameResId;
            this.iconResId = iconResId;
            this.drawerId = drawerId;
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
        navItems.add(new ToggleableNavItem("drawer_news", R.string.nav_news, R.drawable.ic_newspaper, R.id.drawer_news));
        navItems.add(new ToggleableNavItem("drawer_messages", R.string.nav_messages, R.drawable.ic_chat_bubble, R.id.drawer_messages));
        navItems.add(new ToggleableNavItem("drawer_friends", R.string.nav_friends, R.drawable.ic_contacts, R.id.drawer_friends));
        navItems.add(new ToggleableNavItem("drawer_groups", R.string.nav_groups, R.drawable.ic_group, R.id.drawer_groups));
        navItems.add(new ToggleableNavItem("drawer_photos", R.string.nav_photos, R.drawable.ic_photo, R.id.drawer_photos));
        navItems.add(new ToggleableNavItem("drawer_videos", R.string.nav_videos, R.drawable.ic_video_library, R.id.drawer_videos));
        navItems.add(new ToggleableNavItem("drawer_audio", R.string.nav_music, R.drawable.ic_library_music, R.id.drawer_audio));
        navItems.add(new ToggleableNavItem("drawer_notes", R.string.nav_notes, R.drawable.ic_note_stack, R.id.drawer_notes));
        navItems.add(new ToggleableNavItem("drawer_notification", R.string.nav_notifications, R.drawable.ic_notifications, R.id.drawer_notification));
        navItems.add(new ToggleableNavItem("drawer_settings", R.string.nav_settings, R.drawable.ic_settings, R.id.drawer_settings));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_dashboard, container, false);

        initViews(view);
        setupNavigationClicks(view);
        setupNavigationSettings(view);
        loadUserProfile();

        return view;
    }

    private void initViews(View view) {
        profileAvatar = view.findViewById(R.id.menu_profile_avatar);
        profileName = view.findViewById(R.id.menu_profile_name);
        profileUsername = view.findViewById(R.id.menu_profile_username);
        profileCard = view.findViewById(R.id.menu_profile_card);
    }

    private void setupNavigationClicks(View view) {
        profileCard.setOnClickListener(v -> {
            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity != null && mainActivity.getNavigationController() != null) {
                mainActivity.getNavigationController().navigateToFragmentWithBackStack(
                        ProfileFragment.newInstance("", ""), "profile"
                );
                mainActivity.setToolbarTitle(getString(R.string.nav_profile));
            }
        });

        view.findViewById(R.id.card_news).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_news));
        view.findViewById(R.id.card_messages).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_messages));
        view.findViewById(R.id.card_friends).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_friends));
        view.findViewById(R.id.card_groups).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_groups));
        view.findViewById(R.id.card_photos).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_photos));
        view.findViewById(R.id.card_videos).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_videos));
        view.findViewById(R.id.card_music).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_audio));
        view.findViewById(R.id.card_notes).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_notes));
        view.findViewById(R.id.card_notifications).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_notification));
        view.findViewById(R.id.card_settings).setOnClickListener(v -> navigateToDrawerItem(R.id.drawer_settings));
    }

    private void navigateToDrawerItem(int drawerId) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null && mainActivity.getNavigationController() != null) {
            mainActivity.getNavigationController().navigateToDrawerItem(drawerId);
        }
    }

    private void setupNavigationSettings(View view) {
        SharedPreferences prefs = requireContext().getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE);
        
        SwitchMaterial switchBottomNavMode = view.findViewById(R.id.switch_bottom_nav_mode);
        SwitchMaterial switchBottomNavLabels = view.findViewById(R.id.switch_bottom_nav_labels);
        
        View layoutShowLabels = view.findViewById(R.id.layout_show_labels);
        View dividerCustomizer = view.findViewById(R.id.divider_customizer);
        View txtChecklistTitle = view.findViewById(R.id.txt_checklist_title);
        LinearLayout checklistContainer = view.findViewById(R.id.checklist_container);

        boolean isBottomNavEnabled = prefs.getBoolean("bottom_nav_enabled", false);
        switchBottomNavMode.setChecked(isBottomNavEnabled);

        int visibility = isBottomNavEnabled ? View.VISIBLE : View.GONE;
        layoutShowLabels.setVisibility(visibility);
        dividerCustomizer.setVisibility(visibility);
        txtChecklistTitle.setVisibility(visibility);
        checklistContainer.setVisibility(visibility);

        switchBottomNavMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("bottom_nav_enabled", isChecked).apply();
            
            int newVisibility = isChecked ? View.VISIBLE : View.GONE;
            layoutShowLabels.setVisibility(newVisibility);
            dividerCustomizer.setVisibility(newVisibility);
            txtChecklistTitle.setVisibility(newVisibility);
            checklistContainer.setVisibility(newVisibility);

            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity != null) {
                mainActivity.updateNavigationMode();
            }
        });

        switchBottomNavLabels.setChecked(prefs.getBoolean("bottom_nav_show_labels", true));
        switchBottomNavLabels.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("bottom_nav_show_labels", isChecked).apply();
            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity != null) {
                mainActivity.refreshBottomNavigation();
            }
        });

        checklistContainer.removeAllViews();
        String savedItems = prefs.getString("bottom_nav_items", "drawer_news,drawer_messages,drawer_friends,drawer_notification");
        Set<String> activeTags = new HashSet<>(Arrays.asList(savedItems.split(",")));

        for (ToggleableNavItem item : navItems) {
            SwitchMaterial sw = new SwitchMaterial(requireContext());
            sw.setText(getString(item.nameResId));
            sw.setChecked(activeTags.contains(item.tag));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 4, 0, 4);
            sw.setLayoutParams(lp);

            sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    activeTags.add(item.tag);
                } else {
                    activeTags.remove(item.tag);
                }

                StringBuilder sb = new StringBuilder();
                for (ToggleableNavItem ni : navItems) {
                    if (activeTags.contains(ni.tag)) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(ni.tag);
                    }
                }

                prefs.edit().putString("bottom_nav_items", sb.toString()).apply();

                MainActivity mainActivity = (MainActivity) getActivity();
                if (mainActivity != null) {
                    mainActivity.refreshBottomNavigation();
                }
            });

            checklistContainer.addView(sw);
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
