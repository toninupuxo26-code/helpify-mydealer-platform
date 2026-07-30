package app.shared.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DashboardLibraryEntry(
    val key: String,
    val role: String,
    val title: String,
    val description: String,
    val details: String,
    val section: String,
    val badge: String,
    val viewedAtMillis: Long
)

class DashboardLibraryStore(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences(
        "${namespace.lowercase()}_dashboard_library",
        Context.MODE_PRIVATE
    )

    fun isFavorite(role: String, key: String): Boolean =
        preferences.getStringSet(favoritesKey(role), emptySet())
            ?.contains(key) == true

    fun toggleFavorite(role: String, key: String): Boolean {
        val favorites = preferences
            .getStringSet(favoritesKey(role), emptySet())
            .orEmpty()
            .toMutableSet()

        val favorite = if (favorites.contains(key)) {
            favorites.remove(key)
            false
        } else {
            favorites.add(key)
            true
        }

        preferences.edit()
            .putStringSet(favoritesKey(role), HashSet(favorites))
            .apply()

        return favorite
    }

    fun recordRecent(role: String, entry: DashboardLibraryEntry) {
        val updated = recent(role, MAX_RECENT)
            .filterNot { it.key == entry.key }
            .toMutableList()

        updated.add(0, entry.copy(role = role))
        saveRecent(role, updated.take(MAX_RECENT))
    }

    fun recent(role: String, limit: Int = MAX_RECENT): List<DashboardLibraryEntry> {
        val raw = preferences.getString(recentKey(role), "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<DashboardLibraryEntry>()

            for (index in 0 until array.length()) {
                if (result.size >= limit) break
                val item = array.optJSONObject(index) ?: continue

                result += DashboardLibraryEntry(
                    key = item.optString("key"),
                    role = item.optString("role", role),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    details = item.optString("details"),
                    section = item.optString("section"),
                    badge = item.optString("badge"),
                    viewedAtMillis = item.optLong("viewedAtMillis")
                )
            }

            result
        } catch (_: Exception) {
            clearRecent(role)
            emptyList()
        }
    }

    fun clearRecent(role: String) {
        preferences.edit().remove(recentKey(role)).apply()
    }

    private fun saveRecent(
        role: String,
        entries: List<DashboardLibraryEntry>
    ) {
        val array = JSONArray()

        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("role", role)
                    .put("title", entry.title)
                    .put("description", entry.description)
                    .put("details", entry.details)
                    .put("section", entry.section)
                    .put("badge", entry.badge)
                    .put("viewedAtMillis", entry.viewedAtMillis)
            )
        }

        preferences.edit()
            .putString(recentKey(role), array.toString())
            .apply()
    }

    private fun favoritesKey(role: String): String = "favorites_$role"
    private fun recentKey(role: String): String = "recent_$role"

    private companion object {
        const val MAX_RECENT = 20
    }
}
