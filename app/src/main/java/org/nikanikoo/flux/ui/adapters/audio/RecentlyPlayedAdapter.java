package org.nikanikoo.flux.ui.adapters.audio;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.RecentlyPlayedManager;
import org.nikanikoo.flux.utils.AlbumArtFetcher;

import java.util.List;

public class RecentlyPlayedAdapter extends RecyclerView.Adapter<RecentlyPlayedAdapter.ViewHolder> {

    private final List<RecentlyPlayedManager.Item> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(RecentlyPlayedManager.Item item);
    }

    public RecentlyPlayedAdapter(List<RecentlyPlayedManager.Item> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recently_played, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView cover;
        TextView title;
        TextView subtitle;
        AlbumArtFetcher albumArtFetcher;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            cover = itemView.findViewById(R.id.cover_image);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            albumArtFetcher = new AlbumArtFetcher(itemView.getContext());
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

        void bind(RecentlyPlayedManager.Item item) {
            title.setText(item.title);
            
            if ("playlist".equals(item.type)) {
                subtitle.setText(item.creatorName != null && !item.creatorName.isEmpty() ? item.creatorName : "Плейлист");
                if (item.id == -2) {
                    cover.setImageResource(R.drawable.ic_library_music);
                    cover.setBackgroundColor(getGenreColor(item.ownerId));
                    cover.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                } else {
                    cover.setBackgroundColor(0);
                    cover.setImageTintList(null);
                    if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
                        Picasso.get().load(item.coverUrl).placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note).into(cover);
                    } else {
                        cover.setImageResource(R.drawable.ic_music_note);
                    }
                }
            } else {
                subtitle.setText("Исполнитель");
                cover.setBackgroundColor(0);
                cover.setImageTintList(null);
                albumArtFetcher.loadArtistImage(item.title, cover, R.drawable.ic_music_note);
            }

            card.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
