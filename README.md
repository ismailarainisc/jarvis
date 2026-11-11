# JARVIS 6.0 — Android personal assistant

Existing Android app, package **com.raaz.assistant**. Native voice uses **Gemini Live**; the optional wake engine uses **Porcupine** with a separately supplied user AccessKey. No Google/Picovoice/OpenAI credential is embedded.

- [Download Android development APK](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live.apk)
- [Download complete Android Studio source ZIP](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live-Source.zip)
- [Current implementation/evidence and limitations](ASSISTANT-VERIFICATION.md)
- [Setup, privacy and real-device checklist](ASSISTANT-GUIDE.md)
- [Source audit and protocol references](ASSISTANT-AUDIT.md)
- [Build logs and tests](verification/assistant/)

## What is implemented

**Home → Gemini Live** preserves native Google microphone/audio, continuous session logic, measured PCM and interruption handling. **Fallback voice** retains Android speech recognition → text API → TTS; it is turn-based, without full barge-in.

**Home → Tools & background** opens functional tabs:
- Tools: installed-app launch, YouTube/web search, supported HTTPS links, alarms/timers and narrow approved accessibility scroll/Back.
- Contacts: optional local lookup, aliases, SIM dialer, separately enabled direct-call permission, SMS/WhatsApp composers.
- Memory: local notes/preferences/aliases, correction/deletion and separate minimal cloud-context consent.
- MCP: real Streamable HTTP initialization, tool discovery/invocation, encrypted optional bearer credential, per-tool permission and explicit approval.
- Tasks: durable proposals, exact arguments/destination preview, unlocked approval, cancellation and finished-record removal. Interrupted actions are never automatically replayed.
- Background: disclosed microphone foreground service, local JARVIS wake detection, safe handoff to Gemini, notification pause/resume/STOP, output controls and Android assistant selection.
- Diagnostics: actual phases, resource lease, queue counters, build/capability status and sanitized errors.

Enable **Allow Gemini to propose tools** to use documented Live function calls. A model can prepare a request, not authorize it. Explicit shortcuts such as `Search YouTube for Kotlin tutorials`, `Open app YouTube`, `Web search Android`, `Call Baba` and `کال کرو بابا` also prepare actions without requiring a model request.

“LAUNCHED” means an Android intent/navigation request was dispatched. It does not prove a page loaded, a video played, a call connected or a message was sent. WhatsApp/SMS require manual Send. WhatsApp personal-account calling/automatic sending, unrestricted clicking, security bypass, automatic permission approval, self-training and silent boot recording are not implemented.

## Configure voice

Use your own Google AI Studio key and accessible **Live/audio** model/voice in Gemini Live settings. Default model `gemini-3.8-live`, voice `Kore`; Google availability and quotas apply. Saving does not test connectivity. Foreground voice stops on leaving its screen/backgrounding.

Background mode is separately disclosed and explicitly started while visible. It requires microphone/notification permissions, a configured Gemini key, your Picovoice AccessKey and applicable SDK license/activation. The built-in keyword is **Jarvis**, including in “Hi Jarvis”; no custom exact-phrase model or hardware DSP hotword access is claimed. Google receives no ambient audio while waiting for wake. After detection, a real configured Google session is requested. No automatic billable reconnection or sticky restart is used. Unlock/review is required for device actions. OEM restrictions apply; 24/7 reliability, free licensing and battery use are not guaranteed.

Default-assistant role selection opens a visible UI and does not bypass locking. Accessibility is optional, manually enabled and restricted to approved scroll/Back in Chrome/YouTube. Read the full guide and current distribution policies before enabling/distributing these services.

## Preserve and protect data

Original `settings`, chat history, provider profiles and Android Keystore alias `jarvis_key` are retained. New feature data uses a separate additive SQLite database. Keys are AES-GCM encrypted through Android Keystore. Local history, memory and task arguments/results are not separately encrypted; app backups remain disabled. Device compromise can expose client data.

Memory is initially off; cloud-context sharing is a separate consent. Contact aliases are excluded from implicit cloud context. Clear/correct/delete controls are provided. A distributed shared-developer-account product should add a trusted authenticated ephemeral-token backend; the retained legacy OpenAI backend is not used to mint Google credentials.

Text presets remain OpenAI-compatible, including Gemini:
`https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`
and NVIDIA:
`https://integrate.api.nvidia.com/v1/chat/completions`.
Only trust endpoints receiving your key/messages/images. Model IDs are entered manually; text support is not a realtime capability claim.

## Build and verification

JDK17, Android SDK35, Gradle wrapper included:

```sh
./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest
./gradlew lintDebug
# With a device/emulator:
./gradlew connectedDebugAndroidTest
# Retained unchanged legacy backend fixtures, Node 22.6+:
cd backend
npm test
```

The downloadable APK is a development build signed with the previous workspace development certificate. Private signing keys are excluded. A local rebuild normally uses another certificate; use a matching key for in-place upgrades. Do not uninstall to work around a signing mismatch if you need local data.

Current evidence is exclusively in ASSISTANT-VERIFICATION.md and verification/assistant/. Earlier phase documents/results are historical snapshots. Isolated passing tests and compiled instrumentation do not prove Android hardware, Google/Picovoice access, external MCP servers, live calls, app intents, battery or accessibility behavior. No real calls/messages are sent during development tests. See the actual report before treating this as production-ready.
