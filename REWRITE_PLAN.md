# GameLingo — nezávislý přepis, compliance by design

Stav: plán schválení k dopracování, 2026-11-28. Žádná položka tohoto dokumentu sama o sobě nepotvrzuje právní shodu ani připravenost nové aplikace k vydání. Závazné jsou aktuální podmínky poskytovatelů, skutečný obsah balíčku a doložené výsledky testů.

## 0. Zachování funkční verze
- [x] Záloha Master: backup/pre-rewrite-master-20261128, 5d924cdff71c662cfecdbe1cf050602f10715622.
- [x] Záloha auditu: backup/pre-rewrite-store-audit-20261128, 5f25378fc1da89afbe59cc3791d972141ec40680.
- [x] Pracovní větev feature/native-rewrite. Žádný automatický merge nebo release.
- [ ] Inventura skutečných funkcí, stránek, nastavení, protokolů, datových formátů a naměřené výkonnosti referenční verze. Přehled po platformách: funguje / neplatí / chybí / neověřeno, s odkazem na test.

## 1. Původ kódu a rozhodnutí o licenci PŘED implementací
- Evidovat původ každého modulu, autora, podmínky, použitý commit a licenční výjimky. Vlastní kód neznamená automaticky jinou licenci; existující AGPL granty a oznámení zachovat.
- Samostatná specifikace chování bez přebírání původního chráněného kódu, komentářů, assetů a patchů. Oddělit analýzu chování od implementace, zaznamenat původ nových modulů a nezávisle zkontrolovat případnou podobnost. Toto samo nezaručuje právně uznaný clean-room proces; historie dosavadní práce obsahuje kontakt s původním kódem.
- Android/TV: nový přímo sestavitelný Kotlin projekt se společným jádrem, bez PlayCaption checkout/patch v novém buildu. iOS: Swift/SwiftUI, audit původu jednotlivých částí včetně TP Play; zachovat prokazatelně vlastní části, nepřejmenovávat převzatý kód na vlastní.
- Chiaki, TP Play, WebRTC: zvlášť posoudit propojení, odpovídající zdroje, transitive notices, OpenSSL výjimky a podmínky obchodů. Samotná hranice modulu neodstraňuje copyleft. Nepřebírat neznámé předkompilované binárky ani staré zranitelné verze. Použití AGPL komponent nevylučovat bez analýzy, ale nepřejít do uzavřeného režimu bez právního podkladu. Novou licenci nepřidělovat bez rozhodnutí vlastníka a kontroly práv.
- Každá knihovna/model/asset má před přijetím záznam: název, verze, zdroj, SHA256 nebo lock, SPDX či přesné vlastní podmínky, copyright/NOTICE, závislosti, distribuční práva, data/telemetrie, podporované platformy, známé zranitelnosti. Nejasné = blokováno pro produkční integraci. Žádné placené API ani externí AI.
- Externí služby jsou zvláštní kontrola: Xbox Remote Play, PSN/Remote Play, Google GTX, Edge a MyMemory nejsou automaticky povolené jen tím, že fungují. Doložit konkrétní oprávnění, podporovaná API, vlastnictví OAuth klienta, kvóty, podmínky a datové praktiky. Nepřebírat cizí klientské identity ani obcházet omezení. Při chybějícím oprávnění použít schválenou alternativu po ověření parity; funkci neoznačit jako hotovou, dokud není povolená a funkční. Žádné odstranění funkce z aktuálního Masteru.
- ML Kit OCR/ID a překlad posoudit odděleně. Oficiální on-device Translation vyžaduje atribuci; nepoužívat v TV bez předchozího písemného souhlasu Google. Ověřit podmínky i pro zobrazování výsledků z telefonu na TV a pro konkrétní herní obsah. Nepřidávat novou technologii překladu jen kvůli přepisu.
- Vlastní název, design a assety. Cizí názvy jen přesné popisné použití; logo či jiná ochranná známka pouze s odpovídajícím právem. Neprezentovat partnerství. Prověřit dostupnost názvu v cílových jurisdikcích.
- Před vydáním odpovídající zdroj pro každý distribuovaný copyleft artefakt, úplné texty licencí a dostupné notices uvnitř aplikace i s balíčkem; ověřit AGPL §§6,10 a konkrétní distribuční smlouvy/DRM. Neprohlašovat schválení konkurence za právní důkaz.

