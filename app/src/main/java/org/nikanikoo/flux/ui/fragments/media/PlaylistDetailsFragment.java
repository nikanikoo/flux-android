package org.nikanikoo.flux.ui.fragments.media;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.data.managers.AudioManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.data.models.AudioPlaylist;
import org.nikanikoo.flux.ui.adapters.audio.AudioAdapter;
import org.nikanikoo.flux.ui.fragments.BaseFragment;
import org.nikanikoo.flux.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;

public class PlaylistDetailsFragment extends BaseFragment implements AudioAdapter.OnAudioClickListener {

    private static final String TAG = "PlaylistDetailsFragment";

    private int playlistId;
    private int ownerId;
    private String title;
    private String coverUrl;
    private String description;

    private ImageView coverImage;
    private TextView titleText;
    private TextView authorText;
    private TextView descText;
    private TextView infoText;
    private Button btnPlayAll;
    private RecyclerView recyclerView;
    private ProgressBar progressLoading;

    private AudioAdapter audioAdapter;
    private List<Audio> audios;
    private AudioManager audioManager;
    private AudioCacheManager audioCacheManager;

    private org.nikanikoo.flux.utils.AlbumArtFetcher albumArtFetcher;

    private int genreColor = 0;

    public static PlaylistDetailsFragment newInstance(int playlistId, int ownerId, String title, String coverUrl, String description) {
        return newInstance(playlistId, ownerId, title, coverUrl, description, 0);
    }

