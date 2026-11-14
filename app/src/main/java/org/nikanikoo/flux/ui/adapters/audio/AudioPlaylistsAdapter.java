package org.nikanikoo.flux.ui.adapters.audio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.Album;

import java.util.List;

public class AudioPlaylistsAdapter extends RecyclerView.Adapter<AudioPlaylistsAdapter.PlaylistHolder> {
    public interface Listener {
        void onPlaylistClick(Album album);
    }

    private final List<Album> albums;
    private final Listener listener;

    public AudioPlaylistsAdapter(List<Album> albums, Listener listener) {
        this.albums = albums;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PlaylistHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_audio_playlist, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistHolder holder, int position) {
        Album album = albums.get(position);
        holder.title.setText(album.getTitle());
        holder.count.setText(holder.itemView.getResources().getQuantityString(
                R.plurals.audio_playlist_tracks, album.getSize(), album.getSize()));
        if (album.getThumbSrc() == null || album.getThumbSrc().isEmpty()) {
            holder.cover.setImageResource(R.drawable.ic_music_note);
        } else {
            Picasso.get().load(album.getThumbSrc()).placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note).into(holder.cover);
        }
        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(album));
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    static class PlaylistHolder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView count;

        PlaylistHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.playlist_cover);
            title = itemView.findViewById(R.id.playlist_title);
            count = itemView.findViewById(R.id.playlist_count);
        }
    }
}