## 2. Soukromí a bezpečnost jako součást každé funkce
Cílový návrh, dosud neimplementováno/nezměřeno v novém projektu:
| Data | Účel | Cílové zpracování / kontrola |
|---|---|---|
| Video, kamera, obrazovka | OCR v uživatelem vybrané oblasti | Lokálně, bez ukládání snímků ve výchozím režimu; uvolnit buffery při ukončení; žádný mikrofon; respektovat chráněný obsah a systémové omezení |
| OCR text, překlady | Titulky | Lokální cesta dle dostupnosti; u online cesty jen nutný text do ověřené služby, jasná informace před odesláním, možnost vypnout; cache omezená, historie opt-in, mazání |
| Xbox/PSN tokeny, ID | Přihlášení uživatele | Pouze ověřený autorizační tok, Keychain/Keystore, žádné tokeny do logů nebo analytiky, odhlášení a vymazání místních údajů |
| LAN zařízení, QR/PIN, IP | Párování a přenos | Autentizovaná šifrovaná relace včetně řídicích zpráv, jednorázové párování, ochrana proti replay a hádání PIN, omezení přístupu, odebrání zařízení |
| Profily, nastavení, slovník | Uživatelské preference | Lokálně, kontrola záloh/exportu, explicitní mazání; migrace bez ztráty dat a bez přenosu tajných klíčů |
| Diagnostika | Řešení problémů | Minimalizované lokální logy bez snímků/dialogů/tokenů; případný export jen vědomou akcí uživatele |
| SDK a aktualizační metadata | Funkčnost, distribuce | Inventura skutečného síťového provozu a telemetrie SDK; žádné reklamní ani analytické SDK bez nové kontroly; rozdělit GitHub/store varianty |

- Před každou funkcí definovat datový tok, příjemce, důvod, právní titul, přesnou dobu uchování a mazání, přeshraniční přenosy a rizika. Tabulka výše je návrh, nikoli finální GDPR evidence.
- GDPR: ověřit správce, kontakt, právní tituly dle čl. 6, informace čl. 13/14, práva uživatelů, ochranu při návrhu čl. 25, bezpečnost čl. 32, zpracovatele a smlouvy čl. 28 pokud relevantní, přenosy mimo EHP a vhodnost DPIA dle čl. 35. Nezveřejňovat soukromé kontakty bez schválení vlastníka. Souhlas nepoužívat jako univerzální právní titul.
- V aplikaci přístupná politika soukromí, licenční informace, podpora, odhlášení a odstranění místních dat včetně tokenů/cache/historie/profilů (s potvrzením); pokud vznikne vlastní účet, splnit pravidla zrušení účtu v aplikaci a u Google též mimo ni. Odhlášení externího účtu není jeho zrušení.
- Least privilege: camera a local network dle funkce, overlay/MediaProjection jen potřebné, žádný mikrofon, kontakty, plošná úložiště, sledování polohy, QUERY_ALL_PACKAGES nebo AccessibilityService jako obcházení omezení. Důvod oprávnění vysvětlit, odmítnutí nezpůsobí pád.
- TV: souhlas pro každou MediaProjection relaci; správný foreground service, indikace a okamžité ukončení, korektní zánik služby/overlay, žádné obcházení DRM/FLAG_SECURE. Při nepodporovaném snímání HDMI nebo DRM přiznat omezení a nabídnout povolený vstup, nikoli workaround obcházející ochranu.
- Hrozby: cizí klient v LAN, MITM, replay, podvržený QR/URL, únik tokenu, vyčerpání fronty OCR, přetrvávající overlay, nesprávné endpointy. TLS pro externí komunikaci, ověřování protistrany, omezené velikosti a fronty, zrušení relace, timeouty, validace vstupů. Nepoužívat plošné výjimky TLS/ATS, žádné společné tajné klíče v aplikaci. Ověřit migraci starého párování bezpečným způsobem, případné nutné nové párování transparentně vysvětlit.
- Bezpečné buildy: nejmenší oprávnění CI, klíče mimo repozitář, pinning nástrojů/závislostí, kontrola supply chain a zranitelností, podepsané artefakty. Zdokumentovat incidenty, opravy a retest.

