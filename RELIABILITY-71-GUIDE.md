# JARVIS 7.1 — setup and reproducible device acceptance

Application: com.raaz.assistant, versionCode 11 / 7.1-reliability. This update builds on PR #2's 7.0 code; main remains untouched. Existing setup progress, profiles, encrypted credentials, local history/memory, native voice, MCP and explicit background mode remain.

## Setup and real actions

1. Complete or skip optional wizard steps. Existing 7.0 setup completion is retained. Settings → Revisit setup & permissions reopens the wizard.
2. Home → Tools & background → Permissions shows current Android state. Request microphone, Contacts or notifications individually only if needed. Use App settings and Recheck after denial/revocation. Background wake additionally needs the JARVIS microphone notification channel enabled; the channel shortcut opens its actual settings.
3. Configure an accessible Google Live model/voice/key in Voice settings. Saved means configured, not connected. Enable Gemini tool proposals before START; if already connected, STOP and START to advertise declarations. Native Home never falls through to robotic TTS after an API error.
4. Press START, speak, then review the actual pending action. Home/Voice settings/Tasks/Control Center share the foreground session. Supported non-blocking models can continue conversation during approval; other model IDs retain their documented limitations.
5. Use commands such as “Open YouTube/Chrome/Maps”, “Search YouTube for Python tutorials”, “Set an alarm for 7 AM”, or “Check my battery”. Google comprehension and actual external app support require real account/device testing, not a fixture claim.
6. To prepare a message for a selected contact: Contacts → grant only if searching the address book → choose an exact number, or manually enter a number → Use this recipient for voice previews. The selected recipient is shown and can be cleared. Ask “Prepare a message for my selected contact…”. Phone/lookup resolution remains local; inspect the exact frozen recipient/message before approving. Changing the selected contact never silently retargets an already prepared task.
7. Dialer/SMS/WhatsApp remain preparation flows; final Call/Send is manual. The retained direct-call opt-in remains separate and is not an automatic model tool. No development test sends real messages or places calls.
8. Native proposals expire after 90 seconds and cannot execute twice. Preparation has a 15-second cooperative deadline; execution has a 60-second deadline. Side effects already dispatched cannot be rolled back: timeout/cancellation after claim is UNKNOWN_OUTCOME, never an invitation to auto-retry. MCP cleanup can take up to three additional seconds.
9. Contacts queries use a ten-second deadline and Android CancellationSignal. A third-party ContentProvider/native wake SDK can ignore cancellation while inside blocking native code; the app rejects late results/startup and requests cleanup, but cannot certify a hard OS-level deadline without device testing. Wake initialization has a ten-second cooperative deadline and never starts Google capture on timeout.
10. Android fallback remains explicit. A cold device TTS engine now queues one reply up to 16000 characters until real initialization succeeds, with an eight-second timeout. Stop/mode change/close cancels pending speech. This is not native Gemini speech or barge-in support.

Foreground voice still stops when an intent opens another app unless the separate, explicitly enabled background service is actually running and Android permits it. Local task outcomes persist. A launch is not proof of video playback, sent/delivered message, call connection, created alarm or active navigation. Private contacts/memory are not sent back to Google; approved non-identifying battery/network snapshots are strictly formatted. No screenshots, arbitrary taps, permission auto-approval, lock bypass or autonomous accessibility planning.

## Build and artifacts

JDK17, Android SDK35, Gradle wrapper:

```sh
./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
(cd backend && npm test)
```

Two downloadable APKs are provided: the debug app and Android instrumentation APK. Both are signed with the same previous workspace development certificate so instrumentation can target the app. This is not a production signing key; private key is excluded. Local Gradle rebuilds usually use a different debug certificate. Do not uninstall an existing app to fix a signing mismatch if local data matters.

## Exact ADB commands (own authorized test device only)

Use a shell in the directory containing the downloaded files. Android developer options/USB debugging must be enabled manually. No app or test can enable them for you.

```sh
adb devices -l
adb install -r Jarvis-Gemini-Live.apk
adb install -r Jarvis-7.1-androidTest.apk
adb shell am start -n com.raaz.assistant/.MainActivity
adb shell am instrument -w -r com.raaz.assistant.test/androidx.test.runner.AndroidJUnitRunner
```

To run only non-communicating setup/persistence regressions:

