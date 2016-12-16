package com.freshollie.headunitcontroller;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

/**
 * Created by Freshollie on 13/12/2016.
 */

public class MediaMonitoringService extends Service implements
        MediaSessionManager.OnActiveSessionsChangedListener{

    public static String TAG = "MediaMonitoringService";
    private MediaController lastMusicPlaybackController;
    private MediaSessionManager mediaSessionManager;

    private NotificationHandler notificationHandler;

    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        Log.v(TAG, "Started");
        bindActiveSessionsChangedListener();

        notificationHandler = new NotificationHandler(getApplicationContext());

        sharedPreferences = getApplicationContext()
                .getSharedPreferences(
                        getString(R.string.PREFERENCES_KEY),
                        Context.MODE_PRIVATE
                );

        startForeground(
                NotificationHandler.SERVICE_NOTIFICATION,
                notificationHandler.notifyStatus(getString(R.string.notify_running)
                )
        );
    }

    public void bindActiveSessionsChangedListener() {
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        mediaSessionManager.addOnActiveSessionsChangedListener(
                this,
                new ComponentName(getApplicationContext(), DrivingModeListener.class)
        );
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Called if any new app starts or controlling music.
     * If will store the list of MediaControllers for later
     *
     * @param list of MediaControllers
     */
    @Override
    public void onActiveSessionsChanged(List<MediaController> list) {
        Log.i(TAG, "found " + list.size() + " controllers");
        checkMediaControllerList(list);
    }

    public void checkMediaControllerList(List<MediaController> mediaControllers) {
        for (MediaController mediaController: mediaControllers) {
            if (!(mediaController.getPackageName().equals(getPackageName()))) {
                PlaybackState playbackState = mediaController.getPlaybackState();
                if (playbackState != null) {
                    if (playbackState.getState() == PlaybackState.STATE_PLAYING) {
                        registerLastPlaybackApp(mediaController);
                    }
                }
            }
        }
    }

    public void registerLastPlaybackApp(final MediaController appMediaController) {
        if (PowerUtil.isConnected(getApplicationContext())) {
            lastMusicPlaybackController = appMediaController;
            String packageName = "none";

            if (appMediaController != null) {
                appMediaController.registerCallback(new MediaController.Callback() {
                    /**
                     * Register a callback to wait for the music app to stop playing music.
                     * If it stops playing then we make sure the music app wont be played on start
                     *
                     * @param state Playback state of the app
                     */
                    @Override
                    public void onPlaybackStateChanged(PlaybackState state) {
                        super.onPlaybackStateChanged(state);
                        appMediaController.unregisterCallback(this);

                        if (state.getState() == PlaybackState.STATE_PLAYING) {
                            registerLastPlaybackApp(appMediaController);
                        } else {
                            // Check if the current last playback app is this app
                            if (lastMusicPlaybackController == appMediaController) {
                                registerLastPlaybackApp(null);
                            }
                        }
                    }
                });
                packageName = appMediaController.getPackageName();
            }

            Log.v(TAG, "Registering last playback app " + String.valueOf(packageName));

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(getString(R.string.PLAYING_AUDIO_APP_KEY), packageName);
            editor.apply();
        }
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "Stopping");
        notificationHandler.cancel(notificationHandler.SERVICE_NOTIFICATION);
        mediaSessionManager.removeOnActiveSessionsChangedListener(this);

    }

}