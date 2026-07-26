package org.nikanikoo.flux.ui.adapters.photos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.Photo;

import java.util.List;

public class AlbumPhotosAdapter extends RecyclerView.Adapter<AlbumPhotosAdapter.PhotoViewHolder> {

    public interface OnPhotoClickListener {
        void onPhotoClick(Photo photo, int position);
    }

    private final List<Photo> photos;
    private final OnPhotoClickListener listener;

    public AlbumPhotosAdapter(List<Photo> photos, OnPhotoClickListener listener) {
        this.photos = photos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_grid, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        holder.bind(photos.get(position), position);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView photoImage;
        final ImageView placeholder;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImage = itemView.findViewById(R.id.photo_image);
            placeholder = itemView.findViewById(R.id.photo_placeholder);
        }

        void bind(Photo photo, int position) {
            String thumbUrl = photo.getThumbnailUrl();

            if (thumbUrl != null && !thumbUrl.isEmpty()) {
                placeholder.setVisibility(View.GONE);
                Picasso.get()
                        .load(thumbUrl)
                        .fit()
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .into(photoImage, new Callback() {
                            @Override
                            public void onSuccess() {
                                placeholder.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(Exception e) {
                                placeholder.setVisibility(View.VISIBLE);
                            }
                        });
            } else {
                photoImage.setImageDrawable(null);
                placeholder.setVisibility(View.VISIBLE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPhotoClick(photo, position);
            });
        }
    }
}