## 3. Specifikace a architektura
- Native Swift + Kotlin, společné Android/TV jádro; malé moduly (session, capture, OCR, translation, caption, pairing, profile, settings, UI, distribution). Rozhraní služby s explicitním stavem available/unsupported/failed; žádný placeholder vydávaný za hotovou funkci.
- Sdílené specifikace (nikoli přebírání kódu): lokalizace CS/EN/SK, formát profilů, komunikační verze, defaulty, zóny, UX a testovací scénáře. Tři konzistentní platformy, přístupnost, velké písmo a ovladač TV.
- Vydání: dočasně izolovaná testovací identita jen pokud je potřeba paralelní instalace; konečné původní bundle/package ID a migrační/signing strategii ověřit před přechodem. Store/GitHub buildy oddělené; Google Play updater nesmí obsahovat vlastní instalátor ani oprávnění REQUEST_INSTALL_PACKAGES; GitHub podepsané APK zůstanou.
- Zvolit přijatou stabilní verzi Xcode, AGP/Gradle/Kotlin/NDK kompatibilní s požadavky, nikoli automatické latest. Uzamknout dependency graph, zkontrolovat build z čistého checkoutu.

## 4. Implementační pořadí s kontrolami
1. Inventura funkcí a původu + schválené závislosti a datové toky, rozhodnutí o licenční strategii.
2. Samostatný build skeleton, CI, bezpečné defaulty, privacy manifest pro skutečné API, platformní permission policy, žádné zatím neschválené SDK.
3. Společné modely a navigace, lokální profily, nastavení a jazyky; testy migrace a přístupnosti.
4. Lokální snímání obrazu/OCR a zóny; životní cyklus, oprávnění, stabilita a benchmark.
5. Překladový pipeline (stejný funkční rozsah), až po ověření práv poskytovatelů; cache, slovník, atribuce, privacy testy.
6. Autentizované párování a TV titulky (standardní/automatické), testy ztráty spojení, zneplatnění klíče, čištění a interoperability.
7. Konzolové adaptéry: Xbox a PS4/5 pouze s právně a technicky prověřenou integrací; více konzolí, registrace, PIN/QR, reconnect a audio bez mikrofonu. Audit nativních binárek včetně 16 KB.
8. Vyčištění iOS a parity všech stránek; vlastní design, zpět, nápovědy, velikosti, orientace a D-pad. Vlastní GitHub update mimo store.
9. Ověření skutečného podepsaného produktu, regresní porovnání s uloženým baseline, privacy/dependency evidence, distribuční dokumentace.
10. Schválení vlastníkem, teprve poté merge/release a odeslání do obchodů.

## 5. Apple App Store
- K 2026-11-28: Xcode 26 nebo vyšší a iOS SDK 26 nebo vyšší (od 2026-04-28); Apple oznámil SDK 27 a target min iOS 15 od dubna 2027, prověřit při skutečném vydání. SDK není minimální systém uživatele. Náš dosavadní deployment target 17 ponechat pouze pokud jej zvolená sada API podporuje.
- App-level PrivacyInfo.xcprivacy s reálnými kategoriemi required-reason API a jejich schválenými důvody; SDK manifesty a podpisy dle aktuálního seznamu; vyhodnotit skutečný privacy report a App Privacy details včetně SDK. Jasné účelové texty kamera/LAN. Tracking nezavádět; pokud by vznikl, nové posouzení ATT.
- Platný distribuční certifikát/provisioning, správný identifikátor, build number, archive a export pro Store, TestFlight. Nepoužívat unsigned sideload IPA jako podání. Posoudit šifrování/export compliance; neurčovat výjimku bez posouzení.
- App Store Connect: název, popis, lokalizace, ikona, pravdivé screenshoty, věková klasifikace, support URL, veřejná privacy URL, distribuční území, DSA trader status a ověření kontaktů podle skutečného statusu.
- Review: úplně funkční aplikace, testovací přístup nebo plnohodnotný demonstrační režim dle pravidel a případně video pro externí hardware; detailní kroky pro Xbox/PS/TV, schválená práva k externím službám, vysvětlení lokálního OCR a omezení. V podkladech žádné soukromé tokeny nebo běžné účty vlastníka.
- Účet Apple Developer Program obvykle 99 USD/rok; nezakládat ani neplatit bez vlastníka.

