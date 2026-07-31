package app.shared.core

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import java.util.Locale

object DashboardShortcutManager {
    fun install(
        context: Context,
        dashboardClass: Class<*>,
        productName: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        try {
            val manager = context.getSystemService(
                ShortcutManager::class.java
            ) ?: return
            val prefix = productName.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
            val icon = Icon.createWithResource(
                context,
                context.applicationInfo.icon
            )

            val shortcuts = listOf(
                ShortcutInfo.Builder(context, "$prefix-events")
                    .setShortLabel("События")
                    .setLongLabel("$productName: непрочитанные события")
                    .setIcon(icon)
                    .setIntent(
                        DashboardNavigation.createIntent(
                            context,
                            dashboardClass,
                            section = DashboardNavigation.SECTION_UPDATES,
                            unreadOnly = true
                        )
                    )
                    .build(),

                ShortcutInfo.Builder(context, "$prefix-favorites")
                    .setShortLabel("Избранное")
                    .setLongLabel("$productName: избранные карточки")
                    .setIcon(icon)
                    .setIntent(
                        DashboardNavigation.createIntent(
                            context,
                            dashboardClass,
                            section = DashboardNavigation.SECTION_ALL,
                            favoritesOnly = true
                        )
                    )
                    .build(),

                ShortcutInfo.Builder(context, "$prefix-server")
                    .setShortLabel("Сервер")
                    .setLongLabel("$productName: серверные данные")
                    .setIcon(icon)
                    .setIntent(
                        DashboardNavigation.createIntent(
                            context,
                            dashboardClass,
                            section = DashboardNavigation.SECTION_SERVER,
                            refreshNow = true
                        )
                    )
                    .build(),

                ShortcutInfo.Builder(context, "$prefix-sync")
                    .setShortLabel("Синхронизация")
                    .setLongLabel("$productName: синхронизировать сейчас")
                    .setIcon(icon)
                    .setIntent(
                        DashboardNavigation.createIntent(
                            context,
                            dashboardClass,
                            section = DashboardNavigation.SECTION_UPDATES,
                            synchronizeNow = true
                        )
                    )
                    .build()
            )

            val maximum = manager.maxShortcutCountPerActivity
                .coerceAtLeast(1)
            manager.setDynamicShortcuts(shortcuts.take(maximum))
        } catch (_: Exception) {
            // Shortcuts are an optional launcher capability.
        }
    }
}
