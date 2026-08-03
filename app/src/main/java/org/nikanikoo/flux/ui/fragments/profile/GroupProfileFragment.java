package org.nikanikoo.flux.ui.fragments.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.GroupsManager;
import org.nikanikoo.flux.data.managers.PostsManager;
import org.nikanikoo.flux.data.models.Group;
import org.nikanikoo.flux.data.models.Post;
import org.nikanikoo.flux.ui.activities.CreatePostActivity;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.activities.PhotoViewerActivity;
import org.nikanikoo.flux.ui.dialogs.RepostDialog;
import org.nikanikoo.flux.ui.fragments.comments.CommentsFragment;
import org.nikanikoo.flux.ui.fragments.groups.GroupMembersFragment;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Фрагмент профиля группы/сообщества.
 * Наследуется от BaseProfileFragment для использования общей логики постов и RecyclerView.
 */
public class GroupProfileFragment extends BaseProfileFragment {
    private static final String TAG = "GroupProfileFragment";
    
    // Group specific views
    private ImageView groupAvatarLarge;
    private TextView groupNameLarge;
    private ImageView groupVerified;
    private TextView groupType;
    private TextView groupStatus;
    private ProgressBar groupMainProgress;
    private View groupContent;
    private TextView membersCount;
    private TextView followersCount;
    private TextView photosCount;
    private TextView videosCount;
    private TextView audiosCount;
    private TextView topicsCount;
    private MaterialButton btnCreatePostGroup;
    private MaterialButton btnJoinLeave;
    
    // Info card views
    private LinearLayout groupCityRow;
    private TextView groupCityValue;
    private View groupDetailsRow;
    private LinearLayout membersCard;
    private LinearLayout joinLeaveContainer;
    
    private GroupsManager groupsManager;
    private Group currentGroup;
    
    private static final String ARG_GROUP_NAME = "group_name";
    private static final String ARG_GROUP_ID = "group_id";
    
    private String groupName;
    private int groupId;

