package app.shared.core

import org.json.JSONObject

class AuthRepository(baseUrl: String) {
    private val api = ApiClient(baseUrl)

    fun login(email: String, password: String): Pair<ApiResult, AuthSession?> {
        val result = api.post("/auth/login", JSONObject().put("email", email).put("password", password))
        return result to sessionFrom(result)
    }

    fun register(name: String, email: String, password: String, role: String): Pair<ApiResult, AuthSession?> {
        val body = JSONObject()
            .put("name", name)
            .put("email", email)
            .put("password", password)
            .put("role", role)
        val result = api.post("/auth/register", body)
        return result to sessionFrom(result)
    }

    fun me(token: String): Pair<ApiResult, ApiUser?> {
        val result = api.get("/auth/me", token)
        val user = if (result.successful) result.body?.optJSONObject("user")?.let(ApiUser::fromJson) else null
        return result to user
    }

    fun logout(token: String): ApiResult = api.post("/auth/logout", JSONObject(), token)

    fun forgot(email: String): Pair<ApiResult, String?> {
        val result = api.post("/auth/password/forgot", JSONObject().put("email", email))
        return result to result.body?.optString("demo_reset_code")?.takeIf { it.isNotBlank() }
    }

    fun reset(email: String, code: String, newPassword: String): ApiResult {
        val body = JSONObject()
            .put("email", email)
            .put("code", code)
            .put("password", newPassword)
        return api.post("/auth/password/reset", body)
    }

    private fun sessionFrom(result: ApiResult): AuthSession? {
        if (!result.successful) return null
        val token = result.body?.optString("token").orEmpty()
        val userJson = result.body?.optJSONObject("user") ?: return null
        return if (token.isBlank()) null else AuthSession(token, ApiUser.fromJson(userJson))
    }
}
