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

## Verified package evidence — run 492

Independent bundled-text SHA-256 verification passed (132 mobile, 113 TV, 35 iOS notices, including declarations). Mobile has 78/79 and TV 67/67 runtime coordinates with direct or explicit parent POM declarations. AutoValue annotations 1.6.3, the remaining mobile coordinate, has original upstream Apache-2.0 license and source header linked to the exact observed artifact SHA-256 in the inventory, rather than a fabricated POM declaration.

Both Android APKs contain Stream WebRTC 1.3.7 ARM binaries matching release Git blobs arm64 `193297189de7fcbc0d1cfab6e56cabb59934f67e` and armv7 `2a5da428d4ec3a6c73f62075ab26cb477ee9c551`. The underlying native WebRTC source revision is still unverified. The [iOS publisher release 154.0.0](https://github.com/stasel/WebRTC/releases/tag/154.0.0) identifies core revision `3a8b0c76e6b7bf0e72141047f82eb2c3f4862c8d`, branch-heads/8037, and archive SHA-256 `a2bcdda93578c82452ceb6e49d54a2746e1bcb4caf7c2fa601ffac8028b58c16`. Complete native third-party notices are not yet verified.

CameraX 1.5.2 declares Apache-2.0 and libyuv BSD in its actual POM; the embedded license is Apache only. The BSD license and patent grant from libyuv mirror commit `61bdaee13a701d2b52c6dc943ccc5c888077a591` are bundled as supplementary reference texts, not as proof of the exact CameraX libyuv revision. [Official libyuv license at that commit](https://chromium.googlesource.com/libyuv/libyuv/+/61bdaee13a701d2b52c6dc943ccc5c888077a591/LICENSE). Exact CameraX libyuv revision and full WebRTC native third-party notices remain open.

## Native source and package provenance — batch follow-up

The pinned PlayCaption `native/build_chiaki.py` builds Chiaki-TV at `f814cf69d636f149ad5a3cfffd19cb22da90beda`. The AGPL OpenSSL exception in that source is retained verbatim. Its CMake external project pins OpenSSL 1.1.1d and archive SHA-256 `1e3a91bc1f9dfce01af26026f856e064eab4c8ee0a8f457b5ae30b40b8b711f2`. This unsupported crypto release is a security/compatibility review item, not a recommendation to keep using it in a new store release.

Original notices for the exact referenced revisions are stored under `licenses/`. The following paths refer to the original files (GF-Complete and Jerasure use mirrors with matching exact Git commit IDs):

- [505e06b2/Chiaki-TV f814cf69d636 COPYING](https://github.com/505e06b2/Chiaki-TV/blob/f814cf69d636f149ad5a3cfffd19cb22da90beda/COPYING) — `licenses/chiaki-tv-f814cf6/COPYING`; Git blob `c3ef0cb237eb1a941d0ee708baddf490fd17eb94`.
- [google/oboe 0ab5b12a5bc3 LICENSE](https://github.com/google/oboe/blob/0ab5b12a5bc3630a3d6c83b20eed2a669ebf7a24/LICENSE) — `licenses/oboe-0ab5b12/LICENSE`; Git blob `d645695673349e3947e8e5ae42332d0ac3164cd7`.
- [nanopb/nanopb ae9901f2a315 LICENSE.txt](https://github.com/nanopb/nanopb/blob/ae9901f2a31500e8fdc93fa9804d24851c58bb1e/LICENSE.txt) — `licenses/nanopb-ae9901f/LICENSE.txt`; Git blob `d11c9af1d7e469e9a5357a660fd184f58c3a4ff2`.
- [streetpea/gf-complete fa54a4670a57 COPYING](https://github.com/streetpea/gf-complete/blob/fa54a4670a5705c84abf6c24b92b0cd479625478/COPYING) — `licenses/gf-complete-fa54a46/COPYING`; Git blob `df8d9ed33fac25129603eab1c9514af3057c242e`.
- [streetpea/jerasure 505ccb4bc69e COPYING](https://github.com/streetpea/jerasure/blob/505ccb4bc69eee8d7ea40a6089a056b99671134f/COPYING) — `licenses/jerasure-505ccb4/COPYING`; Git blob `20ffbd2d7375ba972da2649f9680fc1e30bb960e`.
- [openssl/openssl OpenSSL_1_1_ LICENSE](https://github.com/openssl/openssl/blob/OpenSSL_1_1_1d/LICENSE) — `licenses/openssl-1.1.1d/LICENSE`; Git blob `9601ab43575f9719ca6d2731580ec31ca5dfb0e7`.

The PlayCaption `native/pcobraz.c` declares AGPLv3. Its ARM prebuilts in both run 491 APKs matched the Git blobs from the exact PlayCaption revision (ARM64 `e91ab4a5d5e94fa6b9d932226f54a347985da784`, ARMv7 `d2daadb9631673ee10857257181cff39f5572385`). The original build script explicitly states that the shim is not rebuilt. The new inventory records the shim source hash, tracked original binary hashes, both Chiaki patch hashes, and native files contained in every resolved Android archive. Final package evidence distinguishes byte matches, different bytes and unmapped libraries. It does not claim that the shim or Chiaki are reproducibly built, or that a byte match proves that all static third-party code is licensed.

Store review remains blocked on exact native WebRTC transitive notices and source provenance, CameraX/libyuv notices, any outstanding publisher notices, 16 KB compatibility and authorization/terms questions. The iOS run 491 includes the WebRTC framework's privacy manifest; the app's own required-reason APIs and privacy manifest still need review.

## Store-only distribution preparation

`tools/store_distribution.py` prepares a disposable generated workspace after the standard branding and permissions patches (Android), or before XcodeGen (iOS). It removes the GitHub APK installer implementation, receiver, provider, path and install permission; on iOS it replaces the GitHub IPA download implementation with an inert compatibility view. The existing GitHub build and updater remain unchanged. This is not yet a complete store build pipeline, and store variants must also remove or replace any other sideload links (including device setup QR links), validate compiled manifests and native libraries, and use correctly signed AAB / App Store archives. Do not use the GitHub APK/IPA as store uploads.

The application privacy manifest declares UserDefaults CA92.1 (app-specific settings) and system boot time 35F9.1 (elapsed time and in-app timers), based on the app's actual Swift usage. It is deliberately not a final data collection declaration: third-party retention, data disclosures, remaining required-reason APIs in the linked native archive, and SDK privacy/signature obligations must be verified against the exact store build. The existing IPA permission check requires the app-level manifest in addition to any framework manifest. See [Apple required reason APIs](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api).

Current official Google Play [target requirements](https://developer.android.com/google/play/requirements/target-sdk) are API 36 mobile and API 34 TV. [TV quality requirements](https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality) require AAB and both ARM architectures plus 16 KB support from August 1, 2026. [Google Play Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646) prohibits self-updates outside Play. [Apple guideline 5.2.2](https://developer.apple.com/app-store/review/guidelines/) requires permission for third-party services. Keep these as explicit gates; do not claim that a technical build substitutes for rights.

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

## Store preflight — isolated compilation (2026-11-28)

A separate manual `store-preflight.yml` workflow builds the iOS store source without signing and Android release AAB/APK from a disposable generated workspace. It collects the release runtime classpath (not debug), validates the exact APK and AAB notices, compares native payloads, requires no install-packages permission and enforces strict 16 KB APK alignment. It never uses GitHub release signing keys or publishes packages. These are technical preflight artifacts only, not submissions. The iOS build is unsigned and is not an App Store archive. AAB signing, bundletool/device validation, removal/replacement of remaining sideload setup links, third-party rights, complete license notices and device QA remain required. This new workflow has not been executed. Run 492 independently confirmed 16 KB alignment for CameraX and WebRTC ARM64; the two Chiaki ARM64 binaries still fail.
