# JARVIS 7.1 — reliability evidence and remaining limits

Baseline: PR #2, ebebb7be2ea519c1383f5e8f79431ea5f532c726 (7.0). Latest main at inspection: e4a33167c2c899dbdb01151c7e8871fc0bcf0813 (6.1). PR #2 was open, so this branch, feat/jarvis-7-1-reliability, is stacked onto feat/mobile-action-engine-7. Main is not overwritten or merged by this task. Package com.raaz.assistant; versionCode 11 / 7.1-reliability.

## Implemented changes

The existing native Google engine, Home/Voice Settings/approval workspace, encrypted credentials, provider profiles, memory, MCP, camera, text/SSE, explicit fallback and optional wake remain. No duplicate engine or destructive schema migration is introduced.

- Real Permissions & readiness page: independent relevant requests, current OS state, resume/recheck, role/settings shortcuts, microphone channel troubleshooting and per-feature requirements. No bulk grants, silent recording or automatic replay after permission changes.
- Local selected-recipient binding for voice previews, clearable and separate from conversational memory. Phone/lookup never goes back in provider task results; communication still needs exact preview, unlock and supported external composition/confirmation.
- Request identity/cancellation/current permission checks for Contacts; formatting-aware identity with disambiguation for separate lookup IDs. Supported ContentProvider reads use CancellationSignal and a deadline, and null provider results fail rather than fabricate an empty query.
- Known spoken app aliases resolve visible localized official YouTube/Chrome/Maps packages. Other ambiguous labels still require explicit selection; the selected package/label is revalidated at execution.
- Single-record ledger reads replace repeated full-history deserialization. Preparation and execution deadlines preserve one-time approval and conservative unknown external outcomes, without retries.
- Background startup authorization is revoked on rejection/cleanup; app/channel notification visibility gates and monitors microphone mode. Failed-start reasons survive cleanup. Wake initialization has a cooperative deadline and cancellation checks before real recording.
- Cold fallback TTS queues one bounded reply until a real initialization callback, with cancellation/failure/deadline handling. Native Gemini never uses this device TTS path.
- Gemini cancellation IDs are tombstoned so a delayed function call cannot recreate cancelled work. Native results continue through the existing session.
- MCP cancellation owns the HTTP Call through response-body reading, not only headers. IDs/number/schema validation is exact and conservative. Stage-aware failures distinguish pre-tool rejection from a tool request that may already have external effects; arbitrary bodies/tokens are not surfaced.

## Evidence levels

Final clean command: BUILD SUCCESSFUL in 9m 9s. JVM: 292 tests, 0 failures, 0 errors, 0 skipped across 25 suites. Lint: 0 errors, 3 dependency-update warnings; one Kotlin unused-local warning. Unchanged backend isolated tests: 9/9. Both APKs compiled. The initial constructor-compatibility compile failure was fixed; its log is retained separately. Actual logs are in verification/reliability-71/. JVM ports and local HTTP/PCM fixtures exercise production client logic; they do not prove model comprehension, physical audio or Android actions.

| Level | Result scope |
|---|---|
| Source | Production Kotlin/Compose/Android code and regression coverage implemented |
| Clean Gradle / unit / lint | Actual result in summary.json and logs; no fabricated results |
| Instrumentation | Android test APK compiled and delivered; execution NOT TESTED |
| Real Android device | NOT TESTED — no attached device/emulator, emulator binary or KVM device |
| Genuine Gemini / Picovoice / external MCP | NOT TESTED — no legitimate account credentials supplied |
| Calls/messages/external app effects | NOT TESTED — no authorized device/data; no real calls/messages sent |

## Fix and regression matrix

PASS applies only to the cited source/unit/build level, not live devices/providers.

