package com.cutejarvis.app.voice

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cutejarvis.app.data.LocalSettings
import com.cutejarvis.app.data.SettingsStore
import com.cutejarvis.app.data.speechTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the single [VoiceSession], the wake-word engine and their settings, so voice keeps working
 * when the Activity is gone (foreground service running). Everything here runs on the main thread.
 */
object VoiceHub {
    private lateinit var app: Application
    private lateinit var store: SettingsStore
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var sessionCreated = false

    @Volatile var settings = LocalSettings(); private set

    /** Set by MainViewModel while it is alive so voice turns show up in Chat; otherwise [HeadlessChat] is used. */
    var responder: (suspend (String) -> Result<String>)? = null

    var wakeEngine: VoskWakeEngine? = null; private set
    var wakeRunning by mutableStateOf(false); private set
    var wakeStatus by mutableStateOf(""); private set
    var modelInstalled by mutableStateOf(false); private set
    var modelProgress by mutableIntStateOf(-1); private set   // -1 = not downloading, else 0..100
    var modelError by mutableStateOf<String?>(null); private set

    val session: VoiceSession by lazy { createSession() }

    fun init(application: Application) {
        if (started) return
        started = true
        app = application
        store = SettingsStore(app)
        modelInstalled = WakeModel.installed(app)
        scope.launch {
            store.flow.collect { s ->
                val wasOn = settings.wakeWordEnabled
                settings = s
                applySettings()
                if (wasOn && !s.wakeWordEnabled && WakeWordService.running) WakeWordService.stop(app)
            }
        }
    }

    // ------------------------------------------------------------------ session wiring
    private fun createSession(): VoiceSession {
        val sess = VoiceSession(app) { text -> handleAsk(text) }
        applyTo(sess, settings)
        sess.onDisarmWake = { wakeEngine?.pause() }
        sess.onArmWake = {
            // give the system recognizer a moment to let go of the microphone before the detector takes it
            main.postDelayed({ if (WakeWordService.running && isStandby(sess.state)) wakeEngine?.resume() }, 300)
        }
        sess.onHandsFree = { on -> scope.launch { store.update { it.copy(handsFree = on) } } }
        sess.onStateChanged = { WakeWordService.refresh() }
        sessionCreated = true
        return sess
    }

    private fun isStandby(s: VoiceState) = s == VoiceState.Idle || s == VoiceState.Sleeping || s == VoiceState.Error

    private fun applySettings() { if (sessionCreated) applyTo(session, settings) }

    private fun applyTo(sess: VoiceSession, s: LocalSettings) {
        sess.config = VoiceConfig(Phrases.sleep(s.assistantName, s.sleepPhrase), s.handsFree, s.wakeWordEnabled && WakeWordService.running && wakeEngine != null)
        sess.wakePhrases = Phrases.wake(s.assistantName, s.wakePhrase)
        sess.speechRate = s.speechRate; sess.volume = s.volume; sess.langTag = s.speechTag()
        sess.voiceName = s.voiceName; sess.soundCues = s.soundCues; sess.batterySaver = s.batterySaver
        wakeEngine?.let { it.phrases = sess.wakePhrases; it.batterySaver = s.batterySaver }
    }

