# JARVIS 7.2 — holographic Home

- [Android app APK](Jarvis-Gemini-Live.apk?raw=true)
- [Instrumentation APK](Jarvis-7.2-androidTest.apk?raw=true)
- [Complete source ZIP](Jarvis-Gemini-Live-Source.zip?raw=true)
- [SHA-256 checksums](SHA256SUMS)
- [Current Home implementation and verification](../HOME-DESIGN-REPORT.md)
- [Approved design reference](../design/approved-home-reference.png)
- [Build/test/lint evidence](../verification/holographic-home/)

Package com.raaz.assistant; versionCode 12 / 7.2-holographic-home. The current Home uses functional Compose controls, a separate decorative holographic face asset and actual existing session state. The approved reference is a concept image, not proof of actual rendering. No fake conversations/connection success are inserted. Native Gemini and explicit fallback retain their shared coordinator and stored settings.

App and instrumentation APK use the previous development certificate; the private key is excluded. Only compilation/signature/automated JVM checks were possible. No attached Android device/emulator or legitimate Gemini credentials were available: visual pixel matching, instrumentation execution, real microphone/playback and live Gemini remain NOT TESTED.

This branch is stacked on PR #3 (7.1), itself based on PR #2 (7.0); main is unchanged. Relative downloads correspond to the branch/commit you are viewing. Full source includes wrapper/resources/tests/backend and evidence, excluding credentials, local settings, caches and recursive APK/ZIP downloads. Older 7.1 instrumentation remains a historical artifact; install the current 7.2 pair for these tests.
