# JARVIS-owned direct timers (7.3)

Based on PR #4's 7.2 app, not the old main branch. Package, premium Home, native shared Gemini controller, provider credentials, memory, MCP, approvals and fallback are preserved. No duplicate voice engine or microphone service is added.

## What changed

Previously `set_timer` dispatched an external Clock intent with skip-UI false and returned LAUNCHED. It neither owned a deadline nor knew whether a timer existed. It also waited for general task approval.

Now `set_timer(seconds)` starts a real single JARVIS-owned timer through AlarmManager. Only timer creation/status/cancel/stop are direct low-risk tools when **Assistant tools** is enabled. Other actions, including external Clock/alarm, calls/messages/MCP, still use their existing approvals. The Gemini worker returns a real persisted/scheduled result without opening an Activity or stopping the native audio session. Tasks records the actual outcome. Failed permission/scheduling/persistence/notification operations cannot report a successful start. The model is instructed to speak only the returned outcome and repeat approximate-alert limitations.

`timer_status`, `cancel_timer`, and `stop_timer` are genuine declared tools. Numeric seconds must be a whole number in 1..86400. Five minutes is 300 seconds; thirty seconds is 30. English, Urdu and Hindi speech is interpreted by the actual selected Gemini model, not a fake recognizer. Explicit text shortcuts cover the example commands and localized durations; natural speech correctness remains a live-provider test.

`set_clock_timer` preserves optional external Clock integration with ACTION_SET_TIMER, EXTRA_LENGTH, EXTRA_SKIP_UI=true and existing SET_ALARM permission. It truthfully reports LAUNCHED, not VERIFIED. `set_alarm(hour, minute)` similarly requests Clock skip-UI after approval. Android Clock implementations may ignore skip-UI; dispatch is not proof an alarm/timer was created. Open YouTube still launches the actual installed app through its existing validated action; no video-playing claim.

## Timing, permissions and user control

- Timer state is private SharedPreferences committed before reporting success, with a monotonic elapsed-realtime deadline, boot count and generation ID. Countdown comes from that deadline, not an invented decrementing counter. Reopening JARVIS restores the same deadline and re-registers the same pending alarm without extending it.
- With already-authorized precise access, AlarmManager.setExactAndAllowWhileIdle is requested. Otherwise setAndAllowWhileIdle is used and the UI/result explicitly warns that Android may delay background alerts. No foreground service or invisible microphone is started.
- On Android 12+, optional SCHEDULE_EXACT_ALARM special access opens **only** after tapping “Enable precise alerts”. It is not automatically requested; USE_EXACT_ALARM is not used. Enable it before starting a new timer. Existing approximate timers keep their original scheduling mode. Revoked precise access interrupts the timer on reconciliation instead of pretending it remains precisely scheduled.
- POST_NOTIFICATIONS on Android 13+, global notification enablement and timer-channel visibility are checked before creation. Use Home's timer notification actions to request/recheck access. A blocked channel needs Android notification settings, not another runtime permission dialog.
- Completion is delivered by a manifest receiver, posting a real high-importance alarm-category notification with the user's channel sound/vibration settings and a Stop action. The running notification has Cancel and a countdown where supported. Home shows remaining time, Stop and Cancel. Stop/cancel removes the pending alarm and dismisses the alert.
- A posted notification is not proof the user heard it. DND, disabled sound, OS idle quotas, OEM restrictions, force-stop and revoked permissions can prevent/delay audible alerts. No unrestricted exact-delivery promise is made. Notification failure records ALERT_FAILED, never “delivered”.
- Process death alone does not remove an AlarmManager pending intent; UI polling stops with its lifecycle. Voice STOP stops voice, not an independently requested timer. Android force-stop suppresses alarms until reopening; the original deadline is restored/rearmed on reopening, with a late finish alert if already elapsed. No app can bypass force-stop.
- On API 23, a conservative boot-time estimate is used; a wall-clock adjustment can also interrupt the timer rather than risking a false elapsed event. On API 24+ the OS boot count is used. Reboot interrupts/cancels the old timer rather than converting an old elapsed deadline into a fabricated completion. It does not silently resume audio or record anything. The app has RECEIVE_BOOT_COMPLETED solely for timer cleanup.
- Only one timer is supported. A second start retains the existing timer and explicitly says no new timer was created. Stop/cancel it before starting another. Stale completion/cancel notification actions cannot affect a newer generation.
- A bounded 256-ID persistent request journal and the Gemini task-ID cache prevent duplicate side effects. The journal fails closed when full; it can be explicitly cleared in timer controls only while no timer is running. Clearing is user-controlled, not an automatic replay.

