package org.nikanikoo.flux.ui.fragments.media;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.managers.ProfileManager;
import org.nikanikoo.flux.data.managers.RecentlyPlayedManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.data.models.AudioPlaylist;
import org.nikanikoo.flux.data.models.UserProfile;
import org.nikanikoo.flux.ui.activities.MainActivity;
import org.nikanikoo.flux.ui.adapters.audio.PlaylistsHorizontalAdapter;
import org.nikanikoo.flux.ui.adapters.audio.PopularTracksAdapter;
import org.nikanikoo.flux.ui.adapters.audio.RecentlyPlayedAdapter;
import org.nikanikoo.flux.ui.adapters.audio.TracksHorizontalAdapter;
import org.nikanikoo.flux.ui.fragments.BaseFragment;

import java.util.ArrayList;
import java.util.List;

public class MusicDiscoverFragment extends BaseFragment {

    private static final String TAG = "MusicDiscoverFragment";

    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;

    // Recently Played
    private View sectionRecentlyPlayed;
    private RecyclerView recyclerRecentlyPlayed;
    private RecentlyPlayedAdapter recentlyPlayedAdapter;
    private List<RecentlyPlayedManager.Item> recentlyPlayedItems;

    // My Playlists
    private View sectionMyPlaylists;
    private RecyclerView recyclerMyPlaylists;
    private PlaylistsHorizontalAdapter playlistsAdapter;
    private List<AudioPlaylist> playlistsList;

    // New Tracks
    private View sectionNewTracks;
    private RecyclerView recyclerNewTracks;
    private org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter newTracksAdapter;
    private List<Audio> newTracksList;

    // Long Tracks
    private View sectionLongTracks;
    private RecyclerView recyclerLongTracks;
    private org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter longTracksAdapter;
    private List<Audio> longTracksList;

    // Popular Tracks
    private View sectionPopular;
    private RecyclerView recyclerPopular;
    private org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter popularTracksAdapter;
    private List<Audio> popularTracksList;

    // Genres
    private RecyclerView recyclerGenres;
    private org.nikanikoo.flux.ui.adapters.audio.GenreAdapter genresAdapter;

    private AudioManager audioManager;
    private ProfileManager profileManager;
    private RecentlyPlayedManager recentlyPlayedManager;
    private int currentUserId = 0;
    private boolean isInitialLoading = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = AudioManager.getInstance(requireContext());
        profileManager = ProfileManager.getInstance(requireContext());
        recentlyPlayedManager = RecentlyPlayedManager.getInstance(requireContext());

