package com.cutejarvis.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class Tokens(val accessToken: String, val refreshToken: String)
@Serializable data class PendingAction(
    val actionId: String, val skillId: String, val args: JsonObject,
    val requiresConfirmation: Boolean, val permissions: List<String> = emptyList(),
)
@Serializable data class ChatResponse(val conversationId: String, val reply: String, val pendingActions: List<PendingAction> = emptyList())
@Serializable data class Memory(val id: String, val content: String)
@Serializable data class Skill(
    val id: String, val version: String, val name: String, val description: String, val category: String,
    val target: String, val permissions: List<String> = emptyList(), val requiresConfirmation: Boolean, val enabled: Boolean,
)
@Serializable data class Provider(
    val id: String, val name: String, val kind: String, val model: String, val baseUrl: String? = null,
    val keyLast4: String, val enabled: Boolean, val isDefault: Boolean,
    val lastTestOk: Boolean? = null, val lastError: String? = null,
)
@Serializable data class TestResult(val ok: Boolean, val error: String? = null)
@Serializable data class RemoteSettings(val assistantName: String, val wakePhrase: String, val sleepPhrase: String)

data class LocalSettings(
    val backendUrl: String = "http://10.0.2.2:3000", // Android emulator -> host machine; change for a real phone
    val assistantName: String = "Jarvis",
    val wakePhrase: String = "Okay Jarvis",
    val sleepPhrase: String = "Okay Bye Jarvis",
    val dark: Boolean? = null,
    val language: String = "en",
    val speechRate: Float = 1f,
    val onboarded: Boolean = false,
    // ---- voice (hands-free / wake word) ----
    val handsFree: Boolean = true,          // keep listening after each spoken answer
    val wakeWordEnabled: Boolean = false,   // on-device wake-word detector (foreground service)
    val speechLang: String = "",            // BCP-47 tag; "" = follow the app language
    val voiceName: String = "",             // TTS voice name; "" = engine default
    val volume: Float = 1f,                 // TTS volume 0.1..1
    val soundCues: Boolean = true,          // short beep when going to sleep / waking
    val batterySaver: Boolean = false,      // wake detector only decodes when it hears sound; auto-off after 30 min
)

/** Speech recognition + TTS language tag actually used (explicit choice, else follows the app language). */
fun LocalSettings.speechTag(): String = speechLang.ifBlank { if (language == "ne") "ne-NP" else "en-US" }

val SPEECH_LANGS = listOf(
    "" to "Same as app language", "en-US" to "English (US)", "en-GB" to "English (UK)", "en-IN" to "English (India)",
    "ne-NP" to "नेपाली (Nepali)", "hi-IN" to "हिन्दी (Hindi)", "es-ES" to "Español", "fr-FR" to "Français",
    "de-DE" to "Deutsch", "pt-BR" to "Português (Brasil)", "ar-SA" to "العربية", "ja-JP" to "日本語",
    "ko-KR" to "한국어", "zh-CN" to "中文 (简体)", "ru-RU" to "Русский",
)
