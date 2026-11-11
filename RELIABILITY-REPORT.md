> Historical 5.1 snapshot. The current 6.0 source, capability limits and actual evidence are in [ASSISTANT-VERIFICATION.md](ASSISTANT-VERIFICATION.md) and [ASSISTANT-GUIDE.md](ASSISTANT-GUIDE.md). Foreground voice still stops with its screen; the new opt-in background service is separate.

# JARVIS 5.1 reliability engineering evidence

Date: 2026-11-11. Baseline inspected: `91502d09e8493ea568636012439ca72edde06a2e` on `main`. Scope: existing Gemini native Live, foreground fallback coordination, credential/history safety and text-request cancellation. Package identity remains `com.raaz.assistant` (versionCode 7). No new branch, history rewrite or forced push is used, following the owner's final instruction.

## Audit of the actual baseline

Reviewed MainActivity, GeminiLiveController, GeminiAudioEngine, GeminiProtocol, GeminiLiveScreen, AssistantViewModel, VoiceSessionManager, SettingsRepository, Manifest, Gradle, provider/text transport, legacy live modules, backend and previous tests/reports. Historical results were not assumed to apply to this version.

Source-confirmed defects included: native stop/join on Main; in-flight PCM excluded from queue accounting; incomplete driver-write validation; no playback-drain stall watchdog; unbounded incoming event dispatch; missing generation on queued output callbacks; mute/unmute failing to invalidate pre-read microphone data; setup/background races; no shared native/fallback lease during async cleanup; premature termination on `goAway`; null/missing credential failure and destructive corrupt-profile fallback; and a shared blocking HttpURLConnection with weak cancellation and unbounded reads. These findings follow concrete control/data paths; their device impact was not measured on a phone.

## Current protocol evidence

Official material fetched successfully during this work:

- https://ai.google.dev/api/live — v1beta BidiGenerateContent, setupComplete, PCM parts, interrupted, turnComplete, interactionStatus, waitingForInput, goAway/timeLeft and audioStreamEnd.
- https://ai.google.dev/gemini-api/docs/live-guide — native audio and automatic turn detection.
- https://github.com/googleapis/python-genai/blob/main/google/genai/live.py and `_api_client.py` — native header authentication, constrained ephemeral-token transport.

The native URL is fixed to Google's TLS WebSocket host. API keys use `x-goog-api-key`; manually provisioned `auth_tokens/...` credentials use `Authorization: Token …` on the constrained method. No secret is put in the URL. Input is PCM16LE mono16k, output PCM16LE mono24k. AutomaticActivityDetection is enabled; mute sends audioStreamEnd and unmute sends audio again. Interruption flushes playback only on serverContent.interrupted. A goAway notice honors the server's advertised grace period. Unknown future fields are ignored; malformed known fields fail safely. Google labels Live preview; access/model/voice compatibility requires actual account tests.

## Build and test outcome

**PASS:** the required clean build finished successfully in 4m 59s: debug APK and Android test APK generated; **116 JVM tests, zero failures/errors/skips** (73 new regression tests, the existing 43 retained). **9/9** retained backend fixture tests passed. See the actual [Gradle log](verification/reliability/gradle-build.log), [test summary](verification/reliability/test-summary.json), [JUnit/HTML reports](verification/reliability/unit-test-html/index.html) and [backend log](verification/reliability/backend-tests.log).

An intermediate run had one failure in the local cancellation fixture: MockWebServer's intentional 10-second header delay exceeded its 5-second shutdown bound. The fixture now uses a nonresponding socket that is released by real cancellation; its focused five-test rerun and the final full 116-test run passed. Intermediate failure and focused rerun logs are retained. The final build also recovered automatically from an unavailable Kotlin compiler daemon by compiling in a separate process; native symbol stripping warnings are retained in the log.

The exact required command is `./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest`. Logs and JUnit XML are in [verification/reliability/](verification/reliability/). Node backend fixtures were also run although that retained backend was not modified. Fixture credentials and local HTTPS/WebSocket servers exist only in tests.

`assembleDebugAndroidTest` compiles test APKs; it does not execute them. No emulator/Android phone is attached (`adb devices` empty), no usable KVM device is available, and no Gemini/Google API credential is configured. Accordingly physical microphone/playback, real provider access, Android Keystore upgrade, UI instrumentation and performance are **NOT TESTED**. No provider inference or audible speech is claimed.

## Defect and feature evidence table

