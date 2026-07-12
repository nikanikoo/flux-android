package org.nikanikoo.flux.ui.fragments.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.adapters.audio.AudioAdapter;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.BaseFragment;

import java.util.ArrayList;
import java.util.List;

public class MusicListFragment extends BaseFragment implements AudioAdapter.OnAudioClickListener {

    private static final String TAG = "MusicListFragment";
    private static final int AUDIOS_PER_PAGE = 20;
    private static final int PLAYBACK_QUEUE_PAGE_SIZE = 100;

    private RecyclerView recyclerAudios;
    private AudioAdapter audioAdapter;
    private AudioManager audioManager;
    private AudioCacheManager audioCacheManager;
    private ProfileManager profileManager;
    private List<Audio> audios;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;
    private TabLayout musicTabs;
    private LinearLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;
    private boolean isSearchMode = false;
    private boolean isQueueLoading = false;
    private boolean isDownloadedMode = false;
    private String currentSearchQuery = "";
    private int currentUserId = 0;
    private final BroadcastReceiver audioCacheReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AudioCacheManager.ACTION_AUDIO_CACHE_CHANGED.equals(intent.getAction()) || !isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (isDownloadedMode) {
                    loadDownloadedAudios();
                } else if (audioAdapter != null) {
                    audioAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_list, container, false);
        
        setHasOptionsMenu(true);
        
        audioManager = AudioManager.getInstance(requireContext());
        audioCacheManager = AudioCacheManager.getInstance(requireContext());
        profileManager = ProfileManager.getInstance(requireContext());
        audios = new ArrayList<>();
        
        initViews(view);
        setupTabs();
        setupRecyclerView();
        setupSwipeRefresh();
        setupEndlessScroll();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(() -> {
            if (isSearchMode) {
                searchAudios(currentSearchQuery, true);
            } else {
                loadAudios(true);
            }
        });
        loadUserProfile();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AudioCacheManager.ACTION_AUDIO_CACHE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(audioCacheReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(audioCacheReceiver, filter);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            requireContext().unregisterReceiver(audioCacheReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void initViews(View view) {
        musicTabs = view.findViewById(R.id.music_tabs);
        recyclerAudios = view.findViewById(R.id.recycler_audios);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressLoading = view.findViewById(R.id.progress_loading);
    }

    private void setupTabs() {
        musicTabs.addTab(musicTabs.newTab().setText(R.string.audio_tab_all));
        musicTabs.addTab(musicTabs.newTab().setText(R.string.audio_tab_downloaded));
        musicTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isDownloadedMode = tab.getPosition() == 1;
                isSearchMode = false;
                currentSearchQuery = "";
                scrollListener.resetState();
                if (isDownloadedMode) {
                    loadDownloadedAudios();
                } else {
                    loadAudios(true);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerAudios.setLayoutManager(layoutManager);
        audioAdapter = new AudioAdapter(audios, this);
        recyclerAudios.setAdapter(audioAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            scrollListener.resetState();
            if (isDownloadedMode) {
                loadDownloadedAudios();
            } else if (isSearchMode) {
                searchAudios(currentSearchQuery, true);
            } else {
                loadAudios(true);
            }
        });
    }

    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(AUDIOS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                if (isDownloadedMode) {
                    paginationHelper.setNoMoreData();
                } else if (isSearchMode) {
                    searchAudios(currentSearchQuery, false);
                } else {
                    loadAudios(false);
                }
            }
        };
        recyclerAudios.addOnScrollListener(scrollListener);
    }

    private void loadUserProfile() {
        profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                currentUserId = profile.getId();
                loadAudios(true);
            }

