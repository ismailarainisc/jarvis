# Home/native voice audit — 2026-11-12

Baseline inspected: `17160996dc4732d80fb0c2897eaa3885565b3d69` on main. This change is developed on `feat/unified-gemini-home`; main is not overwritten. Earlier assistant/reliability reports are historical evidence, not proof of this phase.

## Confirmed source defects

| Finding | Evidence before change | Correction |
|---|---|---|
| Home used a different audio path | MainActivity Home listener invoked VoiceSessionManager/SpeechRecognizer; answers used Android TTS. Native Gemini start was only in GeminiLiveScreen. | HomeVoiceCoordinator defaults to GEMINI_LIVE; MainActivity routes Home and advanced voice commands through GeminiHomeAdapter to the existing controller. |
| Native session lifetime belonged to one screen | GeminiLiveScreen DisposableEffect set screen-visible false when leaving that page. | Root navigation owns one voice workspace (Home + Voice settings). Advanced settings has no microphone disposal hook. |
| Device TTS could initialize independently of native Home preference | Activity created fallback voice machinery for the Home path and text reply callback used it directly. | Lazy fallback creation and explicit-mode, lifecycle, native-session and background-owner guards for all device speech. |
| Default voice choice was unclear and configuration was detached from Home | Home exposed fallback controls alongside a separate Gemini route. | Persistent additive home_voice_mode, native setup action, shared saved voice/model metadata and documented voice examples with editable custom names. |
| Delayed permission or text callbacks could target the wrong foreground intent | Permission/start actions were screen-specific and device speech callbacks lacked shared mode ownership. | Permission tickets and command epochs; cancel text on active voice Stop/background; fallback generation plus mode/lifecycle checks. |
| Home navigation/primary control hierarchy was crowded | Basic Home exposed multiple destinations in one horizontal scrolling row. | Scrollable content with persistent primary controls, 2×2 navigation cards and separate text composer. |

The existing GeminiSession, protocol, PCM capture/playback and interruption implementation are reused, not replaced. Current official Google Live guidance and speech-generation voice catalog were consulted. Live/account behavior still needs genuine provider/device verification.

During final implementation review, an empty-history error-visibility bug in the new Home was found and corrected; settings labels now wrap without pushing the switch beyond the row. Lazy-list UI tests scroll the list to a matching node before lookup. These are covered by final recompilation/lint and compiled UI regressions; device execution remains pending.

## Preserved storage and architecture

Package `com.raaz.assistant`; existing settings file, provider profiles, Keystore alias, Google credential, history and additive feature database remain intact. No database schema change or destructive migration is introduced. New mode preference defaults to native only when absent/unrecognized; saved native voice is never replaced by an example. Existing background service retains its own explicit activation and audio lease; foreground is blocked while it runs or its start authorization is pending.

## Official references

- https://ai.google.dev/gemini-api/docs/live-guide
- https://ai.google.dev/api/live
- https://ai.google.dev/gemini-api/docs/speech-generation

The Live guide links native voices to the speech-generation catalog. Kore, Puck, Charon, Fenrir and Aoede are documented examples, not a claim that every model/account accepts them. No native protocol changes or fabricated events were needed for this Home integration.
