# GameLingo 0.5 — kontrola licencí a překladových služeb

2026-11-24. První post-auditní bod. Statická kontrola skutečných deklarací, připnutého upstreamu, native build skriptů a podmínek poskytovatelů. Není právní posudek ani kompletní licence clearance všech binárních tranzitivních knihoven. Žádné změny aplikačního kódu, žádná placená služba, žádný release.

## 1. Rozhodnutí o rozsahu TV a ověřený build

Uživatel výslovně potvrdil, že Google TV nebude sama provádět **pouze offline překlad**. Režimy Překlad z telefonu a Přímo na TV, OCR, online překlad, SQLite cache, profily, slovník, HDMI overlay a jazyky zůstávají. Nežádat o výjimku, nehledat TV offline model. Cache hit bez internetu není inference nového textu.

Poslední společná sestavená revize `f34348b1cd721c83074bae2590e6047b0a076303`, [CI 45384321662](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662): šest jobů PASS, včetně TV Gradle dependency guard, source audit a DEX absence restricted Translation markerů, pozitivního mobilního DEX checku, iOS IPA, balíčků a SHA-256. První běh 45384133449 selhal výhradně na chybné syntetické DEX hlavičce, opraveno následným test commitem. Konkrétní nález zakázaného ML Kit Translation SDK na TV je na této revizi technicky odstraněn. Nejde o generální právní potvrzení distribuce. OCR a language ID jsou samostatné ML Kit API a zůstávají pod obecnými Google podmínkami; v jejich veřejných API guides ani obecných ML Kit terms nebylo nalezeno stejné specifické TV omezení, což není záruka všech dalších smluvních podmínek. Fyzické testování není provedeno.

## 2. Překladové služby

Inventář z iOS CaptionPipeline.swift a Android Prekladac.kt v PlayCaption + tools/wire_gamelingo_languages.py, tv_translation_policy.py. Cache je oddělena podle engine/source/target/originálu; currentPair/revision a success/error zůstávají.

| Služba | Skutečné použití | Stav oprávnění / riziko | Akce před release |
|---|---|---|---|
| Google GTX | iOS, Android, TV; translate.googleapis.com/translate_a/single, client=gtx, bez klíče | Nedokumentované rozhraní pro tento účel, není doložen souhlas s jeho používáním distribuovanou aplikací. Oficiální Cloud Translation má jiné API a autentizaci. Google ToS omezuje neautorizovaný automatizovaný přístup; samotná existence endpointu není oprávnění. | Neoznačovat za schválené či neomezené bezplatné API. Vyjasnit podmínky, nebo schválená náhrada/disable před veřejnou distribucí. Žádné obcházení limitů. |
| Microsoft Edge | Android a TV; edge.microsoft.com/translate/auth a api-edge.cognitive.microsofttranslator.com | Tokenová cesta pro Edge, nikoli doložený veřejný API kontrakt pro nezávislou aplikaci; oficiální Azure Translator vyžaduje autorizaci. Změna/dostupnost může službu kdykoli rozbít; aktuální živé volání netestováno. | Stejná podmínka před distribucí, nezavádět token workaround. |
| MyMemory | iOS/Android/TV fallback GET /get, bez účtu | Dokumentované API pro lookup, anonymně 5 000 znaků denně, q max 500 UTF-8 bytů. V kódu už byte limit a failure handling. Úplné podmínky a soukromí nutno dočíst a ověřit; přímé načtení ToS a limitů vracelo 403, veřejné indexované výňatky a specifikace dostupné. GET není explicitní /set příspěvek, ale neznamená nulové logování/retenci. | Respektovat limity, nepřidávat osobní e-mail/API klíč bez souhlasu, nepřispívat texty do sdílené paměti, transparentní privacy a opt-out. |
| ML Kit Translation | Pouze Android telefon; 17.0.3, model download, cache/quick/story fallback | Samostatné SDK + vlastní terms; ne obecná open-source licence modelů. TV restriction technicky vyřešeno. Attribution odkazuje na Cloud Translation branding, včetně označení výsledků, dokumentace a disclaimeru v rozsahu použitelném pro API. | Prověřit správné přiřazení engine attribution i při cache hitu a přenosu na TV. Ověřit vhodnost branding guidelines v herním UI, nebrat samotné About jako úplnou nápravu. Doložit licenci redistribuce SDK a AGPL kompatibilitu. |
| ML Kit Text Recognition / Language ID | Android telefon a TV; 16.0.1 / 17.0.6 | Vstup a výsledky zpracovány lokálně podle Google ML Kit Privacy; SDK může odesílat provozní metriky a stahovat modely/aktualizace. | Informovat uživatele dle terms, ověřit redistribuci a transitivní knihovny, neuvádět aplikaci jako zcela offline/bez telemetrie. |

