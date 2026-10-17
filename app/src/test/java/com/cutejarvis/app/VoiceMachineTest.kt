package com.cutejarvis.app

import com.cutejarvis.app.voice.*
import org.junit.Assert.*
import org.junit.Test

class VoiceMachineTest {
    private val sleepPhrases = Phrases.sleep("Jarvis", "Thank You Jarvis")
    private val cfg = VoiceConfig(sleepPhrases, handsFree = true, wakeArmed = true)
    private fun next(s: VoiceState, e: VoiceEvent, c: VoiceConfig = cfg) = VoiceMachine.next(s, e, c)

    // ---- sleep commands (all three required phrases, plus the old one) ----
    @Test fun requiredSleepPhrasesAllWork() {
        listOf("Jarvis, go to sleep", "jarvis sleep mode", "Jarvis, stop listening.", "ok thank you, Jarvis!").forEach { phrase ->
            val t = next(VoiceState.Listening, VoiceEvent.Heard(phrase))
            assertEquals(phrase, VoiceState.Sleeping, t.state)
            assertTrue(t.effects.containsAll(listOf(Effect.StopRecognizer, Effect.StopSpeech, Effect.ReleaseMic, Effect.DisableHandsFree)))
        }
    }
    @Test fun sleepArmsWakeDetectorLastAndOnlyWhenEnabled() {
        val on = next(VoiceState.Listening, VoiceEvent.Heard("jarvis go to sleep"))
        assertEquals(Effect.ArmWake, on.effects.last())
        assertTrue(on.effects.indexOf(Effect.ReleaseMic) < on.effects.indexOf(Effect.ArmWake))
        val off = next(VoiceState.Listening, VoiceEvent.Heard("jarvis go to sleep"), cfg.copy(wakeArmed = false))
        assertFalse(Effect.ArmWake in off.effects)
    }
    @Test fun sleepWhileSpeakingAndThinkingStopsEverything() {
        assertTrue(Effect.StopSpeech in next(VoiceState.Speaking, VoiceEvent.Heard("jarvis stop listening")).effects)
        assertEquals(VoiceState.Sleeping, next(VoiceState.Processing, VoiceEvent.Heard("jarvis sleep mode")).state)
    }
    @Test fun sleepingIgnoresEverythingExceptWakeAndTap() {
        listOf(VoiceEvent.Heard("what time is it"), VoiceEvent.AiReplied, VoiceEvent.SpeechFinished, VoiceEvent.NoSpeech(1), VoiceEvent.Interrupt).forEach {
            val t = next(VoiceState.Sleeping, it)
            assertEquals(VoiceState.Sleeping, t.state); assertTrue(t.effects.isEmpty())
        }
    }
    @Test fun sleepIsIdempotent() {
        assertTrue(next(VoiceState.Sleeping, VoiceEvent.Sleep).effects.isEmpty())
        assertTrue(next(VoiceState.Sleeping, VoiceEvent.Heard("jarvis go to sleep")).effects.isEmpty())
    }
    @Test fun customAndMultiplePhrasesWork() {
        val p = Phrases.sleep("Luna", "Goodnight Luna | shut down now")
        assertEquals(VoiceState.Sleeping, next(VoiceState.Listening, VoiceEvent.Heard("goodnight luna"), cfg.copy(sleepPhrases = p)).state)
        assertEquals(VoiceState.Sleeping, next(VoiceState.Listening, VoiceEvent.Heard("please SHUT DOWN now"), cfg.copy(sleepPhrases = p)).state)
        assertEquals(VoiceState.Sleeping, next(VoiceState.Listening, VoiceEvent.Heard("luna, go to sleep"), cfg.copy(sleepPhrases = p)).state)
    }
    @Test fun nepaliSleepPhraseMatchesDespiteCombiningMarks() {
        assertEquals(VoiceState.Sleeping, next(VoiceState.Listening, VoiceEvent.Heard("जार्भिस सुत्नुहोस्")).state)
        assertEquals("जार्भिस सुत्नुहोस्", PhraseMatcher.normalize("जार्भिस, सुत्नुहोस्!"))
    }
    @Test fun matchingIsWholeWord() {
        assertFalse(PhraseMatcher.matches("jarvis sleeping beauty", "jarvis sleep"))
        assertFalse(PhraseMatcher.matches("anything", ""))
        assertEquals(VoiceState.Processing, next(VoiceState.Listening, VoiceEvent.Heard("tell me a story about a sleep mode phone")).state)
    }