## 6. Google Play (Android a Google TV)
- K 2026-11-28 target API 36 telefon, API 34 TV. Před vydáním ověřit aktualizace požadavků. 16 KB: vyžadovat od prvního nativního modulu a pro obě varianty; TV quality TV-G6 platí od 2026-08-01, obecná stránka k 16 KB uvádí blokování nekompatibilních aktualizací od 2027-02-01. Obě ARM ABI; zkontrolovat také všechny další distribuované ABI a skutečné split APK z AAB, ELF/ZIP alignment a 16 KB runtime. NDK r28+ jako kandidát, ověřit konkrétní toolchain.
- Podepsaný release AAB, Play App Signing a vlastní upload key; plán kompatibility stávajících GitHub podpisů, neprovádět nevratnou registraci klíčů bez vlastníka. Příslušné package ID, rostoucí versionCode. Oddělený self-updater; zkontrolovat i výsledné manifesty.
- TV: LEANBACK_LAUNCHER, touchscreen není požadován, správné TV bannery a ikonografie, dálkové ovládání/D-pad bez slepých míst, 10-foot UI, zpět a obnovení stavu, kontrola fyzického TV a Play TV review.
- Play Console: privacy URL a zásady uvnitř app, Data safety včetně SDK, App access instrukce a účty/demonstrace, obsah/age rating, deklarace reklam (bez reklam pokud odpovídá skutečnosti), target audience, foreground service types a požadované důkazy; relevantní přístup k citlivým oprávněním zdůvodnit.
- Ověření vývojáře, správné identity a kontakty, status obchodníka pro EHP dle pravidel; registrace standardně 25 USD jednorázově. Pro nové osobní účty po 2023-11-13 typicky uzavřený test nejméně 12 testerů přihlášených nepřetržitě 14 dní před žádostí o produkční přístup. Nezaměnit schválení přístupu s garantovaným schválením app.
- Ověřit Android developer verification pro distribuci mimo Play v cílových zemích a termínech. Žádná změna klíčů bez schválení.

## 7. Povinná definice hotové funkce / release gate
Každá etapa: vlastní implementace s doloženým původem; licence všech přidaných závislostí a oprávnění služeb; datový tok a aktualizované privacy podklady; minimální oprávnění; negativní i pozitivní testy; regresní parity; čistý build; kontrola kódu. Chybějící právní podklad = blocker, nikoliv zaškrtávací výjimka. Změny bez skutečných testů označit neověřené.
Před vydáním: aktualizovaný SBOM a notice bundle skutečného release, hash a podpisy, manifesty a AAB split APK, 16 KB runtime, testy na iPhone/Android/Google TV a konzolích, demo, bezpečnostní scan a kontrola úniku údajů včetně SDK, store formuláře v souladu s naměřeným chováním, právní rozhodnutí o AGPL a právech služeb, schválení vlastníkem. Nepouštět opakované kompletní CI kvůli každému textovému bodu; malé lokální testy průběžně a jeden společný build u připraveného kandidáta.

## Oficiální zdroje (ověřeny 2026-11-28; před podáním obnovit)
- Apple: https://developer.apple.com/app-store/review/guidelines/ ; https://developer.apple.com/help/app-review/before-submitting-for-review/complete-review/
- Apple SDK/privacy: https://developer.apple.com/news/upcoming-requirements/ ; https://developer.apple.com/app-store/submitting/ ; https://developer.apple.com/support/third-party-SDK-requirements/ ; https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api ; https://developer.apple.com/app-store/app-privacy-details/
- Apple export/DSA/členství: https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance/ ; https://developer.apple.com/help/app-store-connect/manage-compliance-information/manage-european-union-digital-services-act-trader-requirements/ ; https://developer.apple.com/help/account/membership/program-enrollment/
- Google Play: https://developer.android.com/google/play/requirements/target-sdk ; https://developer.android.com/guide/practices/page-sizes ; https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality ; https://developer.android.com/training/tv/publishing/checklist
- Google privacy a distribuce: https://support.google.com/googleplay/android-developer/answer/10144311 ; https://support.google.com/googleplay/android-developer/answer/10787469 ; https://support.google.com/googleplay/android-developer/answer/16559646 ; https://support.google.com/googleplay/android-developer/answer/13392821 ; https://support.google.com/googleplay/android-developer/answer/14151465 ; https://support.google.com/googleplay/android-developer/answer/6112435
- Android snímání a bezpečnost: https://developer.android.com/media/grow/media-projection ; https://developer.android.com/develop/background-work/services/fgs/service-types ; https://developer.android.com/privacy-and-security/risks/cleartext-communications
- ML Kit: https://developers.google.com/ml-kit/terms ; https://developers.google.com/ml-kit/language/translation/translation-terms
- GDPR: https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng/ ; AGPL: https://www.gnu.org/licenses/agpl-3.0.en.html

Pracovní dokument pouze pro přepis, po dokončení sloučit do stručné provozní dokumentace a zachovat povinné licenční/privacy informace. V této etapě nejsou nové aplikace implementované ani připravené k vydání.
