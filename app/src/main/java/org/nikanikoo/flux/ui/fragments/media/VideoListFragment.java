package org.nikanikoo.flux.ui.fragments.media;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.managers.VideoManager;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.data.models.Video;
import org.nikanikoo.flux.ui.activities.VideoPlayerActivity;
import org.nikanikoo.flux.ui.adapters.video.VideoAdapter;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class VideoListFragment extends BaseFragment implements VideoAdapter.OnVideoClickListener {

    private static final String TAG = "VideoListFragment";
    private static final int VIDEOS_PER_PAGE = 20;

    private RecyclerView recyclerVideos;
    private VideoAdapter videoAdapter;
    private VideoManager videoManager;
    private ProfileManager profileManager;
    private List<Video> videos;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;
    private LinearLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;
    private boolean isSearchMode = false;
    private String currentSearchQuery = "";
    private int currentUserId = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_list, container, false);
        
        setHasOptionsMenu(true);
        
        videoManager = VideoManager.getInstance(requireContext());
        profileManager = ProfileManager.getInstance(requireContext());
        videos = new ArrayList<>();
        
        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupEndlessScroll();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(() -> {
            if (isSearchMode) {
                searchVideos(currentSearchQuery, true);
            } else {
                loadVideos(true);
            }
        });
        loadUserProfile();

        return view;
    }

    private void initViews(View view) {
        recyclerVideos = view.findViewById(R.id.recycler_videos);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressLoading = view.findViewById(R.id.progress_loading);
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerVideos.setLayoutManager(layoutManager);
        
        videoAdapter = new VideoAdapter(videos, this);
        recyclerVideos.setAdapter(videoAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            scrollListener.resetState();
            videos.clear();
            videoAdapter.notifyDataSetChanged();
            if (isSearchMode) {
                searchVideos(currentSearchQuery, true);
            } else {
                loadVideos(true);
            }
        });
    }

    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(VIDEOS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                if (isSearchMode) {
                    searchVideos(currentSearchQuery, false);
                } else {
                    loadVideos(false);
                }
            }
        };
        recyclerVideos.addOnScrollListener(scrollListener);
    }

    private void loadUserProfile() {
        profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                currentUserId = profile.getId();
                Logger.d(TAG, "User ID loaded: " + currentUserId);
                loadVideos(true);
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error loading profile: " + error);
                showErrorAuto(error);
            }
        });
    }

    private void loadVideos(boolean isRefresh) {
        if (!paginationHelper.canLoadMore() && !isRefresh) {
            return;
        }
        
        if (isRefresh) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        int offset = isRefresh ? 0 : paginationHelper.getCurrentOffset();
        
        videoManager.getVideos(currentUserId, offset, VIDEOS_PER_PAGE, new VideoManager.VideoCallback() {
            @Override
            public void onSuccess(List<Video> loadedVideos, int totalCount) {
                paginationHelper.onDataLoaded(loadedVideos.size());
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                hideError();

                if (isRefresh) {
                    videos.clear();
                }

                videos.addAll(loadedVideos);
                videoAdapter.notifyDataSetChanged();

                Logger.d(TAG, "Loaded " + loadedVideos.size() + " videos, total: " + videos.size());
            }

            @Override
            public void onError(String error) {
                paginationHelper.stopLoading();
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showErrorAuto(error);
            }
        });
    }

    private void searchVideos(String query, boolean isRefresh) {
        if (query.trim().isEmpty()) {
            isSearchMode = false;
            scrollListener.resetState();
            loadVideos(true);
            return;
        }
        
        if (!paginationHelper.canLoadMore() && !isRefresh) {
            return;
        }
        
        isSearchMode = true;
        currentSearchQuery = query;
        
        if (isRefresh) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        int offset = isRefresh ? 0 : paginationHelper.getCurrentOffset();
        
        videoManager.searchVideos(query, offset, VIDEOS_PER_PAGE, new VideoManager.VideoCallback() {
            @Override
            public void onSuccess(List<Video> loadedVideos, int totalCount) {
                paginationHelper.onDataLoaded(loadedVideos.size());
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                hideError();

                if (isRefresh) {
                    videos.clear();
                }

                videos.addAll(loadedVideos);
                videoAdapter.notifyDataSetChanged();

                Logger.d(TAG, "Found " + loadedVideos.size() + " videos for query: " + query);
            }

            @Override
            public void onError(String error) {
                paginationHelper.stopLoading();
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showErrorAuto(error);
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_search, menu);
        
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.video_search));
            View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
            if (searchPlate != null) {
                searchPlate.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    if (!query.trim().isEmpty()) {
                        isSearchMode = true;
                        currentSearchQuery = query;
                        videos.clear();
                        videoAdapter.notifyDataSetChanged();
                        searchVideos(query, true);
                    }
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
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
                if (isSearchMode) {
                    isSearchMode = false;
                    currentSearchQuery = "";
                    videos.clear();
                    videoAdapter.notifyDataSetChanged();
                    loadVideos(true);
                }
                return true;
            }
        });
        
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onVideoClick(Video video, int position) {
        VideoPlayerActivity.start(requireContext(), video);
    }
}
