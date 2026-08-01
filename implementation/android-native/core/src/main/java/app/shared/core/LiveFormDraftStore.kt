package app.shared.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LiveFormDraft(
    val values: Map<String, String>,
    val updatedAtMillis: Long
)

data class LiveFormTemplate(
    val id: String,
    val name: String,
    val values: Map<String, String>,
    val updatedAtMillis: Long
)

class LiveFormDraftStore(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences(
        "${namespace.lowercase(Locale.US)}_live_form_drafts",
        Context.MODE_PRIVATE
    )

    fun hasDraft(role: String, cardId: String): Boolean =
        preferences.contains(draftKey(role, cardId))

    fun loadDraft(role: String, cardId: String): LiveFormDraft? {
        val raw = preferences.getString(draftKey(role, cardId), null) ?: return null

        return try {
            val root = JSONObject(raw)
            LiveFormDraft(
                values = valuesFromJson(root.optJSONObject("values") ?: JSONObject()),
                updatedAtMillis = root.optLong("updatedAtMillis", 0L)
            )
        } catch (_: Exception) {
            clearDraft(role, cardId)
            null
        }
    }

    fun saveDraft(
        role: String,
        cardId: String,
        values: Map<String, String>
    ) {
        val root = JSONObject()
            .put("updatedAtMillis", System.currentTimeMillis())
            .put("values", valuesToJson(values))

        preferences.edit()
            .putString(draftKey(role, cardId), root.toString())
            .apply()
    }

    fun clearDraft(role: String, cardId: String) {
        preferences.edit().remove(draftKey(role, cardId)).apply()
    }

    fun templates(role: String, cardId: String): List<LiveFormTemplate> {
        val raw = preferences.getString(templatesKey(role, cardId), "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<LiveFormTemplate>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                result += LiveFormTemplate(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    values = valuesFromJson(
                        item.optJSONObject("values") ?: JSONObject()
                    ),
                    updatedAtMillis = item.optLong("updatedAtMillis", 0L)
                )
            }

            result.sortedByDescending { it.updatedAtMillis }
        } catch (_: Exception) {
            clearTemplates(role, cardId)
            emptyList()
        }
    }

    fun saveTemplate(
        role: String,
        cardId: String,
        name: String,
        values: Map<String, String>
    ): LiveFormTemplate {
        val normalizedName = name.trim().ifBlank { "Шаблон" }
        val current = templates(role, cardId).toMutableList()
        val existing = current.indexOfFirst {
            it.name.equals(normalizedName, ignoreCase = true)
        }
        val now = System.currentTimeMillis()
        val template = LiveFormTemplate(
            id = if (existing >= 0) {
                current[existing].id
            } else {
                "$now-${normalizedName.hashCode().toUInt()}"
            },
            name = normalizedName,
            values = values,
            updatedAtMillis = now
        )

        if (existing >= 0) {
            current[existing] = template
        } else {
            current.add(0, template)
        }

        saveTemplates(role, cardId, current.take(MAX_TEMPLATES))
        return template
    }

    fun deleteTemplate(role: String, cardId: String, templateId: String) {
        val updated = templates(role, cardId).filterNot { it.id == templateId }
        saveTemplates(role, cardId, updated)
    }

    fun clearTemplates(role: String, cardId: String) {
        preferences.edit().remove(templatesKey(role, cardId)).apply()
    }

    private fun saveTemplates(
        role: String,
        cardId: String,
        templates: List<LiveFormTemplate>
    ) {
        val array = JSONArray()

        templates.forEach { template ->
            array.put(
                JSONObject()
                    .put("id", template.id)
                    .put("name", template.name)
                    .put("updatedAtMillis", template.updatedAtMillis)
                    .put("values", valuesToJson(template.values))
            )
        }

        preferences.edit()
            .putString(templatesKey(role, cardId), array.toString())
            .apply()
    }

    private fun valuesToJson(values: Map<String, String>): JSONObject {
        val result = JSONObject()
        values.forEach { (key, value) -> result.put(key, value) }
        return result
    }

    private fun valuesFromJson(json: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val keys = json.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = json.optString(key)
        }

        return result
    }

    private fun draftKey(role: String, cardId: String): String =
        "draft_${role}_$cardId"

    private fun templatesKey(role: String, cardId: String): String =
        "templates_${role}_$cardId"

    private companion object {
        const val MAX_TEMPLATES = 12
    }
}
