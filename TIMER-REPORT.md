# Direct mobile timers — actual evidence

Baseline: PR #4 / b7360c505d0f93a65f2d89b6c50fb5f49c5605b1. Main at inspection was e4a33167c2c899dbdb01151c7e8871fc0bcf0813; 7.2 is still in an open PR. This separate update is stacked on feat/holographic-home, not a main overwrite.

## Audit and fixes

Old set_timer only opened Clock with EXTRA_SKIP_UI=false, waited for task approval, had no owned deadline, no countdown/control/completion receiver and no independent creation proof. It could only return LAUNCHED. Native tools already had a shared session and encrypted settings, so those were reused, not rebuilt.

- TimerMachine.kt / AndroidTimers.kt own persistent monotonic deadlines, real AlarmManager scheduling, generation-safe cancel/stop, lifecycle restoration/rearming, reboot interruption and notification completion.
- AndroidTools.kt routes set_timer to the actual local timer. Optional set_clock_timer and set_alarm request documented skip-UI Clock integration, always LAUNCHED rather than external VERIFIED.
- ToolModels.kt / TimerCommands.kt define validated creation/status/cancel/stop with bounded numeric seconds; examples convert English/Urdu/Hindi durations. GeminiToolBridge.kt directly executes only enabled low-risk timer tools, caches IDs, records outcomes and returns real timer results to the existing voice session. Sensitive tool approvals remain.
- TimerPanel.kt adds Home countdown, controls and explicit notification/optional precise-access actions. AndroidManifest.xml declares exact-alarm special access, boot cleanup receiver and existing SET_ALARM permission. No timer microphone/foreground service is introduced.

## Actual results

Clean Gradle command: **PASS**, 15m 24s. JVM: **316 tests, 0 failures, 0 errors, 0 skipped**, 26 suites. Lint: {'Error': 0, 'Fatal': 0, 'Warning': 3}. App and instrumentation APK built; signature, previous certificate, ZIP alignment and non-signature equality against Gradle outputs checked. Initial nullable/test compilation issues were corrected; failed logs are retained separately. Final compiled source hashes were checked unchanged. No backend was changed.

| Capability | Evidence | Result | Limit |
|---|---|---|---|
| Timer creation/countdown/validation | Production TimerMachine, isolated storage/clock/scheduler ports in OwnedTimerTest | PASS — unit | Not Android scheduling proof |
| Duplicate commands/stale stop/callback | Same production machine and persisted Android journal/cache implementation | PASS — unit/source | Live provider replays not tested |
| Process restoration/boot interruption | Production restoreSchedule/recover tests | PASS — unit | Real OS process/reboot behavior NOT TESTED |
| Finish notification exactly once / failure state | Production notification-port calls, failure injection tests | PASS — unit | Android posting/audibility NOT TESTED |
| Clock extras/permission/framework | TimerIntegrationTest compiled | PASS — compilation only | Instrumentation execution NOT TESTED |
| Gemini direct function result / ongoing audio | Existing bridge/session reused, source and compilation | PASS — source/build | Genuine Gemini speech, Urdu/Hindi recognition and audio continuity NOT TESTED |
| Sensitive communication/MCP approval preservation | Existing paths unchanged, original regression suite run | PASS — unit/source | Actual external effects NOT TESTED |
| Exact/inexact alarms, permissions, Home controls | Android API implementation compiled, official Android docs retrieved | PASS — source/build | On-device permissions/notification/routing UI NOT TESTED |

## Explicit limitations

One timer only; 1..86400 seconds. No automatic replacement/retry. Inexact background alarms may be delayed by Android. Exact alarms need optional user-enabled special access on Android 12+ and still obey OS policies. Notification permission/channel/DND/sound and force-stop can prevent audible delivery. Reboot interrupts timers; API 23 uses conservative boot-time estimation. Request journal is bounded and fails closed until explicitly cleared while idle. Alarm/notification Binder or storage failure is not hidden as successful start. A normal posted notification is not proof the user heard it. External Clock creation is never inferred from an intent launch.

Real phone/emulator and legitimate Gemini credentials were unavailable. No real calls/messages or external Clock actions were performed. No production-readiness or 100% device-control claim. TIMERS-GUIDE.md contains exact configuration/ADB commands, restrictions and physical-device acceptance checklist.
