package com.cutejarvis.app.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.math.sqrt

/** The on-device speech model used ONLY for wake-phrase spotting (Vosk small English, Apache-2.0, ~40 MB). Downloaded on demand. */
object WakeModel {
    const val URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val NAME = "vosk-model-small-en-us-0.15"
    private const val MAX_ZIP = 120L * 1024 * 1024
    private const val MAX_UNPACKED = 400L * 1024 * 1024

    fun dir(ctx: Context) = File(ctx.filesDir, "wake/$NAME")
    fun installed(ctx: Context) = File(dir(ctx), "am/final.mdl").isFile
    fun delete(ctx: Context) { File(ctx.filesDir, "wake").deleteRecursively() }

    /** HTTPS download + safe unzip. [onProgress] is called from a background thread with 0..100. */
    suspend fun download(ctx: Context, onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        val root = File(ctx.filesDir, "wake").apply { mkdirs() }
        val zip = File(root, "model.zip.part"); val tmp = File(root, ".extract")
        try {
            tmp.deleteRecursively(); tmp.mkdirs()
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(URL).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
                val body = r.body ?: error("Empty download")
                val total = body.contentLength(); var done = 0L
                body.byteStream().use { input ->
                    zip.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (done > MAX_ZIP) error("Download is larger than expected")
                            if (total > 0) onProgress((done * 90 / total).toInt())
                        }
                    }
                }
            }
            unzip(zip, tmp)
            val extracted = File(tmp, NAME)
            if (!File(extracted, "am/final.mdl").isFile) error("Downloaded model looks incomplete")
            dir(ctx).deleteRecursively()
            if (!extracted.renameTo(dir(ctx))) error("Couldn't install the model")
            onProgress(100)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            zip.delete(); tmp.deleteRecursively()
        }
    }

    private fun unzip(zip: File, dest: File) {
        val base = dest.canonicalPath + File.separator
        var written = 0L
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val out = File(dest, e.name)
                if (!out.canonicalPath.startsWith(base)) error("Unsafe file path in archive") // zip-slip guard
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = zin.read(buf); if (n < 0) break
                        o.write(buf, 0, n); written += n
                        if (written > MAX_UNPACKED) error("Archive is larger than expected")
                    }
                }
            }
        }
    }
}

/**
 * Genuine on-device wake-phrase spotting: raw microphone audio -> Vosk recognizer restricted to a tiny grammar
 * (the wake phrases + "[unk]"). Nothing is sent over the network. English words only.
 *
 * Owns its own AudioRecord on a worker thread; [pause] fully releases the microphone (so the system SpeechRecognizer
 * can use it) and [resume] re-opens it. Calls to [resume]/[pause] are idempotent. Use from the main thread.
 */
class VoskWakeEngine(
    private val ctx: Context,
    private val onWake: () -> Unit,        // called on the main thread, after the microphone was released
    private val onStatus: (String) -> Unit, // called on the main thread
) {
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var run = false
    @Volatile var phrases: List<String> = emptyList()
    @Volatile var batterySaver = false

    val armed get() = worker?.isAlive == true

    private fun status(msg: String) { main.post { onStatus(msg) } }

    fun loadModel(): Boolean {
        if (model != null) return true
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(WakeModel.dir(ctx).absolutePath); true
        } catch (t: Throwable) { status("Wake-word model couldn't be loaded: ${t.message}"); false }
    }

    @SuppressLint("MissingPermission") // checked by VoiceHub / the service before this is ever called
    @Synchronized fun resume() {
        if (armed) return
        if (!loadModel()) return
        val m = model ?: return
        val words = phrases.map { PhraseMatcher.normalize(it) }.filter { Regex("^[a-z ]+$").matches(it) }.distinct()
        if (words.isEmpty()) { status("Wake phrase must use plain English letters (e.g. “Hey Jarvis”)."); return }
        val grammar = JSONArray().apply { words.forEach { put(it) }; put("[unk]") }.toString()
        run = true
        worker = Thread({ loop(m, grammar, words) }, "wake-word").apply { isDaemon = true; start() }
    }

    @Synchronized fun pause() {
        run = false
        val w = worker
        if (w != null && w !== Thread.currentThread()) runCatching { w.join(600) }
    }

    @Synchronized fun release() {
        pause()
        runCatching { model?.close() }; model = null
    }

    @SuppressLint("MissingPermission")
    private fun loop(m: Model, grammar: String, words: List<String>) {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { status("This phone can't record 16 kHz audio."); return }
        var rec: AudioRecord? = null
        var recognizer: Recognizer? = null
        var fired = false
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val ar = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
            rec = ar
            if (ar.state != AudioRecord.STATE_INITIALIZED) { status("The microphone is busy or unavailable."); return }
            val rcg = Recognizer(m, rate.toFloat(), grammar)
            recognizer = rcg
            ar.startRecording()
            if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) { status("The microphone is being used by another app."); return }
            status("Listening for the wake phrase")
            val buf = ShortArray(1600) // 100 ms
            val preroll = ArrayDeque<ShortArray>()
            var hang = 0
            while (run) {
                val n = ar.read(buf, 0, buf.size)
                if (n < 0) { status("Microphone error ($n)"); break }
                if (n == 0) continue
                if (batterySaver) { // only spend CPU decoding while there is sound (+ 0.8 s tail and 0.3 s pre-roll)
                    val loud = rms(buf, n) > 400
                    if (loud) hang = 8 else if (hang > 0) hang--
                    if (!loud && hang == 0) { preroll.addLast(buf.copyOf(n)); if (preroll.size > 3) preroll.removeFirst(); continue }
                    if (preroll.isNotEmpty()) { preroll.forEach { rcg.acceptWaveForm(it, it.size) }; preroll.clear() }
                }
                val text = if (rcg.acceptWaveForm(buf, n)) JSONObject(rcg.result).optString("text")
                           else JSONObject(rcg.partialResult).optString("partial")
                if (text.isNotBlank() && PhraseMatcher.matchesAny(text, words)) { fired = true; break }
            }
        } catch (t: Throwable) {
            status("Wake detector stopped: ${t.message}")
        } finally {
            runCatching { rec?.stop() }; runCatching { rec?.release() }; runCatching { recognizer?.close() }
            if (worker === Thread.currentThread()) worker = null
        }
        if (fired) main.post { onWake() }
    }

    private fun rms(b: ShortArray, n: Int): Double { var s = 0.0; for (i in 0 until n) s += b[i].toDouble() * b[i]; return sqrt(s / n) }
}