        recentlyPlayedItems = new ArrayList<>();
        playlistsList = new ArrayList<>();
        newTracksList = new ArrayList<>();
        longTracksList = new ArrayList<>();
        popularTracksList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_discover, container, false);

        setHasOptionsMenu(true);

        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        progressLoading = view.findViewById(R.id.progress_loading);

        sectionRecentlyPlayed = view.findViewById(R.id.section_recently_played);
        recyclerRecentlyPlayed = view.findViewById(R.id.recycler_recently_played);

        recyclerGenres = view.findViewById(R.id.recycler_genres);

        sectionMyPlaylists = view.findViewById(R.id.section_my_playlists);
        recyclerMyPlaylists = view.findViewById(R.id.recycler_my_playlists);

        sectionNewTracks = view.findViewById(R.id.section_new_tracks);
        recyclerNewTracks = view.findViewById(R.id.recycler_new_tracks);

        sectionLongTracks = view.findViewById(R.id.section_long_tracks);
        recyclerLongTracks = view.findViewById(R.id.recycler_long_tracks);

        sectionPopular = view.findViewById(R.id.section_popular);
        recyclerPopular = view.findViewById(R.id.recycler_popular);

        setupRecyclerViews();

        swipeRefresh.setOnRefreshListener(() -> {
            loadAllSections(false);
        });

        loadUserProfile();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_audio, menu);
        
        // Hide download all
        MenuItem downloadAll = menu.findItem(R.id.action_download_all);
        if (downloadAll != null) {
            downloadAll.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            openSearchFragment();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Always refresh recently played items because user might have played new songs
        loadRecentlyPlayed();
    }

    private void setupRecyclerViews() {
        // Recently Played - 3 rows horizontal grid (scroll sideways, 4 cols visible)
        GridLayoutManager recentlyPlayedLM = new GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false);
        recyclerRecentlyPlayed.setLayoutManager(recentlyPlayedLM);
        recentlyPlayedAdapter = new RecentlyPlayedAdapter(recentlyPlayedItems, item -> {
            if ("playlist".equals(item.type)) {
                openPlaylistDetails(item.id, item.ownerId, item.title, item.coverUrl, item.creatorName);
            } else {
                openArtistTracks(item.title);
            }
        });
        recyclerRecentlyPlayed.setAdapter(recentlyPlayedAdapter);

        // Genres horizontal row
        recyclerGenres.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        genresAdapter = new org.nikanikoo.flux.ui.adapters.audio.GenreAdapter(item -> {
            openGenrePlaylist(item.id, item.name, item.color);
        });
        recyclerGenres.setAdapter(genresAdapter);

        // Popular Tracks
        recyclerPopular.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        popularTracksAdapter = new org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter(popularTracksList, true, new org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter.OnItemClickListener() {
            @Override
            public void onTrackClick(Audio audio, int position) {
                System.out.println("MusicDiscoverFragment: Popular onTrackClick clicked position=" + position + " title=" + audio.getTitle());
                RecentlyPlayedManager.getInstance(requireContext()).addArtist(audio.getArtist());
                startAudioPlayer(popularTracksList, position);
            }

            @Override
            public void onMoreClick() {
                openTracksList(TracksListFragment.MODE_POPULAR);
            }
        });
        recyclerPopular.setAdapter(popularTracksAdapter);

        // New Tracks
        recyclerNewTracks.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        newTracksAdapter = new org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter(newTracksList, false, new org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter.OnItemClickListener() {
            @Override
            public void onTrackClick(Audio audio, int position) {
                System.out.println("MusicDiscoverFragment: New Tracks onTrackClick clicked position=" + position + " title=" + audio.getTitle());
                RecentlyPlayedManager.getInstance(requireContext()).addArtist(audio.getArtist());
                startAudioPlayer(newTracksList, position);
            }

            @Override
            public void onMoreClick() {
                openTracksList(TracksListFragment.MODE_NEW);
            }
        });
        recyclerNewTracks.setAdapter(newTracksAdapter);

        // Long Tracks
        recyclerLongTracks.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        longTracksAdapter = new org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter(longTracksList, false, new org.nikanikoo.flux.ui.adapters.audio.HorizontalTracksAdapter.OnItemClickListener() {
            @Override
            public void onTrackClick(Audio audio, int position) {
                System.out.println("MusicDiscoverFragment: Long Tracks onTrackClick clicked position=" + position + " title=" + audio.getTitle());
                RecentlyPlayedManager.getInstance(requireContext()).addArtist(audio.getArtist());
                startAudioPlayer(longTracksList, position);
            }

            @Override
            public void onMoreClick() {
                openTracksList(TracksListFragment.MODE_LONG);
            }
        });
        recyclerLongTracks.setAdapter(longTracksAdapter);

        // My Playlists
        recyclerMyPlaylists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        playlistsAdapter = new PlaylistsHorizontalAdapter(playlistsList, playlist -> {
            openPlaylistDetails(playlist.getId(), playlist.getOwnerId(), playlist.getTitle(), playlist.getPhotoUrl(), playlist.getAuthorName());
        });
        recyclerMyPlaylists.setAdapter(playlistsAdapter);
    }

    private void openSearchFragment() {
        SearchTracksFragment fragment = new SearchTracksFragment();
        if (isAdded()) {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "search_tracks")
                    .addToBackStack("search_tracks")
                    .commit();
        }
    }

    private int getGenreColor(int genreId) {
        switch (genreId) {
            case 2: return 0xFFE91E63; // Pop
            case 1: return 0xFF9C27B0; // Rock
            case 3: return 0xFF2196F3; // Rap
            case 5: return 0xFFFF9800; // House
            case 7: return 0xFFF44336; // Metal
            case 8: return 0xFF673AB7; // Dubstep
            case 1001: return 0xFF4CAF50; // Jazz
            case 11: return 0xFF009688; // Trance
            case 12: return 0xFF3F51B5; // Chanson
            case 16: return 0xFF795548; // Classical
            case 17: return 0xFF8BC34A; // Indie Pop
            case 22: return 0xFFE040FB; // Disco
            default: return 0xFF607D8B; // Grey
        }
    }

    private void openGenrePlaylist(int genreId, String genreName, int color) {
        PlaylistDetailsFragment fragment = PlaylistDetailsFragment.newInstance(-2, genreId, genreName, "", "", color);
        
        // Record genre playlist in recently played!
        AudioPlaylist ap = new AudioPlaylist();
        ap.setId(-2); // Genre mode
        ap.setOwnerId(genreId);
        ap.setTitle(genreName);
        ap.setPhotoUrl("");
        ap.setAuthorName("Жанр");
        recentlyPlayedManager.addPlaylist(ap);

        if (isAdded()) {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "genre_playlist")
                    .addToBackStack("genre_playlist")
                    .commit();
        }
    }

    private void loadUserProfile() {
        profileManager.loadProfile(false, new ProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                currentUserId = profile.getId();
                loadAllSections(true);
            }

            @Override
            public void onError(String error) {
                progressLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), "Ошибка загрузки профиля: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAllSections(boolean showProgress) {
        if (showProgress && isInitialLoading) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        loadRecentlyPlayed();
        loadMyPlaylists();
        loadNewTracks();
        loadLongTracks();
        loadPopularTracks();
    }

    private void loadRecentlyPlayed() {
        recentlyPlayedItems.clear();
        recentlyPlayedItems.addAll(recentlyPlayedManager.getItems());
        recentlyPlayedAdapter.notifyDataSetChanged();

        if (recentlyPlayedItems.isEmpty()) {
            sectionRecentlyPlayed.setVisibility(View.GONE);
        } else {
            sectionRecentlyPlayed.setVisibility(View.VISIBLE);
        }
    }

    private void loadMyPlaylists() {
        audioManager.getPlaylists(currentUserId, 0, 15, new AudioManager.PlaylistsCallback() {
            @Override
            public void onSuccess(List<AudioPlaylist> playlists, int totalCount) {
                if (!isAdded()) return;
                checkLoadingComplete();
                playlistsList.clear();
                playlistsList.addAll(playlists);
                playlistsAdapter.notifyDataSetChanged();

                if (playlistsList.isEmpty()) {
                    sectionMyPlaylists.setVisibility(View.GONE);
                } else {
                    sectionMyPlaylists.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                checkLoadingComplete();
                sectionMyPlaylists.setVisibility(View.GONE);
            }
        });
    }

    private void loadNewTracks() {
        audioManager.getFeed(0, 0, 15, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> audios, int totalCount) {
                if (!isAdded()) return;
                checkLoadingComplete();
                newTracksList.clear();
                newTracksList.addAll(audios);
                newTracksAdapter.notifyDataSetChanged();

                if (newTracksList.isEmpty()) {
                    sectionNewTracks.setVisibility(View.GONE);
                } else {
                    sectionNewTracks.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                checkLoadingComplete();
                sectionNewTracks.setVisibility(View.GONE);
            }
        });
    }

    private void loadLongTracks() {
        // sort=1 is track length / Long, using Cyrillic 'о' to match almost all songs in Russian/Cyrillic
        audioManager.searchAudioWithSort("о", 0, 15, 1, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> audios, int totalCount) {
                if (!isAdded()) return;
                checkLoadingComplete();
                longTracksList.clear();
                longTracksList.addAll(audios);
                longTracksAdapter.notifyDataSetChanged();

                if (longTracksList.isEmpty()) {
                    sectionLongTracks.setVisibility(View.GONE);
                } else {
                    sectionLongTracks.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                checkLoadingComplete();
                sectionLongTracks.setVisibility(View.GONE);
            }
        });
    }

    private void loadPopularTracks() {
        audioManager.getPopular(0, 0, 15, new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> audios, int totalCount) {
                if (!isAdded()) return;
                checkLoadingComplete();
                popularTracksList.clear();
                popularTracksList.addAll(audios);
                popularTracksAdapter.notifyDataSetChanged();

                if (popularTracksList.isEmpty()) {
                    sectionPopular.setVisibility(View.GONE);
                } else {
                    sectionPopular.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                checkLoadingComplete();
                sectionPopular.setVisibility(View.GONE);
            }
        });
    }

    private void checkLoadingComplete() {
        isInitialLoading = false;
        progressLoading.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private void openPlaylistDetails(int playlistId, int ownerId, String title, String coverUrl, String description) {
        if (playlistId == -2) {
            openGenrePlaylist(ownerId, title, getGenreColor(ownerId));
            return;
        }

        PlaylistDetailsFragment fragment = PlaylistDetailsFragment.newInstance(playlistId, ownerId, title, coverUrl, description);
        
        // Record playlist in recently played
        AudioPlaylist ap = new AudioPlaylist();
        ap.setId(playlistId);
        ap.setOwnerId(ownerId);
        ap.setTitle(title);
        ap.setPhotoUrl(coverUrl);
        ap.setAuthorName(description);
        recentlyPlayedManager.addPlaylist(ap);

        if (isAdded()) {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "playlist_details")
                    .addToBackStack("playlist_details")
                    .commit();
        }
    }

    private void openArtistTracks(String artistName) {
        PlaylistDetailsFragment fragment = PlaylistDetailsFragment.newInstance(-1, 0, artistName, "", "");
        if (isAdded()) {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "artist_tracks")
                    .addToBackStack("artist_tracks")
                    .commit();
        }
    }

    private void openTracksList(int mode) {
        TracksListFragment fragment = TracksListFragment.newInstance(mode);
        if (isAdded()) {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment, "tracks_list_" + mode)
                    .addToBackStack("tracks_list_" + mode)
                    .commit();
        }
    }

    private void startAudioPlayer(List<Audio> playlist, int startPosition) {
        System.out.println("MusicDiscoverFragment: startAudioPlayer called with playlist size=" + playlist.size() + ", position=" + startPosition);
        if (playlist.size() > startPosition) {
            Audio audio = playlist.get(startPosition);
            System.out.println("MusicDiscoverFragment: track to play: ID=" + audio.getId() + ", title=" + audio.getTitle() + ", url=" + audio.getUrl());
        }
        Intent serviceIntent = new Intent(requireContext(), org.nikanikoo.flux.services.AudioPlayerService.class);
        requireContext().startService(serviceIntent);

        org.nikanikoo.flux.ui.views.AudioPlayerHelper.setPlaylist(requireContext(), playlist, startPosition);
    }
}
