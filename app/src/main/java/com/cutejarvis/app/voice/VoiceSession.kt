package com.cutejarvis.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Drives real speech recognition + text-to-speech through the pure [VoiceMachine].
 * Must be created and used on the main thread. Owned by [VoiceHub] (process-wide), so a voice conversation can
 * continue while the app is in the background as long as the wake-word foreground service is running.
 */
class VoiceSession(ctx: Context, private val send: (String) -> Unit) {
    var state by mutableStateOf(VoiceState.Idle); private set
    var level by mutableFloatStateOf(0f); private set
    var partial by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    /** Non-fatal hint, e.g. no TTS voice installed for the chosen language. */
    var note by mutableStateOf<String?>(null); private set
    var lastHeard by mutableStateOf(""); private set
    var ttsReady by mutableStateOf(false); private set
    /** Increments for every command sent to the AI; lets callers drop a reply that belongs to an older turn. */
    var turn = 0; private set

    // ---- configuration pushed by VoiceHub ----
    var config = VoiceConfig(Phrases.sleep("Jarvis", ""))
    var wakePhrases: List<String> = Phrases.wake("Jarvis", "")
    var speechRate = 1f
    var volume = 1f
    var langTag = "en-US"
    var voiceName = ""
    var soundCues = true
    var batterySaver = false

    // ---- hooks back into VoiceHub (wake detector + persisted settings) ----
    var onArmWake: () -> Unit = {}
    var onDisarmWake: () -> Unit = {}
    var onHandsFree: (Boolean) -> Unit = {}
    var onStateChanged: (VoiceState) -> Unit = {}

    val isActive get() = state == VoiceState.Waking || state == VoiceState.Listening || state == VoiceState.Processing || state == VoiceState.Executing || state == VoiceState.Speaking

    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var heard = ""
    private var toSpeak = ""
    private var retries = 1
    private var busyRetried = false
    private var lastStartMs = 0L
    private var uttSeq = 0
    private var currentUtt = ""
    private var pendingSpeak = false

    private val processingTimeout = Runnable { if (state == VoiceState.Processing) fail("Jarvis took too long to answer. Please try again.") }
    private val speechTimeout = Runnable { if (state == VoiceState.Speaking) { stopTts(); dispatch(VoiceEvent.SpeechFinished) } }

