package app.shared.core

import android.content.Context

private const val DEFAULT_BACKGROUND_SYNC_INTERVAL_MINUTES = 30

data class BackgroundSyncSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_BACKGROUND_SYNC_INTERVAL_MINUTES
)

data class BackgroundSyncState(
    val lastRunMillis: Long = 0L,
    val lastSuccessful: Boolean = false,
    val lastMessage: String = "",
    val lastChanges: Int = 0
)

class BackgroundSyncStore(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences(
        "${namespace.lowercase()}_background_sync",
        Context.MODE_PRIVATE
    )

    fun settings(): BackgroundSyncSettings = BackgroundSyncSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        intervalMinutes = normalizeInterval(
            preferences.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        )
    )

    fun saveSettings(settings: BackgroundSyncSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(
                KEY_INTERVAL_MINUTES,
                normalizeInterval(settings.intervalMinutes)
            )
            .apply()
    }

    fun state(): BackgroundSyncState = BackgroundSyncState(
        lastRunMillis = preferences.getLong(KEY_LAST_RUN, 0L),
        lastSuccessful = preferences.getBoolean(KEY_LAST_SUCCESSFUL, false),
        lastMessage = preferences.getString(KEY_LAST_MESSAGE, "").orEmpty(),
        lastChanges = preferences.getInt(KEY_LAST_CHANGES, 0)
    )

    fun recordQueued() {
        preferences.edit()
            .putString(KEY_LAST_MESSAGE, "Ожидает запуска")
            .apply()
    }

    fun recordRunning() {
        preferences.edit()
            .putLong(KEY_LAST_RUN, System.currentTimeMillis())
            .putString(KEY_LAST_MESSAGE, "Выполняется")
            .apply()
    }

    fun recordResult(
        successful: Boolean,
        message: String,
        changes: Int
    ) {
        preferences.edit()
            .putLong(KEY_LAST_RUN, System.currentTimeMillis())
            .putBoolean(KEY_LAST_SUCCESSFUL, successful)
            .putString(KEY_LAST_MESSAGE, message)
            .putInt(KEY_LAST_CHANGES, changes.coerceAtLeast(0))
            .apply()
    }

    private fun normalizeInterval(value: Int): Int =
        SUPPORTED_INTERVALS.minByOrNull { candidate ->
            kotlin.math.abs(candidate - value)
        } ?: DEFAULT_INTERVAL_MINUTES

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = DEFAULT_BACKGROUND_SYNC_INTERVAL_MINUTES
        val SUPPORTED_INTERVALS = listOf(15, 30, 60, 180)

        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_LAST_RUN = "last_run"
        private const val KEY_LAST_SUCCESSFUL = "last_successful"
        private const val KEY_LAST_MESSAGE = "last_message"
        private const val KEY_LAST_CHANGES = "last_changes"
    }
}
