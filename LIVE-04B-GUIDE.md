# Phase 04B — bidirectional native audio

Use the existing backend from Phase 04A. `backend/server.mjs` and authentication have not changed. See LIVE-SETUP.md for Node startup, HTTPS, OPENAI_API_KEY and per-user expiring hashed backend sessions. Never put a permanent OpenAI key into Android. A backend key with GPT-Live access, a valid backend user token and a real Android device are required to verify this feature. The server uses gpt-live-1; all existing text providers and their encrypted credentials are independent.

Official sources checked during implementation on 2026-11-11:
- https://developers.openai.com/api/docs/guides/live
- https://developers.openai.com/api/docs/guides/voice-webrtc
- https://developers.openai.com/api/docs/guides/live-conversations

## Use

Install the APK over the previous workspace APK (same signing identity) to preserve data, or build with your own established signing identity. Open Home → GPT-Live, scroll to Backend configuration and save your HTTPS backend endpoint and backend user token. START requests microphone permission, acquires communication audio focus and opens the existing SDP/session flow. SESSION_READY requires both connected WebRTC transport and the matching session.started event. The SDK starts its actual microphone sender and native remote renderer. There is no native-mode TTS, audio-file placeholder, fake waveform or app-generated voice response.

Keep the session open and input track active for automatic full-duplex conversation. Do not repeatedly press START, send response.create, session.start or legacy audio-buffer commit commands. GPT-Live manages conversation timing internally. If the model emits delegation requests, those remain unsupported by this unchanged connection backend; delegated knowledge/tasks are not silently simulated. No provider turn-completed, speech-start/end or processing events are fabricated. PROCESSING is reserved and never assigned based on silence. Captions are partial timed fragments; there may be no transcript events and no clear turn boundaries.

Mute disables the sender track, applies SDK microphone mute and disables PeerConnection audio recording; unmute reverses those controls in the same session. Actual engine callbacks and meters determine displayed recording status. Android may keep a microphone privacy indicator briefly or maintain device internals even though input is disabled. PCM levels are zeroed/hidden while muted. Native audio output continues while the microphone is muted. STOP disables native recording/playout and tracks immediately, then requests session.close, with a 15-second finalization bound. Leaving the screen or backgrounding the app releases locally immediately; a network failure can prevent provider-side finalization.

## Measured information

Microphone and playback PCM levels are measured from SDK callbacks before transmission and device output. They are smoothed and limited to 10 Hz per direction; they are not sound-pressure or semantic speech measurements. Native renderer running is separate from non-silent output activity. Local RMS thresholds with hysteresis drive USER_SPEAKING / ASSISTANT_SPEAKING activity labels and the UI explicitly identifies them as audio-energy activity. Input and output flags can be active simultaneously. No internal model reasoning, artificial progress or silence-triggered thinking state is shown.

The UI shows real sender attachment, remote track availability and outbound/inbound audio RTP packet counts when the SDK supplies them. Sent packets plus measured microphone audio indicate an active local media path; they cannot prove that a remote provider heard or understood the audio. An enabled remote track/renderer also cannot prove physical audibility. Confirm those on a real phone. Partial captions are sourced only from the two documented transcript.delta events and preserve text, timestamps and event IDs; they are not stored as invented completed chat turns.

## Routes and lifecycle

Android 12+: choose among actual available communication devices. Bluetooth/headset options appear only when Android exposes them; permission/device restrictions show a notice rather than a fake selected route. Older Android: Speaker or System/headset; Android controls Bluetooth/headset negotiation. The app never opens a second AudioRecord or AudioTrack. If the selected output disconnects, the live session ends with a recovery message. Incoming calls, audio focus loss, capture/playout errors, network loss, session expiration, backgrounding or screen disposal stop/release the session. No automatic billable reconnect is made. Repeat START only when ready to create a new session.

## Build and local tests

With JDK 17 and Android SDK 35:

```sh
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest
cd backend
npm test
```

Run `./gradlew connectedDebugAndroidTest` only with a connected emulator/device. The workspace has no attached Android device and no configured OpenAI API key, so native audio and real-provider tests cannot run here. Verification XML and backend output are included in `verification/`; see LIVE-04B-VERIFICATION.md for the actual results. The separate debug APK is signed with the workspace's existing development key, excluded from source.

## Required real-device checklist

1. Preserve existing app data by installing an APK signed with the same key. Check theme, profiles and history before/after upgrade.
2. Start on a real phone with valid GPT-Live backend credentials. Deny mic permission first: expect an error and no session. Grant it through Android settings and retry.
3. Observe native sender attachment, capture callback, real mic levels, connected transport and SESSION_READY. Speak and verify outbound audio RTP counters increase. Confirm through a real provider response that it heard the question; counters alone are insufficient.
4. Verify remote track, playback engine, inbound RTP and measured output PCM, and actually hear the provider's voice. Ask another question without restarting. Inspect partial captions if supplied; none must be invented.
5. Mute: verify input disabled and meter zero, observe the Android microphone indicator and that new spoken words are not sent. Unmute and verify input resumes in the same session and another actual response is audible.
6. Select speaker, wired/USB and supported Bluetooth routes. Actually listen on each. Disconnect the selected headset: expect a stopped/error session, not undisclosed rerouting. Verify previous route restoration after Stop.
7. Stop during capture, native playback, negotiation and simultaneous activity. Confirm Android capture ends, speaker audio stops, data/peer resources release, and session.closed or a truthful finalization warning. Start/Stop rapidly and check stale callbacks do not resume audio.
8. Test network loss, permission revocation, phone calls, audio-focus interruption, screen navigation, background/foreground, rotation, expired backend token and provider session expiration. None may silently resume recording. Cleanup failures must show an error.
9. Test normal text chat/SSE, camera, saved profiles and SpeechRecognizer/TTS fallback. Start fallback after native Stop; start native after fallback; no concurrent microphone owners or obsolete TTS playback should remain. Typed TTS must stop when backgrounded.

## Limits

No live-provider/device result is claimed. The local energy detector is not a semantic speech detector; echo/noise can affect the raw input meter. WebRTC processing applies to the media path. Exact callback availability and route behavior depend on Android/WebRTC/device support. If no PCM callback arrives, the meter shows unavailable. There is no manual GPT-Live turn boundary protocol here because none is documented for this session type. Phase 04C interruption/cancellation and delegation/tool processing remain outside this change.