“PASS — isolated” means the production Kotlin coordinator/algorithm/transport ran with explicit fake platform ports or local network fixtures. It never means Android hardware or Google passed.

| Defect / feature | Previous behavior | Implemented change | Verification / status | Relevant code and test | Remaining risk |
|---|---|---|---|---|---|
| Connection and readiness | Flags distributed across callbacks; duplicate/delayed events possible | Typed GeminiSession owns all native state; no capture until setupComplete | PASS — isolated — isolated coordinator | [GeminiSession.kt](app/src/main/java/com/raaz/assistant/gemini/GeminiSession.kt), GeminiSessionTest.genuineReadinessStartsExactlyOneAudioEngine | Real setup/account not tested |
| Start/Stop and stale sessions | Old event handlers could affect later UI/audio | Session identity, live gate, cancellable actor, bounded teardown; restart blocked during close | PASS — isolated — isolated coordinator | GeminiSessionTest.repeatedStartStopReleasesEverySessionOnce / staleSessionCannotChangeNewConnection | Native driver release needs device |
| Main-thread shutdown | AudioRecord.stop, AudioTrack flush and thread joins called synchronously on Main | Native lifecycle Mutex and IO cleanup, nonblocking requestStop | Source implemented; native NOT TESTED; coordinator async cleanup PASS — isolated | [GeminiAudioEngine.kt](app/src/main/java/com/raaz/assistant/gemini/GeminiAudioEngine.kt), GeminiSessionTest.stopWhileAudioStartsWaitsForCleanupAndBlocksRestart | OEM native calls can still stall an IO worker |
| Capture permission/foreground | Setup callback could activate capture after background/permission change | Activity+screen guard, repeated checks before recording, active monitor and immediate input gate closure | PASS — isolated — isolated permission/setup/focus tests; native NOT TESTED | MainActivity.kt, AndroidGeminiPorts.kt, GeminiSessionTest.backgroundDuringSetupNeverStartsCapture / revokedPermissionIsDetectedWithoutMoreInput | OS privacy toggle detection may rely on native read failure |
| Single audio owner | New Activity/fallback could acquire mic before old cleanup completed | Shared process lease; failed release quarantines recording until process restart | PASS — isolated — isolated | GeminiSessionPorts.kt, GeminiFailureTest.ownershipCannotBeReleasedByPreviousActivity, GeminiSessionTest.failedCleanupQuarantinesAudioOwnership | Recognition/TTS/OEM behavior needs phone |
| PCM retention bounds | Dequeued in-flight array no longer counted | Actual production PcmPlaybackPump counts all retained chunks until fully written | PASS — isolated — isolated actual pump | [PcmPlaybackPump.kt](app/src/main/java/com/raaz/assistant/gemini/PcmPlaybackPump.kt), PcmPlaybackPumpTest.inFlightArrayStillCountsAgainstBufferLimit | Native AudioTrack has its own small buffer |
| Partial PCM writes | Odd or larger-than-requested writes could corrupt offset/frame count | Validates sign, even length and requested boundary; preserves partial offsets | PASS — isolated — isolated output port | PcmPlaybackPumpTest.partialWritesPreservePcmInOrder / oddOrNegativeOrOversizeWritesFail | Driver integration not tested |
| Stalled audio | Zero reads spun; drained write queue could remain “playing” forever | Bounded zero-read sleep/timeout; write and hardware-head progress watchdogs | PASS — isolated — pump watchdog tests; native capture NOT TESTED | GeminiAudioEngine.kt; PcmPlaybackPumpTest.stalledWriteFailsBoundedly / stalledPlaybackHeadIsNotShownAsPlayingForever | Slow/broken hardware may require manual restart |
| Interruption | Queue generation existed but old posted meter/playback events could reappear | Generation-tagged callbacks, atomic invalidation, IO flush; discard audio bundled with interruption, accept next generation | PASS — isolated — isolated queued/active pump + coordinator | GeminiSessionTest.interruptionInvalidatesPlaybackCallbacksAndKeepsSession / oldAudioInSameInterruptionMessageIsNotQueued; PcmPlaybackPumpTest.interruptionDiscardsActiveAndQueuedChunks | Acoustic barge-in and server timing NOT TESTED |
| Mute/unmute race | Frame read before rapid mute/unmute could be sent afterward | Ticket BEFORE blocking read; ordered streamEnd; old tickets invalid across both transitions | PASS — isolated — isolated production gate/coordinator | [MicrophoneGate.kt](app/src/main/java/com/raaz/assistant/gemini/MicrophoneGate.kt), MicrophoneGateTest, GeminiSessionTest.muteUnmuteRejectsFramesReadAcrossBothTransitions | Local capture continues/discards; already delivered packets cannot be recalled |
| Network input backlog | Queued microphone packets could lag behind mute | Bounded socket queue; excessive mute backlog stops session and cancels queued transport | PASS — isolated — isolated | MicrophoneGateTest.microphoneFrameAndNetworkQueueAreBounded; GeminiSessionTest.largeQueuedInputStopsRatherThanPretendingToMute | Queue counters are not receipt proof |
| Incoming messages | Coroutine per message, no bounded actor inbox | 32-event/2 MiB inbox, 1 MiB message cap, off-Main parsing, coalesced overload failure | PASS — isolated — isolated flood test | GeminiSession.kt; GeminiSessionTest.boundedInboxRejectsUnprocessedFlood | OkHttp assembles a complete socket frame before callback size validation |
| PCM/event parsing | MIME prefix and lenient base64, late format failure | Strict PCM MIME/rate, canonical bounded base64, even PCM, total-event limits and type checks | PASS — isolated — parser fixtures | GeminiProtocol.kt; GeminiProtocolHardeningTest | Future incompatible provider formats fail visibly |
| Expiry/error recovery | goAway terminated a still-valid grace period; coarse errors | Actual timeLeft deadline; typed auth/model/voice/quota/TLS/network errors, no automatic billable retry | PASS — isolated — isolated | GeminiSessionTest.goAwayHonorsServerGracePeriod / disconnectedSocketRequiresManualRestart; GeminiFailureTest | Account-specific messages generalized to avoid leaking bodies |
| WebSocket transport | No real transport fixture regression | Production OkHttp adapter exercised against real local WebSocket server | PASS — isolated — local network fixture | [AndroidGeminiPorts.kt](app/src/main/java/com/raaz/assistant/gemini/AndroidGeminiPorts.kt), GeminiWebSocketTest | Does not test Google handshake/model access |
| Audio routes/focus | Delayed focus/device callbacks could affect another session | Session-scoped platform callback guards and cleanup, selected-route error handling | PASS — isolated — coordinator route/focus fixtures; hardware NOT TESTED | GeminiSessionTest.routeSelectionUsesActivePlatformAndStopsOnFailure / focusLossCancelsNetworkAndReleasesResources | Bluetooth/API23–35 routing needs devices |
| Credential persistence | Missing key could null-fail; missing alias could regenerate on read | Same settings/alias/GCM format, explicit safe errors, never generate encryption key on decrypt | PASS — isolated — repository fixtures; Android Keystore NOT TESTED | SettingsStorage.kt, SettingsPersistenceTest; compiled CredentialStorageTest | Device compromise and actual upgrade remain outside local proof |
| Provider data safety | Corrupt profile JSON silently treated as no profiles and could be overwritten | Preserve corrupt data, reject edits, show startup error; per-profile rotation/remove; endpoint change requires fresh key | PASS — isolated — repository fixtures | SettingsRepository.kt, SettingsPersistenceTest.corruptProfilesArePreservedRatherThanOverwritten / failedEncryptionDoesNotChangeProfileOrKey | Repair/restore UI for damaged raw data not added |
| History corruption | Silently displayed empty and could overwrite damaged stored history | Strict bounded history decode; fail/save protection; explicit clear remains user controlled | PASS — isolated — codec fixtures; UI NOT TESTED | ConversationHistory.kt, AssistantRepository.kt, AssistantViewModel.kt, ConversationHistoryTest | History still local plaintext and capped at existing last 100 messages |
| Text cancellation and SSE | Shared HttpURLConnection.disconnect could block Main; reads/events unbounded | Per-call asynchronous OkHttp cancellation, no redirect/automatic retry, body/event/output bounds; incomplete SSE never committed/spoken | PASS — isolated — real local HTTPS/SSE fixtures | ChatCompletionClient.kt, ChatCompletionClientTest | Real Gemini/NVIDIA text and camera NOT TESTED |
| Fallback coordination | Late TTS/focus/network callbacks could speak in later session | Generation-bound reply, TTS init timeout, shared lease, cleanup attempts all steps, focus generation; no forced IDLE before TTS | Source implemented; native NOT TESTED; existing recognition gate tests PASS — isolated | MainActivity.kt, VoiceSessionManager.kt, AssistantViewModel.kt, VoiceTurnGateTest | Full Android recognition/TTS cycle needs phone |
| Honest UI/audio levels | Independent flags could disagree with status; old level callback survived flush | Face/status derive same typed state, generation-safe actual PCM, 10 Hz meters; explicit local-activity labels | PASS — isolated — state/meter fixtures; UI/native NOT TESTED | GeminiLiveState.kt, GeminiLiveScreen.kt, LiveAudioModels.kt, LiveAudioTest | Measurement is captured/submitted PCM, not acoustic speaker amplitude |
| Android artifacts | Existing previous binary was older implementation | Build app and instrumentation APK, same package; downloadable APK signed with previous development certificate | PASS — debug APK built; signature and ZIP alignment verified | build logs, APK signature/alignment log | Development build, not a production release certification |
| Legacy backend | Retained OpenAI signaling backend | No backend rewrite; existing isolated regression suite rerun | PASS — 9 Node fixture tests | backend/server.test.mjs, verification/reliability/backend-tests.log | Not Gemini backend or live OpenAI validation |

