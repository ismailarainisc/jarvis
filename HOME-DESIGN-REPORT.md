# Holographic Home implementation — 7.2

This change follows the user-approved image at `design/approved-home-reference.png`, on top of JARVIS 7.1 / PR #3. The screenshot is a design reference, not a running-app screenshot. The central illustration was generated from that reference and is stored separately in `app/src/main/res/drawable-nodpi/jarvis_hologram.png`; the application does not render the full reference screenshot as its UI.

## Actual implementation

- `ui/PremiumHomeScreen.kt`: JARVIS identity, reactor emblem, history/settings, real session connection chip, large holographic face, live state heading, waveform, chamfered user/assistant transcript cards, prominent Start/Stop, microphone mute, camera, text composer and Home/Tasks/Memory/Tools navigation.
- `ui/HolographicHomeComponents.kt`: reusable art/Canvas face, reactor mark, measured waveform and selectable expandable transcript surfaces. Fine cyan/violet artwork is decorative; the overlay/waveform reads actual microphone/playback levels. There is no random waveform, synthetic transcript or permanent connected label. Reduced animation preferences disable smoothing.
- `ui/JarvisApp.kt`: removes the duplicate standard Home toolbar; other screens retain their existing toolbar. Tasks opens the real task workspace, not the general tools tab.
- Home `onStart` still delegates through `MainActivity.requestVoiceStart` to the existing `HomeVoiceCoordinator` and `GeminiHomeAdapter` / `GeminiLiveController`; dedicated Live settings observes that same controller. The underlying Gemini engine, credentials and data were not replaced. The selected explicit fallback remains available through the Tune/Voice mode button. No silent preference migration or forced fallback is introduced.
- The chip opens real voice settings. It only says connected when the existing session reports readiness and is not disconnecting/error/stopped. Settings/history/camera, mode selection, mute, Start/Stop, task/memory/tools navigation and composer retain actual callbacks. Text sending remains disabled while voice owns audio, as before; Stop enables normal text sending. A selected camera photo retains its remove action.

## Visual fidelity and accessibility

The reference's dark navy, cyan reactor face/rings, pale headings, user and JARVIS cards, large cyan primary control, composer and four bottom tabs are implemented as Compose views. Touch targets remain at least 48dp. Content scrolls on smaller windows; primary controls stay within reach. Transcript and composer content support Android text direction, selectable text and multiline expansion. No sample conversation from the reference is inserted into user history. Light theme retains readable surfaces and the deliberately dark illustration.

The exact reference is included so review can compare it with an actual device. The extracted face art differs in fine generated details; Android fonts, system bars, font scaling and screen aspect ratios also change the layout. **No 100% pixel-identical claim or screenshot comparison is made.** No emulator/phone is available, so visual rendering, pixel fidelity and large-font/keyboard interaction remain NOT TESTED on-device.

## Verification

Final verification: BUILD SUCCESSFUL in 6m 23s; 292 JVM tests / 25 suites, 0 failures, 0 errors, 0 skipped. Lint: {'Error': 0, 'Fatal': 0, 'Warning': 3}. An initial clean build passed before the final completed-text, waveform and constrained-art refinements; the final incremental build verifies those frozen source inputs. Both logs are retained. App and instrumentation signatures, previous signing certificate, ZIP alignment and non-signature content equality to the actual Gradle APKs were verified. See `verification/holographic-home/summary.json` and logs. `HolographicHomeUiTest` adds readiness/no-fabricated-transcript and separate bottom-navigation callback coverage. Existing `HomeNativeUiTest` and `HomeVoiceCoordinatorTest` remain. Android tests are compiled, not executed; JVM tests are not proof of real microphone/audio or live Google connectivity. An initial Kotlin string/else-spacing compilation error was fixed and its failed log retained separately.

No phone/emulator or legitimate Gemini account credential was supplied. Native audio, interruptions, real provider/model availability and Home-vs-Live audible equivalence therefore remain NOT TESTED. This is a Home visual implementation with existing shared voice wiring preserved; it does not claim to diagnose an unobserved real-device issue.

## Install and device review

Download the app APK from this branch's `downloads/Jarvis-Gemini-Live.apk`. The app keeps package `com.raaz.assistant`, increases versionCode to 12, and uses the previous development signing certificate. Do not uninstall if you need to preserve local data. If Android rejects an update for a different certificate, inspect the installed build rather than deleting data.

```sh
adb install -r Jarvis-Gemini-Live.apk
adb install -r Jarvis-7.2-androidTest.apk
adb shell am start -n com.raaz.assistant/.MainActivity
adb shell am instrument -w -r -e class com.raaz.assistant.HolographicHomeUiTest,com.raaz.assistant.HomeNativeUiTest com.raaz.assistant.test/androidx.test.runner.AndroidJUnitRunner
```

On a real device, compare Home with the supplied reference at default font scaling, then test small-screen/large-font/keyboard/RTL/TalkBack behavior. Configure a legitimate Live model/key/voice in Voice settings. Select Gemini Live from Home's Tune control, press Start, grant microphone access, observe actual readiness, speak two turns, test interruption/mute/unmute, then Stop. Check that idle/error/disconnecting never claims connected. Navigate between Home and Live settings and verify the session/voice is shared. Separately test explicit fallback; its voice is intentionally Android TTS. Test each navigation button, camera attachment/remove, text send after stopping, and task approval access. These steps are reproducible checks, not completed device results.
