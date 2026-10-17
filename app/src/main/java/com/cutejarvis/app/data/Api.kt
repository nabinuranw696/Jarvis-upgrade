package com.cutejarvis.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, override val message: String) : Exception(message)

/** [token] returns this device's random private ID, sent as `Authorization: Device <id>` (no account / login). */
class Api(private val baseUrl: suspend () -> String, private val token: suspend () -> String?) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()

    private fun build(base: String, method: String, path: String, body: JsonElement?, token: String?): Request {
        val b = Request.Builder().url(base.trimEnd('/') + "/api" + path)
        token?.let { b.header("Authorization", "Device $it") }
        val rb = (body ?: if (method == "GET" || method == "DELETE") null else JsonObject(emptyMap()))
            ?.let { json.encodeToString(JsonElement.serializer(), it).toRequestBody("application/json".toMediaType()) }
        when (method) { "GET" -> b.get(); "DELETE" -> b.delete(rb); else -> b.method(method, rb) }
        return b.build()
    }

    private fun message(code: Int, text: String): String {
        val fromBody = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            (o["message"] ?: o["error"])?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            code == 401 || code == 403 -> "The server didn't accept this device. The backend must allow guest device IDs (see BACKEND_CHANGES.md)."
            code == 429 -> "Too many requests. Please wait a moment and retry."
            code in 500..599 && fromBody == null -> "Server error ($code). Try again later."
            else -> fromBody ?: "Request failed ($code)"
        }
    }

    suspend fun request(method: String, path: String, body: JsonElement? = null, auth: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val base = baseUrl()
            suspend fun go(): Response = try {
                client.newCall(build(base, method, path, body, if (auth) token() else null)).execute()
            } catch (e: IOException) {
                throw ApiException(0, "Can't reach the backend at $base. Check the URL, your network, and that the server is running.")
            }
            val resp = go()
            resp.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw ApiException(it.code, message(it.code, text))
                text
            }
        }

    suspend inline fun <reified T> get(path: String): T = json.decodeFromString(request("GET", path))
    suspend inline fun <reified T> post(path: String, body: JsonElement? = null, auth: Boolean = true): T =
        json.decodeFromString(request("POST", path, body, auth))
    suspend inline fun <reified T> patch(path: String, body: JsonElement): T = json.decodeFromString(request("PATCH", path, body))
    suspend fun delete(path: String) { request("DELETE", path) }
}
