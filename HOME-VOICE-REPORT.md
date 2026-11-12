# JARVIS 6.1 — unified native Gemini Home evidence

Date: 2026-11-12. Baseline: `17160996dc4732d80fb0c2897eaa3885565b3d69`. Feature branch: `feat/unified-gemini-home`; review before merging into main. Package `com.raaz.assistant`, versionCode 9, versionName `6.1-native-home`.

## Implemented outcome

Home is the primary native Gemini Live experience. START uses the existing saved Google Live configuration and the existing controller/session/audio engine. Android SpeechRecognizer/TTS is an explicit secondary mode, never an automatic fallback after a native configuration or API error. No new Gemini audio engine, backend or native protocol was introduced.

Actual command path:

`PremiumHomeScreen.PrimaryConversationButton` → `JarvisApp.onListen` → `MainActivity.requestVoiceStart` → `HomeVoiceCoordinator.start` (default GEMINI_LIVE) → `GeminiHomeAdapter.start` → the single Activity-owned `GeminiLiveController` → existing `GeminiSession` / `GeminiAudioEngine`.

Advanced voice settings receives that same controller. Root navigation sets the voice surface visible for Home **or** Voice settings, so navigating between them no longer disposes a live session. Leaving the workspace, Activity backgrounding or explicit Stop cancels foreground commands and closes native/fallback resources. Activity recreation safely stops audio; it does not automatically reactivate recording. Explicit background wake remains separate and mutually exclusive.

Native UI state comes directly from GeminiLiveUi, mapped without timers or simulated events. Coordinator state contains only mode/command/permission information. RECONNECTING is shown only for actual Android fallback retry; this change adds no automatic native reconnect or billable retry. Native waveforms use the existing PCM measurements, while fallback recognition RMS is separately labeled and TTS amplitude is explicitly unavailable.

## Verification levels

See `verification/home-voice/` for exact logs/XML/HTML and summary. The requested clean command passed; after the final empty-history error/wrapping and lazy-list UI test patch, incremental assemble/unit-test/test-APK/lint tasks passed against the final source. Signed APK verification also confirmed all 186 non-signature entries match the Gradle APK byte-for-byte, 16KB ZIP alignment passes and the prior development certificate is retained. Unit tests use isolated fake ports/stores and existing production protocol fixtures. They do **not** prove Google connectivity or hardware audio.

| Evidence level | Result | Meaning |
|---|---|---|
| Source implementation | Implemented | Actual Home command route and shared native objects inspected; old fallback Home removed. |
| Clean debug build | PASS | Exact requested command: BUILD SUCCESSFUL in 12m 13s. Final UI patch recompiled and linted separately; both logs retained. |
| JVM unit tests | PASS — 205/205, 22 suites | Production coordinator/state/configuration logic plus retained regression suites; 28 more tests than baseline. |
| Final UI build/tests/lint | PASS | Final changed source recompiled; all 205 JVM tests passed again, UI test APK compiled and lint passed. |
| Android UI test APK | Compilation only | Includes Home START/STOP/mute and missing-key setup rendering tests; not device-executed. |
| Lint | PASS — 0 errors / 3 warnings | Existing dependency-update warnings for activity/lifecycle versions; not suppressed. |
| Real Android device/emulator | NOT TESTED | adb reports no attached device; emulator binary unavailable. |
| Genuine Gemini service | NOT TESTED | No legitimate provider credential/account supplied. |

## Defects, changes and evidence

PASS in this table applies to the stated source/unit/build check only, not end-to-end audio.

