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
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.ui.adapters.audio.AudioAdapter;
import org.nikanikoo.flux.ui.custom.EndlessScrollListener;
import org.nikanikoo.flux.ui.custom.PaginationHelper;
import org.nikanikoo.flux.ui.fragments.BaseFragment;

import java.util.ArrayList;
import java.util.List;

public class TracksListFragment extends BaseFragment implements AudioAdapter.OnAudioClickListener {

    private static final String TAG = "TracksListFragment";
    private static final int AUDIOS_PER_PAGE = 20;

    public static final int MODE_POPULAR = 0;
    public static final int MODE_NEW = 1;
    public static final int MODE_LONG = 2;

    private int mode = MODE_POPULAR;
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

    public static TracksListFragment newInstance(int mode) {
        TracksListFragment fragment = new TracksListFragment();
        Bundle args = new Bundle();
        args.putInt("mode", mode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mode = getArguments().getInt("mode", MODE_POPULAR);
        }
        audios = new ArrayList<>();
        audioManager = AudioManager.getInstance(requireContext());
        audioCacheManager = AudioCacheManager.getInstance(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_list, container, false);

        setHasOptionsMenu(false);

        // Hide TabLayout
        View tabs = view.findViewById(R.id.music_tabs);
        if (tabs != null) {
            tabs.setVisibility(View.GONE);
        }

        // Setup Toolbar back navigation manually
        Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
            
            // Set toolbar title dynamically
            if (mode == MODE_POPULAR) {
                toolbar.setTitle("Популярное");
            } else if (mode == MODE_NEW) {
                toolbar.setTitle("Новое");
            } else {
                toolbar.setTitle("Длинные треки");
            }
        }

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupEndlessScroll();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(() -> loadTracks(true));

        loadTracks(true);

        return view;
    }

    private void initViews(View view) {
        recyclerAudios = view.findViewById(R.id.recycler_audios);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressLoading = view.findViewById(R.id.progress_loading);
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
            loadTracks(true);
        });
    }

    private void setupEndlessScroll() {
        paginationHelper = new PaginationHelper(AUDIOS_PER_PAGE);
        scrollListener = new EndlessScrollListener(layoutManager, paginationHelper) {
            @Override
            public void onLoadMore(int offset, int totalItemsCount, RecyclerView view) {
                loadTracks(false);
            }
        };
        recyclerAudios.addOnScrollListener(scrollListener);
    }

    private void loadTracks(boolean refresh) {
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

        AudioManager.AudioCallback callback = new AudioManager.AudioCallback() {
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
                    Toast.makeText(requireContext(), "Нет доступных треков", Toast.LENGTH_SHORT).show();
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
        };

        if (mode == MODE_POPULAR) {
            audioManager.getPopular(0, offset, AUDIOS_PER_PAGE, callback);
        } else if (mode == MODE_NEW) {
            audioManager.getFeed(0, offset, AUDIOS_PER_PAGE, callback);
        } else {
            // mode == MODE_LONG: search using wildcard to match all songs
            audioManager.searchAudioWithSort("%", offset, AUDIOS_PER_PAGE, 1, callback);
        }
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
        popupMenu.getMenu().add(Menu.NONE, 5, 4, R.string.audio_lyrics);
        popupMenu.getMenu().add(Menu.NONE, 6, 5, R.string.audio_add_to_playlist);

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                audioCacheManager.deleteAudio(audio);
                audioAdapter.notifyItemChanged(position);
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
            if (item.getItemId() == 5) {
                LyricsBottomSheetDialogFragment dialog = LyricsBottomSheetDialogFragment.newInstance(
                        audio.getTitle(),
                        audio.getArtist(),
                        audio.getLyrics_id(),
                        null
                );
                dialog.show(getParentFragmentManager(), "audio_lyrics");
                return true;
            }
            if (item.getItemId() == 6) {
                showAddToPlaylistDialog(audio);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showAddToPlaylistDialog(Audio audio) {
        audioManager.getPlaylists(0, 0, 50, new AudioManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<org.nikanikoo.flux.data.models.AudioPlaylist> playlists, int totalCount) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (playlists == null || playlists.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.audio_no_playlists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] titles = new String[playlists.size()];
                    for (int i = 0; i < playlists.size(); i++) {
                        titles[i] = playlists.get(i).getTitle();
                    }
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(R.string.audio_select_playlist)
                            .setItems(titles, (dialog, which) -> {
                                org.nikanikoo.flux.data.models.AudioPlaylist playlist = playlists.get(which);
                                audioManager.addTracksToPlaylist(playlist.getId(), String.valueOf(audio.getId()), new AudioManager.AudioActionCallback() {
                                    @Override
                                    public void onSuccess() {
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() ->
                                                Toast.makeText(requireContext(), R.string.audio_added_to_playlist, Toast.LENGTH_SHORT).show());
                                    }

                                    @Override
                                    public void onError(String error) {
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() ->
                                                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show());
                                    }
                                });
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show());
            }
        });
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
}
