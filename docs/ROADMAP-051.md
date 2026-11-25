# GameLingo 0.5.1 — consolidated improvement cycle

Base: e0afff217849a58843bf751d16339ca63bbbc53c. Previous CI 45621740397: all eight jobs passed. Agency Roster guidance: code review, CI/CD, security, networking, privacy, performance, UX, test engineering, software architecture. No external AI API.

| Proposal | Current implementation / remaining gate |
|---|---|
| Central version | shared/version.json, sync_version.py, CI consistency test, iOS version 0.5.1 build 6, Android independent release counter range, matching artifact names; manual exact-commit publication only |
| Encrypted pairing | Not implemented. Existing UDP HMAC is integrity only; bootstrap exposes PIN/key. Require standard authenticated transport and independently reviewed pairing; no ad hoc crypto, no silent downgrade. Existing LAN-only restriction remains. Added explicit unencrypted-transport notices and bounded Android port discovery / source-port checks. This is hardening, not encryption. |
| Connection simplification | iOS diagnostics distinguish paired/verified, first frame and troubleshooting; test caption disabled without verified receiver. Existing onboarding retained. |
| Real latency | Added iOS decoder-arrival-to-output-handoff measurements for both lanes, including queue time. Existing bounded phase P95 retained; actual on-screen latency requires hardware. |
| Online providers | Existing HTTPS allowlist, fallback and breaker retained; terms/authorization and availability not resolved; no new provider. |
| Automatic diagnostics | iOS shows receiver status and frame progress. Android diagnostics shows paired/verified states and offers a signed live probe. |
| Basic/advanced | Collapsible advanced OCR filters on iOS, separate disclosure on Android/TV; values and profiles unchanged. |
| TV remote | Explicit back focus boundary and selected option accessibility label; physical D-pad check required. |
| Code simplification | Version metadata and log version centralized; avoid broad rewrites before hardware baseline. |
| Documentation | README corrected for current offline capability and linked candidate version. This report is current, older audit reports historical. |

Android offline model: both APK guards currently reject ML Kit Translation; do not silently restore the SDK before licensing and technical policy are resolved. Current mobile documentation must not claim offline translation of new text. Cache hits still work locally. The latest license audit records explicit approval to remove the SDK from mobile too; keep the newer policy. Earlier mobile-offline documentation is superseded.

Hardware QA: use same candidate on iPhone, Android and TCL; 30 minute session, fast dialogs including repeated lines, all four areas, multiple Xboxes, Wi-Fi interrupt/reconnect, HDMI overlay, D-pad selected/back, profiles, update over previous version, record P95 per phase. Compare device/commit/settings and sample count, no invented timing. See PHYSICAL_QA_0.5.md.

Release: no automatic publication, no stable release without hardware and license gates. No version rollback or deletion of existing release in this change.

## Provider verification, 2026-11-25
- Google ML Kit Translation terms prohibit embedded TV use without prior written permission: https://developers.google.com/ml-kit/language/translation/translation-terms . Keep both APK SDK-exclusion guards per latest recorded decision.
- Google official Cloud Translation requires authorized authentication: https://docs.cloud.google.com/translate/docs/authentication . Azure Translator requires a resource key or authorized identity: https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/reference/authentication . These do not establish authorization for the existing undocumented GTX/Edge endpoints.
- MyMemory publishes an anonymous 5,000 character daily limit: https://mymemory.translated.net/doc/usagelimits.php . No quota workaround or new provider. No live game-text requests were made in this review.

Version updates: run `python3 tools/sync_version.py --bump patch` (or minor/major), which modifies only `shared/version.json`. Workflows read the validated metadata at runtime; iOS project version is materialized before XcodeGen. No workflow edits or generated version files need committing. Verify full CI; publish only manually from exact validated revision.
