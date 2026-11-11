# JARVIS assistant expansion audit

Baseline: `188f2bfd77936d3edca2e96b79b819cef568895c` (`main`), inspected 2026-11-11. Package `com.raaz.assistant` retained; versionCode 8 / 6.0-assistant. Prior reports were read as historical evidence, not proof for new code.

The baseline had Kotlin/Compose navigation, Gemini WebSocket/audio with bounded playback and session guards, encrypted provider profiles, SSE text chat, camera thumbnails, local history, foreground recognition/TTS fallback and isolated tests. Memory and Tasks screens were placeholders; there was no device tool executor, durable approval store, MCP client, background microphone service or wake engine.

Implementation decisions:

- Keep the existing native Gemini and fallback engines. Add documented Live function declarations, functionResponses and tool-call cancellation; native calls only create persistent proposals, never authorize actions.
- Add a separate SQLiteOpenHelper database, `jarvis_features.db`, schema version 1. This is an additive store; no original settings, history, database or Keystore format is replaced. A future version must explicitly migrate rather than reset data.
- Use Android intents for installed apps, YouTube/web links, alarms/timers, dialer and messaging composers. Direct SIM calling is separately opted in and reviewed. Do not request SEND_SMS, READ_SMS, QUERY_ALL_PACKAGES, boot permission or install privileges.
- Personal WhatsApp has no verified public arbitrary-call/send API available to this integration. Use the officially documented `wa.me` composition flow; never claim sending/calling.
- Use actual Streamable HTTP MCP JSON-RPC with optional bearer credentials, initialization, tools/list, tools/call, JSON/SSE parsing, bounded responses and DELETE cleanup. Explicit per-tool permissions and device approval remain mandatory. Unsupported OAuth/server-client requests fail honestly.
- Use maintained Maven Porcupine 4.0.2 with the built-in JARVIS keyword. User AccessKey, license/activation and Android device verification are required. Low-level AudioRecord avoids the SDK manager's shared recorder. The manager source was inspected; stop/delete alone is not used as a shortcut around microphone ownership.
- Start a microphone foreground service only from visible user consent with notifications/microphone permissions. No sticky restoration, boot restart or shared embedded provider key. Wake capture releases its lease before Gemini starts. A detection starts the real configured Google session; no ambient audio is sent to Gemini while waiting for the keyword.
- AOSP VoiceInteractionServiceInfo requires both sessionService and recognitionService metadata. Implement an actual opt-in, visible-only external-recognizer delegate and exclude JARVIS itself from engine selection to avoid recursion. The assistant role opens the UI; it grants no lock-screen bypass or hardware DSP hotword capability.
- Accessibility is limited to one approved scroll/Back in YouTube/Chrome, with package/lock guards and bounded node/step scans. No arbitrary tapping, permission approval, message sending or text upload is supported.

Security/correctness findings addressed during expansion:

1. Native tool callbacks need the same session identity gates as audio; tool responses acknowledge pending approval instead of pretending execution.
2. Mute/Stop/background behavior must keep foreground and opt-in background owners separate; Home and diagnostics display actual background state.
3. Persisted approvals need an atomic status transition, not an in-memory checkbox, to prevent duplicate execution. Process-interrupted execution is marked unknown/failed without replay.
4. An MCP profile can change after a proposal. Freeze and recheck its endpoint; write configuration and replacement credential in a single preferences edit.
5. Saved contact aliases need a stable lookup key and phone verification, not just a possibly ambiguous display name.
6. Old wake detections/errors cannot restart or pause a newer session. Input ownership must remain quarantined after failed native cleanup.
7. Live notification updates must follow status changes, not every 10 Hz PCM meter sample.
8. Native/socket and feature-store resources need explicit shutdown; do not leave thread pools/database handles to GC.
9. Static Android lint identified an inherited API24 AtomicLong call on a min23 playback path, an unguarded API26 assistant launch and explicit permission/hardware declaration gaps. Replace the atomic update with an API23-safe CAS loop, guard assistant launching, handle permission exceptions, mark telephony optional and explicitly exclude backup/device transfer.
10. New privacy surfaces require explicit memory-cloud consent, credential-pattern guards and removal of finished task records.

Official sources successfully retrieved during this task:
- https://ai.google.dev/api/live
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/guide/components/intents-common
- https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- AOSP `core/java/android/service/voice/VoiceInteractionServiceInfo.java`
- https://faq.whatsapp.com/5913398998672934
- https://modelcontextprotocol.io/specification/2025-11-25/basic/transports
- https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle
- Picovoice/porcupine Android binding source, LICENSE and Maven metadata (4.0.2).

Google, Android, MCP and Picovoice documentation are versioned external dependencies; these source checks do not prove account access, OEM support, app behavior or licensing eligibility. Current outcomes are in ASSISTANT-VERIFICATION.md.
