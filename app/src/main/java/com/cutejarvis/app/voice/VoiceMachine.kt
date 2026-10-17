package com.cutejarvis.app.voice

import java.util.Locale

/** IDLE: nothing active. SLEEPING: put to sleep by voice/UI (hands-free off). Both release the mic; the wake detector may be armed. */
/** Processing = "THINKING" in the UI. Waking = wake word heard, mic being handed over. Executing = a phone action is running. */
enum class VoiceState { Idle, Waking, Listening, Processing, Executing, Speaking, Sleeping, Error }

enum class Effect {
    StartRecognizer, StartRecognizerSoon, StopRecognizer, StopSpeech, ReleaseMic, SendToAi, Speak,
    ArmWake, DisarmWake, EnableHandsFree, DisableHandsFree, CueWake, CueSleep,
}

sealed interface VoiceEvent {
    data class Heard(val text: String) : VoiceEvent
    data class Failed(val message: String) : VoiceEvent
    /** Recognizer ended with nothing usable. [retriesLeft] > 0 lets hands-free mode listen once more instead of giving up. */
    data class NoSpeech(val retriesLeft: Int = 0) : VoiceEvent
    data object WakeDetected : VoiceEvent
    /** Mic handed over after a wake word; begin capturing the command. */
    data object MicReady : VoiceEvent
    data object ActionStarted : VoiceEvent
    data object ActionFinished : VoiceEvent
    data object PushToTalk : VoiceEvent
    data object AiReplied : VoiceEvent
    data object SpeechFinished : VoiceEvent
    data object Interrupt : VoiceEvent
    /** Tap on stop / leaving the screen: back to IDLE without touching the hands-free setting. */
    data object UserStop : VoiceEvent
    /** Explicit sleep (voice command already matched elsewhere, or a UI action). */
    data object Sleep : VoiceEvent
}

/** [wakeArmed] = wake-word detection is enabled AND usable (permission + model + service running). */
data class VoiceConfig(val sleepPhrases: List<String>, val handsFree: Boolean = true, val wakeArmed: Boolean = false)

data class Transition(val state: VoiceState, val effects: List<Effect> = emptyList())

object PhraseMatcher {
    // \p{M} keeps Devanagari vowel signs / viraama, which are combining marks (not letters).
    private val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N} ]")
    private val SPACES = Regex("\\s+")

    fun normalize(s: String) = s.lowercase(Locale.ROOT).replace(NON_WORD, " ").replace(SPACES, " ").trim()

    /** Whole-word match, so "jarvis sleep" does not fire on "jarvis sleeping beauty". */
    fun matches(heard: String, phrase: String): Boolean {
        val p = normalize(phrase)
        return p.isNotEmpty() && " ${normalize(heard)} ".contains(" $p ")
    }

    fun matchesAny(heard: String, phrases: List<String>) = phrases.any { matches(heard, it) }

    /** "Hey Jarvis, what's the time" -> "what's the time". Only strips a phrase at the very start. */
    fun stripLeading(heard: String, phrases: List<String>): String {
        val words = heard.trim().split(SPACES).filter { it.isNotEmpty() }
        for (p in phrases.sortedByDescending { it.length }) {
            val pw = normalize(p).split(' ').filter { it.isNotEmpty() }
            if (pw.isEmpty() || words.size < pw.size) continue
            if (words.take(pw.size).joinToString(" ") { normalize(it) } == pw.joinToString(" ")) {
                return words.drop(pw.size).joinToString(" ").trimStart(',', '.', '!', '?', ' ')
            }
        }
        return heard.trim()
    }
}

object Phrases {
    /** Custom phrase fields may hold several phrases separated by `|`, `;` or a new line. */
    fun split(custom: String) = custom.split('|', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun sleep(name: String, custom: String): List<String> {
        val n = name.ifBlank { "Jarvis" }
        val builtIn = listOf(
            "okay bye $n", "ok bye $n", "okay bye bye $n", "$n go to sleep", "go to sleep $n", "$n sleep mode", "$n stop listening", "$n sleep",
            // Nepali (best effort; the recognizer may transliterate English words, so also add what you see via the custom field)
            "जार्भिस सुत", "जार्विस सुत", "जार्भिस सुत्नुहोस्", "जार्विस सुत्नुहोस्", "जार्विस गो टु स्लिप", "जार्भिस गो टु स्लिप",
        )
        return dedupe(builtIn + split(custom))
    }

    fun wake(name: String, custom: String): List<String> {
        val n = name.ifBlank { "Jarvis" }
        return dedupe(listOf("okay $n", "ok $n", "hey $n", "$n wake up", "wake up $n") + split(custom))
    }

    private fun dedupe(l: List<String>) = l.distinctBy { PhraseMatcher.normalize(it) }.filter { PhraseMatcher.normalize(it).isNotEmpty() }
}

object VoiceMachine {
    private val SHUTDOWN = listOf(Effect.StopRecognizer, Effect.StopSpeech, Effect.ReleaseMic)