| Confirmed defect | Production fix | Regression / file | Evidence | Remaining risk |
|---|---|---|---|---|
| Rejected service left authorization reserved | Shared admission revokes failed starts; cleanup cannot rearm | BackgroundAuthorization; permissionRejectedServiceStartRevokesReservation / serviceCannotRearmDuringCleanup | Unit/source PASS | Android FGS behavior NOT TESTED |
| Notification/channel disabled while microphone continued | Real app/runtime/channel visibility at startup and monitoring; troubleshooting shortcut | DeviceCapabilities, BackgroundAssistantService; blockedNotificationChannelPreventsHiddenRecording | Policy unit/source PASS | Actual channel revocation timing NOT TESTED |
| Failed service start was overwritten by stopped state | Failure projection retained through cleanup | visibleWakeState; backgroundFailureSurvivesNormalCleanupStates | Unit PASS | Actual notification/UI NOT TESTED |
| Wake remained ARMING indefinitely | Cooperative initialization deadline, close-on-failure, pre-capture cancellation checks | WakeSession; suspendedWakeInitializationHasDeadlineAndCannotStartVoice | Unit PASS | Native SDK can ignore cancellation during blocking init; no hard OS deadline claim |
| First fallback typed reply beat TTS readiness | Real readiness gate, bounded latest reply, timeout and stop/close invalidation | SpeechReadyGate / VoiceSessionManager; coldSpeechWaitsForActualInitialization and late/failure/queue tests | Unit/source PASS | Android TTS engine/hardware NOT TESTED |
| Old search or revoked contact results reappeared | Latest request ticket, prior-job cancellation and current grant at delivery | HubViewModel / LatestRequestGate; latestContactRequestRejectsOutOfOrderCompletion / revocationInvalidatesOutstandingContactResult | Unit/source PASS | Real provider cancellation NOT TESTED |
| Null/stuck contacts provider looked empty or hung | Null is failure; CancellationSignal + 10-second cooperative query deadline; cancellation propagated | ContactQueries / ToolResolver; selected-recipient instrumentation compiled, actual provider checklist | Source/compile | Provider may ignore cancellation; physical test NOT TESTED |
| Formatting changes invalidated contact; separate contacts merged | Canonical formatting comparison, lookup+phone identity, explicit ambiguity | samePhone/resolveContactNumber; phoneFormattingDoesNotChangeContactIdentity / genuinelyDifferentContactsSharingNumberRemainAmbiguous | Unit PASS | Real Contacts schema/format variants NOT TESTED |
| Localized known apps did not resolve spoken name | Narrow trusted known-package aliases; exact/ambiguous fallback | resolveInstalledApp; commonSpokenAppAliasResolvesLocalizedOfficialApp / unknownAppNamesRemainAmbiguous | Unit PASS | Installed handlers/package visibility NOT TESTED |
| 'Selected contact' had no real binding | Explicit private recipient prefs, preview-only resolution, permission revalidation and clear control | SelectedRecipientPreferences / ToolResolver / ContactsPanel; persistence/revocation tests; FeaturePersistenceTest compiled | Unit/source PASS | Actual composer/device NOT TESTED |
| Polling loaded all private task history repeatedly | Single-ID database lookup for coordinator/awaiter/native bridge | TaskLedger.task / FeatureStore; approvalAndWaitUseSingleRecordLookup; instrumented lookup test compiled | Unit/source PASS | No device performance claim |
| Preparation/execution had no aggregate deadline | 15-second preparation and 60-second execution limits, unknown after claimed cancellation | ProposalFactory / TaskCoordinator; executionDeadlineRecordsUnknownAndCannotReplay / preparationTimeoutPersistsFailureWithoutExecution | Unit PASS | Blocking OS/native calls cannot be forcibly rolled back |
| Cancel-before-call recreated proposal | Bounded session cancellation tombstone | GeminiSession; cancellationBeforeDelayedFunctionCallCannotRecreateApproval | Unit PASS | Live server ordering NOT TESTED |
| MCP body read outlived cancellation | Structured HTTP cancellation guard remains through body close | McpClient; cancellationAfterHeadersClosesStalledBody (actual local socket fixture) | Local fixture PASS | External TLS/server behavior NOT TESTED |
| Fractional IDs/rounded integers/malformed schema accepted | Exact decimal ID/integer/bound checks and strict supported keyword shapes | McpClientTest fractionalResponseIdCannotMatchIntegerRequest / numericSchemaDoesNotRoundFractionsOrBounds / malformed schema tests | Unit/local fixture PASS | Unsupported complex schemas remain rejected |
| MCP errors lost actionable stage | Safe client message with tool-call-issued flag; unknown effects after dispatch | McpClient / AndroidTools; authenticationFailureBeforeToolCallHasExplicitStage / serverFailureAfterToolDispatchCannotClaimNoExternalEffect | Local fixture PASS | Server-side effects cannot be independently verified |
| Incomplete client workflow evidence | Production requests + ledger approval + executor + function response + incoming PCM path composed in one regression | GeminiSessionTest.nativeToolApprovalRunnerAndReceivedAudioWorkThroughProductionPipeline | Client fixture PASS | NOT real speech/model/action/playback proof |

## Build, artifacts and preservation

Both the app APK and instrumentation APK are signed with the same prior workspace development certificate. Signature, 16KB alignment, package/runner metadata and non-signature content equality against their Gradle outputs are verified. The private key is excluded. Source ZIP includes wrapper, production/test/resources/backend and evidence; generated caches/local configuration, secrets and recursive APK/ZIP downloads are excluded.

Preserved: application identity and additive feature database; existing credential format/Keystore alias; native Google voice and its state/audio owner; fallback speech; setup progress; provider profiles/SSE/history; memory/MCP; task approval ledger/retention; scoped Back/Scroll; camera/theme; explicit background wake. No app main branch overwrite or force push.

Instrumentation, role/permission/device transitions, mic/speaker/interruptions, real Google sessions, selected recipient/Contacts provider, app launches, notification visibility, accessibility, SDK license activation and real external effects all remain NOT TESTED. No 100% control, store-policy approval, free quota or production-readiness assertion. RELIABILITY-71-GUIDE.md includes exact ADB install/instrument/revoke/restart commands and an on-device checklist.
