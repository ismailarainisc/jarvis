# Android downloads — JARVIS 5.1 reliability

- [Download Gemini Live APK](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live.apk)
- [Download complete Android Studio source ZIP](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live-Source.zip)
- [SHA-256 checksums](SHA256SUMS)
- [Actual build/test evidence and remaining limitations](../RELIABILITY-REPORT.md)
- [Setup and real-device checklist](../RELIABILITY-GUIDE.md)

VersionCode 7, versionName 5.1-reliability, package com.raaz.assistant. Development APK signed with the previous workspace development certificate; private signing key is excluded. The clean debug/test-APK build and all 116 JVM tests passed; retained backend fixtures 9/9 passed. Real Android audio, instrumentation execution, upgrades and live Gemini provider verification remain NOT TESTED because no device/emulator or Google credential was available.

Native mode requires your own Google AI Studio credential, supported Live model/voice and account quota. Saving a key is not a successful connection test. Source ZIP excludes generated builds, local secrets and the large APK/ZIP download files, avoiding nested archives; all source, wrapper, tests and evidence are included.
