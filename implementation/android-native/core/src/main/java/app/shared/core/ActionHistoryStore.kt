package app.shared.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ActionHistoryEntry(
    val timestampMillis: Long,
    val title: String,
    val message: String,
    val successful: Boolean
)

class ActionHistoryStore(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences(
        "${namespace.lowercase()}_action_history",
        Context.MODE_PRIVATE
    )

    fun add(title: String, result: ApiResult) {
        val current = entries(limit = MAX_ENTRIES - 1).toMutableList()
        current.add(
            0,
            ActionHistoryEntry(
                timestampMillis = System.currentTimeMillis(),
                title = title,
                message = result.message,
                successful = result.successful
            )
        )
        save(current.take(MAX_ENTRIES))
    }

    fun entries(limit: Int = 15): List<ActionHistoryEntry> {
        val raw = preferences.getString(KEY_ENTRIES, "[]") ?: "[]"

        return try {
            val json = JSONArray(raw)
            val result = mutableListOf<ActionHistoryEntry>()

            for (index in 0 until json.length()) {
                if (result.size >= limit) break
                val item = json.optJSONObject(index) ?: continue
                result += ActionHistoryEntry(
                    timestampMillis = item.optLong("timestampMillis"),
                    title = item.optString("title"),
                    message = item.optString("message"),
                    successful = item.optBoolean("successful")
                )
            }

            result
        } catch (_: Exception) {
            clear()
            emptyList()
        }
    }

    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(entries: List<ActionHistoryEntry>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject()
                    .put("timestampMillis", entry.timestampMillis)
                    .put("title", entry.title)
                    .put("message", entry.message)
                    .put("successful", entry.successful)
            )
        }

        preferences.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 40
    }
}
