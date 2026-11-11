# Phase 04A — GPT-Live WebRTC connection foundation

The existing Android app remains `com.raaz.assistant`. GPT-Live has its own foreground screen (Home → GPT-Live); fallback voice and text-provider credentials are independent. Enter the authenticated backend endpoint, save the backend session token, press START and allow microphone permission. Both WebRTC transport connection and a matching `session.started` event are required for SESSION_READY. SDP success alone never means connected. A Stop sends `session.close` when the data channel is open, mutes capture, and waits up to 15 seconds for `session.closed`; timeout closes locally with a truthful finalization warning. Leaving the screen or backgrounding the app immediately releases local resources. Such emergency cleanup cannot guarantee provider finalization.

## Protocol and dependencies

Official sources were retrieved successfully on 2026-11-11:
- https://developers.openai.com/api/docs/guides/voice-webrtc
- https://developers.openai.com/api/docs/guides/live
- https://developers.openai.com/api/reference/resources/live/methods/create

This integration exclusively uses GPT-Live: POST https://api.openai.com/v1/live/sessions with JSON `{session:{model:"gpt-live-1",instructions:...},transport:{type:"webrtc",sdp:offer}}`. The validated response is reduced to `{sessionId,sdp}` for Android. A native `oai-events` data channel receives `session.started` and `session.closed`; no `session.start` or legacy Realtime events are sent. Session creation may incur initialization charges (the guide describes 15 seconds credited toward a started session). There are no automatic billable retries. No delegation or tool handling is implemented in this connection-only phase.

Android uses the maintained `io.github.webrtc-sdk:android:150.7871.04` package (`org.webrtc`), JavaAudioDeviceModule, unified-plan PeerConnection and an audio-only media track. WebRTC handles negotiated audio formats, native capture/playback, jitter and echo/noise processing. Hardware echo cancellation/noise suppression is requested where supported. Microphone permission and Android audio focus are required. Routing follows Android's communication audio route; explicit device selection and barge-in controls are not implemented. Native media behavior still requires real-device verification.

## Backend configuration

Requires Node.js >=22.6, no third-party Node dependencies. Run `cd backend && npm test` for isolated tests. For startup set:

- `OPENAI_API_KEY`: project API key with GPT-Live access, only on the server.
- `MOBILE_SESSIONS_JSON`: JSON object mapping SHA-256 hashes of operator-issued, random per-user bearer tokens to `{ "user": "your-user-id", "expiresAt": <expiry in epoch milliseconds> }`.
- `HOST`: default `127.0.0.1`; retain loopback behind an HTTPS reverse proxy.
- `PORT`: default `3000`.

Provision a random token through your trusted operator or account system. Example administrator command (outputs the token once; treat that output as a secret):

```sh
node --input-type=module - <<'JS'
import {randomBytes,createHash} from 'node:crypto';
const token=randomBytes(32).toString('base64url');
const hash=createHash('sha256').update(token).digest('hex');
console.log('User token (enter only in Android):',token);
console.log('MOBILE_SESSIONS_JSON:',JSON.stringify({[hash]:{user:'personal-user',expiresAt:Date.now()+3600000}}));
JS
```

Load the resulting hash map and OpenAI key through the hosting platform's secret manager/environment. Start with `npm start`. A public HTTPS reverse proxy should forward `/api/live/sessions` to this service, enforce body/connection limits and disable request-body and Authorization logging. Android endpoint: `https://YOUR-HOST/api/live/sessions`. Android stores only the separately provisioned backend bearer token, encrypted with its existing Android Keystore key. Do not enter an OpenAI key in this screen. The endpoint is restricted to HTTPS without URL credentials, query strings or fragments; redirects are disabled. Only connect to your trusted backend.

Tokens expire and can be revoked by removing their hashes and restarting the server. These are operator-provisioned per-user sessions, not a shared APK secret or a full account/login product. Production multi-user deployment should connect provisioning/revocation to your identity system. Rate limits are three attempts per minute per authenticated user in this single process; use shared rate limiting for multiple instances. The server pins the OpenAI destination and model; mobile requests cannot choose a destination or inject session configuration. `/health` returns no credentials or provider status. Errors expose sanitized status only, never upstream bodies, tokens or SDP. No application request logging is enabled.

## Android build and tests

Install JDK 17, Android SDK 35, then `./gradlew assembleDebug testDebugUnitTest`. Instrumented navigation tests can be compiled/run using `./gradlew assembleDebugAndroidTest` / `./gradlew connectedDebugAndroidTest`. No emulator or physical device is attached here. Signing must match your installed APK for an in-place upgrade; do not uninstall if you need existing local history and credentials. The separately delivered APK is signed with this workspace's previous development signing key; that key is excluded from source archives.

## Real-device checklist

1. First verify backend health over HTTPS and authenticate with your per-user token.
2. START with denied microphone permission: expect an actionable error and no session creation.
3. START with permission and valid backend token: observe CONNECTING → NEGOTIATING. Verify the backend creates exactly one provider session.
4. With an actual GPT-Live-enabled account, verify SDP answer, WebRTC connection, matching `session.started`, and SESSION_READY. Neither this account nor such a response is available here.
5. Verify real microphone capture and remote audio using a phone, headset, and Bluetooth routes supported by the device. These are not proven by compilation.
6. STOP at every stage; verify DISCONNECTING followed by local DISCONNECTED, with `session.closed` or a timeout warning. Check Android's microphone indicator and native resource release.
7. Repeat Start/Stop rapidly; verify old callbacks do not change a newer session. Leave the screen and background the app; verify immediate local cleanup.
8. Disconnect the network, reject the backend token, expire it, revoke GPT-Live access and interrupt audio focus. Expect truthful ERROR and released local resources, no automatic retry.
9. Confirm text profiles, encrypted credentials, SSE chat, camera, history and foreground fallback voice on device.

This phase has no automatic reconnect, live transcript display, playback meters, interruption protocol, conversation-history synchronization, backend login UI or background audio service. Third-party text providers (Gemini compatibility, NVIDIA, custom Chat Completions) remain supported for their existing text/fallback flows and are not advertised as GPT-Live providers.
