# Jarvis voice: hands-free, sleep and wake

## How it works
| Piece | File | What it does |
|---|---|---|
| State machine | `voice/VoiceMachine.kt` | Pure Kotlin. States `IDLE, WAKING, LISTENING, PROCESSING (shown as Thinking), EXECUTING, SPEAKING, SLEEPING, ERROR`; ignores late/duplicate events; emits effects (start/stop mic, speak, arm/disarm wake, toggle hands-free). Unit-tested in `VoiceMachineTest.kt`. |
| Session | `voice/VoiceSession.kt` | Runs the effects with Android `SpeechRecognizer` + `TextToSpeech`. One recognizer, one utterance id, watchdogs for hung AI/TTS, bounded silence retries. |
| Hub | `voice/VoiceHub.kt` | Process-wide owner of the session so a conversation survives the Activity. Persists settings (e.g. sleep → hands-free off). |
| Wake word | `voice/WakeWord.kt` | **Vosk** on-device recognizer limited to the wake phrases (+`[unk]`), fed from its own `AudioRecord`. No network. |
| Service | `voice/WakeWordService.kt` | Foreground service, type `microphone`, with a "Turn off" notification action. |
| Background answers | `voice/HeadlessChat.kt` | Answers a voice command when no screen is alive, using the same backend/provider and saved chat. |

Flow: `Sleeping` (detector armed) → *"Okay Jarvis"* → detector releases the mic → beep → `Waking` (350 ms hand-over) → `Listening` (hands-free ON) → `Processing` (AI) → `Speaking` (mic closed, so Jarvis can't hear itself) → `Listening` … → *"Okay Bye Jarvis"* → recognizer + TTS stopped, mic released, hands-free OFF, `Sleeping`, detector re-armed. Chat history is never touched.

## Phrases
Built in (use the assistant name): sleep — "Okay Bye Jarvis", "Ok Bye Jarvis", "Jarvis go to sleep", "go to sleep Jarvis", "Jarvis sleep mode", "Jarvis stop listening", "Jarvis sleep"; wake — "Okay Jarvis", "Ok Jarvis", "Hey Jarvis", "Jarvis wake up", "Wake up Jarvis". Your own phrases from *Settings → Assistant* are added (separate several with `|`). Nepali sleep phrases are best-effort guesses; Google's Nepali recognizer often writes English words in Devanagari, so add what you actually see on screen.

## What you must know (honest limits)
* **Wake word needs a one-time ~40 MB model** (`vosk-model-small-en-us-0.15`) downloaded in-app over HTTPS from alphacephei.com. It is not bundled and I could not verify a checksum offline.
* **Wake phrases must be English letters.** There is no small Nepali Vosk model; after waking, Nepali commands go through Android's recognizer as usual. A custom phrase is only detected if its words exist in the model's vocabulary — use the Voice Settings status line to confirm it's "Listening for the wake phrase" and test by saying it.
* **Android 14+** lets a microphone foreground service start only while the app is visible. So after a reboot, or if the OS kills the app, open Jarvis once to re-arm. Some OEMs (Xiaomi, Huawei, Oppo…) kill background services regardless; exclude Jarvis from battery optimisation.
* **Only one app can own the mic.** If a call/other recorder holds it, the detector reports it in Voice Settings and the notification.
* Anything said right after the wake phrase in the same breath is usually lost (the detector must release the mic first); say the phrase, wait for the beep.
* Headless (app closed) turns answer questions only; phone actions the AI proposes are reported as cancelled and never run unattended.
* `targetSdk 35`; `minSdk 26`. Dependencies added: `com.alphacephei:vosk-android:0.3.47`, `net.java.dev.jna:jna:5.13.0@aar`.
* The wake-word detector adds ~2× CPU while armed vs. idle; *Battery saving* gates decoding on sound and auto-disables after 30 min.

## Test checklist (run on a device)
1. Settings → Voice → download model → switch wake word on (grant mic + notifications). Notification appears.
2. Home screen, say "Hey Jarvis" → beep → ask something → hear the answer → it listens again.
3. Say "Jarvis, go to sleep" → beep; notification says Sleeping; say "what time is it" → nothing happens.
4. Say "Hey Jarvis" → wakes (hands-free back ON in settings).
5. Repeat 2–4 in Nepali (set Speech language to नेपाली) and with airplane mode (expect the network error message, then Error → tap mic).
6. Deny mic permission → clear error, no crash. Tap "Turn off" in the notification → wake word switches off, notification disappears.
7. `./gradlew testDebugUnitTest` for the state-machine tests.
