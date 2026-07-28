package app.shared.core

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(private val baseUrl: String) {
    fun get(path: String, token: String? = null): ApiResult = request("GET", path, null, token)

    fun post(path: String, body: JSONObject = JSONObject(), token: String? = null): ApiResult =
        request("POST", path, body, token)

    private fun request(method: String, path: String, body: JSONObject?, token: String?): ApiResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null && method != "GET") {
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
            val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            ApiResult(
                successful = status in 200..299,
                statusCode = status,
                body = json,
                message = json.optString("message", if (status in 200..299) "OK" else "HTTP $status")
            )
        } catch (exception: Exception) {
            ApiResult(false, 0, null, exception.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }
}