Žádný doklad, že by bylo nutné hned něco kupovat. Bezplatnost projektu není totéž jako bezplatný a autorizovaný přístup ke cloud API. Pro stabilní veřejné vydání doporučené explicitní rozhodnutí o obou nedokumentovaných providerech. MyMemory samotné při denním limitu nemusí vystačit na souvislé hraní. Oficiální API s kvótami nebo vlastní server jsou možné budoucí varianty, ale nepřidávat účet, server ani placenou službu bez uživatele; žádné automatické nahrazení v tomto bodě. Uživatel nechce offline model na TV.

## 3. Přímé závislosti a původ

Zdroje: PlayCaption pinned `633a847400b6c4bac1e7f034463e7847a53a9590` app/build.gradle.kts, native/chiaki-build.json, native/build_chiaki.py, native/pcobraz.c, assets/licenses; GameLingo project.yml, brand_android.py, wire_android_devices.py, build-gamelingo.yml, ATTRIBUTION.md a docs/TV_ICON_CREDITS.md; TP Play pinned `482611539977496cad047560c9bc1ebc5809a566` ios/scripts/build-core.sh a .gitmodules.

| Součást | Verze / platforma | Licence nebo evidence | Zbývající kontrola |
|---|---|---|---|
| GameLingo / PlayCaption | AGPL-3.0, Android/TV + vlastní iOS | LICENSE, ATTRIBUTION, upstream revision doloženy | Přesný release source bundle, zachovat copyright/licenční texty, změny a postup sestavení. |
| TP Play / Chiaki | iOS pin výše | AGPL-3.0 dle ATTRIBUTION; native build ze zdroje, submodules recursive | Přesné notices, licence jednotlivých submodulů a případné výjimky, manifest zdroje/binárek. |
| Android Chiaki-TV | upstream pin `f814cf69d636f149ad5a3cfffd19cb22da90beda`, NDK 21.4.7075529 | Předkompilované libchiaki-jni.so / libchiaki-pc.so pro ARM; upstream native/chiaki-build.json obsahuje SHA-256 a build_chiaki.py + 2 patch soubory. native/pcobraz.c je AGPLv3 zdroj libpcobraz1.so. | Pozitivní nález: provenance podklady existují. Není ověřen hash skutečně zabalených .so vůči manifestu, dostupnost všech přesných submodulů ani reprodukovatelnost pcobraz1. Ověřit a zaznamenat před release; neoznačit jako chybějící zdroj bez další kontroly. |
| WebRTC iOS | stasel/WebRTC, from 154.0.0 (není exact pin) | BSD-3-Clause build wrapper a WebRTC BSD, další third-party notices | Zjistit skutečné Package.resolved + framework license inventory, zdroje přesného buildu, pin pro reprodukovatelnost; nelze přenést licenci wrapperu na všechny binární součásti. |
| WebRTC Android | io.getstream:stream-webrtc-android:1.3.7 | Apache-2.0 wrapper, upstream WebRTC BSD + third-party | AAR notices a native .so provenance, ne pouze POM licence. |
| AndroidX Core | core-ktx:1.13.1 | Apache-2.0 jako typická licence AndroidX | Ověřit resolved dependencies a notices. |
| JSch | com.github.mwiede:jsch:2.28.8 | Revised BSD, upstream assets/licenses obsahuje JSch/JZlib/jBCrypt notices | Ověřit, zda je stále potřeba pro skutečnou funkci; žádné odstranění zde. |
| ZXing / JourneyApps | core 3.5.3 / embedded 4.3.0 mobil | Apache-2.0, ATTRIBUTION existuje | Zabalit příslušné notice texty, ověřit tranzitivní závislosti. |
| Mbed TLS / Opus | iOS 2.28.0 / 1.5.2 (archivy + SHA-256 pinned ve skriptu) | Apache-2.0 resp. BSD-3-Clause jako primární očekávané licence; přesné soubory a výjimky z konkrétní revize ověřit | Staticky linkované, zahrnout licenční texty a zdrojový manifest. Bezpečnostní stáří Mbed TLS samostatně prověřit v bodě 3, neaktualizovat naslepo. |
| curl, nanopb, Jerasure, GF-Complete | iOS pinned submodules native build | curl license / zlib / BSD-family předběžně podle upstream projektů | Ověřit přesné submodule SHA a LICENSE/COPYING ve skutečném stromu, transitivní licence; žádné definitivní SPDX pro neověřenou revizi. |
| SQLite, Apple system frameworks | iOS / Android systémové API | SQLite public domain, Apple a Android SDK vlastní podmínky | Nezaměňovat systémové SDK s redistribuovanou binárkou; systémové knihovny zvlášť v source manifestu. |
| Xbox / PlayStation ikony | Font Awesome Free 6.7.2, Android/TV | CC BY 4.0 pro SVG, docs/TV_ICON_CREDITS.md, brand_android.py převod na VectorDrawable | Zachovat kredit, změnu barvy/formatu a CC BY URL v distribuovaném notice, respektovat ochranné známky. |
| Build/test dependencies | Gradle, AGP/Kotlin, XcodeGen, Homebrew protobuf, Python protobuf/Pillow, JUnit, AndroidX test | Build-only/test-only oddělit od shipped dependencies | Skutečný build manifest, verze a redistribuované runtime komponenty. Není požadováno svévolně měnit verze. |

