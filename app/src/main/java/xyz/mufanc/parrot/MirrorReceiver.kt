package xyz.mufanc.parrot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class MirrorReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.mirrored_notifications),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        intent.getStringArrayExtra(EXTRA_SYNC_KEYS)?.let { expectedKeys ->
            val expected = expectedKeys.toSet()
            notifications.activeNotifications
                .filter { it.id == MIRROR_NOTIFICATION_ID && it.tag !in expected }
                .forEach { notifications.cancel(it.tag, it.id) }
            updateSummary(context, notifications)
            return
        }
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        if (intent.getBooleanExtra(EXTRA_REMOVE, false)) {
            notifications.cancel(key, MIRROR_NOTIFICATION_ID)
        } else {
            notifications.notify(
                key,
                MIRROR_NOTIFICATION_ID,
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
                    .setContentText(intent.getStringExtra(EXTRA_TEXT))
                    .setSubText(intent.getStringExtra(EXTRA_SUBTEXT))
                    .setWhen(intent.getLongExtra(EXTRA_WHEN, 0))
                    .setGroup(GROUP_KEY)
                    .setLocalOnly(true)
                    .setOnlyAlertOnce(true)
                    .setOngoing(intent.getBooleanExtra(EXTRA_ONGOING, false))
                    .addExtras(preferSmallIconExtras())
                    .build(),
            )
        }
        updateSummary(context, notifications)
    }

    private fun updateSummary(context: Context, notifications: NotificationManager) {
        val count = notifications.activeNotifications.count { it.id == MIRROR_NOTIFICATION_ID }
        if (count == 0) {
            notifications.cancel(SUMMARY_TAG, SUMMARY_NOTIFICATION_ID)
            return
        }
        notifications.notify(
            SUMMARY_TAG,
            SUMMARY_NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.mirrored_notifications))
                .setContentText(context.getString(R.string.notification_count, count))
                .setShowWhen(false)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
                .addExtras(preferSmallIconExtras())
                .build(),
        )
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_REMOVE = "remove"
        const val EXTRA_SYNC_KEYS = "sync_keys"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SUBTEXT = "subtext"
        const val EXTRA_WHEN = "when"
        const val EXTRA_ONGOING = "ongoing"
        private const val GROUP_KEY = "mirrored_notifications"
        private const val SUMMARY_TAG = "summary"
        private const val CHANNEL_ID = "mirrored_notifications"
        private const val MIRROR_NOTIFICATION_ID = 1
        private const val SUMMARY_NOTIFICATION_ID = 2

        private fun preferSmallIconExtras() = Bundle().apply {
            putBoolean(Notification.EXTRA_PREFER_SMALL_ICON, true)
        }
    }
}
