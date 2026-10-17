package com.cutejarvis.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.prefsDs by preferencesDataStore("prefs")

class SettingsStore(private val ctx: Context) {
    private val URL = stringPreferencesKey("url"); private val NAME = stringPreferencesKey("name")
    private val WAKE = stringPreferencesKey("wake"); private val SLEEP = stringPreferencesKey("sleep")
    private val DARK = stringPreferencesKey("dark") // "", "true", "false"
    private val ONB = stringPreferencesKey("onb"); private val DEV = stringPreferencesKey("device_id")
    private val CHAT = stringPreferencesKey("chat"); private val CONV = stringPreferencesKey("conv"); private val MEMS = stringPreferencesKey("mems")
    private val LANG = stringPreferencesKey("lang"); private val RATE = floatPreferencesKey("rate")
    private val HF = booleanPreferencesKey("hands_free"); private val WAKEON = booleanPreferencesKey("wake_on")
    private val SLANG = stringPreferencesKey("speech_lang"); private val VOICE = stringPreferencesKey("voice_name")
    private val VOL = floatPreferencesKey("volume"); private val CUE = booleanPreferencesKey("cues"); private val BATT = booleanPreferencesKey("battery")

    private fun read(p: Preferences): LocalSettings {
        val d = LocalSettings()
        return LocalSettings(
            p[URL] ?: d.backendUrl, p[NAME] ?: d.assistantName, p[WAKE] ?: d.wakePhrase, p[SLEEP] ?: d.sleepPhrase,
            p[DARK]?.toBooleanStrictOrNull(), p[LANG] ?: d.language, p[RATE] ?: d.speechRate, p[ONB]?.toBooleanStrictOrNull() ?: false,
            p[HF] ?: d.handsFree, p[WAKEON] ?: d.wakeWordEnabled, p[SLANG] ?: d.speechLang, p[VOICE] ?: d.voiceName,
            p[VOL] ?: d.volume, p[CUE] ?: d.soundCues, p[BATT] ?: d.batterySaver,
        )
    }
    private fun write(it: MutablePreferences, s: LocalSettings) {
        it[URL] = s.backendUrl; it[NAME] = s.assistantName; it[WAKE] = s.wakePhrase; it[SLEEP] = s.sleepPhrase
        it[DARK] = s.dark?.toString() ?: ""; it[LANG] = s.language; it[RATE] = s.speechRate; it[ONB] = s.onboarded.toString()
        it[HF] = s.handsFree; it[WAKEON] = s.wakeWordEnabled; it[SLANG] = s.speechLang; it[VOICE] = s.voiceName
        it[VOL] = s.volume; it[CUE] = s.soundCues; it[BATT] = s.batterySaver
    }

    val flow: Flow<LocalSettings> = ctx.prefsDs.data.map { read(it) }
    suspend fun current() = flow.first()
    suspend fun save(s: LocalSettings) = ctx.prefsDs.edit { write(it, s) }
    /** Atomic read-modify-write (used by the voice layer so a spoken "go to sleep" can't clobber a concurrent settings edit). */
    suspend fun update(f: (LocalSettings) -> LocalSettings) { ctx.prefsDs.edit { write(it, f(read(it))) } }

    /** Random 256-bit ID created on first launch; it is this device's only identity (no account). Never logged. */
    suspend fun deviceToken(): String {
        var out = ""
        ctx.prefsDs.edit { p ->
            out = p[DEV] ?: ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }.also { p[DEV] = it }
        }
        return out
    }

    class Saved(val chat: String?, val conversationId: String?, val memories: String?)
    suspend fun loadSaved(): Saved = ctx.prefsDs.data.first().let { Saved(it[CHAT], it[CONV], it[MEMS]) }
    suspend fun saveChat(chatJson: String, conversationId: String?) = ctx.prefsDs.edit {
        it[CHAT] = chatJson; if (conversationId == null) it.remove(CONV) else it[CONV] = conversationId
    }
    suspend fun saveMemories(json: String) = ctx.prefsDs.edit { it[MEMS] = json }
}
