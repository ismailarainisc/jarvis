# JARVIS Android — Gemini Live reliability

Personal Android assistant, package **com.raaz.assistant**. Native voice uses **Gemini Live**, directly with your Google AI Studio credential. OpenAI is not required for this path.

- [Download Android APK](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live.apk)
- [Download complete Android Studio source ZIP](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live-Source.zip)
- [Current engineering audit, evidence and limitations](RELIABILITY-REPORT.md)
- [Phone setup and verification checklist](RELIABILITY-GUIDE.md)
- [Build/test logs and XML results](verification/reliability/)

## Native Gemini voice

Open **Home → Gemini Live**, enter your own Google AI Studio credential, an accessible Live model and voice, then save. Saving is not a connection test. START requests microphone permission; recording starts only after the actual Google `setupComplete` message. Mute disables transmission while local capture may remain active. STOP, leaving the screen, backgrounding or losing audio focus ends the session. There is no automatic billable reconnection.

The default model is `gemini-3.8-live` and voice is `Kore`; use the current supported names available to your Google project. Google free-tier eligibility, limits, region, model availability and billing are controlled by Google. Free unlimited access is not promised.

This change adds a single typed native-session state owner, session and playback generation barriers, bounded input/output, validated PCM, asynchronous native cleanup, explicit network errors and non-destructive credential/settings handling. Native audio, focus, routing and live Google compatibility still require real-device/provider verification; isolated automated tests do not prove physical audio works.

## Preserved features

- Multiple HTTPS OpenAI-compatible **text** providers, including Gemini and NVIDIA presets; manual model IDs, authenticated inference test, real SSE, encrypted keys.
- Local history, camera thumbnail attachment for compatible models, themes and navigation.
- Foreground SpeechRecognizer → text API → Android TTS fallback. This is turn-based and does **not** support simultaneous listening/speaking or barge-in.
- Legacy OpenAI connection/audio modules and authenticated backend source are retained; the active native UI uses Gemini.

Text Gemini endpoint: `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`. NVIDIA: `https://integrate.api.nvidia.com/v1/chat/completions`. Custom HTTPS providers receive your key/messages/images; use only trusted services. Text models are not assumed to support native audio.

## Build and install

Install JDK 17, Android SDK platform 35 and build tools, then open this directory in Android Studio or run:

```sh
./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
# With a device/emulator attached:
./gradlew connectedDebugAndroidTest
# Retained legacy backend fixtures (Node 22.6+):
cd backend
npm test
```

The downloadable APK is a **development** build. It is signed with the same workspace development certificate as the previous downloadable APK. The private signing key is not included. A locally rebuilt debug APK normally has a different certificate. Use the same signing key for an in-place upgrade; do not uninstall if you want to preserve local data. Device upgrade/Keystore migration remains untested here.

Credentials retain the original `settings` preferences, `jarvis_key` Android Keystore alias and AES-GCM encoding. Keys are never embedded in source, logged, or placed in Gemini socket URLs. Local encryption does not protect against every compromised-device attack. Distributed apps sharing a developer account should use an authenticated ephemeral-token service; this personal-key change does not implement a new Gemini token backend.

Wake word, lock-screen operation, autonomous mobile tools/MCP, persistent personal memory, full camera streaming and automatic provider failover are **not implemented**. Text usage metrics, Markdown/code-copy UI and realtime text/audio capability discovery are not added in this reliability change.

Earlier phase audit/verification documents are historical snapshots. The current status is exclusively [RELIABILITY-REPORT.md](RELIABILITY-REPORT.md); historical passing test counts are not evidence for this version.
