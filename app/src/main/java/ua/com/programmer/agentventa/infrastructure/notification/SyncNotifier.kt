package ua.com.programmer.agentventa.infrastructure.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.isRelayRest
import ua.com.programmer.agentventa.data.remote.SyncStats
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.presentation.main.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the result of a sync run to the system notification shade.
 *
 * How loud it is depends on the account's connection mode:
 *  - automatic (relay REST): syncs run unattended in the background, so only a
 *    run that actually moved data is worth interrupting for. Silent runs and
 *    transport errors stay in the log.
 *  - manual (direct 1C over HTTP): the user triggered the sync, so every
 *    outcome is reported — including "nothing changed" and errors.
 */
@Singleton
class SyncNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
) {

    private val logTag = "AV-SyncNotifier"
    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Reports the outcome of one sync run. [error] is the message of the last
     * `Result.Error` the run emitted, or null when it finished cleanly.
     */
    fun notifyResult(account: UserAccount?, stats: SyncStats, error: String?) {
        if (account == null) return

        val isAutoMode = account.isRelayRest()
        if (isAutoMode && (error != null || stats.isEmpty)) return

        val title = if (error != null) {
            context.getString(R.string.notification_sync_error_title)
        } else {
            context.getString(R.string.notification_sync_title)
        }
        val text = when {
            error != null -> error
            stats.isEmpty -> context.getString(R.string.notification_sync_no_changes)
            else -> context.getString(R.string.notification_sync_result, stats.sent, stats.received)
        }

        post(title, text)
    }

    private fun post(title: String, text: String) {
        if (!canPost()) {
            logger.d(logTag, "notification skipped: no permission")
            return
        }
        createChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_sync_24)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Pre-O devices have no channel to carry sound/vibration settings.
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            logger.w(logTag, "notification denied: ${e.message}")
        }
    }

    private fun canPost(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // IMPORTANCE_DEFAULT: shows in the shade with the system notification sound
    // and vibration. A channel's importance is fixed once created, so the id
    // carries a version suffix — bump it if the level ever needs to change again.
    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName(context.getString(R.string.notification_channel_sync))
            .setDescription(context.getString(R.string.notification_channel_sync_description))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val CHANNEL_ID = "sync_results_v2"
        // Fixed id: a new result replaces the previous one instead of stacking.
        const val NOTIFICATION_ID = 2001
    }
}
