# JARVIS 6.1 — native Home development downloads

- [Android APK](Jarvis-Gemini-Live.apk?raw=true)
- [Complete Android Studio source ZIP](Jarvis-Gemini-Live-Source.zip?raw=true)
- [SHA-256 checksums](SHA256SUMS)
- [Current native Home implementation/evidence and limitations](../HOME-VOICE-REPORT.md)
- [Setup and actual-device checklist](../HOME-VOICE-GUIDE.md)
- [Preserved assistant capabilities and previous phase evidence](../ASSISTANT-VERIFICATION.md)

VersionCode9 / 6.1-native-home / com.raaz.assistant. Development APK signed with the previous workspace certificate; private signing key excluded. Exact final test/lint results are in ../verification/home-voice/summary.json and ../HOME-VOICE-REPORT.md.

Home START defaults to the existing native Gemini Live engine; advanced voice settings shares the same foreground session. Android SpeechRecognizer/TTS is a separately selected fallback. No actual Gemini connection or Android-device audio verification occurred: no credential, emulator or phone was available. No production-readiness claim.

These relative links download the files from the branch/commit being viewed. This update is submitted on feat/unified-gemini-home for PR review; main retains its previous release until merged. Source excludes generated builds, credentials, private signing keys and recursive APK/ZIP download files.
