package org.nikanikoo.flux.ui.views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.utils.AlbumArtFetcher;

import java.util.List;
import java.util.Collections;

/**
 * Helper class for displaying audio attachments in posts and comments
 */
public class AudioAttachmentView {

    public interface OnAudioClickListener {
        void onAudioPlay(Audio audio);
    }

    /**
     * Add audio attachments to a container view
     *
     * @param context Context
     * @param container Container to add audio views to
     * @param audios List of audio attachments
     * @param listener Click listener for audio playback
     */
    public static void addAudioAttachments(Context context, LinearLayout container,
                                          List<Audio> audios, OnAudioClickListener listener) {
        if (audios == null || audios.isEmpty()) {
            return;
        }

        container.removeAllViews();
        container.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < audios.size(); i++) {
            Audio audio = audios.get(i);
            final int position = i;
            View audioView = inflater.inflate(R.layout.item_audio_attachment, container, false);

            MaterialCardView audioCard = audioView.findViewById(R.id.audio_attachment_card);
            ImageView audioCover = audioView.findViewById(R.id.audio_attachment_cover);
            ImageView coverPlaceholder = audioView.findViewById(R.id.audio_attachment_cover_placeholder);
            TextView artistText = audioView.findViewById(R.id.audio_attachment_artist);
            TextView titleText = audioView.findViewById(R.id.audio_attachment_title);
            TextView durationText = audioView.findViewById(R.id.audio_attachment_duration);

            artistText.setText(audio.getArtist());
            titleText.setText(audio.getTitle());
            durationText.setText(audio.getFormattedDuration());

            AlbumArtFetcher albumArtFetcher = new AlbumArtFetcher(context);
            audioCover.setImageDrawable(null);
            if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.VISIBLE);
            albumArtFetcher.loadAlbumArt(audio.getArtist(), audio.getTitle(), audioCover, 0,
                    new AlbumArtFetcher.AlbumArtCallback() {
                        @Override
                        public void onSuccess(String imageUrl) {
                            if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.GONE);
                        }
                        @Override
                        public void onError(String error) {
                            if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.VISIBLE);
                        }
                    });

            audioCard.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAudioPlay(audio);
                } else {
                    AudioPlayerHelper.playOrToggleTrack(context, audios, position);
                }
            });

            container.addView(audioView);
        }
    }

    public static void addMessageAudioAttachment(Context context, LinearLayout container, Audio audio) {
        addMessageAudioAttachment(context, container, audio, Collections.singletonList(audio), 0);
    }

    public static void addMessageAudioAttachment(Context context, LinearLayout container, Audio audio,
                                                 List<Audio> playlist, int startPosition) {
        if (audio == null) return;
        container.removeAllViews();
        container.setVisibility(View.VISIBLE);

        View audioView = LayoutInflater.from(context).inflate(R.layout.item_audio, container, false);
        MaterialCardView audioCard = audioView.findViewById(R.id.audio_card);
        ImageView audioCover = audioView.findViewById(R.id.audio_cover);
        ImageView coverPlaceholder = audioView.findViewById(R.id.audio_cover_placeholder);
        TextView artistText = audioView.findViewById(R.id.audio_artist);
        TextView titleText = audioView.findViewById(R.id.audio_title);
        View downloadedIndicator = audioView.findViewById(R.id.audio_downloaded_indicator);
        View moreButton = audioView.findViewById(R.id.audio_more_button);

        artistText.setText(audio.getArtist());
        titleText.setText(audio.getTitle());
        downloadedIndicator.setVisibility(View.GONE);
        moreButton.setVisibility(View.GONE);
        audioCover.setImageDrawable(null);
        if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.VISIBLE);
        new AlbumArtFetcher(context).loadAlbumArt(audio.getArtist(), audio.getTitle(), audioCover, 0,
                new AlbumArtFetcher.AlbumArtCallback() {
                    @Override public void onSuccess(String imageUrl) {
                        if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.GONE);
                    }
                    @Override public void onError(String error) {
                        if (coverPlaceholder != null) coverPlaceholder.setVisibility(View.VISIBLE);
                    }
                });
        List<Audio> playbackQueue = playlist != null && !playlist.isEmpty()
                ? playlist : Collections.singletonList(audio);
        int queuePosition = startPosition >= 0 && startPosition < playbackQueue.size()
                ? startPosition : 0;
        audioCard.setOnClickListener(v -> AudioPlayerHelper.playOrToggleTrack(
                context, playbackQueue, queuePosition));
        container.addView(audioView);
    }

    /**
     * Clear audio attachments from container
     *
     * @param container Container to clear
     */
    public static void clearAudioAttachments(LinearLayout container) {
        if (container != null) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
        }
    }
}
