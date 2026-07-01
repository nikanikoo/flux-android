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

public class HorizontalTracksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TRACK = 0;
    private static final int TYPE_MORE = 1;
    private static final int LIMIT = 9;

    private final List<Audio> tracks;
    private final OnItemClickListener listener;
    private final boolean isPopular; // If true, uses larger card layout (160dp), else standard (120dp)

    public interface OnItemClickListener {
        void onTrackClick(Audio audio, int position);
        void onMoreClick();
    }

    public HorizontalTracksAdapter(List<Audio> tracks, boolean isPopular, OnItemClickListener listener) {
        this.tracks = tracks;
        this.isPopular = isPopular;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        int maxTracks = Math.min(tracks.size(), LIMIT);
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
            int layoutId = isPopular ? R.layout.item_popular_track : R.layout.item_track_horizontal;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new TrackViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_more_button, parent, false);
            // Dynamic width adjustment for more button card to match the item size
            if (!isPopular) {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp != null) {
                    lp.width = (int) (120 * parent.getContext().getResources().getDisplayMetrics().density);
                    view.setLayoutParams(lp);
                    // Also adjust the inner MaterialCardView size
                    View card = view.findViewById(R.id.more_button_root).findViewById(android.R.id.content);
                    if (card == null && view instanceof ViewGroup) {
                        View child = ((ViewGroup) view).getChildAt(0);
                        if (child != null) {
                            ViewGroup.LayoutParams clp = child.getLayoutParams();
                            clp.width = (int) (120 * parent.getContext().getResources().getDisplayMetrics().density);
                            clp.height = (int) (120 * parent.getContext().getResources().getDisplayMetrics().density);
                            child.setLayoutParams(clp);
                        }
                    }
                }
            }
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
        int count = Math.min(tracks.size(), LIMIT);
        if (tracks.size() > LIMIT) {
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
            root = itemView;
            cover = itemView.findViewById(isPopular ? R.id.popular_cover : R.id.track_cover);
            title = itemView.findViewById(isPopular ? R.id.popular_title : R.id.track_title);
            artist = itemView.findViewById(isPopular ? R.id.popular_artist : R.id.track_artist);
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
            root = itemView;
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
