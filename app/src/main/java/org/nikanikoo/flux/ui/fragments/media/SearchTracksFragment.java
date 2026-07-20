package org.nikanikoo.flux.ui.fragments.media;

import android.content.Intent;
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
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.ui.adapters.audio.AudioAdapter;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.BaseFragment;

import java.util.ArrayList;
import java.util.List;

public class SearchTracksFragment extends BaseFragment implements AudioAdapter.OnAudioClickListener {

    private static final String TAG = "SearchTracksFragment";
    private static final int AUDIOS_PER_PAGE = 20;

    private RecyclerView recyclerAudios;
    private AudioAdapter audioAdapter;
    private AudioManager audioManager;
    private AudioCacheManager audioCacheManager;
    private List<Audio> audios;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;
    private LinearLayoutManager layoutManager;
    private EndlessScrollListener scrollListener;
    private PaginationHelper paginationHelper;
    private String currentQuery = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audios = new ArrayList<>();
        audioManager = AudioManager.getInstance(requireContext());
        audioCacheManager = AudioCacheManager.getInstance(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_list, container, false);

        setHasOptionsMenu(true);

        View tabs = view.findViewById(R.id.music_tabs);
        if (tabs != null) {
            tabs.setVisibility(View.GONE);
        }

        // Setup Toolbar back navigation manually
        Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        }

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupEndlessScroll();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(() -> searchTracks(currentQuery, true));

        return view;
    }

    private void initViews(View view) {
        recyclerAudios = view.findViewById(R.id.recycler_audios);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressLoading = view.findViewById(R.id.progress_loading);
        progressLoading.setVisibility(View.GONE);
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
            searchTracks(currentQuery, true);
        });
    }

    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(AUDIOS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                searchTracks(currentQuery, false);
            }
        };
        recyclerAudios.addOnScrollListener(scrollListener);
    }

    private void searchTracks(String query, boolean refresh) {
        if (query == null || query.trim().isEmpty()) {
            progressLoading.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
            if (refresh) {
                audios.clear();
                audioAdapter.notifyDataSetChanged();
            }
            return;
        }

        if (refresh) {
            paginationHelper.reset();
        }

        if (!paginationHelper.canLoadMore() && !refresh) {
            return;
        }

        currentQuery = query;

        if (refresh) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        int offset = refresh ? 0 : paginationHelper.getCurrentOffset();

        audioManager.searchAudio(query, offset, AUDIOS_PER_PAGE, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> newAudios, int totalCount) {
                if (!isAdded()) return;
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
                    Toast.makeText(requireContext(), "Ничего не найдено", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                paginationHelper.stopLoading();
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showErrorAuto(error);
            }
        });
    }

    @Override
    public void onPlayClick(Audio audio, int position) {
        startAudioPlayer(audios, position);
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
                audioCacheManager.deleteAudio(audio);
                audioAdapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), getString(R.string.audio_download_removed), Toast.LENGTH_SHORT).show();
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

    private void downloadAudio(Audio audio, int position) {
        if (audioCacheManager.isDownloaded(audio)) {
            Toast.makeText(requireContext(), getString(R.string.audio_already_downloaded), Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), getString(R.string.audio_downloading), Toast.LENGTH_SHORT).show();
        audioCacheManager.downloadAudio(audio, new AudioCacheManager.DownloadCallback() {
            @Override
            public void onSuccess(java.io.File file) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    audioAdapter.notifyItemChanged(position);
                    Toast.makeText(requireContext(), getString(R.string.audio_downloaded), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), getString(R.string.audio_download_error) + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_audio, menu);

        // Hide download all
        MenuItem downloadAll = menu.findItem(R.id.action_download_all);
        if (downloadAll != null) {
            downloadAll.setVisible(false);
        }

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            searchItem.expandActionView();
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.audio_search));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        scrollListener.resetState();
                        searchTracks(query, true);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        scrollListener.resetState();
                        searchTracks(newText, true);
                        return true;
                    }
                });
            }
        }
    }
}
