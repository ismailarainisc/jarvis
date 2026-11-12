# JARVIS 6.1 native Home — setup and device verification

## Install without losing data

Use the development APK in downloads. Package remains `com.raaz.assistant`, versionCode 9. This artifact uses the previous workspace development signing certificate. An in-place upgrade requires the same signing identity. Do not uninstall to resolve a certificate conflict when local settings/history matter; a locally rebuilt debug APK normally uses a different key.

## Native Gemini on Home

1. Open Home. Gemini Live is the initial voice mode; an explicitly saved fallback choice persists.
2. Tap **Voice settings**, or Settings → **Advanced voice settings**. Enter your Google AI Studio credential and a Live/audio model accessible to your project. A text-only model is insufficient. Blank replacement key preserves an existing encrypted key.
3. Choose a documented voice example or keep your supported custom voice. Save. Saving proves persistence only, not a connection.
4. Return Home and press **START**. Allow microphone access when Android asks. Missing configuration opens setup rather than starting Android TTS.
5. Wait for actual SETTING_UP → READY/LISTENING. Speak. Native microphone PCM goes through the existing Gemini session; returned PCM uses the existing AudioTrack engine. No device TTS is used in this mode.
6. Continue speaking across turns; server-signaled interruption uses the existing native playback generation/flush logic. Verify audibility and echo behavior on your actual hardware.
7. Mute blocks new microphone transmission; capture can remain active. The UI explicitly distinguishes that from releasing the microphone. Unmute resumes eligible transmission.
8. Press **STOP** to close capture, playback and WebSocket. Foreground audio also stops when leaving the voice workspace or backgrounding. It does not silently restart after recreation.

Home ↔ Voice settings keeps the same foreground controller/session. History, Tools, Memory and other pages end foreground voice. Native transcripts appear only when received from Google. Measured waveforms reflect PCM; a moving decorative orb is not proof of connectivity or comprehension.

Urdu, Hindi and English depend on the chosen native model. Provider access, quotas and pricing are governed by Google; free usage is not guaranteed.

## Fallback and text/camera

Home → **Voice mode** → **Android fallback** explicitly selects SpeechRecognizer → selected text provider → Android TTS. Mode changes stop existing foreground voice. This mode is turn-based and does not claim native barge-in. Settings → spoken text replies and fallback language affect this explicit mode only.

Text chat retains the independently selected text-provider profile and real SSE behavior. In native mode a typed reply stays text; it is not spoken using robotic device TTS. Stop voice before sending a text request. Camera opens the actual camera activity, attaches a photo to the text composer and offers Remove photo. Model vision support is still required.

## Background mode

The existing opt-in Tools & background flow is unchanged. Stop background mode before starting foreground voice or changing voice modes. The foreground coordinator blocks while the background service is running or reserving startup. The existing audio lease remains the final hardware owner. Background wake has its own permissions, Picovoice licensing/credential and OEM limitations; this release does not promise universal 24/7 operation or lock-screen bypass.

## Reproducible local build

JDK 17, Android SDK 35, network access for dependencies:

```sh
./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
# Device/emulator execution, not just compilation:
./gradlew connectedDebugAndroidTest
# Retained unchanged legacy backend:
(cd backend && npm test)
```

Use app/build/outputs/apk/debug/app-debug.apk for local development. Never commit signing private keys, API credentials or local.properties. This project retains personal bring-your-own-key encrypted storage; it does not embed a shared developer key.

## Physical-device acceptance checklist — NOT TESTED in this environment

Record device/Android version, model/voice and time. Never record/export your key.

- Open Home with an upgraded installation; confirm profiles, memory/history and preferred native voice remain.
- Missing-key START opens configuration and never falls through to TTS.
- Deny microphone permission; observe actionable error and no capture. Grant and START once.
- Confirm real connection/setupComplete, microphone usage indicator and actual Google reply in configured voice.
- Ask at least three questions without restarting; verify actual input/output transcripts where available.
- Interrupt queued and currently playing speech. Confirm obsolete audio stops and the next turn succeeds.
- Mute/unmute rapidly and during assistant speech; verify no new mic frames are queued while muted.
- Home → Voice settings → Home retains one session and same voice. Check output route controls in Advanced settings on supported Android versions.
- Home → History/Tools/Memory stops foreground audio. Return Home requires explicit START.
- Background app, rotate/recreate Activity, receive a phone call, revoke permission, disconnect network/headset: ensure resources close and no stale reply resumes.
- Rapid START/STOP and native/fallback changes never create two microphone owners; no automatic reconnection or fallback occurs after API failure.
- Enable explicit background wake mode, then try foreground START. It must refuse until background mode is stopped.
- Verify text SSE, Stop text generation, camera attachment/removal, aliases, memory, MCP and approvals retain their actual behavior.
- Test 320dp-width phone, large fonts, Urdu/RTL text, light/dark theme, TalkBack and system animations disabled. Controls must remain reachable, with scrollable content above START/STOP.

No device/emulator or legitimate Gemini credential is available in this workspace. Automated fixtures do not demonstrate real microphone transmission, native voice quality, interruption, hardware routing or cloud compatibility.
