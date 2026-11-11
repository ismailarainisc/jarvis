# JARVIS 6.0 — verified development downloads

- [Android APK](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live.apk)
- [Complete Android Studio source ZIP](https://github.com/ismailarainisc/jarvis/raw/refs/heads/main/downloads/Jarvis-Gemini-Live-Source.zip)
- [SHA-256 checksums](SHA256SUMS)
- [Actual implementation/evidence and unsupported capabilities](../ASSISTANT-VERIFICATION.md)
- [Setup, permissions, privacy and real-device checklist](../ASSISTANT-GUIDE.md)

VersionCode8 / 6.0-assistant / com.raaz.assistant. Development APK signed with the previous workspace certificate; private signing key is excluded. Clean debug/test-APK build passed; 177 JVM tests passed; lint 0 errors / 3 dependency-update warnings; unchanged backend fixtures 9/9 passed.

New source implements tools, contact aliases, approved calls/composers, memory, MCP, background wake handoff, assistant-role foundation, scoped navigation and diagnostics. Google/Picovoice, real devices, calls/messages, accessibility and external MCP verification remain NOT TESTED. Wake mode requires your own Picovoice AccessKey/license/activation plus Google configuration. No universal app control, automatic personal WhatsApp/SMS sending, lock bypass or production-readiness claim.

The source ZIP excludes generated builds and the large APK/ZIP download files, while retaining source, wrapper, tests, backend and evidence.
