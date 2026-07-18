package org.nikanikoo.flux.ui.adapters.messages;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import org.nikanikoo.flux.R;

import java.util.List;

public class AttachmentPhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_CAMERA = 0;
    private static final int VIEW_TYPE_PHOTO  = 1;

    public interface OnItemClickListener {
        void onCameraClick();
        void onPhotoClick(Uri photoUri);
    }

    private final Context context;
    private final List<Uri> photoUris;
    private OnItemClickListener listener;

    public AttachmentPhotoAdapter(Context context, List<Uri> photoUris) {
        this.context   = context;
        this.photoUris = photoUris;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public List<Uri> getPhotoUris() {
        return photoUris;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_CAMERA : VIEW_TYPE_PHOTO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_CAMERA) {
            View v = inflater.inflate(R.layout.item_attachment_camera, parent, false);
            return new CameraViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_attachment_photo, parent, false);
            return new PhotoViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CameraViewHolder) {
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCameraClick();
            });
        } else {
            Uri uri = photoUris.get(position - 1);
            PhotoViewHolder pvh = (PhotoViewHolder) holder;
            Picasso.get()
                    .load(uri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .fit()
                    .centerCrop()
                    .into(pvh.thumbnail);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPhotoClick(uri);
            });
        }
    }

    @Override
    public int getItemCount() {
        return photoUris.size() + 1;
    }

    static class CameraViewHolder extends RecyclerView.ViewHolder {
        CameraViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.photo_thumbnail);
        }
    }
}
