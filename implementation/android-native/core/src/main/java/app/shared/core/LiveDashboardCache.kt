package app.shared.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CachedLiveDashboard(
    val payload: LiveDashboardPayload,
    val savedAtMillis: Long
)

class LiveDashboardCache(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences(
        "${namespace.lowercase()}_live_dashboard_cache",
        Context.MODE_PRIVATE
    )

    fun save(role: String, payload: LiveDashboardPayload) {
        val root = JSONObject()
            .put("savedAtMillis", System.currentTimeMillis())
            .put("message", payload.message)
            .put("metrics", metricsToJson(payload.metrics))
            .put("cards", cardsToJson(payload.cards))

        preferences.edit()
            .putString(cacheKey(role), root.toString())
            .apply()
    }

    fun load(role: String): CachedLiveDashboard? {
        val raw = preferences.getString(cacheKey(role), null) ?: return null

        return try {
            val root = JSONObject(raw)
            val metrics = metricsFromJson(root.optJSONArray("metrics") ?: JSONArray())
            val cards = cardsFromJson(root.optJSONArray("cards") ?: JSONArray())

            CachedLiveDashboard(
                payload = LiveDashboardPayload(
                    metrics = metrics,
                    cards = cards,
                    message = root.optString("message", "Сохранённые данные")
                ),
                savedAtMillis = root.optLong("savedAtMillis", 0L)
            )
        } catch (_: Exception) {
            clear(role)
            null
        }
    }

    fun clear(role: String) {
        preferences.edit().remove(cacheKey(role)).apply()
    }

    private fun cacheKey(role: String): String = "payload_$role"

    private fun metricsToJson(metrics: List<DashboardMetric>): JSONArray {
        val result = JSONArray()
        metrics.forEach { metric ->
            result.put(
                JSONObject()
                    .put("label", metric.label)
                    .put("value", metric.value)
            )
        }
        return result
    }

    private fun cardsToJson(cards: List<LiveDashboardCard>): JSONArray {
        val result = JSONArray()
        cards.forEach { card ->
            result.put(
                JSONObject()
                    .put("id", card.id)
                    .put("title", card.title)
                    .put("description", card.description)
                    .put("details", card.details)
                    .put("section", card.section)
                    .put("badge", card.badge)
            )
        }
        return result
    }

    private fun metricsFromJson(items: JSONArray): List<DashboardMetric> {
        val result = mutableListOf<DashboardMetric>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            result += DashboardMetric(
                label = item.optString("label"),
                value = item.optString("value")
            )
        }
        return result
    }

    private fun cardsFromJson(items: JSONArray): List<LiveDashboardCard> {
        val result = mutableListOf<LiveDashboardCard>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            result += LiveDashboardCard(
                id = item.optString("id"),
                title = item.optString("title"),
                description = item.optString("description"),
                details = item.optString("details"),
                section = item.optString("section", "Данные сервера"),
                badge = item.optString("badge"),
                actionId = null,
                actionLabel = "",
                form = null,
                confirmationMessage = ""
            )
        }
        return result
    }
}