    private fun standby(cfg: VoiceConfig, extra: List<Effect> = emptyList()) =
        SHUTDOWN + extra + if (cfg.wakeArmed) listOf(Effect.ArmWake) else emptyList()

    private fun sleep(cfg: VoiceConfig) =
        Transition(VoiceState.Sleeping, standby(cfg, listOf(Effect.DisableHandsFree, Effect.CueSleep)))

    fun next(state: VoiceState, event: VoiceEvent, cfg: VoiceConfig): Transition = when (event) {
        is VoiceEvent.Heard -> when {
            // Nothing should be recording in these states: a late/duplicate result must never trigger anything.
            state == VoiceState.Idle || state == VoiceState.Sleeping || state == VoiceState.Error || state == VoiceState.Waking -> Transition(state)
            PhraseMatcher.matchesAny(event.text, cfg.sleepPhrases) -> sleep(cfg)
            state == VoiceState.Listening -> Transition(VoiceState.Processing, listOf(Effect.StopRecognizer, Effect.SendToAi))
            else -> Transition(state)
        }
        VoiceEvent.WakeDetected ->
            if (state == VoiceState.Sleeping || state == VoiceState.Idle || state == VoiceState.Error)
                Transition(VoiceState.Waking, listOf(Effect.DisarmWake, Effect.EnableHandsFree, Effect.CueWake, Effect.StartRecognizerSoon))
            else Transition(state)
        VoiceEvent.MicReady -> if (state == VoiceState.Waking) Transition(VoiceState.Listening, listOf(Effect.StartRecognizer)) else Transition(state)
        VoiceEvent.ActionStarted -> if (state == VoiceState.Processing) Transition(VoiceState.Executing) else Transition(state)
        VoiceEvent.ActionFinished -> if (state == VoiceState.Executing) Transition(VoiceState.Processing) else Transition(state)
        VoiceEvent.PushToTalk -> when (state) {
            VoiceState.Speaking -> Transition(VoiceState.Listening, listOf(Effect.StopSpeech, Effect.StartRecognizer))
            VoiceState.Sleeping -> Transition(VoiceState.Listening, listOf(Effect.DisarmWake, Effect.EnableHandsFree, Effect.StartRecognizer))
            VoiceState.Idle, VoiceState.Error -> Transition(VoiceState.Listening, listOf(Effect.DisarmWake, Effect.StartRecognizer))
            else -> Transition(state)
        }
        VoiceEvent.AiReplied -> if (state == VoiceState.Processing || state == VoiceState.Executing) Transition(VoiceState.Speaking, listOf(Effect.Speak)) else Transition(state)
        VoiceEvent.SpeechFinished ->
            if (state != VoiceState.Speaking) Transition(state)
            else if (cfg.handsFree) Transition(VoiceState.Listening, listOf(Effect.StartRecognizer))
            else Transition(VoiceState.Idle, standby(cfg))
        VoiceEvent.Interrupt -> if (state == VoiceState.Speaking) Transition(VoiceState.Listening, listOf(Effect.StopSpeech, Effect.StartRecognizer)) else Transition(state)
        is VoiceEvent.NoSpeech ->
            if (state != VoiceState.Listening) Transition(state)
            else if (cfg.handsFree && event.retriesLeft > 0) Transition(VoiceState.Listening, listOf(Effect.StartRecognizer))
            else Transition(VoiceState.Idle, standby(cfg))
        is VoiceEvent.Failed -> Transition(VoiceState.Error, standby(cfg))
        VoiceEvent.UserStop -> if (state == VoiceState.Sleeping) Transition(state) else Transition(VoiceState.Idle, standby(cfg))
        VoiceEvent.Sleep -> if (state == VoiceState.Sleeping) Transition(state) else sleep(cfg)
    }
}
