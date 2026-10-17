package com.cutejarvis.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cutejarvis.app.data.*
import com.cutejarvis.app.device.DeviceActions
import com.cutejarvis.app.voice.VoiceHub
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*

@Serializable data class ChatLine(val fromUser: Boolean, val text: String, val note: Boolean = false)

data class UiState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val settings: LocalSettings = LocalSettings(),
    val chat: List<ChatLine> = emptyList(),
    val conversationId: String? = null,
    val pending: List<PendingAction> = emptyList(),
    val memories: List<Memory>? = null,
    val skills: List<Skill>? = null,
    val providers: List<Provider>? = null,
    val testingId: String? = null,
    val logs: List<String> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val api = Api({ store.current().backendUrl }, { store.deviceToken() })
    private val device = DeviceActions(app)

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private var memoriesStale = true

    /** Lets the voice assistant (hands-free / wake word) talk through this ViewModel so voice turns appear in Chat. */
    private val voiceResponder: suspend (String) -> Result<String> = { text -> ask(text) }

    init {
        VoiceHub.responder = voiceResponder
        viewModelScope.launch {
            // Restore everything saved on this device, then show the app (splash stays up meanwhile).
            val settings = store.current()
            val saved = store.loadSaved()
            val chat = saved.chat?.let { runCatching { api.json.decodeFromString(ListSerializer(ChatLine.serializer()), it) }.getOrNull() } ?: emptyList()
            val mems = saved.memories?.let { runCatching { api.json.decodeFromString(ListSerializer(Memory.serializer()), it) }.getOrNull() }
            kotlinx.coroutines.delay(800)
            _ui.update { it.copy(settings = settings, chat = chat, conversationId = saved.conversationId, memories = mems, ready = true) }
            launch { syncRemoteSettings() }
            _ui.map { it.chat to it.conversationId }.distinctUntilChanged().drop(1).collect { (c, id) ->
                store.saveChat(api.json.encodeToString(ListSerializer(ChatLine.serializer()), c.takeLast(200)), id)
            }
        }
        viewModelScope.launch { store.flow.collect { s -> _ui.update { it.copy(settings = s) } } }
    }

    override fun onCleared() {
        if (VoiceHub.responder === voiceResponder) VoiceHub.responder = null
        super.onCleared()
    }

    fun completeOnboarding() { viewModelScope.launch { store.save(store.current().copy(onboarded = true)) } }

    private fun <T> run(block: suspend () -> T, onOk: (T) -> Unit = {}) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try { onOk(block()) } catch (e: ApiException) {
                log("API error: ${e.message}"); _ui.update { it.copy(error = e.message) }
            } catch (e: Exception) { log("Error: ${e.message}"); _ui.update { it.copy(error = e.message ?: "Something went wrong") } }
            _ui.update { it.copy(busy = false) }
        }
    }

    // ---- local diagnostics (never log keys, tokens or message contents) ----
    fun log(m: String) = _ui.update {
        it.copy(logs = (it.logs + "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}  $m").takeLast(200))
    }
    fun clearLogs() = _ui.update { it.copy(logs = emptyList()) }
    fun setLanguage(code: String) = viewModelScope.launch { store.save(store.current().copy(language = code)) }
    fun setSpeechRate(v: Float) = viewModelScope.launch { store.save(store.current().copy(speechRate = v)) }
    /** Atomic update of any (voice) setting. */
    fun updateSettings(f: (LocalSettings) -> LocalSettings) = viewModelScope.launch { store.update(f) }
    fun showError(msg: String) = _ui.update { it.copy(error = msg) }
    fun resetLocalSettings() = viewModelScope.launch { val url = store.current().backendUrl; store.save(LocalSettings(backendUrl = url)); _ui.update { it.copy(info = "Local settings reset") } }

    /** User-tapped phone control (Phone Control screen). Runs through the same validated DeviceActions as AI skills. */
    fun deviceAction(skillId: String, args: JsonObject) {
        val o = device.execute(skillId, args)
        log("device:$skillId -> ${if (o.ok) "ok" else "failed"}")
        _ui.update { if (o.ok) it.copy(info = o.message) else it.copy(error = o.message) }
    }

    fun clearMessages() = _ui.update { it.copy(error = null, info = null) }

    // ---- settings ----
    private suspend fun syncRemoteSettings() {
        try {
            val r: RemoteSettings = api.get("/settings")
            store.save(store.current().copy(assistantName = r.assistantName, wakePhrase = r.wakePhrase, sleepPhrase = r.sleepPhrase))
        } catch (_: Exception) {}
    }

    fun saveAssistant(name: String, wake: String, sleep: String) {
        if (name.isBlank() || wake.length < 2 || sleep.length < 2) return _ui.update { it.copy(error = "Name and phrases can't be empty.") }
        run({
            store.save(store.current().copy(assistantName = name.trim(), wakePhrase = wake.trim(), sleepPhrase = sleep.trim()))
            api.patch<RemoteSettings>("/settings", buildJsonObject { put("assistantName", name.trim()); put("wakePhrase", wake.trim()); put("sleepPhrase", sleep.trim()) })
        }, { _ui.update { it.copy(info = "Saved") } })
    }

    fun saveBackendUrl(url: String) {
        val u = url.trim().trimEnd('/')
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return _ui.update { it.copy(error = "URL must start with https:// (or http:// for local development).") }
        viewModelScope.launch { store.save(store.current().copy(backendUrl = u)); _ui.update { it.copy(info = "Backend URL saved") } }
    }

    fun checkBackend() = run({ api.request("GET", "/health", auth = false) }, { _ui.update { s -> s.copy(info = "Backend is reachable: $it") } })

    fun setDark(v: Boolean?) = viewModelScope.launch { store.save(store.current().copy(dark = v)) }

    // ---- chat ----
    fun send(text: String) {
        if (text.isBlank() || _ui.value.busy) return
        viewModelScope.launch { ask(text) }
    }

    /** One chat turn. Used by the Chat screen and by the voice assistant; returns the reply (or the failure). */
    suspend fun ask(text: String): Result<String> {
        val msg = text.trim()
        if (msg.isEmpty()) return Result.failure(IllegalArgumentException("Empty message"))
        if (_ui.value.busy) return Result.failure(IllegalStateException("Still working on your last message."))
        _ui.update { it.copy(chat = it.chat + ChatLine(true, msg), busy = true, error = null) }
        val outcome: Result<String> = try {
            val r = api.post<ChatResponse>("/chat", buildJsonObject {
                put("message", msg); _ui.value.conversationId?.let { put("conversationId", it) }
            })
            val reply = r.reply.ifBlank { "…" }
            _ui.update { it.copy(conversationId = r.conversationId, chat = it.chat + ChatLine(false, reply)) }
            r.pendingActions.forEach { a -> if (a.requiresConfirmation) _ui.update { it.copy(pending = it.pending + a) } else runAction(a) }
            Result.success(reply)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ApiException) {
            log("API error: ${e.message}"); _ui.update { it.copy(error = e.message) }; Result.failure(e)
        } catch (e: Exception) {
            log("Error: ${e.message}"); _ui.update { it.copy(error = e.message ?: "Something went wrong") }; Result.failure(e)
        }
        _ui.update { it.copy(busy = false) }
        return outcome
    }

    fun newChat() = _ui.update { it.copy(chat = emptyList(), conversationId = null) }

    fun approve(a: PendingAction) { _ui.update { it.copy(pending = it.pending - a) }; runAction(a) }
    fun deny(a: PendingAction) {
        _ui.update { it.copy(pending = it.pending - a, chat = it.chat + ChatLine(false, "Okay, I won't do that.", note = true)) }
        report(a, "cancelled", null, null)
    }

    private fun runAction(a: PendingAction) {
        if (a.skillId == "propose_memory") { // user approval already given; persist as a long-term memory
            val content = a.args["content"]?.jsonPrimitive?.contentOrNull ?: return
            run({ api.post<Memory>("/memories", buildJsonObject { put("content", content) }) }, {
                _ui.update { s -> s.copy(chat = s.chat + ChatLine(false, "Saved to memories.", note = true), memories = null) }
                report(a, "success", "saved", null)
            }); return
        }
        VoiceHub.executing(true)
        val o = try { device.execute(a.skillId, a.args) } finally { VoiceHub.executing(false) }
        _ui.update { it.copy(chat = it.chat + ChatLine(false, o.message, note = true)) }
        report(a, if (o.ok) "success" else "failed", if (o.ok) o.message else null, if (o.ok) null else o.message)
    }

    private fun report(a: PendingAction, status: String, result: String?, err: String?) = viewModelScope.launch {
        try { api.post<JsonObject>("/actions/${a.actionId}/result", buildJsonObject { put("status", status); result?.let { put("result", it) }; err?.let { put("error", it) } }) } catch (_: Exception) {}
    }

    // ---- memories ----
    fun loadMemories() = run({ api.json.decodeFromString(ListSerializer(Memory.serializer()), api.request("GET", "/memories")) }, { m ->
        _ui.update { it.copy(memories = m) }
        viewModelScope.launch { store.saveMemories(api.json.encodeToString(ListSerializer(Memory.serializer()), m)) } // local copy
    })
    fun loadMemoriesIfNeeded() { if (_ui.value.memories == null || memoriesStale) { memoriesStale = false; loadMemories() } }
    fun addMemory(text: String) {
        if (text.isBlank()) return
        run({ api.post<Memory>("/memories", buildJsonObject { put("content", text.trim()) }) }, { loadMemories() })
    }
    fun deleteMemory(id: String) = run({ api.delete("/memories/$id") }, { loadMemories() })

    // ---- skills ----
    fun loadSkills() = run({ api.json.decodeFromString(ListSerializer(Skill.serializer()), api.request("GET", "/skills")) }, { s -> _ui.update { it.copy(skills = s) } })
    fun toggleSkill(s: Skill) = run({ api.request("POST", "/skills/${s.id}/${if (s.enabled) "disable" else "enable"}") }, { loadSkills() })

    // ---- providers ----
    fun loadProviders() = run({ api.json.decodeFromString(ListSerializer(Provider.serializer()), api.request("GET", "/providers")) }, { p -> _ui.update { it.copy(providers = p) } })
    fun addProvider(name: String, kind: String, model: String, baseUrl: String, key: String) {
        if (name.isBlank() || model.isBlank() || key.length < 8) return _ui.update { it.copy(error = "Name, model and a valid API key are required.") }
        if (baseUrl.isNotBlank() && !baseUrl.startsWith("https://")) return _ui.update { it.copy(error = "Custom endpoints must use https://") }
        run({ api.post<Provider>("/providers", buildJsonObject {
            put("name", name.trim()); put("kind", kind); put("model", model.trim()); put("apiKey", key.trim())
            if (baseUrl.isNotBlank()) put("baseUrl", baseUrl.trim())
        }) }, { loadProviders() })
    }
    fun testProvider(id: String) {
        _ui.update { it.copy(testingId = id) }
        viewModelScope.launch {
            try {
                val r: TestResult = api.post("/providers/test", buildJsonObject { put("id", id) })
                _ui.update { it.copy(info = if (r.ok) "Connection works." else "Test failed: ${r.error}") }
            } catch (e: ApiException) { _ui.update { it.copy(error = "Test failed: ${e.message}") } }
            _ui.update { it.copy(testingId = null) }
            loadProviders()
        }
    }
    fun setDefault(id: String) = run({ api.patch<Provider>("/providers/$id", buildJsonObject { put("isDefault", true) }) }, { loadProviders() })
    fun toggleProvider(p: Provider) = run({ api.patch<Provider>("/providers/${p.id}", buildJsonObject { put("enabled", !p.enabled) }) }, { loadProviders() })
    fun deleteProvider(id: String) = run({ api.delete("/providers/$id") }, { loadProviders() })
}
