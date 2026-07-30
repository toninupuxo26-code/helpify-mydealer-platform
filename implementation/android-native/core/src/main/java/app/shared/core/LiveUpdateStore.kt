package app.shared.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LiveUpdateEntry(
    val id: String,
    val role: String,
    val kind: LiveUpdateKind,
    val title: String,
    val message: String,
    val timestampMillis: Long,
    val read: Boolean
)

enum class LiveUpdateKind {
    NEW_ITEM,
    STATUS_CHANGED
}

data class LiveUpdateSettings(
    val notificationsEnabled: Boolean = true,
    val notifyNewItems: Boolean = true,
    val notifyStatusChanges: Boolean = true
)

class LiveUpdateStore(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences(
        "${namespace.lowercase()}_live_updates",
        Context.MODE_PRIVATE
    )

    fun capture(
        role: String,
        payload: LiveDashboardPayload
    ): List<LiveUpdateEntry> {
        val current = payload.cards.associateBy { it.id }
        val snapshotKey = snapshotKey(role)
        val previousRaw = preferences.getString(snapshotKey, null)

        if (previousRaw == null) {
            saveSnapshot(role, current.values.toList())
            return emptyList()
        }

        val previous = readSnapshot(previousRaw)
        val timestamp = System.currentTimeMillis()
        val changes = mutableListOf<LiveUpdateEntry>()

        current.values.forEach { card ->
            val old = previous[card.id]

            if (old == null) {
                changes += LiveUpdateEntry(
                    id = UUID.randomUUID().toString(),
                    role = role,
                    kind = LiveUpdateKind.NEW_ITEM,
                    title = card.title,
                    message = buildString {
                        append("Новая карточка")
                        if (card.section.isNotBlank()) append(" · ${card.section}")
                        if (card.badge.isNotBlank()) append(" · ${card.badge}")
                    },
                    timestampMillis = timestamp,
                    read = false
                )
            } else if (old.badge != card.badge) {
                changes += LiveUpdateEntry(
                    id = UUID.randomUUID().toString(),
                    role = role,
                    kind = LiveUpdateKind.STATUS_CHANGED,
                    title = card.title,
                    message = "Статус: ${old.badge.ifBlank { "без статуса" }} → " +
                        card.badge.ifBlank { "без статуса" },
                    timestampMillis = timestamp,
                    read = false
                )
            }
        }

        saveSnapshot(role, current.values.toList())

        if (changes.isNotEmpty()) {
            val combined = (changes + entries(role, limit = MAX_EVENTS))
                .distinctBy { it.id }
                .take(MAX_EVENTS)
            saveEvents(role, combined)
        }

        return changes
    }

    fun entries(
        role: String,
        unreadOnly: Boolean = false,
        limit: Int = MAX_EVENTS
    ): List<LiveUpdateEntry> = readEvents(role)
        .asSequence()
        .filter { !unreadOnly || !it.read }
        .take(limit)
        .toList()

    fun markRead(role: String, id: String) {
        val updated = readEvents(role).map { entry ->
            if (entry.id == id) entry.copy(read = true) else entry
        }
        saveEvents(role, updated)
    }

    fun markAllRead(role: String) {
        saveEvents(role, readEvents(role).map { it.copy(read = true) })
    }

    fun clearEvents(role: String) {
        preferences.edit().remove(eventsKey(role)).apply()
    }

    fun settings(): LiveUpdateSettings = LiveUpdateSettings(
        notificationsEnabled = preferences.getBoolean(KEY_ENABLED, true),
        notifyNewItems = preferences.getBoolean(KEY_NEW_ITEMS, true),
        notifyStatusChanges = preferences.getBoolean(KEY_STATUS_CHANGES, true)
    )

    fun saveSettings(settings: LiveUpdateSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.notificationsEnabled)
            .putBoolean(KEY_NEW_ITEMS, settings.notifyNewItems)
            .putBoolean(KEY_STATUS_CHANGES, settings.notifyStatusChanges)
            .apply()
    }

    private fun saveSnapshot(role: String, cards: List<LiveDashboardCard>) {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(
                JSONObject()
                    .put("id", card.id)
                    .put("title", card.title)
                    .put("section", card.section)
                    .put("badge", card.badge)
            )
        }

        preferences.edit()
            .putString(snapshotKey(role), array.toString())
            .apply()
    }

    private fun readSnapshot(raw: String): Map<String, SnapshotCard> {
        return try {
            val array = JSONArray(raw)
            val result = linkedMapOf<String, SnapshotCard>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue

                result[id] = SnapshotCard(
                    id = id,
                    title = item.optString("title"),
                    section = item.optString("section"),
                    badge = item.optString("badge")
                )
            }

            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readEvents(role: String): List<LiveUpdateEntry> {
        val raw = preferences.getString(eventsKey(role), "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<LiveUpdateEntry>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val kind = try {
                    LiveUpdateKind.valueOf(item.optString("kind"))
                } catch (_: Exception) {
                    continue
                }

                result += LiveUpdateEntry(
                    id = item.optString("id"),
                    role = item.optString("role", role),
                    kind = kind,
                    title = item.optString("title"),
                    message = item.optString("message"),
                    timestampMillis = item.optLong("timestampMillis"),
                    read = item.optBoolean("read")
                )
            }

            result
        } catch (_: Exception) {
            clearEvents(role)
            emptyList()
        }
    }

    private fun saveEvents(role: String, entries: List<LiveUpdateEntry>) {
        val array = JSONArray()

        entries.take(MAX_EVENTS).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("role", role)
                    .put("kind", entry.kind.name)
                    .put("title", entry.title)
                    .put("message", entry.message)
                    .put("timestampMillis", entry.timestampMillis)
                    .put("read", entry.read)
            )
        }

        preferences.edit()
            .putString(eventsKey(role), array.toString())
            .apply()
    }

    private fun snapshotKey(role: String): String = "snapshot_$role"
    private fun eventsKey(role: String): String = "events_$role"

    private data class SnapshotCard(
        val id: String,
        val title: String,
        val section: String,
        val badge: String
    )

    private companion object {
        const val KEY_ENABLED = "notifications_enabled"
        const val KEY_NEW_ITEMS = "notify_new_items"
        const val KEY_STATUS_CHANGES = "notify_status_changes"
        const val MAX_EVENTS = 60
    }
}