    // ---- wake ----
    @Test fun wakeFromSleepEnablesHandsFreeAndListens() {
        val t = next(VoiceState.Sleeping, VoiceEvent.WakeDetected)
        assertEquals(VoiceState.Waking, t.state)
        assertEquals(listOf(Effect.DisarmWake, Effect.EnableHandsFree, Effect.CueWake, Effect.StartRecognizerSoon), t.effects)
        val l = next(VoiceState.Waking, VoiceEvent.MicReady)
        assertEquals(VoiceState.Listening, l.state); assertEquals(listOf(Effect.StartRecognizer), l.effects)
    }
    @Test fun wakeDisarmsDetectorBeforeRecognizerStarts() {
        val e = next(VoiceState.Idle, VoiceEvent.WakeDetected).effects
        assertTrue(e.indexOf(Effect.DisarmWake) < e.indexOf(Effect.StartRecognizerSoon))
        assertFalse(Effect.StartRecognizer in e) // the mic only opens after MicReady
    }
    @Test fun duplicateWakeWhileActiveIsIgnored() {
        listOf(VoiceState.Waking, VoiceState.Listening, VoiceState.Processing, VoiceState.Executing, VoiceState.Speaking).forEach {
            val t = next(it, VoiceEvent.WakeDetected); assertEquals(it, t.state); assertTrue(t.effects.isEmpty())
        }
    }
    @Test fun tapMicFromSleepWakesToo() {
        val t = next(VoiceState.Sleeping, VoiceEvent.PushToTalk)
        assertEquals(VoiceState.Listening, t.state); assertTrue(Effect.EnableHandsFree in t.effects)
    }

    // ---- conversation loop ----
    @Test fun fullCycleListenProcessSpeakListen() {
        var s = next(VoiceState.Sleeping, VoiceEvent.WakeDetected).state
        assertEquals(VoiceState.Waking, s)
        s = next(s, VoiceEvent.MicReady).state
        assertEquals(VoiceState.Listening, s)
        val heard = next(s, VoiceEvent.Heard("what time is it")); s = heard.state
        assertEquals(VoiceState.Processing, s); assertTrue(Effect.SendToAi in heard.effects)
        assertEquals(VoiceState.Speaking, next(s, VoiceEvent.AiReplied).also { s = it.state }.state)
        assertEquals(VoiceState.Listening, next(s, VoiceEvent.SpeechFinished).state)
    }
    @Test fun withoutHandsFreeSpeechEndsInIdleAndReleasesMic() {
        val t = next(VoiceState.Speaking, VoiceEvent.SpeechFinished, cfg.copy(handsFree = false))
        assertEquals(VoiceState.Idle, t.state); assertTrue(Effect.ReleaseMic in t.effects); assertEquals(Effect.ArmWake, t.effects.last())
    }
    @Test fun duplicateAndLateEventsAreIgnored() {
        assertEquals(VoiceState.Processing, next(VoiceState.Processing, VoiceEvent.Heard("again")).state)
        assertTrue(next(VoiceState.Processing, VoiceEvent.Heard("again")).effects.isEmpty())
        assertTrue(next(VoiceState.Listening, VoiceEvent.AiReplied).effects.isEmpty())
        assertTrue(next(VoiceState.Listening, VoiceEvent.SpeechFinished).effects.isEmpty())
        assertTrue(next(VoiceState.Idle, VoiceEvent.Heard("late result")).effects.isEmpty())
        assertTrue(next(VoiceState.Listening, VoiceEvent.PushToTalk).effects.isEmpty())
    }
    @Test fun silenceRetriesOnceThenStopsListening() {
        val retry = next(VoiceState.Listening, VoiceEvent.NoSpeech(retriesLeft = 1))
        assertEquals(VoiceState.Listening, retry.state)
        val giveUp = next(VoiceState.Listening, VoiceEvent.NoSpeech(retriesLeft = 0))
        assertEquals(VoiceState.Idle, giveUp.state); assertTrue(Effect.ReleaseMic in giveUp.effects)
        assertEquals(VoiceState.Idle, next(VoiceState.Listening, VoiceEvent.NoSpeech(5), cfg.copy(handsFree = false)).state)
    }
    @Test fun bargeInWhileSpeaking() {
        val t = next(VoiceState.Speaking, VoiceEvent.PushToTalk)
        assertEquals(VoiceState.Listening, t.state); assertTrue(Effect.StopSpeech in t.effects)
    }

