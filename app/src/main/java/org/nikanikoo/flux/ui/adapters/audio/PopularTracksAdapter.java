package org.nikanikoo.flux.ui.adapters.audio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.utils.AlbumArtFetcher;

import java.util.List;

public class PopularTracksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TRACK = 0;
    private static final int TYPE_MORE = 1;

    private final List<Audio> tracks;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onTrackClick(Audio audio, int position);
        void onMoreClick();
    }

    public PopularTracksAdapter(List<Audio> tracks, OnItemClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        int maxTracks = Math.min(tracks.size(), 4);
        if (position < maxTracks) {
            return TYPE_TRACK;
        } else {
            return TYPE_MORE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_TRACK) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_popular_track, parent, false);
            return new TrackViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_more_button, parent, false);
            return new MoreViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_TRACK) {
            ((TrackViewHolder) holder).bind(tracks.get(position), position);
        } else {
            ((MoreViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        int count = Math.min(tracks.size(), 4);
        if (tracks.size() > 4) {
            count += 1; // plus "More" button
        }
        return count;
    }

    class TrackViewHolder extends RecyclerView.ViewHolder {
        View root;
        ImageView cover;
        TextView title;
        TextView artist;
        AlbumArtFetcher albumArtFetcher;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.popular_item_root);
            cover = itemView.findViewById(R.id.popular_cover);
            title = itemView.findViewById(R.id.popular_title);
            artist = itemView.findViewById(R.id.popular_artist);
            albumArtFetcher = new AlbumArtFetcher(itemView.getContext());
        }

        void bind(Audio audio, int position) {
            title.setText(audio.getTitle());
            artist.setText(audio.getArtist());

            if (audio.getArtist() != null && audio.getTitle() != null) {
                albumArtFetcher.loadAlbumArt(audio.getArtist(), audio.getTitle(), cover, R.drawable.ic_music_note);
            } else {
                cover.setImageResource(R.drawable.ic_music_note);
            }

            root.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackClick(audio, position);
                }
            });
        }
    }

    class MoreViewHolder extends RecyclerView.ViewHolder {
        View root;

        MoreViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.more_button_root);
        }

        void bind() {
            root.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoreClick();
                }
            });
        }
    }
}
