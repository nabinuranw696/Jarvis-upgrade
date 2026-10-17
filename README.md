# Jarvis AI — Android app (guest mode, no login)

Open in **Android Studio** (or run the GitHub Action *Build APK*) and run. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## No account
There is no login, signup or third-party auth SDK. On first launch the app creates a random 256-bit **device ID**
(stored privately on the phone) and sends it as `Authorization: Device <id>`. The backend must accept this —
see **BACKEND_CHANGES.md**. Until it does, calls to the backend return 401/403 and the app says so.

## Saved on this device
Settings, chosen language, onboarding state, the current chat (last 200 lines + conversation id) and a copy of your
memories are stored locally and restored on restart. Memories, skills and AI provider keys live on the backend, tied to the device ID.

## Backend URL
Privacy & Security (or "Backend server settings" on the welcome screen). Emulator: `http://10.0.2.2:3000`.
Release builds block plain http.

## Voice
Hands-free mode, sleep/wake voice commands and an on-device wake-word detector (foreground service) — see **VOICE.md**.

## Not built yet
Server-side chat history list • avatar picker • lock-screen assistant • Nepali wake word.