            @Override
            public void onError(String error) {
                showErrorAuto(error);
            }
        });
    }

    private void loadAudios(boolean refresh) {
        if (isDownloadedMode) {
            loadDownloadedAudios();
            return;
        }

        if (refresh) {
            paginationHelper.reset();
        }

        if (!paginationHelper.canLoadMore() && !refresh) {
            return;
        }
        
        if (refresh) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        int offset = refresh ? 0 : paginationHelper.getCurrentOffset();

        audioManager.getAudio(currentUserId, offset, AUDIOS_PER_PAGE, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> newAudios, int totalCount) {
                paginationHelper.onDataLoaded(newAudios.size());
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                hideError();

                if (refresh) {
                    audios.clear();
                }
                audios.addAll(newAudios);
                audioAdapter.notifyDataSetChanged();

                if (audios.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.audio_no_tracks), Toast.LENGTH_SHORT).show();
                }
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

    private void searchAudios(String query, boolean refresh) {
        if (isDownloadedMode) {
            loadDownloadedAudios(query);
            return;
        }

        if (query.trim().isEmpty()) {
            isSearchMode = false;
            scrollListener.resetState();
            loadAudios(true);
            return;
        }

        if (refresh) {
            paginationHelper.reset();
        }

        if (!paginationHelper.canLoadMore() && !refresh) {
            return;
        }
        
        isSearchMode = true;
        currentSearchQuery = query;
        
        if (refresh) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        int offset = refresh ? 0 : paginationHelper.getCurrentOffset();

        audioManager.searchAudio(query, offset, AUDIOS_PER_PAGE, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> newAudios, int totalCount) {
                paginationHelper.onDataLoaded(newAudios.size());
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                hideError();

                if (refresh) {
                    audios.clear();
                }
                audios.addAll(newAudios);
                audioAdapter.notifyDataSetChanged();

                if (audios.isEmpty() && refresh) {
                    Toast.makeText(requireContext(), getString(R.string.audio_nothing_found), Toast.LENGTH_SHORT).show();
                }
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

    private void loadDownloadedAudios() {
        loadDownloadedAudios(currentSearchQuery);
    }

    private void loadDownloadedAudios(String query) {
        progressLoading.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
        hideError();
        paginationHelper.setNoMoreData();

        audios.clear();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        for (Audio audio : audioCacheManager.getDownloadedAudios()) {
            if (normalizedQuery.isEmpty() ||
                    audio.getArtist().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery) ||
                    audio.getTitle().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)) {
                audios.add(audio);
            }
        }
        audioAdapter.notifyDataSetChanged();

        if (audios.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.audio_no_downloaded), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPlayClick(Audio audio, int position) {
        boolean isCurrentlyPlaying = false; // TODO: Check service state
        
        if (isCurrentlyPlaying) {
            Intent intent = new Intent(requireContext(), org.nikanikoo.flux.ui.activities.AudioPlayerActivity.class);
            startActivity(intent);
        } else {
            startAudioPlayer(audios, position);
            loadRemainingPlaybackQueue();
        }
    }

    @Override
    public void onAddClick(Audio audio, int position) {
        if (audio.isAdded()) {
            audioManager.deleteAudio(audio.getId(), audio.getOwnerId(), new AudioManager.AudioActionCallback() {
                @Override
                public void onSuccess() {
                    audio.setAdded(false);
                    audioAdapter.updateAudio(position, audio);
                    Toast.makeText(requireContext(), getString(R.string.audio_removed), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(requireContext(), getString(R.string.audio_remove_error) + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            audioManager.addAudio(audio.getId(), audio.getOwnerId(), new AudioManager.AudioActionCallback() {
                @Override
                public void onSuccess() {
                    audio.setAdded(true);
                    audioAdapter.updateAudio(position, audio);
                    Toast.makeText(requireContext(), getString(R.string.audio_added), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(requireContext(), getString(R.string.audio_add_error) + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void startAudioPlayer(List<Audio> playlist, int startPosition) {
        Intent serviceIntent = new Intent(requireContext(), org.nikanikoo.flux.services.AudioPlayerService.class);
        requireContext().startService(serviceIntent);

        org.nikanikoo.flux.ui.views.AudioPlayerHelper.setPlaylist(requireContext(), playlist, startPosition);
    }

    private void loadRemainingPlaybackQueue() {
        if (isQueueLoading || currentUserId == 0) {
            return;
        }

        isQueueLoading = true;
        loadPlaybackQueuePage(audios.size());
    }

    private void loadPlaybackQueuePage(int offset) {
        AudioManager.AudioCallback callback = new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> newAudios, int totalCount) {
                if (!isAdded()) {
                    isQueueLoading = false;
                    return;
                }

                if (newAudios.isEmpty()) {
                    paginationHelper.setNoMoreData();
                    isQueueLoading = false;
                    return;
                }

                audios.addAll(newAudios);
                paginationHelper.onDataLoaded(newAudios.size());
                audioAdapter.notifyItemRangeInserted(audios.size() - newAudios.size(), newAudios.size());
                org.nikanikoo.flux.ui.views.AudioPlayerHelper.appendToPlaylist(requireContext(), newAudios);

                int nextOffset = offset + newAudios.size();
                if (totalCount > 0 && nextOffset >= totalCount) {
                    paginationHelper.setNoMoreData();
                    isQueueLoading = false;
                    return;
                }

                loadPlaybackQueuePage(nextOffset);
            }

            @Override
            public void onError(String error) {
                isQueueLoading = false;
            }
        };

        if (isSearchMode) {
            audioManager.searchAudio(currentSearchQuery, offset, PLAYBACK_QUEUE_PAGE_SIZE, callback);
        } else {
            audioManager.getAudio(currentUserId, offset, PLAYBACK_QUEUE_PAGE_SIZE, callback);
        }
    }

    @Override
    public void onMoreClick(Audio audio, int position, View anchor) {
        showAudioContextMenu(audio, position, anchor);
    }

    private void showAudioContextMenu(Audio audio, int position, View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        if (audioCacheManager.isDownloaded(audio)) {
            popupMenu.getMenu().add(Menu.NONE, 1, 1, R.string.audio_remove_download);
        } else {
            popupMenu.getMenu().add(Menu.NONE, 2, 1, R.string.audio_download);
        }
        popupMenu.getMenu().add(Menu.NONE, 3, 2, R.string.audio_play_next);
        popupMenu.getMenu().add(
                Menu.NONE,
                4,
                3,
                audio.isAdded() ? R.string.audio_remove_from_library : R.string.audio_add_to_library);

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                removeDownloadedAudio(audio, position);
                return true;
            }
            if (item.getItemId() == 2) {
                downloadAudio(audio, position);
                return true;
            }
            if (item.getItemId() == 3) {
                org.nikanikoo.flux.ui.views.AudioPlayerHelper.playNext(requireContext(), audio);
                Toast.makeText(requireContext(), getString(R.string.audio_added_next), Toast.LENGTH_SHORT).show();
                return true;
            }
            if (item.getItemId() == 4) {
                onAddClick(audio, position);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void removeDownloadedAudio(Audio audio, int position) {
        audioCacheManager.deleteAudio(audio);
        if (isDownloadedMode && position >= 0 && position < audios.size()) {
            audios.remove(position);
            audioAdapter.notifyItemRemoved(position);
        } else {
            audioAdapter.notifyItemChanged(position);
        }
        Toast.makeText(requireContext(), getString(R.string.audio_download_removed), Toast.LENGTH_SHORT).show();
    }

    private void downloadAudio(Audio audio, int position) {
        if (audioCacheManager.isDownloaded(audio)) {
            Toast.makeText(requireContext(), getString(R.string.audio_already_downloaded), Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), getString(R.string.audio_downloading), Toast.LENGTH_SHORT).show();
        audioCacheManager.downloadAudio(audio, new AudioCacheManager.DownloadCallback() {
            @Override
            public void onSuccess(java.io.File file) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    audioAdapter.notifyItemChanged(position);
                    Toast.makeText(requireContext(), getString(R.string.audio_downloaded), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), getString(R.string.audio_download_error) + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void downloadAllAudios() {
        if (currentUserId == 0) {
            Toast.makeText(requireContext(), getString(R.string.audio_wait_for_profile), Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), getString(R.string.audio_download_all_started), Toast.LENGTH_SHORT).show();
        downloadAllAudiosPage(0);
    }

    private void downloadAllAudiosPage(int offset) {
        audioManager.getAudio(currentUserId, offset, PLAYBACK_QUEUE_PAGE_SIZE, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> newAudios, int totalCount) {
                if (!isAdded()) {
                    return;
                }

                for (Audio audio : newAudios) {
                    if (!audioCacheManager.isDownloaded(audio)) {
                        audioCacheManager.downloadAudio(audio, new AudioCacheManager.DownloadCallback() {
                            @Override
                            public void onSuccess(java.io.File file) {
                            }

                            @Override
                            public void onError(String error) {
                            }
                        });
                    }
                }

                int nextOffset = offset + newAudios.size();
                if (!newAudios.isEmpty() && (totalCount <= 0 || nextOffset < totalCount)) {
                    downloadAllAudiosPage(nextOffset);
                    return;
                }

                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), getString(R.string.audio_download_all_queued), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), getString(R.string.audio_download_error) + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_audio, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.audio_search));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        scrollListener.resetState();
                        currentSearchQuery = query;
                        if (isDownloadedMode) {
                            loadDownloadedAudios(query);
                        } else {
                            searchAudios(query, true);
                        }
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentSearchQuery = newText;
                        if (isDownloadedMode) {
                            loadDownloadedAudios(newText);
                        } else if (newText.isEmpty() && isSearchMode) {
                            isSearchMode = false;
                            scrollListener.resetState();
                            loadAudios(true);
                        }
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_download_all) {
            downloadAllAudios();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}