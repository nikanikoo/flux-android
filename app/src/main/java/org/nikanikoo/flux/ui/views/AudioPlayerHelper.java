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