    public static PlaylistDetailsFragment newInstance(int playlistId, int ownerId, String title, String coverUrl, String description, int color) {
        PlaylistDetailsFragment fragment = new PlaylistDetailsFragment();
        Bundle args = new Bundle();
        args.putInt("playlist_id", playlistId);
        args.putInt("owner_id", ownerId);
        args.putString("title", title);
        args.putString("cover_url", coverUrl);
        args.putString("description", description);
        args.putInt("genre_color", color);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            playlistId = getArguments().getInt("playlist_id");
            ownerId = getArguments().getInt("owner_id");
            title = getArguments().getString("title");
            coverUrl = getArguments().getString("cover_url");
            description = getArguments().getString("description");
            genreColor = getArguments().getInt("genre_color", 0);
        }
        audios = new ArrayList<>();
        audioManager = AudioManager.getInstance(requireContext());
        audioCacheManager = AudioCacheManager.getInstance(requireContext());
        albumArtFetcher = new org.nikanikoo.flux.utils.AlbumArtFetcher(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_details, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        if (playlistId > 0) {
            toolbar.inflateMenu(R.menu.menu_playlist_details);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_edit_playlist) {
                    showEditPlaylistDialog();
                    return true;
                } else if (item.getItemId() == R.id.action_delete_playlist) {
                    showDeletePlaylistDialog();
                    return true;
                }
                return false;
            });
        }

        coverImage = view.findViewById(R.id.playlist_details_cover);
        titleText = view.findViewById(R.id.playlist_details_title);
        authorText = view.findViewById(R.id.playlist_details_author);
        descText = view.findViewById(R.id.playlist_details_desc);
        infoText = view.findViewById(R.id.playlist_details_info);
        btnPlayAll = view.findViewById(R.id.btn_play_all);
        recyclerView = view.findViewById(R.id.recycler_playlist_tracks);
        progressLoading = view.findViewById(R.id.progress_loading);

        setupViews();
        loadPlaylistTracks();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Hide MainActivity toolbar to avoid duplicate back button
        View mainToolbar = requireActivity().findViewById(R.id.toolbar);
        if (mainToolbar != null) mainToolbar.setVisibility(View.GONE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Restore MainActivity toolbar
        View mainToolbar = requireActivity().findViewById(R.id.toolbar);
        if (mainToolbar != null) mainToolbar.setVisibility(View.VISIBLE);
    }

    private void setupViews() {
        titleText.setText(title);
        
        // Playlist description
        if (description != null && !description.isEmpty()) {
            descText.setText(description);
            descText.setVisibility(View.VISIBLE);
        } else {
            descText.setVisibility(View.GONE);
        }

        // Playlist creator & cover
        if (playlistId == -1) {
            authorText.setText("Исполнитель");
            albumArtFetcher.loadArtistImage(title, coverImage, R.drawable.ic_music_note);
        } else if (playlistId == -2) {
            authorText.setText("Жанр");
            coverImage.setImageResource(R.drawable.ic_library_music);
            coverImage.setBackgroundColor(genreColor);
            coverImage.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        } else {
            authorText.setText(ownerId > 0 ? "Пользователь" : "Сообщество");
            if (coverUrl != null && !coverUrl.isEmpty()) {
                Picasso.get().load(coverUrl).placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note).into(coverImage);
            } else {
                coverImage.setImageResource(R.drawable.ic_music_note);
            }
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        audioAdapter = new AudioAdapter(audios, this);
        recyclerView.setAdapter(audioAdapter);

        btnPlayAll.setOnClickListener(v -> {
            if (!audios.isEmpty()) {
                startAudioPlayer(audios, 0);
            } else {
                Toast.makeText(requireContext(), "Нет доступных треков", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPlaylistTracks() {
        progressLoading.setVisibility(View.VISIBLE);
        AudioManager.AudioCallback callback = new AudioManager.AudioCallback() {
            @Override
            public void onSuccess(List<Audio> newAudios, int totalCount) {
                if (!isAdded()) return;
                progressLoading.setVisibility(View.GONE);
                audios.clear();
                audios.addAll(newAudios);
                audioAdapter.notifyDataSetChanged();

                String info = audios.size() + " " + getTrackWord(audios.size());
                infoText.setText(info);
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Ошибка загрузки: " + error, Toast.LENGTH_SHORT).show();
            }
        };

        if (playlistId == -1) {
            audioManager.searchAudio(title, 0, 100, callback);
        } else if (playlistId == -2) {
            audioManager.getPopular(ownerId, 0, 100, callback);
        } else {
            audioManager.getPlaylistAudios(ownerId, playlistId, null, 0, 100, callback);
        }
    }

    private String getTrackWord(int count) {
        int lastDigit = count % 10;
        int lastTwoDigits = count % 100;
        if (lastTwoDigits >= 11 && lastTwoDigits <= 19) {
            return "треков";
        }
        if (lastDigit == 1) {
            return "трек";
        }
        if (lastDigit >= 2 && lastDigit <= 4) {
            return "трека";
        }
        return "треков";
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
        if (playlistId > 0) {
            popupMenu.getMenu().add(Menu.NONE, 6, 5, R.string.audio_remove_from_playlist);
        }

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
                showLyricsDialog(audio);
                return true;
            }
            if (item.getItemId() == 6) {
                removeTrackFromPlaylist(audio, position);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showLyricsDialog(Audio audio) {
        LyricsBottomSheetDialogFragment dialog = LyricsBottomSheetDialogFragment.newInstance(
                audio.getTitle(),
                audio.getArtist(),
                audio.getLyrics_id(),
                null
        );
        dialog.show(getParentFragmentManager(), "audio_lyrics");
    }

    private void removeTrackFromPlaylist(Audio audio, int position) {
        audioManager.removeTracksFromPlaylist(playlistId, String.valueOf(audio.getId()), new AudioManager.AudioActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (position >= 0 && position < audios.size()) {
                        audios.remove(position);
                        audioAdapter.notifyItemRemoved(position);
                        String info = audios.size() + " " + getTrackWord(audios.size());
                        infoText.setText(info);
                        Toast.makeText(requireContext(), R.string.audio_removed_from_playlist, Toast.LENGTH_SHORT).show();
                    }
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

    private void showEditPlaylistDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 16);

        final android.widget.EditText inputTitle = new android.widget.EditText(requireContext());
        inputTitle.setHint(R.string.audio_title_hint);
        inputTitle.setText(title);
        layout.addView(inputTitle);

        final android.widget.EditText inputDesc = new android.widget.EditText(requireContext());
        inputDesc.setHint(R.string.profile_info_desc);
        inputDesc.setText(description != null ? description : "");
        layout.addView(inputDesc);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.audio_edit_playlist)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newTitle = inputTitle.getText().toString().trim();
                    String newDesc = inputDesc.getText().toString().trim();
                    if (newTitle.isEmpty()) return;

                    audioManager.editPlaylist(playlistId, newTitle, newDesc, new AudioManager.AudioActionCallback() {
                        @Override
                        public void onSuccess() {
                            if (!isAdded()) return;
                            title = newTitle;
                            description = newDesc;
                            requireActivity().runOnUiThread(() -> {
                                titleText.setText(newTitle);
                                if (!newDesc.isEmpty()) {
                                    descText.setText(newDesc);
                                    descText.setVisibility(View.VISIBLE);
                                } else {
                                    descText.setVisibility(View.GONE);
                                }
                                Toast.makeText(requireContext(), R.string.audio_playlist_updated, Toast.LENGTH_SHORT).show();
                            });
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
    }

    private void showDeletePlaylistDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.audio_delete_playlist)
                .setMessage(getString(R.string.audio_delete_playlist_confirm, title))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    audioManager.deletePlaylist(playlistId, new AudioManager.AudioActionCallback() {
                        @Override
                        public void onSuccess() {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), R.string.audio_playlist_deleted, Toast.LENGTH_SHORT).show();
                                requireActivity().getSupportFragmentManager().popBackStack();
                            });
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