    private val tts: TextToSpeech = TextToSpeech(app) { status ->
        val ok = status == TextToSpeech.SUCCESS
        ttsReady = ok
        if (!ok) { if (state == VoiceState.Speaking) fail("Text-to-speech isn't available on this phone.") }
        else if (pendingSpeak) { pendingSpeak = false; speak() }
    }.also { engine ->
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { if (utteranceId == currentUtt) dispatch(VoiceEvent.SpeechFinished) } }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { main.post { if (utteranceId == currentUtt) dispatch(VoiceEvent.SpeechFinished) } }
        })
    }

    fun dispatch(e: VoiceEvent) {
        val old = state
        val t = VoiceMachine.next(state, e, config)
        if (e is VoiceEvent.Failed) error = e.message else if (t.state != VoiceState.Error) error = null
        state = t.state
        if (state != VoiceState.Listening) { partial = ""; level = 0f }
        if (state == VoiceState.Listening && old != VoiceState.Listening) { retries = if (batterySaver) 0 else 1; busyRetried = false }
        if (old == VoiceState.Processing && state != VoiceState.Processing) main.removeCallbacks(processingTimeout)
        if (old != VoiceState.Processing && state == VoiceState.Processing) main.postDelayed(processingTimeout, 75_000)
        if (old == VoiceState.Speaking && state != VoiceState.Speaking) main.removeCallbacks(speechTimeout)
        t.effects.forEach { run(it) }
        if (old != state) onStateChanged(state)
    }

    // ---- public controls ----
    fun pressMic() = when (state) {
        VoiceState.Listening -> dispatch(VoiceEvent.UserStop)
        VoiceState.Processing, VoiceState.Executing, VoiceState.Waking -> Unit
        else -> dispatch(VoiceEvent.PushToTalk)
    }
    fun stop() = dispatch(VoiceEvent.UserStop)
    fun sleepNow() = dispatch(VoiceEvent.Sleep)
    fun wake() = dispatch(VoiceEvent.WakeDetected)
    fun actionStarted() = dispatch(VoiceEvent.ActionStarted)
    fun actionFinished() = dispatch(VoiceEvent.ActionFinished)
    fun aiReplied(text: String) { toSpeak = text; dispatch(VoiceEvent.AiReplied) }
    fun fail(message: String) = dispatch(VoiceEvent.Failed(message))

    fun voicesFor(tag: String): List<String> = runCatching {
        val lang = Locale.forLanguageTag(tag).language
        tts.voices.orEmpty().filter { it.locale.language == lang && !it.features.orEmpty().contains("notInstalled") }.map { it.name }.sorted()
    }.getOrDefault(emptyList())

    // ---- effects ----
    private fun run(f: Effect) {
        when (f) {
            Effect.StartRecognizer -> startRecognizer()
            Effect.StartRecognizerSoon -> main.postDelayed({ dispatch(VoiceEvent.MicReady) }, 350) // let the wake detector release the mic and the beep finish
            Effect.StopRecognizer -> runCatching { rec?.cancel() }
            Effect.StopSpeech -> stopTts()
            Effect.ReleaseMic -> releaseRecognizer()
            Effect.SendToAi -> { turn++; send(heard) }
            Effect.Speak -> speak()
            Effect.ArmWake -> onArmWake()
            Effect.DisarmWake -> onDisarmWake()
            Effect.EnableHandsFree -> onHandsFree(true)
            Effect.DisableHandsFree -> onHandsFree(false)
            Effect.CueWake -> cue(true)
            Effect.CueSleep -> cue(false)
        }
    }

    private fun releaseRecognizer() { runCatching { rec?.destroy() }; rec = null }

    private fun stopTts() {
        main.removeCallbacks(speechTimeout)
        pendingSpeak = false
        currentUtt = "" // any late onDone from the interrupted utterance is ignored
        runCatching { tts.stop() }
    }

    private fun cue(wake: Boolean) {
        if (!soundCues) return
        runCatching {
            val g = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            g.startTone(if (wake) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_ACK, 180)
            main.postDelayed({ runCatching { g.release() } }, 500)
        }
    }

    private fun startRecognizer() {
        onDisarmWake() // never let two things record at once
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Microphone permission is needed. Allow it in Settings → Permissions."); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) { fail("Speech recognition isn't available on this phone."); return }
        val r = rec ?: SpeechRecognizer.createSpeechRecognizer(app).also { it.setRecognitionListener(listener); rec = it }
        runCatching { r.cancel() }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        lastStartMs = System.currentTimeMillis()
        try { r.startListening(i) } catch (e: Exception) { fail("Couldn't start listening: ${e.message}") }
    }

    private fun noSpeech() { val r = retries; retries--; dispatch(VoiceEvent.NoSpeech(r)) }

    private fun applyVoice() {
        val locale = Locale.forLanguageTag(langTag)
        val chosen = if (voiceName.isNotBlank()) runCatching { tts.voices?.firstOrNull { it.name == voiceName } }.getOrNull() else null
        if (chosen != null) { tts.voice = chosen; note = null }
        else {
            val r = tts.setLanguage(locale)
            note = if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                "No text-to-speech voice for ${locale.displayName} is installed, so answers may sound wrong. Install one in Android Settings → Text-to-speech." else null
        }
        tts.setSpeechRate(speechRate)
    }

    private fun speak() {
        if (!ttsReady) { pendingSpeak = true; main.postDelayed(speechTimeout, 8_000); return }
        val text = cleanForSpeech(toSpeak)
        if (text.isBlank()) { dispatch(VoiceEvent.SpeechFinished); return }
        applyVoice()
        val chunks = chunk(text, minOf(TextToSpeech.getMaxSpeechInputLength(), 3500))
        val base = "jarvis-${++uttSeq}"
        chunks.forEachIndexed { i, c ->
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.1f, 1f)) }
            val id = "$base-$i"
            currentUtt = id // the last chunk's id is the one that ends the turn
            if (tts.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) {
                fail("Text-to-speech failed to start."); return
            }
        }
        main.postDelayed(speechTimeout, 20_000L + text.length * 120L) // safety net if an engine never reports "done"
    }

    private fun cleanForSpeech(s: String) = s.replace(Regex("https?://\\S+"), " ").replace(Regex("[*_`#>|~]+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun chunk(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>(); var rest = text
        while (rest.length > max) {
            var cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?', '।', '\n'), max - 1)
            if (cut < max / 2) cut = rest.lastIndexOf(' ', max - 1)
            if (cut <= 0) cut = max - 1
            out += rest.substring(0, cut + 1).trim(); rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotBlank()) out += rest
        return out
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {
            if (state != VoiceState.Listening) return
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
        }
        override fun onResults(results: Bundle?) {
            if (state != VoiceState.Listening) return // late/duplicate result: ignore
            val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            lastHeard = raw
            if (raw.isEmpty()) { noSpeech(); return }
            val isSleep = PhraseMatcher.matchesAny(raw, config.sleepPhrases)
            val command = if (isSleep) raw else PhraseMatcher.stripLeading(raw, wakePhrases) // "Hey Jarvis, what time is it" -> "what time is it"
            if (command.isBlank()) { noSpeech(); return }  // only the wake phrase was said
            heard = command
            dispatch(VoiceEvent.Heard(raw))
        }
        override fun onError(error: Int) {
            if (state != VoiceState.Listening) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> noSpeech()
                SpeechRecognizer.ERROR_CLIENT -> if (System.currentTimeMillis() - lastStartMs > 400) noSpeech() // else: stale callback from our own cancel()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    if (!busyRetried) { busyRetried = true; releaseRecognizer(); main.postDelayed({ if (state == VoiceState.Listening) startRecognizer() }, 350) }
                    else fail("The speech recognizer is busy. Close other apps that use the microphone and try again.")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission is needed. Allow it in Settings → Permissions.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    fail("Speech recognition needs a network connection on this phone.")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    fail("Speech recognition for $langTag isn't available. Pick another speech language, or install it in the Google app → Settings → Voice → Languages.")
                SpeechRecognizer.ERROR_AUDIO -> fail("The microphone couldn't be opened. Another app may be using it.")
                else -> fail("Couldn't hear you (code $error). Try again.")
            }
        }
    }

    fun release() {
        main.removeCallbacks(processingTimeout); main.removeCallbacks(speechTimeout)
        releaseRecognizer()
        runCatching { tts.stop(); tts.shutdown() }
    }
}
