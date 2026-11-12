# JARVIS 7.1 — development and instrumentation artifacts

- [Android app APK](Jarvis-Gemini-Live.apk?raw=true)
- [Android instrumentation APK](Jarvis-7.1-androidTest.apk?raw=true)
- [Complete Android Studio source ZIP](Jarvis-Gemini-Live-Source.zip?raw=true)
- [SHA-256 checksums](SHA256SUMS)
- [Current fixes and evidence](../RELIABILITY-71-REPORT.md)
- [Updated setup, exact ADB commands and device checklist](../RELIABILITY-71-GUIDE.md)
- [Source/PR audit](../RELIABILITY-71-AUDIT.md)
- [Actual build/test/lint logs](../verification/reliability-71/)

App versionCode 11 / 7.1-reliability / com.raaz.assistant. App and test APK share the previous workspace development certificate; private key excluded. Instrumentation package com.raaz.assistant.test targets the app through androidx.test.runner.AndroidJUnitRunner. Compilation is not instrumentation execution.

PR #2 (7.0) was still open at inspection. This 7.1 update is a separate stacked pull request against its branch; main is not overwritten. Links resolve to the branch/commit being viewed. Current exact counts/results are in verification/reliability-71/summary.json.

No phone/emulator, KVM device, legitimate Gemini/Picovoice credential or external account was available. No real calls/messages were sent. No complete production readiness or universal phone-control claim. The source archive contains the project, wrapper, tests, backend and evidence; Gradle resolves dependencies. It excludes private configuration/keys, caches and recursive APK/ZIP downloads.