    private fun handleAsk(text: String) {
        val turn = session.turn
        scope.launch {
            val result = try {
                responder?.invoke(text) ?: HeadlessChat.ask(app, text)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            // The user may have said "go to sleep", stopped, or started a newer turn while we waited: drop stale replies.
            if ((session.state != VoiceState.Processing && session.state != VoiceState.Executing) || session.turn != turn) return@launch
            result.fold(
                { session.aiReplied(it) },
                { session.fail(it.message ?: "I couldn't get an answer. Check your AI provider in Settings.") },
            )
        }
    }

    // ------------------------------------------------------------------ wake word service
    fun micGranted(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts the foreground service. Must be called while the app is visible. Returns an error message, or null on success. */
    fun startWake(ctx: Context): String? {
        if (!micGranted(ctx)) return "Microphone permission is needed first."
        if (!WakeModel.installed(ctx)) return "Download the wake-word model first."
        return try { WakeWordService.start(ctx); null } catch (e: Exception) { "Android wouldn't start the listening service: ${e.message}" }
    }

    /** Settings toggle. Returns an error message when it can't be switched on. */
    fun setWakeEnabled(ctx: Context, on: Boolean): String? {
        if (on) startWake(ctx)?.let { return it }
        scope.launch { store.update { it.copy(wakeWordEnabled = on) } }
        if (!on) { wakeEngine?.pause(); WakeWordService.stop(ctx) }
        return null
    }

    /** Called when the app becomes visible: restore the service if the user had wake word switched on. */
    fun syncWake(ctx: Context) {
        scope.launch {
            val s = store.current()
            if (s.wakeWordEnabled && !WakeWordService.running) {
                startWake(ctx)?.let { err ->
                    wakeStatus = err
                    store.update { it.copy(wakeWordEnabled = false) }
                }
            }
        }
    }

    /** Service callbacks */
    fun attachWake() {
        if (wakeEngine == null) {
            wakeEngine = VoskWakeEngine(app, onWake = { onWakeWord() }, onStatus = { postWakeStatus(it) })
        }
        wakeRunning = true
        applySettings()
        if (sessionCreated) {
            if (isStandby(session.state)) { wakeEngine?.phrases = session.wakePhrases; wakeEngine?.batterySaver = settings.batterySaver; wakeEngine?.resume() }
        } else {
            val s = settings
            wakeEngine?.phrases = Phrases.wake(s.assistantName, s.wakePhrase); wakeEngine?.batterySaver = s.batterySaver
            wakeEngine?.resume()
        }
    }

    fun detachWake() {
        wakeEngine?.release(); wakeEngine = null
        wakeRunning = false
        applySettings()
    }

    fun wakeStoppedByUser() { scope.launch { store.update { it.copy(wakeWordEnabled = false) } } }
    fun wakeAutoOff() { scope.launch { store.update { it.copy(wakeWordEnabled = false) } } }
    /** Called by the ViewModel around a phone action so the UI/notification show EXECUTING during a voice turn. */
    fun executing(on: Boolean) { if (sessionCreated) { if (on) session.actionStarted() else session.actionFinished() } }
    fun postWakeStatus(msg: String) { wakeStatus = msg; WakeWordService.refresh() }

    private fun onWakeWord() {
        WakeWordService.touch()
        session.dispatch(VoiceEvent.WakeDetected)
    }

    fun statusLine(): String {
        val wake = Phrases.wake(settings.assistantName, settings.wakePhrase).firstOrNull() ?: "the wake phrase"
        if (!sessionCreated) return "Waiting for “$wake”"
        return when (session.state) {
            VoiceState.Listening -> "Listening…"
            VoiceState.Waking -> "Waking up…"
            VoiceState.Processing -> "Thinking…"
            VoiceState.Executing -> "Running an action…"
            VoiceState.Speaking -> "Speaking…"
            VoiceState.Sleeping -> "Sleeping — say “$wake” to wake me"
            else -> if (wakeStatus.startsWith("Listening for")) "Waiting for “$wake”" else wakeStatus.ifBlank { "Waiting for “$wake”" }
        }
    }

    // ------------------------------------------------------------------ screen / app lifecycle
    fun onVoiceScreenClosed() { if (sessionCreated && !WakeWordService.running && session.isActive) session.stop() }
    /** Without the foreground service Android blocks microphone use in the background, so stop cleanly instead of failing. */
    fun onAppBackgrounded() { onVoiceScreenClosed() }

    // ------------------------------------------------------------------ wake-word model
    fun downloadModel() {
        if (modelProgress >= 0) return
        modelError = null; modelProgress = 0
        scope.launch {
            val r = WakeModel.download(app) { p -> main.post { modelProgress = p } }
            modelProgress = -1
            r.onSuccess { modelInstalled = true }.onFailure { modelError = it.message ?: "Download failed" }
        }
    }

    fun deleteModel() {
        scope.launch { store.update { it.copy(wakeWordEnabled = false) } }
        wakeEngine?.release()
        if (WakeWordService.running) WakeWordService.stop(app)
        WakeModel.delete(app); modelInstalled = false
    }
}
