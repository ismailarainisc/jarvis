# JARVIS 7.0 — setup, actions and real-device verification

Package com.raaz.assistant, versionCode 10 / 7.0-actions. Upgrade with the matching development signing certificate; do not uninstall to work around a signing mismatch if local history/settings matter. Existing provider profiles, Google key, native voice selection, Home, memory, MCP and wake configuration are retained.

## First-time setup and permissions

The first 7.0 launch shows an 11-step wizard, including on upgrade from 6.1. Progress is saved in the existing private settings; provider secrets stay in their original Keystore-backed storage. Optional steps can be skipped and setup can finish with no permission grants. Settings → Revisit setup & permissions reopens it. Re-entering an unfinished wizard preserves its step; revisiting a completed wizard starts a new review without resetting providers or other data.

1. Read Welcome and scope. This app cannot control every app or bypass security.
2. Configure your accessible Google Live model/voice/key, or use Manage text providers for existing Gemini/NVIDIA/OpenAI-compatible profiles. A saved key is not a successful connection. Do not put developer production keys into the APK.
3. Grant microphone only if you want explicit START voice. No audio recording starts during setup.
4. Optionally grant Contacts. It enables local search/recipient verification; aliases never authorize communication.
5. Optionally grant notifications. Foreground chat remains available if skipped; JARVIS requires visible notification access for its explicitly enabled background mode.
6. Optionally choose the assistant role through Android's own UI. Role status is queried on Android 10+; older Android versions offer manual assistant settings without claiming the current role was verified.
7. Read accessibility disclosure, acknowledge it, then manually enable the service in Android settings if desired. Also opt into Chrome/YouTube separately in Control Center → Apps & actions. Restricted settings, permission dialogs and locking are never bypassed.
8. Optionally open background wake configuration. It still needs your own Picovoice credential/license, explicit disclosure, microphone/notification permission and a visible foreground start. Nothing starts on wizard completion, reboot or process death.
9. Recheck actual OS capabilities. Battery/network values are actual Android snapshots, not provider tests. Open native test controls to perform real START/STOP yourself. Return with Back to resume the wizard.
10. Complete setup and open Home.

Every grant/setup page has status/recheck, Skip and Android app-settings access. Revocation is checked again on resume and at execution; granting access does not automatically replay any action. A permission-blocked task must be re-prepared and separately approved.

## Enable real Gemini voice tools

Home → Tools & background opens **Control Center**. Enable “Allow Gemini to propose tools” **before** START. If already connected, STOP and START again to advertise declarations; disabling immediately revokes unapproved requests and the approval gate checks the current flag.

Home START uses the same saved native Gemini engine as 6.1. Home, Voice settings, Tasks and the Control Center workspace share its foreground session. Setup, provider/settings pages, leaving the app and Activity recreation stop foreground capture. The existing opt-in background service remains mutually exclusive and is shown separately.

Supported voice examples (with a configured model and tools enabled):

- “Search YouTube for Urdu Kotlin tutorials.”
- “Open YouTube.” (exact visible launcher name or choose an app in Tools)
- “Find the installed browser apps.”
- “Show my battery and network status.”
- “Find Ahmed in contacts.” (optional Contacts permission and device approval)
- “Open maps for Lahore airport.” / “Prepare directions to Lahore airport.”
- “Prepare a call to Baba.” (save an unambiguous alias first, or select an exact number)
- “Prepare an SMS/WhatsApp message to Baba saying I will arrive at six.”
- “Set a timer for sixty seconds.” / “Prepare an alarm for 08:30.”
- “Remember that I prefer tea without sugar.” (Memory enabled, explicit review)
- “Find my saved tea preference.” (private results remain in local Tasks)

These are user instructions to an actual model, not deterministic comprehension promises. Native structured function calling is implemented for Gemini Live; text providers retain SSE chat and the existing limited deterministic shortcuts, not a fictional universal tool protocol.

