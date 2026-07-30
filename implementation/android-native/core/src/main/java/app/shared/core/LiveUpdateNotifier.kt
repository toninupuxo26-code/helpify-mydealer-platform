package app.shared.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class LiveUpdateNotifier(
    private val context: Context,
    private val productName: String
) {
    private val manager = context.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    fun notifyChanges(
        entries: List<LiveUpdateEntry>,
        settings: LiveUpdateSettings
    ) {
        if (!settings.notificationsEnabled) return

        val selected = entries.filter { entry ->
            when (entry.kind) {
                LiveUpdateKind.NEW_ITEM -> settings.notifyNewItems
                LiveUpdateKind.STATUS_CHANGED -> settings.notifyStatusChanges
            }
        }

        if (selected.isEmpty()) return

        ensureChannel()

        val newCount = selected.count { it.kind == LiveUpdateKind.NEW_ITEM }
        val changedCount = selected.count {
            it.kind == LiveUpdateKind.STATUS_CHANGED
        }
        val summary = buildString {
            if (newCount > 0) append("Новых: $newCount")
            if (changedCount > 0) {
                if (isNotEmpty()) append(" · ")
                append("Статусы: $changedCount")
            }
        }
        val details = selected
            .take(5)
            .joinToString("\n") { "• ${it.title}: ${it.message}" }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("$productName · изменения")
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setAutoCancel(true)

        manager.notify(notificationId(), builder.build())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "$productName: события сервера",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Новые объекты и изменения статусов"
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(): Int =
        (productName.hashCode() and 0x7fffffff) % 100000 + 1400

    private companion object {
        const val CHANNEL_ID = "server_updates"
    }
}
