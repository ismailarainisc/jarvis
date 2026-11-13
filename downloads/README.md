# JARVIS 7.3 — real owned timers

- [Android APK](Jarvis-Gemini-Live.apk?raw=true)
- [Android instrumentation APK](Jarvis-7.3-androidTest.apk?raw=true)
- [Complete source ZIP](Jarvis-Gemini-Live-Source.zip?raw=true)
- [Checksums](SHA256SUMS)
- [Timer implementation, configuration and actual-device checklist](../TIMERS-GUIDE.md)
- [Actual build/test/lint results](../verification/owned-timers/)

Package com.raaz.assistant, versionCode 13 / 7.3-owned-timers. Upgrades preserve the previous development signing certificate and private settings; no credentials/private keys are included. Source contains the Gradle wrapper, implementation, resources, tests and evidence, excluding private configuration/caches/recursive binaries.

Native Gemini voice stays on its shared controller. Enable Assistant tools for direct voice timer functions. Grant relevant notifications explicitly. Android 12+ precise alarms are optional, user-enabled special access; inexact mode clearly warns of background delay. Only timer operations bypass the existing action-approval flow; sensitive actions remain gated. One timer is supported. See the guide for reboot, force-stop, DND, notification and exact-alarm restrictions.

Compiled instrumentation is not device testing. No real Android device/emulator or Gemini credentials were available. No genuine voice, audible notification or external Clock effect has been verified. This update is a separate PR stacked on PR #4; main is not overwritten. Older APKs/reports are historical; current links refer to the branch/commit being viewed.
