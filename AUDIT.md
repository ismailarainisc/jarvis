# Source audit

The supplied ZIP contains one Java Activity, a manifest, empty resource directories and an outdated Urdu README. There are no Gradle files, tests, or Android Studio project. The README describes an older relationship/calling app; those features are not present in the Java source.

The actual Java code contains an OpenAI-compatible non-streaming chat HTTP request, in-memory conversation, Android speech recognition activity, device text-to-speech, camera thumbnail attachment, an animated Canvas face, and settings persisted in SharedPreferences. Its AES/GCM credential uses Android Keystore alias `jarvis_key` and preference file `settings`. These behaviors were reviewed in source, not verified against a real provider or device. There is no MCP, continuous voice, audio level, wake word, autonomous tools, persistent conversation or model self-learning.

The old app targets SDK 28, uses fixed pixel sizes, deprecated activity-result handling, has no durable chat, and uses a single Activity for all concerns. It has HTTPS enabled, disallows backup and holds no hardcoded provider secret. It does not constrain redirects when sending credentials. The replacement disables redirects and maintains package ID and credential format. Upgrade requires a matching signing key; an independently signed build cannot update an existing installation. Do not uninstall if you need existing app data.

The speech recognition and camera use external Android activities; availability, permission UX and recognition accuracy depend on the user's device. Only the camera thumbnail is transferred. Chat history is now saved locally in app-private preferences with backup disabled. User messages may be sent to the configured provider; an attached image is included with the next request.
