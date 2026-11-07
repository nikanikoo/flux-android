package org.nikanikoo.flux.ui.views;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.nikanikoo.flux.data.models.Audio;
import org.nikanikoo.flux.services.AudioPlayerService;
import org.nikanikoo.flux.utils.Logger;

import java.util.List;

/**
 * Helper class for managing audio player service
 */
public class AudioPlayerHelper {
    private static final String TAG = "AudioPlayerHelper";

    public static void playOrToggleTrack(Context context, List<Audio> playlist, int startPosition) {
        if (playlist == null || playlist.isEmpty() || startPosition < 0 || startPosition >= playlist.size()) {
            return;
        }

        Intent intent = new Intent(context, AudioPlayerService.class);
        context.startService(intent);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                AudioPlayerService playerService = ((AudioPlayerService.AudioBinder) service).getService();
                Audio selectedAudio = playlist.get(startPosition);
                Audio currentAudio = playerService.getCurrentAudio();
                if (isSameAudio(currentAudio, selectedAudio)) {
                    if (playerService.isPlaying()) {
                        playerService.pause();
                    } else {
                        playerService.play();
                    }
                } else {
                    playerService.setPlaylist(playlist, startPosition);
                }
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        }, Context.BIND_AUTO_CREATE);
    }

    private static boolean isSameAudio(Audio first, Audio second) {
        if (first == null || second == null) return false;
        if (first.getId() > 0 && second.getId() > 0) {
            return first.getId() == second.getId() && first.getOwnerId() == second.getOwnerId();
        }
        if (first.getUniqueId() != null && !first.getUniqueId().isEmpty()
                && second.getUniqueId() != null && !second.getUniqueId().isEmpty()) {
            return first.getUniqueId().equals(second.getUniqueId());
        }
        return first.getUrl() != null && !first.getUrl().isEmpty()
                && first.getUrl().equals(second.getUrl());
    }

    public static void setPlaylist(Context context, List<Audio> playlist, int startPosition) {
        Intent intent = new Intent(context, AudioPlayerService.class);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                AudioPlayerService.AudioBinder binder = (AudioPlayerService.AudioBinder) service;
                AudioPlayerService playerService = binder.getService();
                playerService.setPlaylist(playlist, startPosition);
                context.unbindService(this);
                Logger.d(TAG, "Playlist set successfully");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
    }

    public static void appendToPlaylist(Context context, List<Audio> audios) {
        Intent intent = new Intent(context, AudioPlayerService.class);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                AudioPlayerService.AudioBinder binder = (AudioPlayerService.AudioBinder) service;
                AudioPlayerService playerService = binder.getService();
                playerService.appendToPlaylist(audios);
                context.unbindService(this);
                Logger.d(TAG, "Playlist appended successfully");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
    }

    public static void playNext(Context context, Audio audio) {
        Intent intent = new Intent(context, AudioPlayerService.class);
        context.startService(intent);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                AudioPlayerService.AudioBinder binder = (AudioPlayerService.AudioBinder) service;
                AudioPlayerService playerService = binder.getService();
                playerService.playNext(audio);
                context.unbindService(this);
                Logger.d(TAG, "Track inserted next successfully");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
    }
}
