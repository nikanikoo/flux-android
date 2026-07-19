package org.nikanikoo.flux.ui.fragments.groups;

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
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.nikanikoo.flux.data.models.Group;
import org.nikanikoo.flux.ui.adapters.groups.GroupsAdapter;
import org.nikanikoo.flux.data.managers.GroupsManager;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.ui.fragments.profile.GroupProfileFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GroupsListFragment extends BaseFragment implements GroupsAdapter.OnGroupClickListener {
    
    private RecyclerView recyclerView;
    private GroupsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextInputEditText searchEditText;
    private ChipGroup filterChips;
    private Chip chipAll, chipAdmin;
    private LinearLayout emptyState;
    
    private GroupsManager groupsManager;
    private List<Group> allGroups;
    private List<Group> filteredGroups;
    private String currentSearchQuery = "";
    private boolean showAdminOnly = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups_list, container, false);
        
        initViews(view);
        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupToolbarTitle();
        setHasOptionsMenu(true);
        setupErrorView(view, R.id.recycler_view);
        setRetryCallback(() -> loadGroups());
        loadGroups();

        return view;
    }
    
    private void setupToolbarTitle() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.setToolbarTitle(getString(R.string.groups_title));
        }
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        searchEditText = view.findViewById(R.id.search_edit_text);
        filterChips = view.findViewById(R.id.filter_chips);
        chipAll = view.findViewById(R.id.chip_all);
        chipAdmin = view.findViewById(R.id.chip_admin);
        emptyState = view.findViewById(R.id.empty_state);
        
        System.out.println("GroupsListFragment: Views initialized:");
        System.out.println("  recyclerView: " + (recyclerView != null ? "OK" : "NULL"));
        System.out.println("  swipeRefreshLayout: " + (swipeRefreshLayout != null ? "OK" : "NULL"));
        System.out.println("  emptyState: " + (emptyState != null ? "OK" : "NULL"));
        
        groupsManager = GroupsManager.getInstance(requireContext());
        allGroups = new ArrayList<>();
        filteredGroups = new ArrayList<>();
        
        swipeRefreshLayout.setOnRefreshListener(this::loadGroups);
    }

    private void setupRecyclerView() {
        System.out.println("GroupsListFragment: Setting up RecyclerView");
        adapter = new GroupsAdapter(requireContext(), new ArrayList<>());
        adapter.setOnGroupClickListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        System.out.println("GroupsListFragment: RecyclerView setup complete");
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
                searchView.setQueryHint(getString(R.string.groups_search));
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
                        filterGroups();
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
                        filterGroups();
                    }
                    return true;
                }
            });
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void setupFilters() {
        filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_admin)) {
                showAdminOnly = true;
            } else {
                showAdminOnly = false;
            }
            filterGroups();
        });
    }

    private void loadGroups() {
        System.out.println("GroupsListFragment: Loading groups...");
        swipeRefreshLayout.setRefreshing(true);
        
        groupsManager.getGroups(50, 0, new GroupsManager.GroupsCallback() {
            @Override
            public void onSuccess(List<Group> groups) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        hideError();
                        System.out.println("GroupsListFragment: Received " + groups.size() + " groups");
                        allGroups.clear();
                        allGroups.addAll(groups);
                        filterGroups();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        System.err.println("GroupsListFragment: Error loading groups: " + error);
                        showErrorAuto(error);
                        swipeRefreshLayout.setRefreshing(false);
                        updateEmptyState();
                    });
                }
            }
        });
    }

    private void filterGroups() {
        filteredGroups.clear();
        
        for (Group group : allGroups) {
            boolean matchesSearch = currentSearchQuery.isEmpty() || 
                    group.getName().toLowerCase(Locale.ROOT).contains(currentSearchQuery) ||
                    (group.getDescription() != null && group.getDescription().toLowerCase(Locale.ROOT).contains(currentSearchQuery));
            
            boolean matchesFilter = !showAdminOnly || group.isAdmin();
            
            if (matchesSearch && matchesFilter) {
                filteredGroups.add(group);
            }
        }
        
        System.out.println("GroupsListFragment: Filtered to " + filteredGroups.size() + " groups");
        adapter.updateGroups(filteredGroups);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredGroups.isEmpty()) {
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
    public void onGroupClick(Group group) {
        System.out.println("GroupsListFragment: Group clicked: " + group.getName() + " (ID: " + group.getId() + ")");
        
        // Открываем профиль группы
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            GroupProfileFragment groupProfileFragment = GroupProfileFragment.newInstance(group.getId(), group.getName());
            
            mainActivity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, groupProfileFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.onDestroy();
        }
    }
}