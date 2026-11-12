# JARVIS 7.0 implementation audit

Baseline: main `e4a33167c2c899dbdb01151c7e8871fc0bcf0813` (merged JARVIS 6.1). Inspected MainActivity, native Gemini controller/session/audio/protocol/bridge, shared Home coordinator, settings/credential storage, tools/resolver, task coordinator/ledger, feature store, scoped accessibility, manifest, Compose navigation/Hub, existing regression suites and prior reports. Changes are on feat/mobile-action-engine-7; main is not overwritten.

Confirmed source gaps before changes:
- Clock arguments used int conversion before bounding; oversized integers could wrap into apparently valid timers/alarms. Validation now uses exact decimal-to-long conversion before range checking.
- Native declarations mixed standard JSON Schema with the enum-based parameters field. The official discovery schema documents parametersJsonSchema for raw schemas and scheduling as a top-level FunctionResponse field; serialization now follows that contract.
- geo URI query-building erased the opaque coordinates. Maps search now uses the documented geo:0,0?q= form with encoded query and regression checks.
- Capacity count+insert was not atomic across database helpers; insertion/retention now share a SQLite transaction. Native approval expiry is also checked from the durable creation timestamp after process death.
- No first-launch setup wizard or resumable setup progress; optional grants were scattered across feature screens.
- Gemini tool proposals immediately returned needs_approval and never returned the eventual approved outcome.
- Tasks/Control navigation marked the native voice surface invisible, terminating the session during approvals.
- App search was only a UI filter; no structured device-status, maps, approved contact lookup or memory-read/delete tools existed.
- Task recovery labelled interrupted execution FAILED despite an unknown external outcome; cancellation after execution claim also used CANCELLED despite uncertainty.
- Task pruning removed every record beyond the most recent 100, including pending/executing approvals. New retention preserves bounded pending work and caps finished history separately.
- Permission/preparation failures disappeared into a Hub error rather than a durable task record.
- A final regression test caught overly broad forwarding of device-status detail text. Cloud-visible snapshots are now restricted to the exact non-identifying battery/network formatter; arbitrary/private/malformed detail text is withheld.
- Accessibility traversal had a visit limit but could queue substantially more nodes; revocation was first checked after target launch.

The native engine and encrypted key formats, application ID, profiles/history/memory/MCP/background wake modules are retained. No database schema change or destructive migration is necessary for additive task status strings; legacy COMPLETED/UNSUPPORTED remain readable and are explicitly labelled rather than retroactively treated as verified.

Official sources consulted 2026-11-12:
- https://ai.google.dev/gemini-api/docs/live-tools (documented NON_BLOCKING behavior and response scheduling; async explicitly unavailable for gemini-3.1-flash-live-preview).
- https://generativelanguage.googleapis.com/$discovery/rest?version=v1beta (FunctionDeclaration/FunctionResponse formal schema; raw parametersJsonSchema and top-level scheduling).
- https://developer.android.com/guide/components/intents-common
- https://developer.android.com/training/permissions/requesting
- https://support.google.com/googleplay/android-developer/answer/10964491

Async declarations are enabled only for exact gemini-3.8-live and gemini-3.8-live-extended-thinking IDs explicitly documented in the current guide. Other model IDs retain ordinary sequential function calling; continuous conversation while awaiting approval is not promised there. No Google Maps grounding API is claimed: maps tools dispatch documented geo/HTTPS links only. Accessibility remains one user-approved Back/Scroll, with no autonomous planning/clicking or permission/security bypass. Policy compliance and store approval require review for the actual distributed product; this source change does not claim approval.

The Live guide scheduling example nests the value in response data, while the formal FunctionResponse schema declares it top-level. The implementation uses the formal schema and tests the exact wire shape; live account verification is still NOT TESTED. Registry JSON Schema is copied into the documented mutually exclusive parametersJsonSchema field, without mutating local/MCP schemas.
