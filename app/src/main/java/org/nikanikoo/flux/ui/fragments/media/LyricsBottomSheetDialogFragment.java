package org.nikanikoo.flux.ui.fragments.media;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioManager;

public class LyricsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_ARTIST = "artist";
    private static final String ARG_LYRICS_ID = "lyrics_id";
    private static final String ARG_LYRICS_TEXT = "lyrics_text";

    private String title;
    private String artist;
    private int lyricsId;
    private String lyricsText;

    private TextView titleView;
    private TextView artistView;
    private TextView contentView;
    private TextView emptyView;
    private ProgressBar progressBar;
    private NestedScrollView scrollView;
    private ImageView btnClose;

    public static LyricsBottomSheetDialogFragment newInstance(String title, String artist, int lyricsId, String lyricsText) {
        LyricsBottomSheetDialogFragment fragment = new LyricsBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_ARTIST, artist);
        args.putInt(ARG_LYRICS_ID, lyricsId);
        args.putString(ARG_LYRICS_TEXT, lyricsText);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            title = getArguments().getString(ARG_TITLE, "");
            artist = getArguments().getString(ARG_ARTIST, "");
            lyricsId = getArguments().getInt(ARG_LYRICS_ID, 0);
            lyricsText = getArguments().getString(ARG_LYRICS_TEXT, null);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_lyrics, container, false);

        titleView = view.findViewById(R.id.lyrics_track_title);
        artistView = view.findViewById(R.id.lyrics_track_artist);
        contentView = view.findViewById(R.id.lyrics_content);
        emptyView = view.findViewById(R.id.lyrics_empty_text);
        progressBar = view.findViewById(R.id.lyrics_progress);
        scrollView = view.findViewById(R.id.lyrics_scroll);
        btnClose = view.findViewById(R.id.btn_close_lyrics);

        titleView.setText(title);
        artistView.setText(artist);
        btnClose.setOnClickListener(v -> dismiss());

        loadLyrics();

        return view;
    }

    private void loadLyrics() {
        if (lyricsText != null && !lyricsText.trim().isEmpty()) {
            showLyrics(lyricsText);
            return;
        }

        if (lyricsId <= 0) {
            showEmpty();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        AudioManager.getInstance(requireContext()).getLyrics(lyricsId, new AudioManager.LyricsCallback() {
            @Override
            public void onSuccess(String text) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (text != null && !text.trim().isEmpty()) {
                        showLyrics(text);
                    } else {
                        showEmpty();
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showEmpty();
                });
            }
        });
    }

    private void showLyrics(String text) {
        contentView.setText(text.trim());
        scrollView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showEmpty() {
        scrollView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }
}