```sh
adb shell am instrument -w -r -e class com.raaz.assistant.SetupWizardTest,com.raaz.assistant.FeaturePersistenceTest com.raaz.assistant.test/androidx.test.runner.AndroidJUnitRunner
```

Runtime permission-denial/revocation smoke checks (commands revoke access; they do not bypass a permission dialog):

```sh
adb shell pm revoke com.raaz.assistant android.permission.RECORD_AUDIO
adb shell am start -n com.raaz.assistant/.MainActivity
adb shell pm revoke com.raaz.assistant android.permission.READ_CONTACTS
adb shell am start -n com.raaz.assistant/.MainActivity
# Only on API 33+:
adb shell pm revoke com.raaz.assistant android.permission.POST_NOTIFICATIONS
# Open actual app settings; grant or deny manually and return to Recheck:
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.raaz.assistant
# Inspect foreground-service lifecycle without exporting private audio/transcripts:
adb shell dumpsys activity services com.raaz.assistant
# Process interruption: does not uninstall or clear user data:
adb shell am force-stop com.raaz.assistant
adb shell am start -n com.raaz.assistant/.MainActivity
```

Optional handler inspection (shell visibility can differ from app package visibility; this is not a JARVIS end-to-end pass):

```sh
adb shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d 'geo:0,0?q=Airport'
adb shell cmd package resolve-activity --brief -a android.intent.action.SET_TIMER
adb shell cmd package resolve-activity --brief -a android.intent.action.DIAL -d 'tel:+12025550123'
```

Never use ACTION_CALL, automatic Send or real recipient data in unattended tests. Configure API keys only in the masked app UI, not shell history, screenshots or exported logs. Instrumentation uses isolated feature databases for persistence checks; use a test installation/device, not a phone containing irreplaceable data. No credential, microphone hardware, account or real communication success is implied by running mocked/JVM tests.

## On-device acceptance — all NOT TESTED in this workspace

Record Android version, device, build version, model/voice and each result without recording keys.

- Upgrade without uninstalling. Confirm old keys/profiles, Home voice choice, setup progress, history, memory and MCP/wake settings remain.
- Enter Permissions; deny optional grants, open Settings, return, revoke and recheck. Verify actual status and feature requirements. No hidden recording or bulk requests.
- Disable app notifications and then only the microphone channel. Background start must refuse and release its reservation; foreground START must remain available after cleanup. Re-enable explicitly. Rapid Stop/Start during cleanup must not create a second owner.
- With legitimate Google access, START once; verify actual input, native speech, several turns, mute/unmute, server interruption, headset/focus changes, network loss and resource release on STOP/backgrounding. Native mode must never speak through device TTS.
- Enable tools before START. Speak each example. Verify actual proposal, permission failure, unlocked approval once, correct target, truthful status and native result where the session remains in foreground. Check received audio with real hardware; PCM fixtures do not prove audibility.
- Search Contacts rapidly with different queries, then revoke access while a read is pending. No stale names/numbers may repopulate results. Formatting variants of one lookup should resolve, separate contacts sharing a line should require selection.
- Select an authorized dummy recipient, prepare but do not Send/Call. Verify the exact local preview; change selection/contact data before execution and confirm no silent retargeting. Clear selection and verify missing-selection guidance.
- Inspect localized YouTube/Chrome/Maps names, missing/ambiguous handlers and supported settings/alarms/timer/maps intents. Record LAUNCHED separately from actual completion.
- Cancel pending/in-flight tasks, wait for expiry/deadlines and force-stop/restart during execution. No automatic retries; ambiguous external effects remain UNKNOWN_OUTCOME.
- With an authorized test MCP server, test headers-then-stalled-body cancellation, TLS/auth/network errors, malformed IDs/schemas and tool-permission revocation. No secrets may appear in diagnostics or provider responses.
- Enable scoped accessibility manually for one supported app; test exact target/lock/scope revocation, one Back/Scroll and emergency Stop. Never test bypassing system permission/authentication dialogs.
- Explicit fallback: send a text reply before device TTS has initialized, cancel before readiness, retry after failure, then switch back to native. No late speech or microphone conflict.
- Test small screens, large fonts, RTL/Urdu, TalkBack, keyboard, light/dark and reduced motion. Recheck camera, text streaming, memory and approvals.

No phone/emulator, KVM device or legitimate provider credentials are available in this workspace. The verification report separates code, isolated tests, instrumentation compilation, device and live-provider evidence.
