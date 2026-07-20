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

public class TracksHorizontalAdapter extends RecyclerView.Adapter<TracksHorizontalAdapter.ViewHolder> {

    private final List<Audio> tracks;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Audio audio, int position);
    }

    public TracksHorizontalAdapter(List<Audio> tracks, OnItemClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track_horizontal, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(tracks.get(position), position);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View root;
        ImageView cover;
        TextView title;
        TextView artist;
        AlbumArtFetcher albumArtFetcher;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.track_item_root);
            cover = itemView.findViewById(R.id.track_cover);
            title = itemView.findViewById(R.id.track_title);
            artist = itemView.findViewById(R.id.track_artist);
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
                    listener.onItemClick(audio, position);
                }
            });
        }
    }
}