All listed test classes are under `app/src/test/java/com/raaz/assistant/`, except CredentialStorageTest under androidTest. Unit fixture ports are injected into the **same production implementation**; no separate simplified coordinator is used as a substitute.

## Acceptance levels

| Acceptance | Status and evidence |
|---|---|
| Source implementation | Implemented for the scoped source-confirmed defects above; native integration remains unverified |
| Android debug compilation/APK | PASS — debug APK built; signature and ZIP alignment verified |
| Production-logic JVM regressions | PASS — isolated, actual XML and logs linked above |
| Instrumentation APK compilation | PASS — test APK compiled, execution NOT TESTED |
| Instrumentation execution/navigation | NOT TESTED — no device/emulator |
| Real AudioRecord reaches Gemini | NOT TESTED — no Google credential or Android device |
| Actual AI audio heard/multiple turns | NOT TESTED — no Google credential or Android device |
| Server interruption while physically speaking | NOT TESTED — no Google credential or Android device |
| Actual microphone release/mute/routes/focus | NOT TESTED — native audio hardware unavailable |
| Process restart, upgrade and Keystore migration on Android | NOT TESTED — device unavailable; repository reconstruction tested in isolation only |
| Real text providers, fallback speech and camera | NOT TESTED — external accounts/device unavailable; source preserved and local text fixtures tested |