    public static GroupProfileFragment newInstance(int groupId, String groupName) {
        GroupProfileFragment fragment = new GroupProfileFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_GROUP_ID, groupId);
        args.putString(ARG_GROUP_NAME, groupName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupName = getArguments().getString(ARG_GROUP_NAME);
            groupId = getArguments().getInt(ARG_GROUP_ID, -1);
        }
        groupsManager = GroupsManager.getInstance(requireContext());
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_group_profile;
    }

    @Override
    protected void initViews(View view) {
        super.initViews(view);
        
        // Initialize group specific views
        groupAvatarLarge = view.findViewById(R.id.group_avatar_large);
        groupNameLarge = view.findViewById(R.id.group_name_large);
        groupVerified = view.findViewById(R.id.group_verified_indicator);
        groupType = view.findViewById(R.id.group_type);
        groupStatus = view.findViewById(R.id.group_status);
        groupMainProgress = view.findViewById(R.id.group_main_progress);
        groupContent = view.findViewById(R.id.group_content);
        membersCount = view.findViewById(R.id.members_count);
        followersCount = view.findViewById(R.id.followers_count);
        photosCount = view.findViewById(R.id.photos_count);
        videosCount = view.findViewById(R.id.videos_count);
        audiosCount = view.findViewById(R.id.audios_count);
        topicsCount = view.findViewById(R.id.topics_count);
        btnCreatePostGroup = view.findViewById(R.id.btn_create_post_group);
        btnJoinLeave = view.findViewById(R.id.btn_join_leave);
        
        // Info card
        groupCityRow = view.findViewById(R.id.group_city_row);
        groupCityValue = view.findViewById(R.id.group_city_value);
        groupDetailsRow = view.findViewById(R.id.group_details_row);
        membersCard = view.findViewById(R.id.members_card);
        joinLeaveContainer = view.findViewById(R.id.join_leave_container);
        
        showLoadingState();
        
        // Setup click listeners
        setupClickListeners(view);
    }
    
    private void setupClickListeners(View view) {
        // Create post button
        if (btnCreatePostGroup != null) {
            btnCreatePostGroup.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), CreatePostActivity.class);
                intent.putExtra("owner_id", -groupId);
                startActivity(intent);
            });
        }
        
        // Open details sheet
        if (groupDetailsRow != null) {
            groupDetailsRow.setOnClickListener(v -> showGroupDetailsSheet());
        }
        
        // Avatar click
        if (groupAvatarLarge != null) {
            groupAvatarLarge.setOnClickListener(v -> openGroupAvatarFullScreen());
        }
        
        // Join/Leave button
        if (btnJoinLeave != null) {
            btnJoinLeave.setOnClickListener(v -> handleJoinLeave());
        }
        
        // Members card click
        if (membersCard != null) {
            membersCard.setOnClickListener(v -> {
                if (currentGroup != null) {
                    openGroupMembers();
                }
            });
        }
    }

    private void setToolbarTitleSafe(String title) {
        if (getActivity() == null) {
            return;
        }
        
        try {
            java.lang.reflect.Method method = getActivity().getClass().getMethod("setToolbarTitle", String.class);
            method.invoke(getActivity(), title);
        } catch (Exception e) {
            if (getActivity() instanceof androidx.appcompat.app.AppCompatActivity) {
                androidx.appcompat.app.AppCompatActivity appCompatActivity = 
                    (androidx.appcompat.app.AppCompatActivity) getActivity();
                if (appCompatActivity.getSupportActionBar() != null) {
                    appCompatActivity.getSupportActionBar().setTitle(title);
                }
            }
        }
    }

    @Override
    protected void loadData() {
        loadGroupData();
    }

    @Override
    protected void loadPosts(boolean isRefresh) {
        if (currentGroup == null) {
            Logger.d(TAG, "currentGroup is NULL, cannot load posts");
            return;
        }
        
        if (!paginationHelper.canLoadMore() && !isRefresh) {
            return;
        }
        
        if (isRefresh) {
            paginationHelper.reset();
        }
        
        paginationHelper.startLoading();
        
        if (!isRefresh) {
            if (postAdapter != null && postAdapter.getPostsCount() > 0) {
                postAdapter.showLoading();
            }
        }
        
        int offset = paginationHelper.getCurrentOffset();
        
        postsManager.loadWallPosts(-groupId, org.nikanikoo.flux.Constants.Api.POSTS_PER_PAGE, offset,
            new PostsManager.PostsCallback() {
                @Override
                public void onSuccess(List<Post> loadedPosts) {
                    onPostsLoaded(loadedPosts, isRefresh);
                }

                @Override
                public void onError(String error) {
                    onPostsError(error, isRefresh);
                }
            });
    }

    private void loadGroupData() {
        groupsManager.getGroupById(groupId, new GroupsManager.GroupCallback() {
            @Override
            public void onSuccess(Group group) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        currentGroup = group;
                        updateUI(group);
                        loadPosts(true);
                        swipeRefresh.setRefreshing(false);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getString(R.string.group_load_error) + error, Toast.LENGTH_SHORT).show();
                        swipeRefresh.setRefreshing(false);
                    });
                }
            }
        });
    }

    private void updateUI(Group group) {
        hideLoadingState();
        setToolbarTitleSafe(group.getName());

        if (groupNameLarge != null) {
            groupNameLarge.setText(group.getName());
        }
        
        if (groupVerified != null) {
            groupVerified.setVisibility(group.isVerified() ? View.VISIBLE : View.GONE);
        }
        
        if (groupType != null) {
            groupType.setText(group.getTypeDisplayName());
            groupType.setVisibility(View.VISIBLE);
        }
        
        if (groupStatus != null) {
            String status = group.getStatus();
            groupStatus.setText(status);
            groupStatus.setVisibility(status != null && !status.isEmpty() ? View.VISIBLE : View.GONE);
        }
        
        // Load avatar
        if (groupAvatarLarge != null && group.getPhoto200() != null && !group.getPhoto200().isEmpty()) {
            Picasso.get()
                    .load(group.getPhoto200())
                    .placeholder(R.drawable.camera_200)
                    .error(R.drawable.camera_200)
                    .into(groupAvatarLarge);
        }
        
        // Update counters
        if (membersCount != null) membersCount.setText(String.valueOf(group.getMembersCount()));
        if (followersCount != null) followersCount.setText(String.valueOf(group.getFollowersCount()));
        if (photosCount != null) photosCount.setText(String.valueOf(group.getPhotosCount()));
        if (videosCount != null) videosCount.setText(String.valueOf(group.getVideosCount()));
        if (audiosCount != null) audiosCount.setText(String.valueOf(group.getAudiosCount()));
        if (topicsCount != null) topicsCount.setText(String.valueOf(group.getTopicsCount()));
        
        updateCityRow(group);
        updateButtons(group);
    }

    private void updateCityRow(Group group) {
        if (groupCityRow == null || groupCityValue == null) {
            return;
        }
        
        String city = group.getCity();
        String country = group.getCountry();
        
        StringBuilder location = new StringBuilder();
        if (city != null && !city.isEmpty() && !"null".equals(city)) {
            location.append(city);
        }
        if (country != null && !country.isEmpty() && !"null".equals(country)) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(country);
        }
        
        if (location.length() > 0) {
            groupCityValue.setText(location.toString());
            groupCityRow.setVisibility(View.VISIBLE);
        } else {
            groupCityRow.setVisibility(View.GONE);
        }
    }

    private void updateButtons(Group group) {
        boolean showJoinLeave = !(group.isClosed() && !group.isMember());
        
        if (btnJoinLeave != null) {
            btnJoinLeave.setText(group.isMember() ? getString(R.string.group_leave) : getString(R.string.group_join));
            btnJoinLeave.setVisibility(showJoinLeave ? View.VISIBLE : View.GONE);
        }
        
        if (joinLeaveContainer != null) {
            joinLeaveContainer.setVisibility(showJoinLeave ? View.VISIBLE : View.GONE);
        }
        
        if (btnCreatePostGroup != null) {
            boolean canShowButton = group.isAdmin() || group.canPost();
            btnCreatePostGroup.setVisibility(canShowButton ? View.VISIBLE : View.GONE);
        }
    }

    private void showLoadingState() {
        if (groupMainProgress != null) groupMainProgress.setVisibility(View.VISIBLE);
        if (groupContent != null) groupContent.setVisibility(View.GONE);
    }
    
    private void hideLoadingState() {
        if (groupMainProgress != null) groupMainProgress.setVisibility(View.GONE);
        if (groupContent != null) groupContent.setVisibility(View.VISIBLE);
    }

    private void showGroupDetailsSheet() {
        if (currentGroup == null || getActivity() == null) {
            return;
        }
        
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.sheet_group_details, null);
        
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(sheetView);
        
        MaterialButton btnClose = sheetView.findViewById(R.id.btn_close_details);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        
        bindGroupDetails(sheetView, currentGroup);
        
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }
    
    private void bindGroupDetails(View sheetView, Group group) {
        if (sheetView == null || group == null) {
            return;
        }
        
        // Основная информация
        setSheetText(sheetView, R.id.sheet_row_name,
                getString(R.string.profile_name), group.getName());
        setSheetText(sheetView, R.id.sheet_row_screen_name,
                getString(R.string.profile_username), group.getScreenName(),
                value -> "@" + value);
        setSheetText(sheetView, R.id.sheet_row_id,
                getString(R.string.id), String.valueOf(group.getId()));
        
        // Контактная информация
        boolean hasContact = setSheetText(sheetView, R.id.sheet_row_city,
                getString(R.string.profile_city), group.getCity());
        hasContact |= setSheetText(sheetView, R.id.sheet_row_country,
                getString(R.string.group_country), group.getCountry());
        hasContact |= setSheetText(sheetView, R.id.sheet_row_website,
                getString(R.string.group_website), group.getWebsite());
        
        View contactTitle = sheetView.findViewById(R.id.sheet_contact_title);
        View contactCard = sheetView.findViewById(R.id.sheet_contact_card);
        if (contactTitle != null) {
            contactTitle.setVisibility(hasContact ? View.VISIBLE : View.GONE);
        }
        if (contactCard != null) {
            contactCard.setVisibility(hasContact ? View.VISIBLE : View.GONE);
        }
        
        // О группе
        boolean hasAbout = setSheetText(sheetView, R.id.sheet_row_description,
                getString(R.string.group_description), group.getDescription());
        hasAbout |= setSheetText(sheetView, R.id.sheet_row_activity,
                getString(R.string.group_activity), group.getActivity());
        
        View aboutTitle = sheetView.findViewById(R.id.sheet_about_title);
        View aboutCard = sheetView.findViewById(R.id.sheet_about_card);
        if (aboutTitle != null) {
            aboutTitle.setVisibility(hasAbout ? View.VISIBLE : View.GONE);
        }
        if (aboutCard != null) {
            aboutCard.setVisibility(hasAbout ? View.VISIBLE : View.GONE);
        }
    }
    
    private boolean setSheetText(View sheetView, int rowId, String label, String value) {
        return setSheetText(sheetView, rowId, label, value, null);
    }
    
    private boolean setSheetText(View sheetView, int rowId, String label, String value,
                                 TextFormatter formatter) {
        View row = sheetView.findViewById(rowId);
        if (row == null) {
            return false;
        }
        
        TextView labelView = row.findViewById(R.id.item_info_row_label);
        TextView valueView = row.findViewById(R.id.item_info_row_value);
        if (labelView != null) {
            labelView.setText(label);
        }
        
        if (value != null && !value.isEmpty() && !"null".equals(value)) {
            if (valueView != null) {
                valueView.setText(formatter != null ? formatter.format(value) : value);
                if (rowId == R.id.sheet_row_website) {
                    android.text.util.Linkify.addLinks(valueView, android.text.util.Linkify.WEB_URLS);
                    valueView.setMovementMethod(org.nikanikoo.flux.utils.SafeLinkMovementMethod.getInstance());
                }
            }
            row.setVisibility(View.VISIBLE);
            return true;
        }
        
        row.setVisibility(View.GONE);
        return false;
    }
    
    private interface TextFormatter {
        String format(String value);
    }

    private void handleJoinLeave() {
        if (currentGroup == null) return;
        
        if (currentGroup.isMember()) {
            groupsManager.leaveGroup(currentGroup.getId(), new GroupsManager.ActionCallback() {
                @Override
                public void onSuccess() {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            currentGroup.setMember(false);
                            updateButtons(currentGroup);
                            Toast.makeText(getContext(), getString(R.string.group_left), Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } else {
            groupsManager.joinGroup(currentGroup.getId(), new GroupsManager.ActionCallback() {
                @Override
                public void onSuccess() {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            currentGroup.setMember(true);
                            updateButtons(currentGroup);
                            Toast.makeText(getContext(), getString(R.string.group_joined), Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), getString(R.string.error_loading) + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        }
    }
    
    private void openGroupMembers() {
        if (currentGroup != null && getActivity() instanceof MainActivity) {
            GroupMembersFragment membersFragment = GroupMembersFragment.newInstance(groupId, currentGroup.getName());
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, membersFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void openGroupAvatarFullScreen() {
        if (currentGroup != null && currentGroup.getPhoto200() != null && !currentGroup.getPhoto200().isEmpty()) {
            List<String> avatarUrls = new ArrayList<>();
            avatarUrls.add(currentGroup.getPhoto200());
            
            PhotoViewerActivity.start(getContext(), avatarUrls, 0, currentGroup.getName());
        }
    }

    @Override
    public void onAuthorClick(int authorId, String authorName, boolean isGroup) {
        if (getActivity() instanceof MainActivity) {
            if (isGroup) {
                int groupId = authorId < 0 ? -authorId : authorId;
                GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(groupId, authorName);
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, groupProfileFragment)
                        .addToBackStack(null)
                        .commit();
            } else {
                ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(authorId, authorName);
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, profileFragment)
                        .addToBackStack(null)
                        .commit();
            }
        }
    }

    @Override
    public void onShareClick(Post post) {
        RepostDialog.show(requireContext(), post, (repostedPost, comment) -> {
            Logger.d(TAG, "Repost with comment: " + comment);
        });
    }

    @Override
    public void onCommentClick(Post post) {
        CommentsFragment commentsFragment = CommentsFragment.newInstance(post);
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, commentsFragment)
                    .addToBackStack("comments_" + post.getPostId())
                    .commit();
        }
    }

}
