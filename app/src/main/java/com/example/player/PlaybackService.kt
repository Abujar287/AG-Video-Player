package com.example.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val stopCommand = SessionCommand(ACTION_STOP_AND_DISMISS, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(MEDIA_CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(MEDIA_NOTIFICATION_ID)
            .build().apply {
                setSmallIcon(R.drawable.ic_notif_video)
            }
        setMediaNotificationProvider(notificationProvider)

        val player = PlayerSessionManager.getOrCreatePlayer(this)
        val launchIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName("Turn Off")
            .setCustomIconResId(R.drawable.ic_notif_stop)
            .setSessionCommand(stopCommand)
            .setExtras(
                Bundle().apply {
                    putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, 2)
                }
            )
            .build()

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(stopCommand)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_STOP_AND_DISMISS) {
                    PlayerSessionManager.stopPlaybackFromNotification()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(callback)
            .setCustomLayout(listOf(stopButton))
            .build()

        PlayerSessionManager.setOnPlayerRebuiltListener { newPlayer ->
            mediaSession?.player = newPlayer
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (!PlayerSessionManager.isBackgroundPlayEnabled.value ||
            player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            PlayerSessionManager.releasePlayer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlayerSessionManager.setOnPlayerRebuiltListener(null)
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val MEDIA_CHANNEL_ID = "ag_video_playback_channel"
        private const val MEDIA_NOTIFICATION_ID = 2001
        const val ACTION_STOP_AND_DISMISS = "com.example.player.ACTION_STOP_AND_DISMISS"
    }
}