    // ---- errors / stop ----
    @Test fun failureReleasesMicAndShowsError() {
        val t = next(VoiceState.Listening, VoiceEvent.Failed("x"))
        assertEquals(VoiceState.Error, t.state); assertTrue(Effect.ReleaseMic in t.effects)
        assertEquals(VoiceState.Listening, next(VoiceState.Error, VoiceEvent.PushToTalk).state)
    }
    @Test fun userStopGoesIdleWithoutChangingHandsFree() {
        val t = next(VoiceState.Speaking, VoiceEvent.UserStop)
        assertEquals(VoiceState.Idle, t.state)
        assertFalse(Effect.DisableHandsFree in t.effects)
        assertTrue(t.effects.containsAll(listOf(Effect.StopRecognizer, Effect.StopSpeech, Effect.ReleaseMic)))
    }

    // ---- phrase helpers ----
    @Test fun wakePhraseIsStrippedFromCommand() {
        val wake = Phrases.wake("Jarvis", "")
        assertEquals("what's the time", PhraseMatcher.stripLeading("Hey Jarvis, what's the time", wake))
        assertEquals("", PhraseMatcher.stripLeading("hey jarvis", wake))
        assertEquals("tell hey jarvis a joke", PhraseMatcher.stripLeading("tell hey jarvis a joke", wake))
    }
    @Test fun builtInPhrasesFollowAssistantName() {
        assertTrue(Phrases.wake("Luna", "").contains("hey Luna"))
        assertTrue(Phrases.sleep("Luna", "").contains("Luna go to sleep"))
        assertEquals(1, Phrases.wake("Jarvis", "Hey Jarvis").count { PhraseMatcher.normalize(it) == "hey jarvis" })
    }

    // ---- "Okay Jarvis" / "Okay Bye Jarvis" ----
    @Test fun okayByeJarvisSleeps() {
        listOf("Okay Bye Jarvis", "okay, bye Jarvis!", "ok bye jarvis").forEach {
            assertEquals(it, VoiceState.Sleeping, next(VoiceState.Listening, VoiceEvent.Heard(it)).state)
        }
    }
    @Test fun okayJarvisIsAWakePhraseAndIsStripped() {
        val wake = Phrases.wake("Jarvis", "")
        assertTrue(PhraseMatcher.matchesAny("okay jarvis", wake))
        assertEquals("what's the time", PhraseMatcher.stripLeading("Okay Jarvis, what's the time", wake))
        assertFalse("sleep phrase must not look like a wake phrase", PhraseMatcher.matchesAny("okay bye jarvis", wake))
    }
    @Test fun okayJarvisWakesFromSleep() {
        assertEquals(VoiceState.Waking, next(VoiceState.Sleeping, VoiceEvent.WakeDetected).state)
    }

    // ---- WAKING / EXECUTING ----
    @Test fun micReadyOutsideWakingIsIgnored() {
        listOf(VoiceState.Sleeping, VoiceState.Listening, VoiceState.Processing, VoiceState.Speaking, VoiceState.Idle).forEach {
            val t = next(it, VoiceEvent.MicReady); assertEquals(it, t.state); assertTrue(t.effects.isEmpty())
        }
    }
    @Test fun speechHeardWhileWakingIsIgnored() {
        assertEquals(VoiceState.Waking, next(VoiceState.Waking, VoiceEvent.Heard("what time is it")).state)
    }
    @Test fun sleepCommandWorksWhileWaking() {
        assertEquals(VoiceState.Sleeping, next(VoiceState.Waking, VoiceEvent.Sleep).state)
    }
    @Test fun executingRoundTripAndReply() {
        assertEquals(VoiceState.Executing, next(VoiceState.Processing, VoiceEvent.ActionStarted).state)
        assertEquals(VoiceState.Processing, next(VoiceState.Executing, VoiceEvent.ActionFinished).state)
        assertEquals(VoiceState.Speaking, next(VoiceState.Executing, VoiceEvent.AiReplied).state)
    }
    @Test fun actionEventsOutsideATurnAreIgnored() {
        listOf(VoiceState.Sleeping, VoiceState.Listening, VoiceState.Speaking).forEach {
            assertEquals(it, next(it, VoiceEvent.ActionStarted).state)
            assertEquals(it, next(it, VoiceEvent.ActionFinished).state)
        }
    }
    @Test fun sleepWhileExecutingStopsEverything() {
        val t = next(VoiceState.Executing, VoiceEvent.Heard("okay bye jarvis"))
        assertEquals(VoiceState.Sleeping, t.state); assertTrue(Effect.StopSpeech in t.effects)
    }
}
