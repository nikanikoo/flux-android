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

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.AudioCacheManager;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.utils.AlbumArtFetcher;

import java.util.List;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {

    private final List<Audio> audios;
    private final OnAudioClickListener listener;
    private AudioCacheManager audioCacheManager;

    public interface OnAudioClickListener {
        void onPlayClick(Audio audio, int position);
        void onAddClick(Audio audio, int position);
        void onMoreClick(Audio audio, int position, View anchor);
    }

    public AudioAdapter(List<Audio> audios, OnAudioClickListener listener) {
        this.audios = audios;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_audio, parent, false);
        return new AudioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
        holder.bind(audios.get(position));
    }

    @Override
    public int getItemCount() {
        return audios.size();
    }

    public void updateAudio(int position, Audio audio) {
        if (position >= 0 && position < audios.size()) {
            audios.set(position, audio);
            notifyItemChanged(position);
        }
    }

    class AudioViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView audioCard;
        ImageView audioCover;
        TextView artistText;
        TextView titleText;
        TextView durationText;
        ImageView downloadedIndicator;
        ImageView addButton;
        ImageView moreButton;

        AlbumArtFetcher albumArtFetcher;

        AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            Context context = itemView.getContext();
            audioCard = itemView.findViewById(R.id.audio_card);
            audioCover = itemView.findViewById(R.id.audio_cover);
            artistText = itemView.findViewById(R.id.audio_artist);
            titleText = itemView.findViewById(R.id.audio_title);
            downloadedIndicator = itemView.findViewById(R.id.audio_downloaded_indicator);
            moreButton = itemView.findViewById(R.id.audio_more_button);
            albumArtFetcher = new AlbumArtFetcher(context);
            audioCacheManager = AudioCacheManager.getInstance(context);
        }

        void bind(Audio audio) {
            artistText.setText(audio.getArtist());
            titleText.setText(audio.getTitle());
            downloadedIndicator.setVisibility(audioCacheManager.isDownloaded(audio) ? View.VISIBLE : View.GONE);
            loadAlbumArt(audio);

            moreButton.setImageResource(R.drawable.ic_more_vert);
            moreButton.setContentDescription(itemView.getContext().getString(R.string.audio_more));

            audioCard.setOnClickListener(v -> dispatchPosition((currentAudio, position) ->
                    listener.onPlayClick(currentAudio, position)));

            moreButton.setOnClickListener(v -> dispatchPosition((currentAudio, position) ->
                    listener.onMoreClick(currentAudio, position, v)));
        }

        private void dispatchPosition(AudioAction action) {
            if (listener == null) {
                return;
            }

            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || position >= audios.size()) {
                return;
            }

            action.run(audios.get(position), position);
        }

        private void loadAlbumArt(Audio audio) {
            if (audioCover == null) return;

            String artist = audio.getArtist();
            String title = audio.getTitle();

            if (artist == null || title == null || artist.isEmpty() || title.isEmpty()) {
                audioCover.setImageResource(R.drawable.ic_music_note);
                return;
            }

            albumArtFetcher.loadAlbumArt(artist, title, audioCover, R.drawable.ic_music_note);
        }
    }

    private interface AudioAction {
        void run(Audio audio, int position);
    }
}
