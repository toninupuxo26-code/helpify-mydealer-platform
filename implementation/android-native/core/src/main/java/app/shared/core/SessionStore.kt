package app.shared.core

import android.content.Context
import org.json.JSONObject

class SessionStore(context: Context, productName: String) {
    private val preferences = context.getSharedPreferences(
        "${productName.lowercase()}_secure_session",
        Context.MODE_PRIVATE
    )

    fun save(session: AuthSession) {
        preferences.edit()
            .putString("token", session.token)
            .putString("user", session.user.toJson().toString())
            .apply()
    }

    fun token(): String? = preferences.getString("token", null)

    fun user(): ApiUser? {
        val raw = preferences.getString("user", null) ?: return null
        return try { ApiUser.fromJson(JSONObject(raw)) } catch (_: Exception) { null }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
