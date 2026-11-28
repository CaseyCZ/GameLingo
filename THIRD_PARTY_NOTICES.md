# GameLingo — Third-party notices / Oznámení třetích stran

GameLingo is an independent AGPL-3.0 project. See the accompanying project license and attribution. This inventory is a starting point, not a complete SBOM or a declaration of permission to distribute every component. Build-generated inventories record the actual source revisions and available license files.

| Component | Platform | Source / license information |
|---|---|---|
| PlayCaption (AdamRuzick4) | Android / TV, derived code | AGPL-3.0; https://github.com/AdamRuzick4/PlayCaption-app/tree/633a847400b6c4bac1e7f034463e7847a53a9590 |
| TP Play / Chiaki / chiaki-ng | iOS, Android / TV native core | AGPL-3.0, original notices and applicable exceptions retained; https://github.com/mB14ck22/tp-play/tree/482611539977496cad047560c9bc1ebc5809a566 ; Android provenance: upstream native/chiaki-build.json |
| stasel/WebRTC | iOS | BSD-3-Clause wrapper; CI now requires the resolved checkout license and original XCFramework LICENSE; transitive native notices remain subject to review; https://github.com/stasel/WebRTC |
| Stream WebRTC Android 1.3.7 | Android / TV | Apache-2.0 wrapper, underlying WebRTC and third-party licenses; https://github.com/GetStream/webrtc-android |
| AndroidX Core 1.13.1 | Android / TV | Apache-2.0; resolved dependencies require verification |
| AndroidX Activity 1.8.2 and CameraX 1.5.2 | Android mobile only | Apache-2.0; https://developer.android.com/jetpack/androidx ; resolved dependencies and notices require verification |
| ZXing 3.5.3 and JourneyApps 4.3.0 | Android, camera scanner mobile only | Apache-2.0; https://github.com/zxing/zxing ; https://github.com/journeyapps/zxing-android-embedded |
| JSch 2.28.8 | Android / TV inherited dependency | BSD-family; preserve original JSch/JZlib/jBCrypt notices as applicable |
| JSpecify 1.0.0 | Android mobile, resolved runtime dependency observed in CI run 487 | Apache-2.0; https://central.sonatype.com/artifact/org.jspecify/jspecify/1.0.0 ; retain applicable original notices |
| Mbed TLS 2.28.0 / Opus 1.5.2 | iOS native core | Apache-2.0 / BSD-family, exact source licenses and exceptions retained when available |
| curl / nanopb / Jerasure / GF-Complete | Native core | See exact submodule licenses; do not infer from project names |
| Font Awesome Free 6.7.2 Brands | Android / TV | © Fonticons, Inc.; CC BY 4.0 https://creativecommons.org/licenses/by/4.0/ ; Xbox and PlayStation SVG paths converted to VectorDrawable, color changed; https://github.com/FortAwesome/Font-Awesome/tree/6.7.2 |
| ML Kit OCR / Language ID; Translation (mobile only) | Android / TV | Google ML Kit terms and privacy, not an open-source license for the SDK: https://developers.google.com/ml-kit/terms |
| SQLite / system frameworks | Platforms | SQLite public domain; system SDK terms apply |

Xbox, PlayStation, Google TV and other marks belong to their respective owners. GameLingo is not sponsored by or affiliated with those owners.

Online Google GTX, Microsoft Edge and MyMemory services are separate from library licenses. Authorization, attribution and service terms are not established by this notice. A test build is not a public distribution clearance.

## Resolved dependency license follow-up — run 491

Run 491 passed regression, native builds and package verification: mobile 79 runtime artifacts (74 direct POM declarations, 27 with collected archive notices), TV 67 (65 direct POM declarations, 20 with collected archive notices); iOS 30 collected notices including both WebRTC files. Counts of bundled notices include POM declarations, not only full license texts.

The five missing direct POM declarations refer to explicit parent POMs: auto-value-annotations 1.6.3, listenablefuture 1.0, ZXing core 3.5.3, OkHttp 3.0.0 and Okio 1.6.0. CI now requests these exact parent versions, and checks the child's parent coordinate before including any inherited license declaration. This does not establish full corresponding source or native license coverage.

