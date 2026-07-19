package org.nikanikoo.flux.ui.fragments.friends;

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
import org.nikanikoo.flux.data.managers.FriendsManager;
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

public class UserFriendsFragment extends org.nikanikoo.flux.ui.fragments.BaseFragment implements FriendsAdapter.OnFriendClickListener {

    private static final String ARG_USER_ID = "user_id";
    private static final String ARG_USER_NAME = "user_name";

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;
    private LinearLayout emptyState;
    private TextView emptyTextTitle, emptyTextSubtitle;
    private TextInputEditText searchEditText;
    
    private FriendsAdapter friendsAdapter;
    private FriendsManager friendsManager;
    private List<Friend> allFriends;
    private List<Friend> filteredFriends;
    private String currentSearchQuery = "";
    
    private LinearLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;
    
    private int userId;
    private String userName;

    public static UserFriendsFragment newInstance(int userId, String userName) {
        UserFriendsFragment fragment = new UserFriendsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_USER_ID, userId);
        args.putString(ARG_USER_NAME, userName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getInt(ARG_USER_ID);
            userName = getArguments().getString(ARG_USER_NAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_friends, container, false);
        
        friendsManager = FriendsManager.getInstance(requireContext());
        allFriends = new ArrayList<>();
        filteredFriends = new ArrayList<>();
        
        initViews(view);
        setupRecyclerView();
        setupEndlessScroll();
        setupSearch();
        setupSwipeRefresh();
        setupToolbarTitle();
        setupErrorView(view, R.id.recycler_view);
        setRetryCallback(() -> loadFriends(true));
        loadFriends(true);
        
        return view;
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(getString(R.string.friends_title2) + (userName != null ? userName : ""));
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
        friendsAdapter = new FriendsAdapter(getContext(), filteredFriends);
        friendsAdapter.setOnFriendClickListener(this);
        
        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(friendsAdapter);
    }
    
    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(Constants.Api.FRIENDS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                if (currentSearchQuery.isEmpty()) {
                    loadFriends(false);
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
                filterFriends();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            scrollListener.resetState();
            loadFriends(true);
        });
    }

    private void loadFriends(boolean isRefresh) {
        if (!paginationHelper.canLoadMore() && !isRefresh) {
            return;
        }
        
        showLoading(isRefresh);
        
        int offset = isRefresh ? 0 : paginationHelper.getCurrentOffset();
        
        // Используем прямой вызов через FriendsManager с параметром user_id
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(userId));
        params.put("count", String.valueOf(Constants.Api.FRIENDS_PER_PAGE));
        params.put("offset", String.valueOf(offset));
        params.put("fields", "photo_50,photo_100,online,screen_name,status");
        params.put("order", "name");
        
        // Получаем API через рефлексию или создаем метод в FriendsManager
        org.nikanikoo.flux.data.managers.api.OpenVKApi api =
            org.nikanikoo.flux.data.managers.api.OpenVKApi.getInstance(requireContext());
        
        api.callMethod("friends.get", params, new org.nikanikoo.flux.data.managers.api.OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                try {
                    org.json.JSONObject responseObj = response.getJSONObject("response");
                    org.json.JSONArray items;
                    
                    if (responseObj.has("items")) {
                        items = responseObj.getJSONArray("items");
                    } else {
                        items = response.getJSONArray("response");
                    }
                    
                    List<Friend> friends = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        org.json.JSONObject friendJson = items.getJSONObject(i);
                        Friend friend = Friend.fromJson(friendJson);
                        friends.add(friend);
                    }
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            paginationHelper.onDataLoaded(friends.size());
                            hideLoading();
                            
                            if (isRefresh) {
                                allFriends.clear();
                            }
                            allFriends.addAll(friends);
                            filterFriends();
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

    private void filterFriends() {
        filteredFriends.clear();
        
        for (Friend friend : allFriends) {
            boolean matchesSearch = currentSearchQuery.isEmpty() || 
                    friend.getFullName().toLowerCase().contains(currentSearchQuery);
            
            if (matchesSearch) {
                filteredFriends.add(friend);
            }
        }
        
        friendsAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = filteredFriends.isEmpty();
        
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
    public void onFriendClick(Friend friend) {
        ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(friend.getId(), friend.getFullName());
        
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, profileFragment)
                    .addToBackStack("friend_profile")
                    .commit();
        }
    }

    @Override
    public void onMessageClick(Friend friend) {
        ChatFragment chatFragment = ChatFragment.newInstance(friend.getId(), friend.getFullName());
        
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, chatFragment)
                    .addToBackStack("friend_chat")
                    .commit();
        }
    }

    private void showLoading(boolean isRefresh) {
        boolean isEmpty = filteredFriends.isEmpty();
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
