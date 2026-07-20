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

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.ui.adapters.audio.AudioAdapter;
import org.nikanikoo.flux.ui.fragments.BaseFragment;

import java.util.ArrayList;
import java.util.List;

public class DownloadedMusicTabFragment extends BaseFragment implements AudioAdapter.OnAudioClickListener {

    private static final String TAG = "DownloadedMusicTabFragment";

    private RecyclerView recyclerAudios;
    private AudioAdapter audioAdapter;
    private AudioCacheManager audioCacheManager;
    private AudioManager audioManager;
    private List<Audio> audios;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;
    private String currentSearchQuery = "";

    private final BroadcastReceiver audioCacheReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AudioCacheManager.ACTION_AUDIO_CACHE_CHANGED.equals(intent.getAction()) || !isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                loadDownloadedAudios();
            });
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_list, container, false);

        setHasOptionsMenu(true);

        // Hide TabLayout because it is managed by parent container
        View tabs = view.findViewById(R.id.music_tabs);
        if (tabs != null) {
            tabs.setVisibility(View.GONE);
        }

        audioManager = AudioManager.getInstance(requireContext());
        audioCacheManager = AudioCacheManager.getInstance(requireContext());
        audios = new ArrayList<>();

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupErrorView(view, R.id.swipe_refresh);
        setRetryCallback(this::loadDownloadedAudios);

        loadDownloadedAudios();

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
        recyclerAudios = view.findViewById(R.id.recycler_audios);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressLoading = view.findViewById(R.id.progress_loading);
        progressLoading.setVisibility(View.GONE);
    }

    private void setupRecyclerView() {
        recyclerAudios.setLayoutManager(new LinearLayoutManager(requireContext()));
        audioAdapter = new AudioAdapter(audios, this);
        recyclerAudios.setAdapter(audioAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            loadDownloadedAudios();
        });
    }

    private void loadDownloadedAudios() {
        loadDownloadedAudios(currentSearchQuery);
    }

    private void loadDownloadedAudios(String query) {
        progressLoading.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
        hideError();

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
        startAudioPlayer(audios, position);
    }

    @Override
    public void onAddClick(Audio audio, int position) {
        // No action needed for Add button in downloaded tab, or we can mirror library add/remove
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
        popupMenu.getMenu().add(Menu.NONE, 1, 1, R.string.audio_remove_download);
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
        if (position >= 0 && position < audios.size()) {
            audios.remove(position);
            audioAdapter.notifyItemRemoved(position);
        }
        Toast.makeText(requireContext(), getString(R.string.audio_download_removed), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_audio, menu);

        // Hide "Download All" option in downloaded tab
        MenuItem downloadAll = menu.findItem(R.id.action_download_all);
        if (downloadAll != null) {
            downloadAll.setVisible(false);
        }

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.audio_search));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        currentSearchQuery = query;
                        loadDownloadedAudios(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentSearchQuery = newText;
                        loadDownloadedAudios(newText);
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}
