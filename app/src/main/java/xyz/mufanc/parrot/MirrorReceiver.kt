package xyz.mufanc.parrot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MirrorReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (intent.getBooleanExtra(EXTRA_REMOVE, false)) {
            notifications.cancel(key, MIRROR_NOTIFICATION_ID)
            return
        }
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.mirrored_notifications),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        notifications.notify(
            key,
            MIRROR_NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_parrot)
                .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
                .setContentText(intent.getStringExtra(EXTRA_TEXT))
                .setSubText(intent.getStringExtra(EXTRA_SUBTEXT))
                .setWhen(intent.getLongExtra(EXTRA_WHEN, 0))
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
                .setOngoing(intent.getBooleanExtra(EXTRA_ONGOING, false))
                .build(),
        )
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_REMOVE = "remove"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SUBTEXT = "subtext"
        const val EXTRA_WHEN = "when"
        const val EXTRA_ONGOING = "ongoing"
        private const val CHANNEL_ID = "mirrored_notifications"
        private const val MIRROR_NOTIFICATION_ID = 1
    }
}
