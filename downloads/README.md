# JARVIS 7.0 — Android action engine development downloads

- [Android development APK](Jarvis-Gemini-Live.apk?raw=true)
- [Complete Android Studio source ZIP](Jarvis-Gemini-Live-Source.zip?raw=true)
- [SHA-256 checksums](SHA256SUMS)
- [Current implementation, changed files and evidence](../ACTION-ENGINE-REPORT.md)
- [Setup, capabilities and physical-device checklist](../ACTION-ENGINE-GUIDE.md)
- [Audit and official protocol/platform references](../ACTION-ENGINE-AUDIT.md)
- [Actual build/test/lint reports](../verification/actions-7/)

VersionCode 10 / 7.0-actions / com.raaz.assistant. Matching previous workspace development signing certificate; private signing key excluded. Actual final clean-build, JVM and lint counts are recorded in verification/actions-7/summary.json.

This build adds real first-run setup, explicit permissions, typed Android actions, durable approvals, native Gemini tool outcomes and Control Center. Native Google voice, Home/session, existing profiles/encrypted keys, local history/memory, SSE, camera, MCP and opt-in wake remain intact. No universal phone control or production-readiness claim. Android instrumentation, physical audio/actions and genuine Gemini/external account tests are NOT TESTED in this workspace.

Links resolve to the branch/commit being viewed. The 7.0 update is submitted on feat/mobile-action-engine-7 for PR review; main remains on its previous version until merged. The complete source archive excludes build caches, private configuration/keys and recursive download artifacts; it includes Gradle wrapper, source/resources, tests, retained backend and evidence. Gradle resolves third-party dependencies during import/build.
