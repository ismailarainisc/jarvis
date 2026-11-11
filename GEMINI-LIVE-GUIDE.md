# JARVIS — Gemini native Live voice

The app's native voice entry is now **Home → Gemini Live**. It uses Google's Gemini Live WebSocket protocol, not OpenAI/GPT-Live, WebRTC SDP, SpeechRecognizer or Android TTS. You do not need an OpenAI account, the old backend, or a backend URL for the personal API-key mode. Legacy source/modules and existing provider/history data remain intact; the active app wiring uses GeminiLiveController and GeminiLiveScreen.

## Setup on your phone

1. Install the new APK over the old workspace-signed APK to preserve app data. Do not uninstall unless you want to erase data. An APK rebuilt with another signing key cannot update the installed app in place.
2. Open JARVIS → **Gemini Live**. Scroll to **Gemini Live settings**.
3. Enter your Google AI Studio API key. Default Live model: `gemini-3.8-live`; default voice: `Kore`. Model/voice can be changed manually to supported names available in your account. A text-only Gemini model will not work for native audio.
4. Tap **Save Gemini settings**. The key is encrypted through Android Keystore and persists after restart. Blank key preserves the saved key. To reuse an existing text-provider key, select a Google Gemini profile in AI Providers and tap **Use selected Gemini provider key** here. The Live model remains independent from the text model.
5. Tap **START**, grant microphone permission, wait for the real Gemini `setupComplete`, then speak. Actual PCM audio is streamed to Google; returned PCM is played natively. The microphone continues listening between responses. No repeated microphone taps are needed during the active session.
6. **Mute** suppresses audio transmission and sends `audioStreamEnd`; **Unmute** resumes input in the same session. The capture engine may remain active while muted, but frames are not sent. **STOP**, leaving the screen, audio focus loss or backgrounding closes audio and the connection. The app does not secretly record in the background.

Deleting the Gemini Live key removes that configuration's credential; imported text-provider keys are separate and can be deleted in AI Providers. No real API key is included in source or APK.

## Free tier and costs

Google's current pricing page lists a free tier for `gemini-3.8-live`, as well as the native-audio preview model listed in the capabilities guide. Your account's eligibility, project billing tier, country, quotas and model availability still apply. The app does not enable billing, guarantee unlimited free usage, automatically switch to OpenAI, or retry quota failures. Check Google AI Studio and the pricing page before use. Default model names can change; edit the model field if Google retires a model.

## Actual implementation

- Native WebSocket: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`.
- Authentication: `x-goog-api-key` header for the user's own API key, as used by Google's native Python GenAI SDK. Secrets are never appended to the URL or logged. Direct connections are pinned to Google's hostname and do not use arbitrary user-configured endpoints.
- Setup: `models/<model>`, generationConfig with responseModalities AUDIO and the chosen voice, input/output transcription enabled, automaticActivityDetection enabled. Recording begins only after an actual setupComplete object.
- Microphone: Android AudioRecord, mono, signed PCM16 little-endian, 16 kHz. Each audio frame is sent as `realtimeInput.audio` with `audio/pcm;rate=16000`. Native AEC/noise suppression is requested when the platform reports support; a headset is recommended for initial testing. There is no second recorder or fallback recognizer running simultaneously.
- Output: `serverContent.modelTurn.parts[].inlineData` is decoded and rendered using Android AudioTrack at 24 kHz. All parts in a message are processed. Bounded playback and network queues fail truthfully rather than growing indefinitely or silently dropping arbitrary audio.
- Server automatic activity detection manages turns. `turnComplete` and `interactionStatus` are actual provider events; input, interim and output transcriptions come only from Google's messages. Thought/internal reasoning content is not displayed or played.
- `serverContent.interrupted` clears queued/current playback using a playback generation barrier; older queued audio cannot resume. This is a provider-signaled interruption, not a fabricated animation.
- Microphone and playback meters measure PCM at capture/write time, not acoustic output. Counters distinguish bytes successfully queued for the socket from audio actually heard by the provider. Neither meter nor queue counters prove live provider comprehension or physical speaker audibility.
- Android communication audio focus and available routes use the existing supported device manager. Device disconnect, capture/playback failure, network loss, expired token and goAway stop the session. There is no automatic billable reconnection. Stop cancels queued network input and closes an otherwise empty socket with a bounded fallback.
- Existing text chat/SSE, provider profiles, camera attachments, history, themes and explicit turn-based fallback remain. The Home voice button is labeled **Fallback voice**; native Gemini is opened through **Gemini Live**.

## Personal keys versus distributed production apps

This is a personal bring-your-own-key client: your key is entered by you and encrypted on your device. It is not a shared developer production key embedded in the APK. Device compromise can still expose client credentials. For distributing a production application with a shared developer account, provision short-lived Gemini tokens on a trusted authenticated backend instead.

A manually provisioned `auth_tokens/...` token can be entered in the key field. The client uses the documented constrained method and `Authorization: Token <token>` header. Token provisioning/login backend is not added in this personal-key change; expired tokens require replacement. The previous OpenAI backend is not used to mint Gemini tokens.

## Build and tests

Use JDK 17 and Android SDK 35:

```sh
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

The source has protocol tests for setup/audio serialization, actual setupComplete readiness, multipart audio and captions, interruption/goAway/processing events, format validation, key-free URLs and ephemeral header selection. Existing Android/provider/audio tests are retained. These do not prove a live connection. No Google API key or Android device was available in this execution environment. See GEMINI-LIVE-VERIFICATION.md for the actual final result.

## Real-device checklist

- Verify a valid API key, model and voice in AI Studio first. Save/restart app and confirm the key stays configured without entering it again.
- Grant/deny microphone permission. START once; observe CONNECTING → SETTING_UP → READY only after Google's setupComplete.
- Speak, hear real Gemini audio, and ask a second question without restarting. Inspect actual partial transcripts, queued/received byte counters and PCM levels.
- Mute while speaking; verify no new microphone frames are transmitted and output can continue. Unmute and speak again.
- Speak during output. Only the server's interrupted event should flush obsolete playback. Confirm old audio does not resume.
- Test speaker, supported headset/Bluetooth routes, phone calls, network loss, microphone permission revocation, screen navigation and backgrounding. Verify audio stops and stale events do not restart it.
- Test STOP during connection, capture and playback, and repeated Start/Stop. Confirm Android's microphone indicator and native resources are released.
- Verify an invalid key, unavailable/text-only model, invalid voice, quota exhaustion and session expiry show errors, with no fallback to a paid OpenAI service.
- Verify normal text/SSE, camera, saved history and the explicit SpeechRecognizer/TTS fallback independently.

## Official sources consulted

Retrieved during implementation on 2026-11-11:
- https://ai.google.dev/gemini-api/docs/live
- https://ai.google.dev/gemini-api/docs/live-guide
- https://ai.google.dev/gemini-api/docs/live-api/get-started-websocket
- https://ai.google.dev/api/live
- https://ai.google.dev/gemini-api/docs/ephemeral-tokens
- https://ai.google.dev/gemini-api/docs/pricing
- https://github.com/googleapis/python-genai/blob/main/google/genai/live.py
- https://github.com/googleapis/python-genai/blob/main/google/genai/_api_client.py

The current Google documentation and SDK use header authentication for native clients. The raw browser tutorial also shows query-string authentication; this Android implementation intentionally uses the supported native headers. Pricing, preview API and model availability may change.
