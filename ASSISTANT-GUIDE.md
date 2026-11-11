# JARVIS 6.0 setup and real-device checklist

## Install safely

Download the new Android APK from `downloads/`. Upgrade using the same development signing certificate; do not uninstall if you want to retain existing local keys/history. A locally rebuilt APK uses your own debug signing key unless you configure the original certificate, which is deliberately not distributed. Android 6/API23+ is the configured minimum; actual OEM behavior is unverified here.

Foreground Gemini still opens from **Home → Gemini Live**. Enter your own accessible Google Live model/voice/key, save, START and grant microphone access. Saving is not a connection test. Quota failures require manual recovery/provider selection; automatic quota-based provider failover is not implemented. The fallback uses an actual external Android recognition engine and Android TTS; it is turn-based and does not support simultaneous listening/speaking or barge-in.

## Tools and approvals

Open **Home → Tools & background**. Tabs: Tools, Contacts, Memory, MCP, Tasks, Background, Diagnostics.

- Prepare a YouTube/web search, HTTPS link, alarm, timer or installed launcher app. Open **Tasks → Review and approve → Approve once**. “LAUNCHED” means an intent/navigation request was dispatched, not that the app loaded a page, played a video or placed/connected a call.
- You can also type/speak explicit shortcuts such as `Search YouTube for Kotlin tutorials`, `Open app YouTube`, `Web search Android`, `Call Baba`, `کال کرو بابا`. Other natural chat remains with the existing provider.
- Enable **Allow Gemini to propose tools** in Tools to advertise documented Live function declarations. Gemini can understand natural requests and submit proposals. It receives an honest `needs_approval` response; it cannot approve or execute actions. Review proposals on the device. Native foreground conversation stops when leaving its screen; the optional background mode is separate.
- Every action requires an unlocked, visible approving screen. Tool IDs and atomic durable transitions prevent duplicate approvals. Cancellation cannot undo a message, call or remote side effect already initiated; outcome can remain unknown and is not replayed.
- Emergency STOP cancels active tool execution, foreground voice and the background microphone service. A bounded, explicitly approved navigation action can temporarily continue while its target app is visible; no general autonomous app control is provided.

## Contacts, SIM and messaging

Grant optional Contacts permission and search. Select a number; enter a recipient alias such as Baba or Staff. Enable local Memory before saving an alias. Stable contact lookup and the saved phone number are rechecked before execution; changed/ambiguous contacts require a new selection.

Phone numbers can be entered manually without Contacts permission. Use international `+countrycode` numbers for WhatsApp. The dialer asks/uses the phone's configured SIM. Optional direct calling requires a separate toggle, CALL_PHONE permission and per-action confirmation. Direct-call intent success is not evidence of connection.

SMS and WhatsApp open supported composers/links; **you tap Send yourself**. JARVIS does not request SEND_SMS or silently send messages. Personal WhatsApp calling, automatic sending and WhatsApp Business cloud-account integration are not implemented.

## Memory and feedback

Enable **local personal memory** to save notes, relationships/preferences and aliases. Inspect, edit/correct or delete each item. Cloud-context sharing is a separate, initially disabled switch. Only up to four relevant notes/preferences (1200 characters maximum) are included; aliases/contact numbers are excluded from implicit context. Native setup includes permitted preferences; text chat can include notes matching the current query.

Common credential labels/patterns are rejected; never paste passwords, keys or tokens into memory or tool descriptions. Corrections update local preferences, not model weights. There is no self-training or automatic code modification. Local feature data and task arguments/results are private app SQLite data but are not separately encrypted. Keys use the existing Android Keystore encryption. Backups remain disabled. Root/device compromise can expose local data.

Use **Tasks → Clear finished task records** to remove retained arguments/results for finished tasks. Pending/executing tasks remain; cancel pending items explicitly. The ledger retains at most 100 recent tasks and local memory at most 200 items. No task is scheduled or replayed automatically after process death.

## Background “Jarvis” mode

1. Configure your Google Gemini Live key first.
2. Obtain your own Picovoice AccessKey from https://console.picovoice.ai/ and review current account/license/product terms. The bundled binding license/notice is under `third_party/` and in APK assets. This project does not promise unlimited free usage or offline license activation.
3. Background tab → save the Picovoice key. The actual built-in keyword is **Jarvis**; saying “Hi Jarvis” contains it. A custom exact phrase/model is not included.
4. Tap Enable, read/accept the disclosure, and grant microphone plus notification permissions. The app must be visible when starting the microphone foreground service.
5. A persistent private notification shows local wake/session state. While ARMED, keyword inference is local and ambient audio is not sent to Gemini. On a detection, the wake recorder stops/releases before the configured real Gemini session starts. Subsequent conversation audio goes to Google under its account/quota rules.
6. Use the notification or Background tab to pause, resume wake detection, mute/unmute a ready Gemini session, choose an available communication output, or STOP. Home also shows the actual background state and offers Stop background.
7. Errors, focus/call interruptions and provider expiry do not automatically reconnect or repeatedly create billable sessions. Resume explicitly. No boot receiver, sticky restart or silent post-process-death activation exists. Keyword detection is not biometric speaker authentication; any detected matching utterance can activate the explicitly enabled session. Swiping the app task away stops the service.

