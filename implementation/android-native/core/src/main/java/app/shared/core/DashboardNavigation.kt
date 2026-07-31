package app.shared.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

data class DashboardDestination(
    val section: String? = null,
    val query: String? = null,
    val favoritesOnly: Boolean? = null,
    val unreadOnly: Boolean? = null,
    val synchronizeNow: Boolean = false,
    val refreshNow: Boolean = false
)

object DashboardNavigation {
    const val EXTRA_SECTION = "dashboard_section"
    const val EXTRA_QUERY = "dashboard_query"
    const val EXTRA_FAVORITES_ONLY = "dashboard_favorites_only"
    const val EXTRA_UNREAD_ONLY = "dashboard_unread_only"
    const val EXTRA_SYNC_NOW = "dashboard_sync_now"
    const val EXTRA_REFRESH_NOW = "dashboard_refresh_now"

    const val SECTION_ALL = "Все"
    const val SECTION_SERVER = "Данные сервера"
    const val SECTION_UPDATES = "События"
    const val SECTION_HISTORY = "История действий"
    const val SECTION_RECENT = "Недавние"

    fun parse(intent: Intent?): DashboardDestination? {
        if (intent == null) return null

        val data = intent.data
        val hasNavigationExtras =
            intent.hasExtra(EXTRA_SECTION) ||
                intent.hasExtra(EXTRA_QUERY) ||
                intent.hasExtra(EXTRA_FAVORITES_ONLY) ||
                intent.hasExtra(EXTRA_UNREAD_ONLY) ||
                intent.hasExtra(EXTRA_SYNC_NOW) ||
                intent.hasExtra(EXTRA_REFRESH_NOW)

        if (!hasNavigationExtras && data == null) return null

        val section = normalizeSection(
            intent.getStringExtra(EXTRA_SECTION)
                ?: data?.getQueryParameter("section")
        )

        val query = if (intent.hasExtra(EXTRA_QUERY)) {
            intent.getStringExtra(EXTRA_QUERY)
        } else {
            data?.getQueryParameter("q")
                ?: data?.getQueryParameter("query")
        }

        return DashboardDestination(
            section = section,
            query = query,
            favoritesOnly = booleanValue(
                intent,
                data,
                EXTRA_FAVORITES_ONLY,
                "favorites"
            ),
            unreadOnly = booleanValue(
                intent,
                data,
                EXTRA_UNREAD_ONLY,
                "unread"
            ),
            synchronizeNow = booleanValue(
                intent,
                data,
                EXTRA_SYNC_NOW,
                "sync"
            ) == true,
            refreshNow = booleanValue(
                intent,
                data,
                EXTRA_REFRESH_NOW,
                "refresh"
            ) == true
        )
    }

    fun createIntent(
        context: Context,
        dashboardClass: Class<*>,
        section: String? = null,
        query: String? = null,
        favoritesOnly: Boolean? = null,
        unreadOnly: Boolean? = null,
        synchronizeNow: Boolean = false,
        refreshNow: Boolean = false
    ): Intent = Intent(context, dashboardClass).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        section?.let { putExtra(EXTRA_SECTION, it) }
        query?.let { putExtra(EXTRA_QUERY, it) }
        favoritesOnly?.let { putExtra(EXTRA_FAVORITES_ONLY, it) }
        unreadOnly?.let { putExtra(EXTRA_UNREAD_ONLY, it) }
        if (synchronizeNow) putExtra(EXTRA_SYNC_NOW, true)
        if (refreshNow) putExtra(EXTRA_REFRESH_NOW, true)
    }

    private fun normalizeSection(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        return when (value.lowercase(Locale.US)) {
            "all", "все" -> SECTION_ALL
            "server", "live", "данные сервера" -> SECTION_SERVER
            "events", "updates", "notifications", "события" -> SECTION_UPDATES
            "history", "история", "история действий" -> SECTION_HISTORY
            "recent", "недавние" -> SECTION_RECENT
            "favorites", "favourites", "избранное" -> SECTION_ALL
            else -> value
        }
    }

    private fun booleanValue(
        intent: Intent,
        data: Uri?,
        extraName: String,
        queryName: String
    ): Boolean? {
        if (intent.hasExtra(extraName)) {
            return intent.getBooleanExtra(extraName, false)
        }

        val raw = data?.getQueryParameter(queryName) ?: return null
        return when (raw.trim().lowercase(Locale.US)) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
    }
}
