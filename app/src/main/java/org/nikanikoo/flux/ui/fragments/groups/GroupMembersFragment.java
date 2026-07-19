package org.nikanikoo.flux.ui.fragments.groups;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;

import org.nikanikoo.flux.Constants;
import org.nikanikoo.flux.data.models.Friend;
import org.nikanikoo.flux.ui.adapters.friends.FriendsAdapter;
import org.nikanikoo.flux.data.managers.GroupsManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.messages.ChatFragment;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupMembersFragment extends org.nikanikoo.flux.ui.fragments.BaseFragment implements FriendsAdapter.OnFriendClickListener {

    private static final String ARG_GROUP_ID = "group_id";
    private static final String ARG_GROUP_NAME = "group_name";

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;
    private LinearLayout emptyState;
    private TextView emptyTextTitle, emptyTextSubtitle;
    private TextInputEditText searchEditText;
    
    private FriendsAdapter membersAdapter;
    private GroupsManager groupsManager;
    private List<Friend> allMembers;
    private List<Friend> filteredMembers;
    private String currentSearchQuery = "";
    
    private LinearLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;
    
    private int groupId;
    private String groupName;

    public static GroupMembersFragment newInstance(int groupId, String groupName) {
        GroupMembersFragment fragment = new GroupMembersFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_GROUP_ID, groupId);
        args.putString(ARG_GROUP_NAME, groupName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getInt(ARG_GROUP_ID);
            groupName = getArguments().getString(ARG_GROUP_NAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_members, container, false);
        
        groupsManager = GroupsManager.getInstance(requireContext());
        allMembers = new ArrayList<>();
        filteredMembers = new ArrayList<>();
        
        initViews(view);
        setupRecyclerView();
        setupEndlessScroll();
        setupSearch();
        setupSwipeRefresh();
        setupToolbarTitle();
        setupErrorView(view, R.id.recycler_view);
        setRetryCallback(() -> loadMembers(true));
        loadMembers(true);
        
        return view;
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(getString(R.string.group_members2) + (groupName != null ? groupName : ""));
        }
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressLoading = view.findViewById(R.id.progress_loading);
        emptyState = view.findViewById(R.id.empty_state);
        emptyTextTitle = view.findViewById(R.id.empty_text_title);
        emptyTextSubtitle = view.findViewById(R.id.empty_text_subtitle);
        searchEditText = view.findViewById(R.id.search_edit_text);
    }

    private void setupRecyclerView() {
        membersAdapter = new FriendsAdapter(getContext(), filteredMembers);
        membersAdapter.setOnFriendClickListener(this);
        
        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(membersAdapter);
    }
    
    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(Constants.Api.FRIENDS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                if (currentSearchQuery.isEmpty()) {
                    loadMembers(false);
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase().trim();
                filterMembers();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            scrollListener.resetState();
            loadMembers(true);
        });
    }

    private void loadMembers(boolean isRefresh) {
        if (!paginationHelper.canLoadMore() && !isRefresh) {
            return;
        }
        
        showLoading(isRefresh);
        
        int offset = isRefresh ? 0 : paginationHelper.getCurrentOffset();
        
        Map<String, String> params = new HashMap<>();
        params.put("group_id", String.valueOf(groupId));
        params.put("count", String.valueOf(Constants.Api.FRIENDS_PER_PAGE));
        params.put("offset", String.valueOf(offset));
        params.put("fields", "photo_50,photo_100,online,screen_name,status");
        params.put("sort", "id_asc");
        
        // Получаем API напрямую
        org.nikanikoo.flux.data.managers.api.OpenVKApi api =
            org.nikanikoo.flux.data.managers.api.OpenVKApi.getInstance(requireContext());
        
        api.callMethod("groups.getMembers", params, new org.nikanikoo.flux.data.managers.api.OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                try {
                    org.json.JSONObject responseObj = response.getJSONObject("response");
                    org.json.JSONArray items = responseObj.getJSONArray("items");
                    
                    List<Friend> members = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        org.json.JSONObject memberJson = items.getJSONObject(i);
                        Friend member = Friend.fromJson(memberJson);
                        members.add(member);
                    }
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            paginationHelper.onDataLoaded(members.size());
                            hideLoading();
                            
                            if (isRefresh) {
                                allMembers.clear();
                            }
                            allMembers.addAll(members);
                            filterMembers();
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            paginationHelper.stopLoading();
                            hideLoading();
                            showErrorAuto(e.getMessage());
                            updateEmptyState();
                        });
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        paginationHelper.stopLoading();
                        hideLoading();
                        showErrorAuto(error);
                        updateEmptyState();
                    });
                }
            }
        });
    }

    private void filterMembers() {
        filteredMembers.clear();
        
        for (Friend member : allMembers) {
            boolean matchesSearch = currentSearchQuery.isEmpty() || 
                    member.getFullName().toLowerCase().contains(currentSearchQuery);
            
            if (matchesSearch) {
                filteredMembers.add(member);
            }
        }
        
        membersAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = filteredMembers.isEmpty();
        
        if (isEmpty) {
            recyclerView.setVisibility(View.GONE);
            if (!currentSearchQuery.isEmpty()) {
                showError(org.nikanikoo.flux.utils.ErrorViewHandler.ErrorType.EMPTY_SEARCH);
            } else {
                showError(org.nikanikoo.flux.utils.ErrorViewHandler.ErrorType.EMPTY_STATE);
            }
        } else {
            hideError();
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onFriendClick(Friend member) {
        ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(member.getId(), member.getFullName());
        
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, profileFragment)
                    .addToBackStack("member_profile")
                    .commit();
        }
    }

    @Override
    public void onMessageClick(Friend member) {
        ChatFragment chatFragment = ChatFragment.newInstance(member.getId(), member.getFullName());
        
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, chatFragment)
                    .addToBackStack("member_chat")
                    .commit();
        }
    }

    private void showLoading(boolean isRefresh) {
        boolean isEmpty = filteredMembers.isEmpty();
        if (isEmpty && isRefresh) {
            progressLoading.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
        }
        if (isRefresh) {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void hideLoading() {
        progressLoading.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(false);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (recyclerView != null && scrollListener != null) {
            recyclerView.removeOnScrollListener(scrollListener);
        }
    }
}
