package app.shared.core

import android.content.Context

class ScenarioStore(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences(
        "${namespace.lowercase()}_scenario_state",
        Context.MODE_PRIVATE
    )

    fun step(id: String): Int = preferences.getInt("step_$id", 0)

    fun advance(id: String, maximum: Int): Int {
        val next = (step(id) + 1).coerceAtMost(maximum)
        preferences.edit().putInt("step_$id", next).apply()
        return next
    }

    fun reset(id: String) {
        preferences.edit().remove("step_$id").apply()
    }

    fun resetAll() {
        preferences.edit().clear().apply()
    }
}
