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
import org.nikanikoo.flux.data.models.AudioPlaylist;

import java.util.List;

public class PlaylistsHorizontalAdapter extends RecyclerView.Adapter<PlaylistsHorizontalAdapter.ViewHolder> {

    private final List<AudioPlaylist> playlists;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AudioPlaylist playlist);
    }

    public PlaylistsHorizontalAdapter(List<AudioPlaylist> playlists, OnItemClickListener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_horizontal, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(playlists.get(position));
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View root;
        ImageView cover;
        TextView title;
        TextView author;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView;
            cover = itemView.findViewById(R.id.playlist_cover);
            title = itemView.findViewById(R.id.playlist_title);
            author = itemView.findViewById(R.id.playlist_author);
        }

        void bind(AudioPlaylist playlist) {
            title.setText(playlist.getTitle());
            author.setText(playlist.getAuthorName() != null && !playlist.getAuthorName().isEmpty() ? playlist.getAuthorName() : "Плейлист");

            if (playlist.getPhotoUrl() != null && !playlist.getPhotoUrl().isEmpty()) {
                Picasso.get().load(playlist.getPhotoUrl()).placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note).into(cover);
            } else {
                cover.setImageResource(R.drawable.ic_music_note);
            }

            root.setOnClickListener(v -> {
                System.out.println("PlaylistsHorizontalAdapter: item CLICKED title=" + playlist.getTitle());
                if (listener != null) {
                    listener.onItemClick(playlist);
                }
            });
        }
    }
}