## Remaining limitations and release gates

This is not declared fully production-ready. Android audio drivers, Bluetooth routes, acoustics/echo, permissions, process recreation, accessibility and long-session CPU/battery/memory must pass the [real-device checklist](RELIABILITY-GUIDE.md). No native audio device test was performed. Slow/blocking OEM native calls can still delay IO cleanup; the UI does not block on them and recording reuse is denied after an observed cleanup failure. Permissions/privacy-switch behavior varies by device.

There is deliberately no automatic billable reconnect, generic realtime protocol, shared embedded production key, new Gemini token backend, wake word, background/lock-screen recorder, memory or autonomous automation. Local key encryption cannot defeat a fully compromised device. Text history remains unencrypted, with the existing user-controlled clear action. Corrupt data is retained for recovery instead of overwritten; no new raw-data recovery editor is provided.

Existing theme/providers/history/camera/SSE/recognition/TTS code and legacy OpenAI modules remain. Preserving source and compiling it is not equivalent to proving every feature works on hardware. Legacy WebRTC/OpenAI runtime compatibility is not claimed. Historical documents are marked superseded for this version.

For reproducible builds/configuration use README and RELIABILITY-GUIDE. The signing private key is intentionally excluded. Source ZIP excludes generated builds, private files, `.git`, and binary download ZIP/APK files to avoid recursive archives; all Android/backend source, Gradle wrapper, tests and evidence remain included.

## Artifact identity

Downloadable app: versionCode **7**, versionName **5.1-reliability**, package **com.raaz.assistant**. APK v1/v2/v3 signatures verify. Development certificate SHA-256: `91cf099174951ce2d669a696a9277f85f93001198ba685ee6b2a19ef3fcd4aa5`, matching the previous tracked downloadable APK. APK signing metadata warnings about META-INF entries do not replace v2/v3 full-file integrity; the raw verifier output is included. ZIP 16 KiB native-library alignment was checked; this is not a certification of every OEM or Play Store requirement. File SHA-256 values are in `downloads/SHA256SUMS`.
