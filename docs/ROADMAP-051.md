# GameLingo 0.5.1 — consolidated improvement cycle

Base: e0afff217849a58843bf751d16339ca63bbbc53c. Previous CI 45621740397: all eight jobs passed. Agency Roster guidance: code review, CI/CD, security, networking, privacy, performance, UX, test engineering, software architecture. No external AI API.

| Proposal | Current implementation / remaining gate |
|---|---|
| Central version | shared/version.json, sync_version.py, CI consistency test, iOS version 0.5.1 build 6, Android independent release counter range, matching artifact names; manual exact-commit publication only |
| Encrypted pairing | Not implemented. Existing UDP HMAC is integrity only; bootstrap exposes PIN/key. Require standard authenticated transport and independently reviewed pairing; no ad hoc crypto, no silent downgrade. Existing LAN-only restriction remains. |
| Connection simplification | iOS diagnostics distinguish paired/verified, first frame and troubleshooting; test caption disabled without verified receiver. Existing onboarding retained. |
| Real latency | Existing bounded phase P95 metrics retained; physical measurements required. |
| Online providers | Existing HTTPS allowlist, fallback and breaker retained; terms/authorization and availability not resolved; no new provider. |
| Automatic diagnostics | iOS shows actual receiver status and frame progress; no false online from saved IP. |
| Basic/advanced | Collapsible advanced OCR filters on iOS, separate disclosure on Android/TV; values and profiles unchanged. |
| TV remote | Explicit back focus boundary and selected option accessibility label; physical D-pad check required. |
| Code simplification | Version metadata and log version centralized; avoid broad rewrites before hardware baseline. |
| Documentation | README corrected for current offline capability and linked candidate version. This report is current, older audit reports historical. |

Android offline model: both APK guards currently reject ML Kit Translation; do not silently restore the SDK before licensing and technical policy are resolved. Current mobile documentation must not claim offline translation of new text. Cache hits still work locally. User preference is mobile offline, TV online-only; restoring mobile fallback is an open implementation/approval gate.

Hardware QA: use same candidate on iPhone, Android and TCL; 30 minute session, fast dialogs including repeated lines, all four areas, multiple Xboxes, Wi-Fi interrupt/reconnect, HDMI overlay, D-pad selected/back, profiles, update over previous version, record P95 per phase. Compare device/commit/settings and sample count, no invented timing. See PHYSICAL_QA_0.5.md.

Release: no automatic publication, no stable release without hardware and license gates. No version rollback or deletion of existing release in this change.