### Další nálezy pro audit kódu / úklid

Upstream Android assets/testy obsahuje čtyři jpg herní testovací snímky; jejich práva k dalšímu veřejnému šíření zatím nedoložena. Upstream Gradle také automaticky sestavuje LG IPK a Samsung WGT do Android assets a obsahuje SSH installer. Současný GameLingo plán Samsung/LG je pouze návrh. Ověřit skutečné zahrnutí do finálního APK, dostupnost UI, zda jsou tyto zděděné balíčky/asset potřeba a jejich autorství/licence. Neodstraňovat bez auditu využití a souhlasu, neměnit P3 na novou podporu těchto TV. Žádné kopírování konkurence.

### Co prokazatelně chybí k uzavření auditu distribuce

- Kompletní resolved Gradle releaseRuntimeClasspath a SwiftPM Package.resolved pro oba Android buildy a iOS; SBOM včetně staticky linkovaného Chiaki/third-party kódu, přesných zdrojů, hashů, licencí a runtime assets. Samotný statický seznam není SBOM ani binární kontrola všech licencí.
- Vytvořit THIRD_PARTY_NOTICES a zabalit skutečné licenční texty do všech tří distribucí, včetně přístupné položky O aplikaci, ne pouze URL na cizí master. Zachovat ATTRIBUTION a AGPL LICENSE. Připravit odpovídající zdrojový balíček z přesného release commitu včetně pinned upstreamu, relevantních submodulů, úprav a build skriptů. Nezahrnovat tajemství, proprietární modely ani materiály bez práva šíření. AGPL §6 a případně §13, kompatibilita s proprietárními SDK musí být posouzena, nepředpokládat automaticky.
- Neprohlašovat Google GTX/Edge endpointy za licenčně schválené. Rozhodnutí o nich a právu užití herních textů/obrazů, ochranných známek a Sony/Microsoft Remote Play podmínkách před public release; vlastní účty, nepřekonávat DRM/HDCP ani ochrany služeb.
- Privacy notice: transparentně vysvětlit online přenos OCR textu poskytovateli, model download/ML Kit metriky, lokální cache/historii, možnost vymazání, LAN HMAC bez šifrování, credential storage a žádné přidávání nevyžádaného cloudu. Potvrdit skutečné logy a data-flow v následujícím bezpečnostním auditu. Dokumentace sama nenahrazuje potřebnou atribuci v UI.

## 4. Podklady

- AGPL: https://www.gnu.org/licenses/agpl-3.0.en.html
- ML Kit obecné podmínky a soukromí: https://developers.google.com/ml-kit/terms
- ML Kit Translation zvláštní podmínky (2026-11-09): https://developers.google.com/ml-kit/language/translation/translation-terms
- Google attribution (2026-11-10): https://docs.cloud.google.com/translate/attribution
- Google API terms: https://developers.google.com/terms/ ; Google ToS https://policies.google.com/terms
- Oficiální Cloud Translation autentizace: https://docs.cloud.google.com/translate/docs/authentication
- Microsoft Services Agreement: https://www.microsoft.com/en-ie/servicesagreement ; oficiální Azure Translator https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/reference/authentication
- MyMemory: https://mymemory.translated.net/doc/spec.php ; https://mymemory.translated.net/doc/usagelimits.php ; https://mymemory.translated.net/terms-and-conditions (přímé načtení některých stránek blokováno 403, viz výše)
- stasel/WebRTC: https://github.com/stasel/WebRTC ; Stream WebRTC: https://github.com/GetStream/webrtc-android ; JSch: https://central.sonatype.com/artifact/com.github.mwiede/jsch
- Font Awesome: https://fontawesome.com/license/free ; https://creativecommons.org/licenses/by/4.0/
- Relevantní nativní licenční soubory ověřit přes přesné revize, nikoli automaticky podle aktuálních default větví.