Additional original texts, stored under `licenses/` and included by the existing notice collector:
- Apache-2.0 text from [AutoValue 1.6.3](https://github.com/google/auto/blob/auto-value-1.6.3/LICENSE.txt), covering the common license text for artifacts whose actual publisher declaration specifies Apache-2.0. Component-specific copyright and NOTICE obligations still apply.
- [Guava v26.0 COPYING](https://github.com/google/guava/blob/v26.0/COPYING), upstream license evidence for the Listen­ableFuture family; exact parent declaration 26.0-android remains separately checked.
- [ZXing 3.5.3 LICENSE](https://github.com/zxing/zxing/blob/zxing-3.5.3/LICENSE), including its additional original notices.
- [Stream WebRTC Android 1.3.7 LICENSE](https://github.com/GetStream/webrtc-android/blob/1.3.7/LICENSE) and the release-tag README license section. The wrapper's Apache-2.0 license alone does not cover libjingle_peerconnection_so.so. Obtain the exact underlying WebRTC/third-party native source revision and notices before marking its license review complete. CameraX's libyuv BSD notice and bundled Google SDK terms also require final review.

## Translation services — distribution review (2026-11-28)

| Engine | Current use | Distribution gate |
|---|---|---|
| Google GTX | `translate.googleapis.com/translate_a/single?client=gtx`; online, first preference | Not the documented Cloud Translation v2/v3 API. No verified permission for this integration. Do not treat availability without a key as authorization. [Official API](https://docs.cloud.google.com/translate/docs/reference/rest) |
| Microsoft Edge | `edge.microsoft.com/translate/auth` and `api-edge.cognitive.microsofttranslator.com/translate`; online fallback | Not the documented Azure Translator integration. Authorization for independent app distribution remains unverified. [Official authentication](https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/reference/authentication) |
| MyMemory | Public GET API; online fallback | Anonymous limit 5,000 characters/day; query limit 500 bytes per documented API; no guarantee of availability or confidentiality. Review terms and retention before production distribution. [API](https://mymemory.translated.net/doc/spec.php) · [Limits](https://mymemory.translated.net/doc/usagelimits.php) · [Terms](https://mymemory.translated.net/terms-and-conditions) |
| Google ML Kit Translation | Android phone fallback, models downloaded on Wi-Fi; excluded from TV package | Official local SDK. [Translation terms](https://developers.google.com/ml-kit/language/translation/translation-terms) restrict embedded-device use without prior written permission. [Attribution](https://docs.cloud.google.com/translate/attribution) applies as relevant, including visible attribution alongside results; the current About notice alone is not sufficient. Resolve accurate per-result attribution (including cache and TV output), relevant branding restrictions for game content and SDK terms before store submission. [Data disclosure](https://developers.google.com/ml-kit/android-data-disclosure). |
| Apple Translation | Not integrated | Candidate for a local iOS engine where language availability and OS support permit. Verify Czech/Slovak language pairs on target devices before adoption. [Translation framework](https://developer.apple.com/documentation/translation) |

Current GitHub test behavior is unchanged. Do not add billing accounts, publish shared API credentials, circumvent provider quotas, or assume a free tier is unlimited. Production distribution remains blocked on provider authorization or replacement and attribution verification. Existing HTTPS host allowlists, cache, request limits and circuit breakers reduce exposure and load but do not establish permission.

## Console authentication — distribution review (2026-11-28)

Xbox uses an inherited OAuth public client ID and xHome streaming integration. Microsoft supports device-code flow for registered public clients, but that does not grant access to Xbox Remote Play endpoints. Before store distribution, register the project's own application and obtain confirmation of the required Xbox scopes and service access. [Microsoft registration](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-registration) · [Microsoft Services Agreement](https://www.microsoft.com/en/servicesagreement).

The PlayStation account-ID resolver inherits Sony Remote Play client constants from the reference implementation. These embedded values are not GameLingo-owned credentials or evidence of authorization. Obtain permission or use an authorized alternative before store distribution; do not publish any newly issued confidential secret in the app. [PlayStation Terms](https://www.playstation.com/cs-cz/legal/terms-of-service/). Local manual account-ID and console PIN registration remain available; the legal status of the Remote Play protocol itself still needs review. OAuth state/PKCE compatibility must be verified with the authorized Sony client flow; the current pasted-link flow is not a complete modern OAuth implementation.

Current test-distribution protocol remains unchanged. Secure local storage and strict credential destinations reduce risk but are not a grant of service rights.

## Binary and store distribution review (2026-11-28)

- AGPL-3.0 sections 6 and 10 require corresponding source for distributed object code and prohibit additional restrictions. Preserve the complete source/build provenance and applicable upstream exceptions for the exact published version. [License](https://www.gnu.org/licenses/agpl-3.0.en.html).
- Apple distribution is not cleared merely by publishing source or supplying a custom EULA. Review the Apple Developer Program License Agreement, usage rules and any technical restrictions against AGPL rights, and obtain any required permissions from all relevant copyright holders. [Apple EULA minimum terms](https://www.apple.com/legal/internet-services/itunes/dev/minterms/) · [Review guidelines](https://developer.apple.com/app-store/review/guidelines/).
- Google Play requires rights to third-party intellectual property; icon licenses are not trademark licenses. Retain accurate attribution and obtain any required brand or service permission. [Google Play intellectual property](https://support.google.com/googleplay/android-developer/answer/9888072).
- Build evidence records SHA-256 of the pinned Chiaki ARM prebuilts and the actual packaged native files, source and submodule revisions, plus hashes of bundled notice texts. Byte matching does not establish a reproducible build, complete corresponding source or full dependency/license coverage. Differences between packaged and upstream binaries require review. The iOS native archive hash does not prove the content of the final linked executable.
- Run 490 verified all three packages: 79 Android mobile and 67 TV resolved runtime artifacts (88 unique coordinates combined); 71 mobile and 65 TV lacked a collected embedded notice under the original scanning rules. This is not proof of missing licenses. The scanner now recognizes Google third_party_licenses.txt/json and related metadata; it also records direct license declarations from the exact resolved Maven POM, separate from full original license texts. Missing POMs and absent direct declarations remain explicit review items; no permission is inferred.
- Build collection now reads available original text notices from resolved Android runtime archives (including nested JARs), records each artifact coordinate and SHA-256, and explicitly flags archives without embedded notices. iOS collection checks the resolved WebRTC checkout revision and includes its license and the XCFramework LICENSE. Missing archive notices and complete transitive native rights still require individual review.
- CI exports the resolved Android debug runtime dependency trees and the iOS Package.resolved / native submodule revisions as separate dependency-evidence artifacts. These are inputs to license review, not evidence of complete coverage of native and embedded code. The narrow Android packaging exclusion for the colliding Java 9 OSGi manifest does not exclude LICENSE or NOTICE files.
- Remaining: resolve and review Gradle transitive runtime dependencies and exact Swift package resolution (including bundled WebRTC licenses), verify complete Chiaki source/build inputs and all applicable exceptions, audit actual third-party SDK license/telemetry terms, confirm trademark rights and legal compatibility for each store. Do not claim store clearance before these checks are complete.

## Android SDK and native alignment (2026-11-28)

The Android candidate toolchain is pinned to AGP 8.10.1, Gradle 8.11.1, Kotlin 2.2.21 and compile SDK 36. Mobile targets API 36; TV targets API 34; both keep min SDK 26 and the two ARM ABIs. [Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk) · [16 KB compatibility](https://developer.android.com/guide/practices/page-sizes) · [TV quality](https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality).

CI audits each APK's actual min/target levels, ELF PT_LOAD segments and 16 KB ZIP alignment. The report is informational for GitHub builds; --strict can be used for store validation. The inherited Chiaki source build is pinned to NDK r21e; an APK report may reveal that Chiaki or other native SDKs need source rebuilds or verified compatible dependency releases. No native binary has been replaced, and no 16 KB runtime test has been performed. Store distribution remains blocked until both static checks and actual 16 KB runtime tests pass. Mobile safe-area handling is included for Android 15/16, but physical UI and session lifecycle QA is required.

### 16 KB findings and candidate remediation

CI run 474 confirmed only the two ARM64 Chiaki binaries fail on TV; mobile additionally fails on libimage_processing_util_jni.so. APK ZIP alignment passes for both. CameraX is pinned to 1.5.2 as the first remediation candidate; verify the actual APK again. A separate manually triggered Chiaki candidate workflow uses NDK r28c, the exact pinned Chiaki-TV source and original reviewed audio patches/tests. It checks the existing JNI exports and shared dependencies through the upstream builder, plus ELF PT_LOAD alignment and SHA-256. It never replaces production binaries automatically. The inherited OpenSSL 1.1.1d is obsolete; its build compatibility, security review, and migration to a supported crypto library remain open. No store clearance or device compatibility is implied by static tests. Test both ARM ABIs, real 16 KB Android, PS4/PS5 registration, reconnect, HDMI/audio and shutdown before adoption. The experimental workflow may require further fixes for old source dependencies.