The current Google guide documents async calling for gemini-3.8-live and gemini-3.8-live-extended-thinking. JARVIS adds NON_BLOCKING declarations only to those exact IDs and top-level WHEN_IDLE function responses, following the formal API schema. Native raw parameter schemas use the documented parametersJsonSchema field. Other/custom IDs keep ordinary sequential tool calls; the provider may pause voice while awaiting approval. No unsupported async capability is assumed.

## Review and execute

A model proposal is data, never authorization. The app validates public fields, resolves local app/contact/MCP targets, records a task, and waits up to 90 seconds for approval/result. It does not block the native receive/audio loop. Open the Home “Review” notice or Control Center → Tasks & approvals. Inspect the exact target, recipient, message, memory snapshot, permissions or server. Unlock and select **Preview and approve once → Approve once**.

- Missing permission/configuration produces a durable NEEDS_PERMISSION record; nothing executes. Grant/recheck the specific capability and prepare a new request.
- Ambiguous contacts/apps fail safely. Select the exact app/number in the existing Tools/Contacts UI and save an alias if desired.
- Expired/cancelled proposals are not automatically retried. Native expiry is enforced from the durable creation time even after process death or a clock rollback; manually prepared tasks are not silently expired by this voice policy. Duplicate approvals cannot execute the same ledger record twice.
- Memory deletion needs an exact local ID and a device-prepared label/value/kind preview. A database compare-and-delete rejects a record edited since preview. Ordinary Memory Center correction/deletion remains available.
- Private Contacts/Memory results remain on device; Gemini receives only a sanitized status. Approved non-sensitive battery/network snapshots may be returned for a spoken answer. Stored task arguments/results may contain sensitive user data and remain in private app storage; use Cancel/Clear finished to remove records. They are not separately encrypted. Android backup is disabled; device compromise can expose app data.
- MCP results are untrusted and never count as independent proof of external effects. No result can grant a permission or authorize another tool.

Foreground-only voice stops when an intent opens another app; do not expect an out-of-app spoken result unless the separately enabled background service is actually running and Android permits it. The local task outcome remains available. Local in-app queries/memory tasks can return their outcome to the same native session. Cloud/hardware operation still requires real-device verification.

## Status meanings

| Status | Meaning |
|---|---|
| PROPOSED | Recognized pending state; never a fake progress stage |
| NEEDS_PERMISSION | Missing/revoked capability; no execution, new preview needed |
| NEEDS_APPROVAL | Prepared, waiting for an explicit unlocked approval |
| EXECUTING | Durable approval claimed once; actual runner in progress |
| LAUNCHED | Android accepted an intent/navigation action; final external effect not verified |
| VERIFIED | Limited local result checked: query/snapshot, exact memory readback or conditional deletion/absence |
| FAILED / unsupported | Rejected, unavailable or failed operation; explanation retained |
| CANCELLED | Pending operation cancelled/expired before claim, or runner confirms no dispatch |
| UNKNOWN_OUTCOME | Execution interrupted or external effects not independently knowable; check before repeating |

Legacy COMPLETED records remain readable and are labelled unverified, not promoted to VERIFIED. No call connection, sent/delivered SMS/WhatsApp, YouTube playback, alarm creation or active navigation is inferred from an intent. SIM selection and final Call/Send remain controlled by Android/the external app. The existing opt-in manual direct-call path still needs CALL_PHONE and exact approval; it is not exposed as an automatic model tool.

## Accessibility and emergency Stop

Only one approved Back/Scroll in explicitly enabled Chrome/YouTube is supported. The target must be the active, unlocked accessible window. System permission/authentication windows fail the target check. Traversal and wait are bounded, per-app revocation/cancellation is rechecked, and no text extraction, screenshots, arbitrary taps, purchases, sending, password entry or autonomous chains are implemented. Google Play distribution approval is not claimed.

