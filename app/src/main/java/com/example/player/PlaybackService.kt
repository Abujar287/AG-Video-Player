package com.example.player

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.R

class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isRunning = false
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_DETACH -> {
                isRunning = false
                val notif = MediaNotificationHelper.latestNotification
                    ?: MediaNotificationHelper.buildFallbackNotification(this)
                startForegroundCompat(notif)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                return START_NOT_STICKY
            }
            else -> {
                val notif = MediaNotificationHelper.latestNotification
                if (notif == null) {
                    isRunning = false
                    startForegroundCompat(MediaNotificationHelper.buildFallbackNotification(this))
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    isRunning = true
                    startForegroundCompat(notif)
                }
                return START_NOT_STICKY
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                MediaNotificationHelper.NOTIFICATION_ID,
                notification,
                fgsType
            )
        }
    }

    override fun onDestroy() {
        isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START_OR_UPDATE = "com.example.player.ACTION_NOTIFICATION_START"
        const val ACTION_PAUSE_DETACH = "com.example.player.ACTION_NOTIFICATION_PAUSE"
        const val ACTION_STOP = "com.example.player.ACTION_NOTIFICATION_STOP"
    }
}

object MediaNotificationHelper {
    const val CHANNEL_ID = "ag_media_playback_channel"
    const val NOTIFICATION_ID = 4102

    const val ACTION_PLAYER_CONTROL = "com.example.player.ACTION_PIP_CONTROL"
    const val EXTRA_CONTROL_TYPE = "control_type"
    const val CONTROL_TYPE_PREVIOUS = 1
    const val CONTROL_TYPE_PLAY_PAUSE = 2
    const val CONTROL_TYPE_NEXT = 3
    const val CONTROL_TYPE_STOP = 4

    @Volatile
    var latestNotification: Notification? = null
        private set

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback Controls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows currently playing video controls and metadata"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun buildFallbackNotification(context: Context): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_video)
            .setContentTitle("AG Video Player")
            .setContentText("Preparing playback…")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    @OptIn(UnstableApi::class)
    fun showOrUpdate(
        context: Context,
        session: MediaSession?,
        title: String,
        isPlaying: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean
    ) {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        val contentIntent = PendingIntent.getActivity(
            appContext,
            200,
            Intent(appContext, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPending = controlPendingIntent(appContext, CONTROL_TYPE_PREVIOUS, 201)
        val playPausePending = controlPendingIntent(appContext, CONTROL_TYPE_PLAY_PAUSE, 202)
        val nextPending = controlPendingIntent(appContext, CONTROL_TYPE_NEXT, 203)
        val stopPending = controlPendingIntent(appContext, CONTROL_TYPE_STOP, 204)

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_video)
            .setContentTitle(title.ifBlank { "AG Video Player" })
            .setContentText(if (isPlaying) "Playing · AG Video Player" else "Paused · AG Video Player")
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(isPlaying)
            .setShowWhen(false)

        if (hasPrevious) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    prevPending
                ).build()
            )
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePending
            ).build()
        )

        if (hasNext) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextPending
                ).build()
            )
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notif_stop,
                "Stop",
                stopPending
            ).build()
        )

        if (session != null) {
            val compactIndices = when {
                hasPrevious && hasNext -> intArrayOf(0, 1, 2)
                hasPrevious || hasNext -> intArrayOf(0, 1)
                else -> intArrayOf(0)
            }
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(*compactIndices)
            )
        }

        val notification = builder.build()
        latestNotification = notification

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.notify(NOTIFICATION_ID, notification) }
        }

        if (isPlaying) {
            val serviceIntent = Intent(appContext, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_START_OR_UPDATE
            }
            runCatching { ContextCompat.startForegroundService(appContext, serviceIntent) }
        } else if (PlaybackService.isRunning) {
            val serviceIntent = Intent(appContext, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PAUSE_DETACH
            }
            runCatching { appContext.startService(serviceIntent) }
        }
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        latestNotification = null
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(NOTIFICATION_ID) }
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
    }

    private fun controlPendingIntent(context: Context, controlType: Int, requestCode: Int): PendingIntent {
        val intent = Intent(ACTION_PLAYER_CONTROL).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CONTROL_TYPE, controlType)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