## 5. Výsledek bodu

Statická kontrola a evidence dokončeny v dostupném rozsahu. Překladový ML Kit na TV odstraněn a CI potvrzeno; uživatel nechce TV offline inference ani její budoucí návrat. Zbývající release otázky (provider clearance, complete notices/SBOM/corresponding source, privacy, hardware) otevřeny. Kód, jazyky a design beze změny, docs-only commit nespouští CI. Poslední společné testovací IPA/APK existují jako Actions artefakty run 45384321662, bez veřejného release; nepovažovat je za licenčně schválený veřejný balíček. Další bod 2: závěrečný audit kódu všech tří aplikací, s prioritou inherited assets, skutečných závislostí, compatibility a neověřených execution paths. Případné opravy odděleně.

## SEC-05 — opětovná kontrola 2026-11-24
- [Google Cloud Translation authentication](https://docs.cloud.google.com/translate/docs/authentication), [Google Terms](https://policies.google.com/terms?hl=en): veřejné API má autentizaci, obecné podmínky omezují automatizovaný přístup v rozporu se strojově čitelnými instrukcemi. Žádný získaný doklad oprávnění pro GTX endpoint; nelze z toho samotného prohlásit konkrétní použití za porušení smlouvy.
- [Azure Translator authentication](https://learn.microsoft.com/cs-cz/azure/ai-services/translator/text-translation/reference/authentication), [Azure Translator resources](https://learn.microsoft.com/azure/ai-services/translator/how-to/create-translator-resource): oficiální rozhraní používá autorizované prostředky/klíče nebo identitu. Edge token endpoint není doložený distribuční kontrakt.
- [MyMemory limits](https://mymemory.translated.net/doc/usagelimits.php), [API specs](https://mymemory.translated.net/doc/spec.php): anonymní 5000 chars/day, q max 500 UTF-8 bytes. [Terms](https://mymemory.translated.net/terms-and-conditions) indexovaný výňatek potvrzuje odpovědnost uživatele za práva třetích osob; přímé načtení celého textu vrací HTTP 403. Neověřena retence, důvěrnost a přesný kontrakt. Pouze GET lookup, žádné set/import ani přidání identity nebo obcházení kvót.
- Implementační hardening SEC-05: no redirect/no HTTP cache, allowlist endpointů, iOS ephemeral no cookies/credential storage. Neřeší právní oprávnění ani serverovou retenci. Žádný živý požadavek s reálným herním textem, žádná změna providerů. Release gate zůstává otevřená.

## Implementace oznámení třetích stran
- Přidán kořenový THIRD_PARTY_NOTICES.md, platformní inventář, rozlišení open-source licencí od podmínek SDK a služeb, kredit Font Awesome.
- `tools/package_license_notices.py` sbírá dostupné textové tracked licence z přesného checkoutu GameLingo a PlayCaption/TP Play včetně dostupných submodulů, doplňuje dostupné texty Mbed TLS/Opus ze zdrojů v build adresáři iOS, a přidává LICENSE, ATTRIBUTION a THIRD_PARTY_NOTICES. Generuje manifest zdrojových HEAD a SHA-256 shromážděných souborů. Nezaručuje úplnost transitivních/binary notices; chybějící nebo netextové licence vyžadují samostatný audit. Společný build balí soubory před podpisem/zip a ověřuje jejich přítomnost a základní obsah v ZIP/APK/IPA před uploadem. Staré samostatné release workflow tímto nezměněny.
- Native About iOS/Android/TV nabízí offline čtení. Žádný provider, SDK, model, design ani jazyk odstraněn. Neověřená dostupnost notice souboru v dřívějších sestaveních má explicitní fallback. Zbývá skutečný resolved Gradle/SwiftPM inventář, native a WebRTC binární notices, SBOM, corresponding source release bundle, kompatibilita SDK, provider terms/attribution a práva inherited assets. Žádný claim o právním schválení. Nový build a ověření až po opravách dle uživatele.