| Feature/defect | Previous behavior | Implemented change | Verification / file and test | Result | Remaining risk |
|---|---|---|---|---|---|
| Home default | SpeechRecognizer and device TTS | Native default using existing controller | MainActivity.kt, HomeVoiceCoordinator.kt; homeStartDefaultsToNativeWithoutStartingFallback | PASS (source/unit) | Native service/device not tested |
| Missing native key | Separate native page required, fallback prominent | Setup action, no implicit TTS | noKeyOpensSetupInsteadOfRoboticFallback; HomeNativeUiTest compiled | PASS (unit) | Permission UI not executed |
| Shared ownership | Native screen owned disposal | One foreground coordinator; Home/settings voice workspace | GeminiHomeAdapter.kt, JarvisApp.kt; homeAndSettingsShareOneNativeSession | PASS (source/unit) | Navigation on real Activity not tested |
| Explicit fallback | Home default, text speech independent | Persisted secondary selection and device-speech guard | explicitFallbackSelectionPersistsAndUsesOnlyFallback; nativeSelectionStopsFallbackAndSuppressesLateTts | PASS (unit) | Android speech service/TTS availability |
| Duplicate commands | Independent screen requests | Busy/native/fallback guards and command epochs | repeatedStartStopDoesNotDuplicateSessions; repeatedFallbackStartDoesNotRestartTheActiveRecognizer | PASS (unit) | Hardware cleanup timing |
| Permission callbacks | Screen-specific ownership | Ticket validation across Stop, mode/nav/lifecycle | microphoneDenialNeverStartsEitherEngine; stopInvalidatesOutstandingPermissionResult; leavingVoiceWorkspaceRejectsDelayedPermission | PASS (unit) | Actual Android permission flow |
| Foreground/background conflict | Multiple entry points | Running-service and pending-authorization guard; retained hardware lease | MainActivity.kt; backgroundServicePreventsAnotherForegroundOwner | PASS (source/unit) | FGS/OEM/device behavior |
| Delayed fallback reply | Device speech lacked mode ownership | Cancel pending text on active Stop/background and generation/mode checks | fallbackBackgroundingCancelsPendingChatAndTts | PASS (unit) | Actual HTTP/audio lifecycle |
| Voice persistence | Free text on separate screen | Same stored value, documented examples/custom entry, no silent substitution | SettingsPersistenceTest: native voice/key restart and custom-name preservation | PASS (unit) | Android Keystore/device storage not retested |
| Native activity rendering | Separate native/fallback UI | Home uses actual native phase/PCM; independent fallback status | HomeVoicePresentation.kt; nativePresentationCannotBeOverwrittenByFallbackStates | PASS (unit/source) | Audibility is not inferred from PCM |
| Interruption/obsolete audio | Existing native engine already handles server interruption | Reused unchanged in Home; no duplicate playback engine | Retained GeminiSessionTest/PcmPlaybackPumpTest; Home native route inspection | PASS (regression fixtures/source) | Real spoken interruption NOT TESTED |
| Reactive service status | First implementation read StateFlow.value in composition | Lifecycle-aware collection in MainActivity | lintDebug | PASS after correction | Device recomposition not executed |
| Home layout | Basic panel and scrolling destination row | Premium orb, sticky controls, 2×2 destinations, actual transcripts, text/photo actions | PremiumHomeScreen.kt; HomeNativeUiTest compilation | PASS (compile/source) | Small-screen/RTL/TalkBack device review pending |
| Empty-history errors | New Home initially gated text/camera errors behind existing messages | Independent actionable error card, including when history is empty | PremiumHomeScreen.kt; HomeNativeUiTest compiled regression | PASS (source/compile) | UI assertion not device-executed |
| Audio routes | Native page exposed communication routes | Preserved in Advanced settings against same controller | GeminiLiveScreen.kt → controller.selectRoute | PASS (source/compile) | Android route switching NOT TESTED |
| Preserved features | 6.0 tools/memory/MCP/SSE/history/camera/background | Existing modules retained; camera composer/removal and nav callbacks wired | Retained regression suite; AssistantHubNavigationTest compiled | PASS (regression/source) | External MCP, apps, camera and services NOT TESTED |

## Changed-file report