Emergency STOP ends foreground/background audio and cancels the active tool. An already dispatched external effect cannot be rolled back; UNKNOWN_OUTCOME does not invite automatic retry. Pending requests can be cancelled individually. At most 100 pending/executing and 100 finished records are retained; finished-history pruning cannot delete approvals still awaiting review.

## Reproducible build

JDK17, Android SDK35, Gradle wrapper included, internet access for Maven dependencies:

```sh
./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
# Only with an Android runtime attached:
./gradlew connectedDebugAndroidTest
# Unchanged retained legacy backend, isolated fixtures:
(cd backend && npm test)
```

The source ZIP excludes generated build caches, local.properties, credentials, private signing keys and recursive downloadable APK/ZIP files. It includes Gradle wrapper/configuration, all Android source/resources, tests and evidence. Import in Android Studio and select your local Android SDK. A locally rebuilt debug APK normally uses a different signing key from the downloadable workspace-signed development artifact.

## Real-device acceptance checklist — NOT TESTED in this workspace

Use authorized test contacts and avoid real calls/messages/purchases during development. Record Android/device version, model/voice and result separately from mocked/JVM tests.

1. Fresh installation shows Welcome; skip all optional grants, finish, restart and verify completion. Revisit, pause midway, recreate Activity and verify resume without resetting profiles/history.
2. Deny/revoke each optional permission; return from Android settings and recheck actual status. No recording or hidden permission request may start automatically.
3. Configure a legitimate Live model/voice/key, enable tool proposals before START, connect and confirm actual native microphone/audio, multiple turns, mute, interruption and Stop.
4. Request device status. Review its task, approve once, verify real OS values locally and a truthful spoken result where the session supports it.
5. Request a contact lookup; deny Contacts and verify NEEDS_PERMISSION. Grant then prepare/approve a new request. Confirm exact local matches and no private names/numbers in returned provider payloads.
6. Create duplicate contact/app names and verify disambiguation, exact target preview, one-time approval and repeated-tap protection.
7. Prepare app/YouTube/web/maps/settings/alarm/timer intents. Approve once and check actual target activity. Confirm tasks say LAUNCHED, never falsely VERIFIED for final effects.
8. Prepare dialer/SMS/WhatsApp composition using authorized dummy data. Inspect preview, cancel before final Call/Send. Confirm no automatic sending or personal WhatsApp calling.
9. Save/read memory with approval. Prepare deletion, edit the memory before approving, and verify deletion is refused. Re-prepare exact current record and verify local absence after approval.
10. While native voice is active, enter Tasks/Control Center. Verify one session continues, audio receive remains responsive during approval, and async behavior matches the configured model's actual capability. Other model IDs must not be advertised as async.
11. Cancel/expire/revoke a proposal; deliver delayed/duplicate callbacks; verify no late execution or speech. Disable assistant tools while pending and executing. Inspect truthful unknown outcomes after interrupted dispatch.
12. Enable scoped accessibility manually and opt into one allowed app. Verify one Back/Scroll, wrong-window/security-overlay refusal, per-app revocation, disconnect, locked screen and Emergency STOP. Never test bypassing a security dialog.
13. Recreate/terminate during execution. On restart, EXECUTING becomes UNKNOWN_OUTCOME without replay. Pending approvals survive history pruning. Clear finished preserves pending work.
14. Exercise background wake with legitimate Picovoice licensing only if desired; check notification, foreground handoff, audio ownership, calls/headsets, revocation and shutdown on actual Android/OEM versions.
15. Recheck native/fallback voice, provider profiles/encrypted keys, SSE/history, camera/photo removal, themes, memory, MCP authentication/permissions and task approvals after upgrade.
16. Test 320dp screens, large fonts, keyboard, light/dark theme, Urdu/RTL, TalkBack and reduced motion. Long task history uses explicit load-more pagination.

No phone/emulator, legitimate Gemini credential or external MCP/Picovoice account was available for these acceptance checks. Compiled instrumentation and passing isolated fixtures do not prove physical-device or live-service operation.
