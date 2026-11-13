# JARVIS 7.3 — real owned timers

[Timer implementation, Android restrictions and device checklist](TIMERS-GUIDE.md). Voice timers use the existing Gemini session and require Assistant tools enabled. Timer alerts are approximate unless precise access was explicitly granted. Actual device/Gemini verification remains NOT TESTED.

## Previous phase — JARVIS 7.2 — Holographic Home

[Current Home design, implementation and verification](HOME-DESIGN-REPORT.md). The approved reference and actual reusable face artwork are included. Device/pixel-fidelity and live Google verification remain NOT TESTED.

## Previous phase — JARVIS 7.1 — action and voice reliability

Existing Android app, package **com.raaz.assistant**. Native voice uses **Gemini Live**; the optional wake engine uses **Porcupine** with a separately supplied user AccessKey. No Google/Picovoice/OpenAI credential is embedded.

- [Download Android development APK](downloads/Jarvis-Gemini-Live.apk?raw=true)
- [Download complete Android Studio source ZIP](downloads/Jarvis-Gemini-Live-Source.zip?raw=true)
- [Current 7.1 fixes, regression evidence and limits](RELIABILITY-71-REPORT.md)
- [7.1 setup and exact ADB/device checklist](RELIABILITY-71-GUIDE.md)
- [7.1 actual source/PR audit](RELIABILITY-71-AUDIT.md)
- [7.0 action engine implementation/evidence](ACTION-ENGINE-REPORT.md)
- [7.0 setup, capabilities and device checklist](ACTION-ENGINE-GUIDE.md)
- [7.0 source audit and official references](ACTION-ENGINE-AUDIT.md)
- [Preserved 6.1 Home/native voice evidence](HOME-VOICE-REPORT.md)
- [Native Home setup and real-device checklist](HOME-VOICE-GUIDE.md)
- [Home voice audit](HOME-VOICE-AUDIT.md)
- [Preserved assistant features and historical verification](ASSISTANT-VERIFICATION.md)
- [Setup, privacy and real-device checklist](ASSISTANT-GUIDE.md)
- [Source audit and protocol references](ASSISTANT-AUDIT.md)
- [Current build logs and tests](verification/reliability-71/)

## What is implemented

7.1 adds a revisitable Permissions & readiness page, current notification-channel checks, explicit selected-recipient voice previews, stale-contact protection, cold fallback TTS readiness, bounded action execution and MCP body cancellation. See the current report for actual verification limits.


First launch opens a resumable 11-step setup wizard. Every optional capability can be skipped and rechecked; no blanket startup grants or recording occur. Revisit from Settings → Revisit setup & permissions.

Home → Tools & background opens the new Control Center. Enable Gemini tool proposals before START (restart an already connected voice session to advertise new declarations). Voice requests become validated durable tasks; unlock, inspect and approve once. Home, Voice settings, Tasks and Control Center share the foreground native session. Pending permission/consent revocation, duplicate/stale requests, bounded approval waits and unknown outcomes are handled explicitly. Private contact/memory query results stay local; approved battery/network snapshots may be spoken.

New typed tools include local installed-app/Contacts search, actual device status, maps/directions and memory read/conditional deletion. LAUNCHED never proves external completion; VERIFIED is limited to checked local results. No autonomous arbitrary app clicking or automatic personal WhatsApp/SMS sending is implemented.


**Home START defaults to native Gemini Live**, reusing the saved Google Live model/voice and the existing microphone/audio, continuous session, measured PCM and interruption implementation. Home and **Voice settings** share one foreground controller/session. **Voice mode → Android fallback** explicitly selects Android speech recognition → text API → TTS; it remains turn-based without full barge-in. Missing native configuration opens setup instead of silently using TTS. Typed replies in native mode remain text.

**Control Center → Apps & actions / feature tabs** includes:
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

Use your own Google AI Studio key and accessible **Live/audio** model/voice in Gemini Live settings. Default model `gemini-3.8-live`, voice `Kore`; Google availability and quotas apply. Saving does not test connectivity. Home ↔ Voice settings preserves the same foreground session. Tasks and Control Center also preserve the foreground session for approvals. Other screens, Activity recreation and app backgrounding stop foreground voice; it never silently reactivates.

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
./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
# With a device/emulator:
./gradlew connectedDebugAndroidTest
# Retained unchanged legacy backend fixtures, Node 22.6+:
cd backend
npm test
```

The downloadable APK is a development build signed with the previous workspace development certificate. Private signing keys are excluded. A local rebuild normally uses another certificate; use a matching key for in-place upgrades. Do not uninstall to work around a signing mismatch if you need local data.

Current 7.1 evidence is in RELIABILITY-71-REPORT.md and verification/reliability-71/. PR #2 remained open at inspection, so 7.1 is stacked on its 7.0 branch for isolated review; main is not overwritten. Earlier Home/assistant/reliability documents/results are historical snapshots. Isolated passing tests and compiled instrumentation do not prove Android hardware, Google/Picovoice access, external MCP servers, live calls, app intents, battery or accessibility behavior. No real calls/messages are sent during development tests. See the actual report before treating this as production-ready.
