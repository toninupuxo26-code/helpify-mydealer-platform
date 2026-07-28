package app.shared.core

import org.json.JSONObject

data class ApiUser(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val status: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("email", email)
        .put("role", role)
        .put("status", status)

    companion object {
        fun fromJson(json: JSONObject): ApiUser = ApiUser(
            id = json.optLong("id"),
            name = json.optString("name"),
            email = json.optString("email"),
            role = json.optString("role"),
            status = json.optString("status", "active")
        )
    }
}

data class AuthSession(val token: String, val user: ApiUser)

data class ApiResult(
    val successful: Boolean,
    val statusCode: Int,
    val body: JSONObject?,
    val message: String
)