A service explicitly started while visible can continue while the screen is off/locked where Android/OEM policies permit. This is not a promise of 24/7 operation, zero battery use or hardware DSP hotword access. Background memory/contacts/tool requests still cannot authorize sensitive actions: unlock and review them.

**Choose default Android assistant** opens the platform role/selection UI. It only gives a supported invocation path to visible JARVIS. It does not bypass locking. The optional recognition delegate only operates while JARVIS is visible and separately enabled, and forwards to an installed external engine. If a suitable engine is unavailable, configure Android speech services. Device validation is required.

## Optional accessibility navigation

Enable only if you want deterministic scroll/Back in YouTube or Chrome. First enable its per-app switch, review the disclosure, then manually enable JARVIS in Android Accessibility settings. Sideloaded apps may also require Android's manual restricted-settings approval. JARVIS cannot grant this itself.

Only one approved forward/backward scroll or Back is dispatched after checking the target foreground package and unlocked state, within a roughly two-second wait and a 200-node scan bound. The service does not upload text, click arbitrary elements, type messages, approve permissions, or bypass authentication. A changed/unavailable app or non-scrollable UI fails visibly. Platform/store policy eligibility must be reviewed for your distribution.

## MCP

Add a trusted **HTTPS Streamable HTTP** endpoint on port 443, with no query/fragment/embedded credentials. Public endpoints or your manually configured bearer token are supported. Saving leaves the server disabled and UNKNOWN. Test/discover performs actual initialize → initialized notification → tools/list and attempts DELETE cleanup; it does not execute a tool.

Enable the server and individually allow selected tools. The server description and input schema are displayed as untrusted data. Enter a JSON object matching the supported schema subset, prepare the call, then review the destination/tool/arguments in Tasks. Invocation initializes a real session, refreshes definitions, validates arguments and calls the allowed tool once. Results are actual server results, not independent proof of external effects.

Changing an endpoint invalidates old proposals and requires a replacement credential for the edited profile. Configuration/key replacement is atomic in the feature preferences. Credentials are never put in the endpoint URL or forwarded on redirects. MCP output cannot grant device approval, change server permissions or obtain provider secrets.

Supported negotiated versions: 2025-11-25 and 2025-06-18. No OAuth login, stdio, legacy HTTP+SSE transport, unsolicited sampling/elicitation, resumable reconnect, or complex JSON Schema/combinator support is claimed. No automatic retry of potentially consequential calls. Revoking server/tool permission or changing/removing its configuration cancels the active MCP operation; authorization is checked again immediately before tools/call. Already initiated external effects cannot be undone by cancellation. MCP alone grants no Android permissions. A server can decline DELETE; then closure is reported unconfirmed and the server controls expiry.

## Device/account verification — all currently NOT TESTED here

Record device/Android/app version and model/voice without credentials, raw audio or private transcripts.

| Check | Procedure |
|---|---|
| Upgrade/process restart | Install over matching APK; verify original providers, keys, history/theme and new memory/aliases. Restart process; confirm no recording auto-starts and interrupted tools are not replayed. |
| Foreground voice | START once, actual setupComplete, ask three questions, hear genuine audio, interrupt active/queued output, mute/unmute rapidly, STOP during setup/playback. |
| Wake licensing | Test valid, missing and invalid Picovoice key; verify actual local detection and no ambient frames to Google before wake. Confirm one microphone owner during handoff. |
| Background permissions | Deny/revoke mic/notifications; test background starts, pause/resume/STOP, task swipe and process kill. Confirm OS mic indicator/resources are released. |
| Lock screen | Enable while visible, lock phone, test supported wake/audio. A call/message/tool approval must remain blocked until unlock. Never attempt bypass. |
| Focus/routes | Phone/VoIP calls, Bluetooth/wired/USB disconnects, speaker route changes, provider expiry and network loss. Confirm explicit recovery and no billable reconnect loop. |
| Tools | Open installed/ambiguous/missing apps; search real YouTube; inspect browser/clock intents and accurate LAUNCHED results. |
| Communication | Use only explicitly authorized test recipients/numbers. Verify dialer and optional direct-call behavior, dual-SIM handling, SMS/WhatsApp composer without automatic sending. |
| Contacts/memory | Deny contacts, select duplicate names, edit contact number, test alias revalidation; enable/disable both memory consents, correct/delete notes, ensure aliases are excluded from implicit context. |
| MCP | With your test server/account, test real initialization/discovery, per-tool approval, valid/invalid schema, auth failure, cancellation, redirect rejection, destination edits and untrusted descriptions/results. No destructive live calls during tests. |
| Accessibility | Enable manually for allowed apps only; test scrollable/non-scrollable UI, wrong app, overlay, lock, service disconnect, cancellation and no arbitrary clicks. |
| Assistant role | Select JARVIS, invoke through the supported system gesture/button; test installed recognition/TTS engines, no recursion, and visibility restrictions. |
| UI/accessibility | Small phones, keyboard, font scaling, TalkBack/labels, RTL content and system animations off. |
| Performance | Profile CPU, memory and battery over long wake/conversation sessions; measure native stop latency and check for Main-thread stalls. No performance certification has been performed. |

Build with JDK17/SDK35:
`./gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest`

With a device: `./gradlew connectedDebugAndroidTest`. The compiled tests include navigation, isolated real Keystore and feature SQLite persistence, but compilation is not execution. Retained legacy backend fixtures: `cd backend && npm test`; this is not a Gemini token backend.
