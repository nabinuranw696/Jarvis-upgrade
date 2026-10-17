package com.cutejarvis.app.voice

import android.content.Context
import com.cutejarvis.app.ChatLine
import com.cutejarvis.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Answers a voice command when no screen/ViewModel is alive (wake word heard while the app is in the background).
 * Uses the same backend + AI provider as the Chat screen and appends to the same saved conversation.
 * It never runs phone actions unattended: anything the AI asks to do is reported back as cancelled.
 */
object HeadlessChat {
    suspend fun ask(ctx: Context, text: String): Result<String> {
        return try {
            val store = SettingsStore(ctx.applicationContext)
            val api = Api({ store.current().backendUrl }, { store.deviceToken() })
            val saved = store.loadSaved()
            val chat = saved.chat?.let { runCatching { api.json.decodeFromString(ListSerializer(ChatLine.serializer()), it) }.getOrNull() } ?: emptyList()
            val r = api.post<ChatResponse>("/chat", buildJsonObject {
                put("message", text); saved.conversationId?.let { put("conversationId", it) }
            })
            r.pendingActions.forEach { a ->
                runCatching {
                    api.post<JsonObject>("/actions/${a.actionId}/result", buildJsonObject { put("status", "cancelled"); put("error", "Open the app to approve phone actions.") })
                }
            }
            val reply = r.reply.ifBlank { "…" }
            val updated = (chat + ChatLine(true, text) + ChatLine(false, reply)).takeLast(200)
            store.saveChat(api.json.encodeToString(ListSerializer(ChatLine.serializer()), updated), r.conversationId)
            Result.success(if (r.pendingActions.isNotEmpty()) "$reply I can't do that while the app is closed. Open Jarvis to approve it." else reply)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}