## Verification and acceptance

See verification/owned-timers/ for actual build results and tests. OwnedTimerTest uses isolated scheduler/notification/storage ports against the production TimerMachine; it is not Android alarm or audible delivery verification. TimerIntegrationTest compiles framework intent/manifest checks without launching external apps. No device/emulator or legitimate Gemini account was available. All physical and live-provider tests below remain NOT TESTED until performed.

```sh
./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
adb devices
adb install -r Jarvis-Gemini-Live.apk
adb install -r Jarvis-7.3-androidTest.apk
adb shell am start -n com.raaz.assistant/.MainActivity
adb shell am instrument -w -r -e class com.raaz.assistant.TimerIntegrationTest com.raaz.assistant.test/androidx.test.runner.AndroidJUnitRunner
adb shell dumpsys alarm | grep -A 8 com.raaz.assistant
adb shell dumpsys notification --noredact | grep -A 8 jarvis_timer_alerts
```

Use dumpsys only locally: notification output can contain private device data; do not export it unredacted.

Device checklist (authorized test data only):
1. Upgrade without uninstalling, confirm keys/history/Home/Live voice intact. Enable Assistant tools and timer notifications.
2. Home START; say “start a timer for five minutes”, “پانچ منٹ کا ٹائمر لگاؤ”, or “पाँच मिनट का टाइमर लगाओ”. Verify tool args=300, actual task result, Home countdown and scheduled alarm BEFORE accepting spoken success. Repeat with thirty seconds.
3. Ask remaining time in all three languages. Check count against monotonic elapsed time, navigate screens/recreate Activity and reopen app; no reset/new timer.
4. Repeat the identical tool ID via an isolated fixture, then send a new start while running. Verify only one timer/deadline. Cancel, then exercise an old notification callback after a new timer; it must not affect the new one.
5. Let a short timer finish in foreground, background and with process killed using `adb shell am kill com.raaz.assistant` (not force-stop). Verify one real notification, sound per channel/DND, Stop dismissal and no stale replay.
6. Deny notifications using Android settings (API 33+: `adb shell pm revoke com.raaz.assistant android.permission.POST_NOTIFICATIONS` on a test device). Start must return NEEDS_PERMISSION without scheduling. Block/unblock the timer channel separately. Revoke access after starting and confirm truthful delivery-failure behavior.
7. On API 31+, test precise access absent/granted/revoked through Android special-access settings. Verify approximate-mode warning vs exact requested scheduling and interruption on revoked access. Test Doze/OEM behavior separately; do not generalize one device's delivery timing.
8. Force-stop using `adb shell am force-stop com.raaz.assistant`, observe Android suppresses delivery, reopen and verify original deadline recovery. Reboot with a running timer and verify INTERRUPTED, no false completion.
9. Stop/cancel through UI, notification and live voice; confirm removed pending alarm and no future notification. Stop Live voice while a timer runs and confirm no microphone remains while the timer continues.
10. Test invalid zero/negative/fraction/oversize seconds, storage/alarm/notification failure fixtures and journal-full behavior. No successful-start claims on failure.
11. With explicit approval, test YouTube launch and Clock 7AM request. Verify LAUNCHED vs actual external effect separately; no silent messages/calls.

No physical-device, notification-audibility, external Clock or genuine Gemini acceptance item is marked PASS from compilation or isolated unit tests.
