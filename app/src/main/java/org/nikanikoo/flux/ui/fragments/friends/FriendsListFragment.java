package org.nikanikoo.flux.ui.fragments.friends;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.appcompat.widget.SearchView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.nikanikoo.flux.Constants;
import com.google.android.material.textfield.TextInputEditText;

import org.nikanikoo.flux.data.models.Friend;
import org.nikanikoo.flux.data.models.FriendRequest;
import org.nikanikoo.flux.ui.adapters.friends.FriendRequestsAdapter;
import org.nikanikoo.flux.ui.adapters.friends.FriendsAdapter;
import org.nikanikoo.flux.data.managers.FriendsManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.ui.fragments.messages.ChatFragment;
import org.nikanikoo.flux.ui.fragments.profile.ProfileFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FriendsListFragment extends BaseFragment implements FriendsAdapter.OnFriendClickListener, FriendRequestsAdapter.OnRequestActionListener {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;
    private LinearLayout emptyState;
    private TextView emptyTextTitle, emptyTextSubtitle;
    private TextInputEditText searchEditText;
    private ChipGroup filterChips;
    private Chip chipAll, chipOnline, chipRequests;
    
    private FriendsAdapter friendsAdapter;
    private FriendRequestsAdapter requestsAdapter;
    private FriendsManager friendsManager;
    private List<Friend> allFriends;
    private List<Friend> filteredFriends;
    private List<FriendRequest> friendRequests;
    private String currentSearchQuery = "";
    private boolean showOnlineOnly = false;
    private boolean showRequests = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends_list, container, false);
        
        friendsManager = FriendsManager.getInstance(requireContext());
        allFriends = new ArrayList<>();
        filteredFriends = new ArrayList<>();
        friendRequests = new ArrayList<>();
        
        initViews(view);
        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupSwipeRefresh();
        setupToolbarTitle();
        setHasOptionsMenu(true);
        setupErrorView(view, R.id.recycler_view);
        setRetryCallback(() -> {
            if (showRequests) {
                loadFriendRequests();
            } else {
                loadFriends();
            }
        });
        loadFriends();

        return view;
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(getString(R.string.friends_title));
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
        filterChips = view.findViewById(R.id.filter_chips);
        chipAll = view.findViewById(R.id.chip_all);
        chipOnline = view.findViewById(R.id.chip_online);
        chipRequests = view.findViewById(R.id.chip_requests);
    }

    private void setupRecyclerView() {
        friendsAdapter = new FriendsAdapter(getContext(), filteredFriends);
        friendsAdapter.setOnFriendClickListener(this);
        
        requestsAdapter = new FriendRequestsAdapter(getContext(), friendRequests);
        requestsAdapter.setOnRequestActionListener(this);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(friendsAdapter); // По умолчанию показываем друзей
    }

    private void setupSearch() {
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_search, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.friends_search));
                View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
                if (searchPlate != null) {
                    searchPlate.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentSearchQuery = newText.toLowerCase(Locale.ROOT).trim();
                        if (!showRequests) {
                            filterFriends();
                        }
                        return true;
                    }
                });
            }

            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    if (!currentSearchQuery.isEmpty()) {
                        currentSearchQuery = "";
                        if (!showRequests) {
                            filterFriends();
                        }
                    }
                    return true;
                }
            });
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setupFilters() {
        filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_requests)) {
                showRequests = true;
                showOnlineOnly = false;
                switchToRequests();
            } else if (checkedIds.contains(R.id.chip_online)) {
                showRequests = false;
                showOnlineOnly = true;
                switchToFriends();
                filterFriends();
            } else {
                showRequests = false;
                showOnlineOnly = false;
                switchToFriends();
                filterFriends();
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            if (showRequests) {
                loadFriendRequests();
            } else {
                loadFriends();
            }
        });
    }

    private void switchToFriends() {
        recyclerView.setAdapter(friendsAdapter);
        searchEditText.setVisibility(View.VISIBLE);
        updateEmptyStateTexts(getString(R.string.friends_not_found), getString(R.string.search_none2));
    }

    private void switchToRequests() {
        recyclerView.setAdapter(requestsAdapter);
        searchEditText.setVisibility(View.GONE);
        updateEmptyStateTexts(getString(R.string.friend_request_none), getString(R.string.friend_request_none_desc));
        loadFriendRequests();
    }

    private void updateEmptyStateTexts(String title, String subtitle) {
        emptyTextTitle.setText(title);
        emptyTextSubtitle.setText(subtitle);
    }

    private void loadFriends() {
        showLoading();
        
        friendsManager.getFriends(Constants.Api.FRIENDS_PER_PAGE, 0, new FriendsManager.FriendsCallback() {
            @Override
            public void onSuccess(List<Friend> friends) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        hideError();
                        hideLoading();
                        allFriends.clear();
                        allFriends.addAll(friends);
                        if (!showRequests) {
                            filterFriends();
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        hideLoading();
                        showErrorAuto(error);
                        updateEmptyState();
                    });
                }
            }
        });
    }

    private void loadFriendRequests() {
        showLoading();

        friendsManager.getFriendRequests(new FriendsManager.FriendRequestsCallback() {
            @Override
            public void onSuccess(List<FriendRequest> requests) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        hideError();
                        hideLoading();
                        friendRequests.clear();
                        friendRequests.addAll(requests);
                        requestsAdapter.notifyDataSetChanged();
                        updateEmptyState();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
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
                    friend.getFullName().toLowerCase(Locale.ROOT).contains(currentSearchQuery);
            boolean matchesFilter = !showOnlineOnly || friend.isOnline();
            
            if (matchesSearch && matchesFilter) {
                filteredFriends.add(friend);
            }
        }
        
        friendsAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = showRequests ? friendRequests.isEmpty() : filteredFriends.isEmpty();
        
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

    // FriendsAdapter.OnFriendClickListener
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

    // FriendRequestsAdapter.OnRequestActionListener
    @Override
    public void onAcceptRequest(FriendRequest request) {
        friendsManager.acceptFriendRequest(request.getUserId(), new FriendsManager.ActionCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        requestsAdapter.removeRequest(request);
                        Toast.makeText(getContext(), "Заявка принята", Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                        // Обновляем список друзей
                        loadFriends();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    @Override
    public void onDeclineRequest(FriendRequest request) {
        friendsManager.declineFriendRequest(request.getUserId(), new FriendsManager.ActionCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        requestsAdapter.removeRequest(request);
                        Toast.makeText(getContext(), "Заявка отклонена", Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    @Override
    public void onRequestClick(FriendRequest request) {
        ProfileFragment profileFragment = ProfileFragment.newInstanceWithId(request.getUserId(), request.getName());

        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, profileFragment)
                    .addToBackStack("request_profile")
                    .commit();
        }
    }

    public void updateUserOnlineStatus(int userId, boolean isOnline) {
        for (Friend friend : allFriends) {
            if (friend.getId() == userId) {
                friend.setOnline(isOnline);
                break;
            }
        }
        if (!showRequests) {
            filterFriends();
        }
    }

    private void showLoading() {
        boolean isEmpty = showRequests ? friendRequests.isEmpty() : filteredFriends.isEmpty();
        if (isEmpty) {
            progressLoading.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
        }
        swipeRefresh.setRefreshing(false);
    }

    private void hideLoading() {
        progressLoading.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(false);
    }
}