- `MainActivity.kt`: one foreground controller/coordinator, lazy explicit fallback, permission tickets, lifecycle/voice guards, common Home/settings commands, background reservation safety.
- `voice/HomeVoiceCoordinator.kt` (new): mode persistence, typed ports and cancellation-safe command/permission owner. No audio capture implementation.
- `voice/GeminiHomeAdapter.kt` (new): direct existing-controller delegation.
- `voice/HomeVoicePresentation.kt` (new): factual native/fallback phase and visual mapping.
- `gemini/GeminiLiveController.kt`: observable nonsecret configuration metadata, safe initial read, refresh after save/import/delete.
- `gemini/GeminiLiveScreen.kt`: advanced shared-session settings, documented voice examples and preserved manual names, shared controls, real route/diagnostic controls.
- `ui/PremiumHomeScreen.kt` (new): functional Material 3 Home, actual native transcripts/measurements, primary/mute/mode controls, text/camera and navigation.
- `ui/JarvisApp.kt`: workspace lifecycle/navigation, new Home, header history/settings, wrapping fallback preference labels; retained face enhanced with themed orbital detail.
- `app/build.gradle.kts`: version 9 / 6.1-native-home only; no new dependencies.
- `HomeVoiceCoordinatorTest.kt` (new): shared ownership, default routing, denial/stale callbacks, transitions/cleanup/mode and persistence safeguards.
- `SettingsPersistenceTest.kt`: native voice/key restart and custom voice preservation regressions.
- `HomeNativeUiTest.kt` (new): isolated rendered Home command/setup/empty-history error tests; compiled, NOT device-run.
- `AssistantHubNavigationTest.kt`: scroll the lazy Home content to its Tools node before clicking; offscreen item lookup no longer assumes it is already composed.
- README, downloads, HOME-VOICE documents and verification/home-voice: matching artifacts and evidence. Previous phase reports remain historical.

## Real Home acceptance test

All rows below require a real Android phone with legitimate Gemini access. None is claimed PASS from unit fixtures.

| # | Criterion | Result | Available evidence / remaining requirement |
|---|---|---|---|
| 1 | Open JARVIS Home | NOT TESTED on device | Home compiles; layout tests compiled only |
| 2 | Press START | NOT TESTED on device | Default native command routing unit-tested and source-inspected |
| 3 | Genuine Gemini Live connection | NOT TESTED | No supplied credential; actual existing session transport reused |
| 4 | Say Hello JARVIS | NOT TESTED | No microphone hardware |
| 5 | Hear configured natural voice | NOT TESTED | Voice persistence tested; native output code retained |
| 6 | Next question without START | NOT TESTED | Continuous existing session reused; live turns need hardware/service |
| 7 | Speak during output | NOT TESTED | No physical duplex/echo test |
| 8 | Real interruption | NOT TESTED | Existing server-interruption regression fixtures pass, not spoken proof |
| 9 | Mute/unmute | NOT TESTED on device | Shared command/state unit tests; actual transmission requires service |
| 10 | Press STOP | NOT TESTED on device | Command cancellation/cleanup unit tests |
| 11 | Microphone/audio/network released | NOT TESTED on device | Existing cleanup fixtures and new owner guards, hardware verification pending |

## Limitations

No production-readiness, guaranteed free quota, universally supported voice/model, native reconnection, background 24/7 or lock-screen bypass claim. Existing supplied model defaults are retained; choose a model accessible to your Google account. The example voice catalog is documentation-based, not an authenticated provider enumeration. Native language capability depends on the model. Foreground voice intentionally ends on Activity recreation/backgrounding; explicit wake service remains its own opt-in path. Text-chat history remains separate from current live transcripts; this phase does not add persistent native voice conversation history. Typed native-mode replies remain text; device TTS is permitted only in explicit fallback mode and still depends on Android engine availability/initialization. No destructive settings/database migration, new microphone permission or secret is introduced.
