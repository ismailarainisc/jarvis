> Historical 5.1 snapshot. The current 6.0 source, capability limits and actual evidence are in [ASSISTANT-VERIFICATION.md](ASSISTANT-VERIFICATION.md) and [ASSISTANT-GUIDE.md](ASSISTANT-GUIDE.md). Foreground voice still stops with its screen; the new opt-in background service is separate.

# Configure and verify JARVIS 5.1

## Installation and personal Google configuration

1. Download `downloads/Jarvis-Gemini-Live.apk`. Install on an Android phone (Android 6/API 23 or later), allowing installation from that download source if Android requests it. This is an APK, not an iPhone application.
2. Upgrade over the previous matching workspace-signed APK to preserve settings. Never uninstall as a migration workaround unless you accept losing local data. An unrelated signing certificate cannot upgrade in place.
3. Open JARVIS → **Gemini Live**. Enter a Google AI Studio credential, a **Live/audio model** accessible to your project, and a supported voice. Default model `gemini-3.8-live`, voice `Kore`. Provider access must be confirmed with Google; entering a name does not create access.
4. Tap **Save Gemini settings**. Blank credential keeps the saved credential. To reuse an existing Google text profile, select it under Providers, return to Gemini Live and tap **Use selected Gemini provider key**. The import only accepts Google's host. Neither action tests a connection.
5. Tap **START**, grant microphone permission and wait for **READY/LISTENING**. Actual setup confirmation starts native capture. If an error appears, correct key/model/voice/network and manually start again. No automatic quota-consuming reconnect is performed.
6. Speak. Native Gemini uses AudioRecord → Google WebSocket → AudioTrack, not Android TTS. Transcripts are shown only when Google supplies them. Input/output energy indicates PCM activity, not guaranteed speech recognition or physical audibility.
7. Tap **Mute** to stop transmitting new microphone frames. Capture/discard can remain active so Android's mic indicator may remain visible. `audioStreamEnd` tells Google's automatic VAD that input ended. Packets already delivered cannot be recalled; excessive socket backlog stops the session rather than claiming it was muted. Unmute reopens input in the same session.
8. Tap **STOP** and wait for **STOPPED**. Native stop/release happens off Main; START stays disabled while disconnecting. A cleanup failure blocks microphone reuse and asks you to restart the application.

No secret needs to be sent to the developer. Do not paste credentials into issues, screenshots or logs. Shared developer keys must never be distributed in an APK. Manually provisioned short-lived Google `auth_tokens/...` credentials are accepted via the documented constrained endpoint; token provisioning/backend login is not added here.

## Reproducible device acceptance matrix

Record phone/Android build, app version, Google model and voice, and observations **without keys/audio/private transcripts**. Mark each row PASS/FAIL only after running it. All rows below are currently NOT TESTED on hardware.

| Check | Procedure and required evidence |
|---|---|
| Upgrade/data | Install with `adb install -r`; verify prior profiles, history, theme and encrypted credentials still load. Cold-start twice. |
| Permission denied | Deny RECORD_AUDIO, START, confirm actionable error and no socket/capture; grant and retry. |
| Real session | START once; confirm Google setupComplete before AudioRecord activation; do not equate saved key/socket bytes with inference. |
| Two-way audio | Speak a question, hear actual Gemini audio, inspect provider transcript if available; repeat for at least three turns without START again. |
| Interruption | During active speech AND while several output chunks are buffered, speak again. Confirm Google's interrupted event count increases, old audio flushes, and the new turn can play. |
| Mute races | Mute/unmute rapidly; mute while output plays and while setup connects. Verify new transmitted PCM counters stop during mute (already queued packets may have delivered), then safely resume. |
| Stop races | Stop during connect, setup, native startup, capture and playback; repeatedly START/STOP. Confirm microphone indicator disappears and obsolete audio/events never reactivate it. |
| Output routes | Switch speaker/earpiece, wired/USB and supported Bluetooth. Disconnect the selected headset, check truthful stop/error or system fallback notice. Test Android 6–11 and Android 12+ routing separately. |
| Focus/calls | Receive a phone call or interrupt audio focus; verify session stops, resources release and a manual restart is required. |
| Lifecycle | Background, switch screen, lock screen, rotate, destroy/recreate Activity and kill process. No undisclosed mic survives; recreated UI does not auto-start recording. |
| Revoked access | Revoke mic permission or disable system microphone access while active; confirm capture error/stop without reactivation. |
| Network | Disable internet during setup/playback/capture. Observe truthful error, no repeated sessions or automatic billable retries. Restore manually. |
| Provider failures | Use invalid key, inaccessible/text-only model and invalid voice; check useful error with no credential in logs. Test quota/session expiry with legitimate account conditions. |
| No-AEC hardware | Use headphones if echo cancellation is unsupported; ensure notice appears and feedback does not self-trigger. |
| Fallback | Stop native mode, open fallback, speak/receive Android TTS/ask second question; no simultaneous native recorder. |
| Text/SSE | Send a real text request, observe incremental server chunks, Stop during response, retry manually; incomplete response must not be saved/spoken. Test Gemini/NVIDIA with accessible models. |
| Camera/history | Attach a photo to a supported vision text model; check history, clear confirmation, provider select/edit/remove, theme persistence. |
| Accessibility | Large font, small screen, Urdu/Arabic RTL, screen reader, keyboard, system animations disabled. Controls remain operable and no random waveform is shown. |

Run compiled instrumentation with `./gradlew connectedDebugAndroidTest`. It includes navigation and isolated real Android Keystore tests; execution requires an attached device/emulator. Add real-device profiling (Main-thread traces, CPU/battery and memory over a long session) before a production release.

## Backend

Gemini personal-key native mode does not use `backend/`. The unchanged legacy OpenAI backend and its configuration instructions remain in `LIVE-SETUP.md`; its isolated Node tests are separate regression evidence, not Gemini verification. Do not configure the old backend to pretend it mints Google tokens.
