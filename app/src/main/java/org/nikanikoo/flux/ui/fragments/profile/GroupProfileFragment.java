package org.nikanikoo.flux.ui.fragments.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

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
    private TextView groupType;
    private TextView groupStatus;
    private ProgressBar groupMainProgress;
    private View groupContent;
    private TextView membersCount;
    private TextView followersCount;
    private TextView photosCount;
    private TextView videosCount;
    private TextView audiosCount;
    private MaterialButton btnCreatePostGroup;
    
    // Details card views
    private CardView groupMainCard;
    private CardView groupDetailsCard;
    private ImageView expandArrow;
    private TextView groupIdText;
    private TextView groupScreenName;
    private TextView groupDescription;
    private TextView groupActivity;
    private TextView groupWebsite;
    private TextView groupCity;
    private TextView groupCountry;
    private LinearLayout screenNameLayout;
    private LinearLayout descriptionLayout;
    private LinearLayout activityLayout;
    private LinearLayout websiteLayout;
    private LinearLayout cityLayout;
    private LinearLayout countryLayout;
    private MaterialButton btnJoinLeave;
    
    private boolean isDetailsExpanded = false;
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
        groupType = view.findViewById(R.id.group_type);
        groupStatus = view.findViewById(R.id.group_status);
        groupMainProgress = view.findViewById(R.id.group_main_progress);
        groupContent = view.findViewById(R.id.group_content);
        membersCount = view.findViewById(R.id.members_count);
        followersCount = view.findViewById(R.id.followers_count);
        photosCount = view.findViewById(R.id.photos_count);
        videosCount = view.findViewById(R.id.videos_count);
        audiosCount = view.findViewById(R.id.audios_count);
        btnCreatePostGroup = view.findViewById(R.id.btn_create_post_group);
        
        // Details card
        groupMainCard = view.findViewById(R.id.group_main_card);
        groupDetailsCard = view.findViewById(R.id.group_details_card);
        expandArrow = view.findViewById(R.id.expand_arrow);
        groupIdText = view.findViewById(R.id.group_id);
        groupScreenName = view.findViewById(R.id.group_screen_name);
        groupDescription = view.findViewById(R.id.group_description);
        groupActivity = view.findViewById(R.id.group_activity);
        groupWebsite = view.findViewById(R.id.group_website);
        groupCity = view.findViewById(R.id.group_city);
        groupCountry = view.findViewById(R.id.group_country);
        screenNameLayout = view.findViewById(R.id.screen_name_layout);
        descriptionLayout = view.findViewById(R.id.description_layout);
        activityLayout = view.findViewById(R.id.activity_layout);
        websiteLayout = view.findViewById(R.id.website_layout);
        cityLayout = view.findViewById(R.id.city_layout);
        countryLayout = view.findViewById(R.id.country_layout);
        btnJoinLeave = view.findViewById(R.id.btn_join_leave);
        
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
        
        // Expand details card
        if (groupMainCard != null) {
            groupMainCard.setOnClickListener(v -> toggleDetailsCard());
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
        CardView membersCard = view.findViewById(R.id.members_card);
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
        
        updateDetailedInfo(group);
        updateButtons(group);
    }

    private void updateDetailedInfo(Group group) {
        if (groupIdText != null) {
            groupIdText.setText(getString(R.string.id) + group.getId());
        }
        
        if (groupScreenName != null && screenNameLayout != null) {
            if (group.getScreenName() != null && !group.getScreenName().isEmpty()) {
                groupScreenName.setText(group.getScreenName());
                screenNameLayout.setVisibility(View.VISIBLE);
            } else {
                screenNameLayout.setVisibility(View.GONE);
            }
        }
        
        if (groupDescription != null && descriptionLayout != null) {
            if (group.getDescription() != null && !group.getDescription().isEmpty()) {
                groupDescription.setText(group.getDescription());
                descriptionLayout.setVisibility(View.VISIBLE);
            } else {
                descriptionLayout.setVisibility(View.GONE);
            }
        }
        
        if (groupActivity != null && activityLayout != null) {
            if (group.getActivity() != null && !group.getActivity().isEmpty()) {
                groupActivity.setText(group.getActivity());
                activityLayout.setVisibility(View.VISIBLE);
            } else {
                activityLayout.setVisibility(View.GONE);
            }
        }
        
        if (groupWebsite != null && websiteLayout != null) {
            if (group.getWebsite() != null && !group.getWebsite().isEmpty()) {
                groupWebsite.setText(group.getWebsite());
                websiteLayout.setVisibility(View.VISIBLE);
            } else {
                websiteLayout.setVisibility(View.GONE);
            }
        }
        
        if (groupCity != null && cityLayout != null) {
            if (group.getCity() != null && !group.getCity().isEmpty()) {
                groupCity.setText(group.getCity());
                cityLayout.setVisibility(View.VISIBLE);
            } else {
                cityLayout.setVisibility(View.GONE);
            }
        }
        
        if (groupCountry != null && countryLayout != null) {
            if (group.getCountry() != null && !group.getCountry().isEmpty()) {
                groupCountry.setText(group.getCountry());
                countryLayout.setVisibility(View.VISIBLE);
            } else {
                countryLayout.setVisibility(View.GONE);
            }
        }
    }

    private void updateButtons(Group group) {
        if (btnJoinLeave != null) {
            btnJoinLeave.setText(group.isMember() ? getString(R.string.group_leave) : getString(R.string.group_join));
            btnJoinLeave.setVisibility(group.isClosed() && !group.isMember() ? View.GONE : View.VISIBLE);
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

    private void toggleDetailsCard() {
        if (groupDetailsCard == null || expandArrow == null) return;
        
        if (isDetailsExpanded) {
            groupDetailsCard.setVisibility(View.GONE);
            expandArrow.setImageResource(R.drawable.ic_keyboard_arrow_down);
            isDetailsExpanded = false;
        } else {
            groupDetailsCard.setVisibility(View.VISIBLE);
            expandArrow.setImageResource(R.drawable.ic_keyboard_arrow_up);
            isDetailsExpanded = true;
        }
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
