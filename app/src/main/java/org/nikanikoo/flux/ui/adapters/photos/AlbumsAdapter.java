package org.nikanikoo.flux.ui.adapters.photos;

import android.content.Context;
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

public class AlbumsAdapter extends RecyclerView.Adapter<AlbumsAdapter.AlbumViewHolder> {

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    public interface OnAlbumLongClickListener {
        void onAlbumLongClick(Album album);
    }

    private final List<Album> albums;
    private final OnAlbumClickListener listener;
    private OnAlbumLongClickListener longClickListener;

    public AlbumsAdapter(List<Album> albums, OnAlbumClickListener listener) {
        this.albums = albums;
        this.listener = listener;
    }

    public void setOnAlbumLongClickListener(OnAlbumLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        holder.bind(albums.get(position));
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    class AlbumViewHolder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final ImageView placeholderIcon;
        final TextView title;
        final TextView photoCount;

        AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.album_cover);
            placeholderIcon = itemView.findViewById(R.id.album_placeholder_icon);
            title = itemView.findViewById(R.id.album_title);
            photoCount = itemView.findViewById(R.id.album_photo_count);
        }

        void bind(Album album) {
            title.setText(album.getTitle());

            Context ctx = itemView.getContext();
            int size = album.getSize();
            photoCount.setText(ctx.getResources().getQuantityString(
                    R.plurals.album_photos_count, size, size));

            String thumbUrl = album.getThumbSrc();
            if (thumbUrl != null && !thumbUrl.isEmpty()) {
                placeholderIcon.setVisibility(View.GONE);
                Picasso.get()
                        .load(thumbUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .fit()
                        .centerCrop()
                        .into(cover);
            } else {
                cover.setImageDrawable(null);
                placeholderIcon.setVisibility(View.VISIBLE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAlbumClick(album);
            });
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onAlbumLongClick(album);
                    return true;
                }
                return false;
            });
        }
    }
}
