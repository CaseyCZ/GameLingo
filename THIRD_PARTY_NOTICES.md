# GameLingo — Third-party notices / Oznámení třetích stran

GameLingo is an independent AGPL-3.0 project. See the accompanying project license and attribution. This inventory is a starting point, not a complete SBOM or a declaration of permission to distribute every component. Build-generated inventories record the actual source revisions and available license files.

| Component | Platform | Source / license information |
|---|---|---|
| PlayCaption (AdamRuzick4) | Android / TV, derived code | AGPL-3.0; https://github.com/AdamRuzick4/PlayCaption-app/tree/633a847400b6c4bac1e7f034463e7847a53a9590 |
| TP Play / Chiaki / chiaki-ng | iOS, Android / TV native core | AGPL-3.0, original notices and applicable exceptions retained; https://github.com/mB14ck22/tp-play/tree/482611539977496cad047560c9bc1ebc5809a566 ; Android provenance: upstream native/chiaki-build.json |
| stasel/WebRTC | iOS | BSD-3-Clause wrapper; WebRTC and third-party notices must be checked against the actual framework; https://github.com/stasel/WebRTC |
| Stream WebRTC Android 1.3.7 | Android / TV | Apache-2.0 wrapper, underlying WebRTC and third-party licenses; https://github.com/GetStream/webrtc-android |
| AndroidX Core 1.13.1 | Android / TV | Apache-2.0; resolved dependencies require verification |
| AndroidX Activity 1.8.2 and CameraX 1.3.4 | Android mobile only | Apache-2.0; https://developer.android.com/jetpack/androidx ; resolved dependencies and notices require verification |
| ZXing 3.5.3 and JourneyApps 4.3.0 | Android, camera scanner mobile only | Apache-2.0; https://github.com/zxing/zxing ; https://github.com/journeyapps/zxing-android-embedded |
| JSch 2.28.8 | Android / TV inherited dependency | BSD-family; preserve original JSch/JZlib/jBCrypt notices as applicable |
| Mbed TLS 2.28.0 / Opus 1.5.2 | iOS native core | Apache-2.0 / BSD-family, exact source licenses and exceptions retained when available |
| curl / nanopb / Jerasure / GF-Complete | Native core | See exact submodule licenses; do not infer from project names |
| Font Awesome Free 6.7.2 Brands | Android / TV | © Fonticons, Inc.; CC BY 4.0 https://creativecommons.org/licenses/by/4.0/ ; Xbox and PlayStation SVG paths converted to VectorDrawable, color changed; https://github.com/FortAwesome/Font-Awesome/tree/6.7.2 |
| ML Kit OCR / Language ID; Translation (mobile only) | Android / TV | Google ML Kit terms and privacy, not an open-source license for the SDK: https://developers.google.com/ml-kit/terms |
| SQLite / system frameworks | Platforms | SQLite public domain; system SDK terms apply |

Xbox, PlayStation, Google TV and other marks belong to their respective owners. GameLingo is not sponsored by or affiliated with those owners.

Online Google GTX, Microsoft Edge and MyMemory services are separate from library licenses. Authorization, attribution and service terms are not established by this notice. A test build is not a public distribution clearance.

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
- Remaining: resolve and review Gradle transitive runtime dependencies and exact Swift package resolution (including bundled WebRTC licenses), verify complete Chiaki source/build inputs and all applicable exceptions, audit actual third-party SDK license/telemetry terms, confirm trademark rights and legal compatibility for each store. Do not claim store clearance before these checks are complete.
