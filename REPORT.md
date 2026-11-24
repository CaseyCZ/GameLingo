# GameLingo 0.5 — audit a pracovní checkpoint
Datum: 2026-11-24. Stav: P0.1–P0.3 sestaveny, P0.4 čeká na CI.

## Základ a pravidla
- Na výslovné upřesnění uživatele je auditní větev založena na poslední vlastní vývojové verzi 0.5, nikoliv na Masteru. Původní dokumentační checkpoint `92026c899c3ebb2db0b1e917cb7ed29647c642d8` byl zachován obsahem v tomto souboru.
- Auditovaný vývojový základ: `feature/gamelingo-v05-platform-rebuild` @ `e0e4c8b290961ffdad3f6902fca64a09346b9176` (480 commitů před Master, 0 za, 94 změněných souborů).
- Master obsahuje pouze README, LICENSE, ATTRIBUTION a dvě dokumentace. Neobsahuje aplikace, build ani testy. Auditované vlastnosti tedy na samotném Masteru nelze potvrdit.
- **Základ opraven:** auditní větev vychází přímo z uvedené revize 0.5, včetně třísloupcového nastavení TV. Kontrola triggerů všech 11 workflow: push publikačních workflow je omezen na jiné konkrétní větve; žádné se automaticky nespouští na auditní větvi. Nikdy ručně nespouštět publish workflow. Další krok: baseline build/test před P0.1.
- Každý implementační bod oddělený commit, po něm build/test, pokud dostupné; neopravené selhání revertovat a zapsat. Žádný release. Zachovat modrobílý design, čtyři oblasti, nezávislou geometrii, konzole, profily, port fallback, platformy a jazyky. AGPL-3.0, ATTRIBUTION.md a původní autorská oznámení zachovat. Nekopírovat konkurenci.
- Android/TV vzniká z PlayCaption @ `633a847400b6c4bac1e7f034463e7847a53a9590` a cílených build patchů; níže výslovně rozlišujeme upstream a patch. iOS Chiaki z TP Play @ `482611539977496cad047560c9bc1ebc5809a566`.

## Fáze 0: výsledky statické kontroly
| Oblast | Zjištění na uvedené revizi 0.5 |
|---|---|
| iOS OCR | CaptionPipeline.swift 58–71: 600/420/280 ms; samostatné intervaly snímků 550/370/260 ms. |
| Android OCR | tools/GameLingoOCRConfig.kt 28–35: 100/85/70 ms; tools/redesign_gamelingo.py kolem 560–566 zapojuje do telefonního RemotePlayService, TV ponechává původní intervaly. Nejde o naměřenou latenci. |
| Žlutá oblast iOS | CaptionPipeline.swift 178–200: nezávislá větev, guard > 1 s. |
| iOS cache | CaptionPipeline.swift 256–289: RAM, klíč zdroj/cíl/text, při count >250 před vložením vymaže všechny (může dosáhnout 251); bez trvalého uložení v této implementaci. Pětisekundový timeout Google endpointu, při chybě vrací originál; stejný zdroj/cíl je legitimní zvláštní případ. |
| TV PIN | tools/IPhoneTVPairing.kt 34–85: šestimístný PIN, 180 s, 5 platných pokusů, náhodný 32bajtový klíč. Retry úspěšné žádosti 15 s. |
| TV klíč | IPhoneTVPairing.kt 32,73–74,100–113: jediný phone_id/token v privátních SharedPreferences, bez Keystore; HMAC SHA256 a konstantní porovnání. |
| TV replay | patch_tv_iphone.py 120–154: TEXT/STYLE podpis nepokrývá čas/sekvenci; PING nonce není evidován přijímačem jako jednorázový. Transport UDP není šifrován, párovací tajemství jde v odpovědi OK. TLS ani šifrování přenosu nyní neimplementovat; riziko důvěryhodné LAN. |
| Legacy | patch_tv_iphone.py 94–160, 368–370 a wire_android_devices.py 107–140: PCT1/PCV1 stále přijímané bez podpisu, Android stále vysílá starý protokol. Pozor i na příběhové a clear/preview zprávy. |
| Port | patch_tv_iphone.py 68–86: 41989 až 41993; GameLingoTVPairing.kt: GLPORT1 discovery. Zachovat. |
| iOS staré výsledky | PlayCaptionIOSApp.swift 425–530, CaptionPipeline.swift 89–101: sessionRevision, revize rozpoznání, samostatná větev herních textů. Limit stáří je volitelný skipOldTranslations, nikoli povinný. |
| Android staré výsledky – oprava dřívějšího auditu | Připnutý upstream RemotePlayService.kt již má relaceCteni (79, 607–639, 899–929, 2235–2236, 2367–2368), PlatnostPrekladu (1242,1290), posledniUkazany (1292–1295), volby revize (1781–1810,1859–1923). Ochrany existují; P1.9 je sjednocení a regresní ověření, ne vývoj od nuly. Vygenerovaný výstup všech patchů dosud nesestaven. |
| Android OCR – oprava dřívějšího auditu | Upstream RemotePlayService.kt 846–868 a 1381–1405 už porovnává otisk posledního OCR (mřížka 96×24), a 987–990 obsahuje stabilitu titulku; P1.1/P1.5 zachovat a ověřit, neduplikovat. |
| Android překladač – oprava dřívějšího auditu | Upstream Prekladac.kt 115–176 má alternativní online služby a ML Kit offline fallback, success/error callback; wire_gamelingo_languages.py 39–70 přizpůsobuje jazyk. Není doložen jednotný circuit breaker ani SQLite. Offline nový engine pouze návrh; existující funkci neodstraňovat. |
| HDMI | Upstream OverlayService.kt 169–175,350–355 používá TYPE_APPLICATION_OVERLAY, NOT_FOCUSABLE, NOT_TOUCHABLE. Samostatná vrstva již existuje; fyzický HDMI test nutný. |

## Testy a commity
- F0: tento dokument (commit `docs: record phase 0 audit and baseline checkpoint`). Statické čtení připnutých souborů a porovnání větví provedeno.
- Build/test po původním F0 nebyl možný na Master základu. Po opravě základu je nutný baseline build/test; existující workflow push triggery auditní větev nezahrnují. Žádný test nebyl označen za úspěšný. Aplikační kód nebyl změněn.
- P0/P1/P2: dosud nezačato. Zaznamenávat SHA, příkazy/workflow, výsledek, revert a zbývající rizika po každém bodu.

## Fronta úkolů
- [x] F0 — průzkum a checkpoint
- [x] Založit auditní větev přímo z poslední vývojové revize 0.5 a ověřit push triggery publikace.
- [x] Baseline build/test na auditní větvi: run 45343148434, všechny tři nativní buildy, kontroly a balíčky success.
- [x] P0.1 povinné HMAC všech typů titulků, legacy opt-in default off — CI success (45344083071), HW čeká.
- [x] P0.2 ochrana proti replay (podepsaná sekvence/nonce, restart relace a ochrana pořadí) — CI success (45345559792), HW čeká.
- [x] P0.3 Keystore na TV — CI success (45346535102), instrumentation zkompilována, runtime/HW čeká.
- [x] P0.4 více telefonů a jednotlivé odpojení — poslední kumulativní CI 45348736716 success; HW čeká.
- [x] P0.5 konzole + HDMI; ruční QA matice doplněna, CI 45349556066 success; HW čeká.
- [x] P1.1 obrazový otisk posledního OCR, periodická kontrola a krátké věty — CI 45353138433 success; HW měření čeká.
- [x] P1.2 nejnovější snímek, omezená fronta — CI 45356504392 success; HW čeká.
- [x] P1.3 SQLite LRU ~20k, engine/jazyky — CI 45357244015 success; HW měření čeká.
- [x] P1.4 explicitní success/error, žádný falešný překlad — CI 45359504848 success; HW čeká.
- [x] P1.5 stabilizace, 800 ms / max ~4 s při stejném obrazu — CI 45360814692 success; HW čeká.
- [x] P1.6 circuit breaker (2 chyby/30 s/1 pokus) — CI 45362779460 success; HW čeká.
- [ ] P1.7 latence, paměť, teplota, nečekané konce
- [ ] P1.8 společná semantika OCR režimů a dynamická žlutá oblast
- [ ] P1.9 jednotná ochrana starých překladů
- [ ] P2.1 QR párování
- [ ] P2.2 návrh automatické instalace TV
- [ ] P2.3 historie
- [ ] P2.4 slovník a kontext profilu
- [ ] P2.5 návrh offline překladu
- [ ] P3 návrh AI, dabingu, Samsung/LG; žádná implementace

## Ruční QA – předběžná matice (vše neověřeno na této revizi)
| Zdroj | Konzole | Výstup | Stav |
|---|---|---|---|
| iOS | Xbox / PS4 / PS5 (každá zvlášť) | telefon + TCL C835 HDMI | čeká |
| Android mobil | Xbox / PS4 / PS5 (každá zvlášť) | telefon + TCL C835 HDMI | čeká |
| Android TV | Xbox / PS4 / PS5 (každá zvlášť, podle podporovaného režimu) | samostatný overlay | čeká |
| Oba telefony | postupně připojené | jedna TV, změna portu, odpojení každého | čeká |
Ověřit čtyři oblasti, oranžový náhled, profily, barvy, přepnutí HDMI, restart, výpadek sítě, staré pakety, delší relaci. Výsledek zapsat pro každou kombinaci, ne zaměnit CI s HW testem.

## Odložená architektura (pouze návrh)
- Šifrování transportu/TLS: případně samostatné budoucí bezpečnostní rozhodnutí; nyní HMAC není důvěrnost.
- Automatická instalace: opt-in přes oficiální ADB párování tam, kde je dostupné, jinak manuální APK; žádné obcházení oprávnění.
- Offline: abstrakce poskytovatele, stažení modelu se souhlasem, dostupnost jazyka, velikost/úložiště, lokální inference; zachovat existující Android ML Kit.
- AI: volitelný poskytovatel za stejným rozhraním, explicitní souhlas se zasíláním textu, kontext omezený na profil.
- Dabing: samostatná audio/ASR/TTS větev s časováním a ochranou před vlastní zpětnou vazbou.
- Samsung/LG: samostatné přijímače dle oficiálních SDK a kompatibilního verzovaného protokolu, bez příslibu univerzální HDMI podpory.

## 2026-11-24 — výchozí CI před P0.1
- Samostatný commit `ci: enable audit-only baseline and per-change checks` zapojuje existující rychlé kontroly a celý nativní build přes workflow_call, spouštěné pouze z feature/audit-0.5 při změně kódu/testů/workflow. Pouze contents: read, žádný publish workflow, instalační soubory jen jako Actions artefakty.
- Výchozí kontrola: lokální git ls-remote nedostupné (DNS github.com); dostupný GitHub konektor nemá dispatch API, proto baseline spouští push tohoto CI commitu. Výsledky zatím nepotvrzené. P0.1 ještě nezačal; navázat po ověření runu pro tento commit. Dokumentační změny samy nový nativní build nespouští.

### Výsledek výchozí CI (run 45343148434)
- CI commit `6af49ee02bf271153395cf4e65208e42e4edac96`, [run](https://github.com/CaseyCZ/GameLingo/actions/runs/45343148434). Stav při kontrole: in_progress; Python integration contracts, Python tooling syntax a Parse every iOS Swift source: jednotlivé kroky success. Nativní sestavení zatím bez výsledku.
- Návaznost P0.1: cíleně prověřeny `tools/wire_android_devices.py` (všechny 4 sender rewrite), `tools/GameLingoTVPairing.kt` (Keystore + HMAC), `tools/patch_tv_iphone.py` (receiver dispatch a PCV1), `ios/GameLingoIOS/App/PlayCaptionTV.swift` (sendSignedSubtitle, clear, subtitle, choices, legacy fallback). Před změnou navíc zkontrolovat upstream příběhové zprávy a všechna odesílací/přijímací místa; legacy opt-in na TV i odesílatelích bez automatického downgradu. Žádná aplikační změna dosud necommitována. Další krok: zkontrolovat výsledek tohoto runu a případně opravit baseline, pak jediný atomický P0.1 commit.

## P0.1 — povinný podepsaný přenos
- Baseline run 45343148434 dokončen success: iOS (Chiaki + IPA), Android mobil APK, TV APK, quick checks a SHA-256 balíčků.
- Commit `security: require authenticated TV game data and explicit legacy opt-in`: iOS bez automatického unsigned fallbacku; Android všechny čtyři Vysilac cesty i samostatný příběhový socket podepisují; TV kontroluje HMAC před vstupem do všech herních parserů. Povolené vnitřní typy PCT1/PCV1/PCO1/PCQ1/PCS1 story; původní PCS1 ZADOST/KOD přenos účtu zůstává oddělený. GLPORT1 discovery zachováno. Legacy přepínač pouze na přijímači (default off) pro starší klienty; nové klienty nikdy automaticky nesníží na nepodepsaný režim. Explicitní volba v TV Nastavení > Zabezpečení. Samostatné lokální překrytí není síťový přenos.
- Přidán regresní kontrakt do test_v05_integrations.py; plný build/test spustí auditní CI. P0.1 zatím čeká na výsledek; nepovažovat za HW ověřené. P0.2 replay, P0.3 TV Keystore a P0.4 více telefonů zůstávají otevřené. P0.1 zajišťuje podpis herních dat, ne důvěrnost, ani autentizaci potvrzení přijímače.

- P0.1 commit `b2fe43209049a386fcbdb6c712707358e20686e3`; [CI run 45344083071](https://github.com/CaseyCZ/GameLingo/actions/runs/45344083071). Stav při poslední kontrole: quick / verify: success; native / GameLingo · integration contracts: success; native / Android phone · PS5 / PS4 foundation: in_progress; native / Google TV · Orange V5 receiver: in_progress; native / iPhone · unsigned IPA: queued. Před P0.2 ověřit dokončení všech jobů; pokud P0.1 neprojde, opravit nebo revertovat podle pravidel. Fyzický test podepsaných dialogů, voleb, preview, story, clear, legacy off/on a port fallback stále čeká.

## P0.2 — ochrana proti replay
- P0.1 run 45344083071 dokončen success: quick, contracts, iPhone, Android mobil, Google TV, balíčky/SHA-256. HW dosud neověřeno.
- P0.2 samostatný commit: TEXT/STYLE/PING podepisují i timestamp v ms a rostoucí wire sequence; přijímač kontroluje stáří ±120 s, HMAC a per-lane high-water (dialog, volby, OCR, story, styl, ping). Ukládá sequence synchronně před vykonáním, odmítne opakování i po restartu; při novém spárování resetuje high-water spolu s rotací klíče. Každý legitimní retransmit je nově podepsán s vyšším číslem. PONG je podepsaný a vázaný na čerstvý jednorázový nonce, iOS jej spotřebuje. Starší verze podepsaného protokolu bez freshness jsou odmítnuty; kompatibilita unsigned pouze explicitním legacy na TV.
- Rizika: požaduje přibližně synchronní hodiny (automatický čas); při velkém posunu dozadu může být nutné nové párování. Synchronní zápisy vyžadují zátěžové měření (P1.7). Legacy záměrně obchází replay; bez TLS není důvěrnost. Potvrzení story/PCA1 dosud nejsou kryptograficky ověřená, stará aplikace může nepřijmout nový protokol. P1.9 řeší aplikačně zastaralý obsah, nejen síťové přehrání.
- Přidán statický regresní test; CI po commitu. Ruční test zachyceného opakovaného paketu, prohozeného pořadí, restartu TV, nové relace, UDP opakování, story a odchylky hodin čeká. P0.3 teprve po výsledku tohoto runu.

- P0.2 commit `d8cb12210270cefc2463fbdbf0f001ae0d8d5f77`, [CI run 45345559792](https://github.com/CaseyCZ/GameLingo/actions/runs/45345559792). Poslední stav: quick / verify: in_progress. Další krok: ověřit všechny joby, případně opravit/revertovat, pak P0.3. P0.2 zatím neoznačovat za otestovaný na HW.

## P0.3 — šifrované uložení párovacího klíče na TV
- P0.2 run 45345559792 dokončen success: všechny buildy, kontrakty, balíčky a SHA-256. HW čeká.
- Samostatný commit `security: protect TV pairing keys with Android Keystore`: AES-256-GCM, náhodné IV, AAD vázané na phone_id, samostatný alias přijímače, ciphertext `token_v1` v privátních preferencích. Původní `token` je jednorázově odstraněn při startu TV/receiveru; platná migrace zachová identitu i replay high-water, při selhání je nutné nové párování. Žádný návrat k plaintext ani při poškození ciphertextu. Retry PIN načítá klíč z chráněného úložiště, nikoli z dlouhodobě uložené RAM kopie. Explicitní odpárování odstraní záznam a alias. Síťový formát nezměněn. Upstream minSdk 26, allowBackup=false.
- Přidán statický kontrakt a tři Android instrumentation testy: migrace + replay, poškozený ciphertext + zákaz plaintext fallbacku, nové párování + retry + clear. TV CI navíc kompiluje androidTest APK (nepřidává ho do distribučních balíčků). Běh instrumentation na skutečné TV nebo emulátoru zatím neproveden; příkaz v připraveném upstream checkoutu `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.titulky.tv.IPhoneTVPairingKeystoreTest`. POZOR: testy mažou testovanou párovací databázi; spouštět jen na vyhrazeném testovacím zařízení/instalaci.
- Rizika: Keystore nemusí být hardwarový, při ztrátě/poškození klíče nové párování (případně nejdříve odpárovat na TV); starší diskové kopie nelze garantovaně fyzicky vymazat. Neprovádíme šifrování síťového přenosu. Náklady dešifrování při HMAC ověřování měřit v P1.7. P0.4 až po výsledku CI tohoto commitu.

- P0.3 commit `bcce7415a7950a5412e1de97a72e3f275b3b09d3`, [CI run 45346535102](https://github.com/CaseyCZ/GameLingo/actions/runs/45346535102). Poslední stav: quick / verify: success; native / GameLingo · integration contracts: queued. Další krok: ověřit všechny joby včetně kompilace androidTest, případně opravit/revertovat P0.3, následně P0.4. Runtime Keystore testy dosud neprovedeny.

## P0.4 — více telefonů a jednotlivé odpárování
- P0.3 run 45346535102 dokončen success včetně kompilace androidTest APK. Runtime testy Keystore nadále čekají na vyhrazené zařízení.
- Samostatný commit `feat: support multiple TV phone pairings with isolated replay and revocation`: max. 32 párování, každé má vlastní 256bit tajemství, ciphertext AES-GCM vázaný AAD na ID a replay high-water pro jednotlivé lanes. Společný obalový Keystore klíč zůstává v zařízení. Migrace single-phone plaintext/v1 na v2 bez ztráty platného klíče nebo replay hodnot. Nový PIN rotuje jen klíč daného ID. Odpárování jednotlivě odstraní pouze vybraný token, replay a retry stav. Seznam v TV dialogu `Spárované telefony`, označení iPhone/Android + koncovka ID; starší migrace označena Telefon. Změna PAIR je kompatibilní volitelným polem platformy, nikoliv změna jazyka aplikace.
- Jedna TV = jeden aktivní zdroj herního textu, automatické převzetí po 5 s bez game-data; PING jiného telefonu zdroj neobsazuje. Změna zdroje/revokace resetuje dedup dialogů/voleb, story receiver, vyčistí overlay oblasti. Legacy nesmí přepsat aktivní podepsaný zdroj. Ruční výběr zdroje není implementován, zastavení vysílání na původním telefonu uvolní lease. Připojené telefony mohou současně ověřovat PING. Platnost ověření v UI není důkaz zobrazení.
- Přidány statické kontrakty a instrumentation test více telefonů, oddělených replay čítačů, revokace a rotace. Existující migrace/poškození testy přizpůsobeny v2. Full CI po commitu. Rizika: HW souběh iOS/Android, změna zdroje, story reset, odpárování při probíhajícím streamu, migrace a poškození Keystore čekají. V případě selhání persistence přijímač fail-closed; restart/nové párování nebo reset appky může být potřeba. P0.5 až po CI.

- P0.4 commit `9944475ddb2987af4b25a633efa78c102ced6829`, [CI run 45347464381](https://github.com/CaseyCZ/GameLingo/actions/runs/45347464381). Poslední stav: quick / verify: success; native / GameLingo · integration contracts: in_progress. Před P0.5 zkontrolovat všechny joby, případně opravit/revertovat. Zvláštní otevřené riziko: původní Android Vysilac může periodicky posílat i prázdný stav; ověřit uvolnění pětisekundového lease po zastavení překladu a přepnutí na druhý telefon. Pokud prázdné heartbeat drží zdroj, opravit v rámci P0.4 před P0.5 (neoznačovat automatické přepnutí za HW ověřené). Testy na zařízení stále čekají.


## P0.5 — průzkum konzolí a HDMI (před implementací)
- Auditovaná revize `8d3a90fafed24f0d1df3a7150e7bc0c10114283b`, upstream PlayCaption `633a847400b6c4bac1e7f034463e7847a53a9590`.
- P0.4 CI 45347464381: quick / verify: success; native / GameLingo · integration contracts: success; native / Android phone · PS5 / PS4 foundation: success; native / iPhone · unsigned IPA: in_progress; native / Google TV · Orange V5 receiver: in_progress. P0.4 dosud neuzavírat, pokud není celý run success a riziko source lease vyřešeno.
- P0.4 upřesnění: upstream TelefonActivity.kt 218–225 při běžném Stop volá Vysilac.zmena("",0,"") a nastavCil(null,""), takže opakování skončí. RemotePlayService.kt 224–231 (ACTION_STOP), 2232–2260 a 2363–2385 (zastav/onDestroy) však samy cilIp neresetují. Propojeni.kt 529–554 opakuje stav každou sekundu i při neaktivní službě, dokud se nezmění generace cíle. Na zastavení z jiné obrazovky/služby tak může držet P0.4 lease. Cílená oprava P0.4 po CI: zastavit periodické odesílání při neaktivní RemotePlayService mimo časově omezený explicitní náhled, po stop vyčistit výstup; zachovat uložený TV profil a bez restartu umožnit nové spuštění. Otestovat běžný Stop, stop z hlavní nabídky, pád spojení, náhled bez streamu a handover. Nepřepisovat celý sender.
- iOS: PlayCaptionIOSApp.swift 406–423, 558 a PlayStation/GameLingoPlayStation.swift 44–94 + RemotePlaySession.swift 64–111: Xbox a PS4/PS5 mají cestu dekódovaných snímků do OCR; PS4 H.264, PS5 HEVC; fyzicky neověřeno.
- **Oprava dosavadního auditu PS4 Android/TV:** upstream HledacPS.kt 23–52 skutečně vyhledává PS4 i PS5, ale RemotePlay.kt 66–67 registruje výhradně Target.PS5_1; RemotePlayService.kt 655 má ConnectInfo(ps5 = true). GameLingoPlayStationConsoles.kt ukládá IP, jméno a registrační údaje, nikoli typ konzole. Žádný z build patchů tyto dvě konstanty nemění. Tedy PS4 discovery ano, PS4 registrace/stream na Androidu nedokončen. Název CI jobu PS5/PS4 foundation není důkaz podpory. P0.5 implementační úkol: uchovat typ PS v registraci, aktivním profilu i při přepnutí, volit správný Target a ConnectInfo pro PS4; zkontrolovat discovery fallback a kodek. PS4 Android ani TV neslibovat jako fungující, dokud fyzicky neověřeno.
- Xbox Android: tools/android_multi_xbox.py volí uložené selectedXboxId, upstream RemotePlayService.kt 456 čte XboxUcet.konzole. PS5 Android: připnuté Chiaki jádro. Nejde o fyzický test.
- HDMI: upstream OverlayService.kt používá TYPE_APPLICATION_OVERLAY, žádné zachytávání/přehrávání HDMI v appce. PrijemService.kt 98–135 spouští receiver a zastavuje vlastní Remote Play. tools/patch_tv_iphone.py 222–229 již nastavuje vstup=true pro standardní titulky a volby, aby TCL neblokoval HDMI. **Nekonzistence story:** PrijemService.kt 131 a OverlayService.kt 51 dále používají Popredi.jeVstupTv(); ten může chybně vyhodnotit HDMI podle usage stats/launcher. P0.5 cílená oprava: konzistentní policy pro telefonní story a přímo na TV, nerozbít lokální režim; zkontrolovat také RemotePlayService.kt 800 (přerušení OCR na základě Popredi). Povolení overlay je nutné, nativní HDMI není zobrazováno uvnitř aplikace. Zvážit test známý HDMI balíček, neznámý stav a přepnutí vstupu; dokumentovat riziko překryvu mimo hru. Nezměnit jazykové nabídky/design.
- Tento dokumentační checkpoint neobsahuje změnu aplikačního kódu. Po P0.4 CI a případné opravě lease navázat P0.5, samostatný commit s regresními testy a plným CI.

### P0.5 — detailní ruční akceptační matice
Všechny případy čekají na test na zařízení. Zapsat model, firmware/OS, konzoli, SHA, datum, PASS/FAIL/BLOCKED. Statický kód a build nenahrazují HW.

| ID | Zařízení | Konzole | Režim / výstup | Stav |
|---|---|---|---|---|
| C01 | iPhone | Xbox | Remote Play → telefon | HW čeká |
| C02 | iPhone | PS4 | Remote Play → telefon | HW čeká |
| C03 | iPhone | PS5 | Remote Play → telefon | HW čeká |
| C04 | iPhone + TCL C835 | Xbox | telefon → HDMI overlay | HW čeká |
| C05 | iPhone + TCL C835 | PS4 | telefon → HDMI overlay | HW čeká |
| C06 | iPhone + TCL C835 | PS5 | telefon → HDMI overlay | HW čeká |
| C07 | Android mobil | Xbox | Remote Play → telefon | HW čeká |
| C08 | Android mobil | PS4 | Remote Play → telefon | BLOCKED: PS4 registrace/stream nedokončeny |
| C09 | Android mobil | PS5 | Remote Play → telefon | HW čeká |
| C10 | Android mobil + TCL C835 | Xbox | telefon → HDMI overlay | HW čeká |
| C11 | Android mobil + TCL C835 | PS4 | telefon → HDMI overlay | BLOCKED: PS4 registrace/stream nedokončeny |
| C12 | Android mobil + TCL C835 | PS5 | telefon → HDMI overlay | HW čeká |
| C13 | TCL C835 | Xbox | přímo na TV → HDMI overlay | HW čeká |
| C14 | TCL C835 | PS4 | přímo na TV → HDMI overlay | BLOCKED: PS4 registrace/stream nedokončeny |
| C15 | TCL C835 | PS5 | přímo na TV → HDMI overlay | HW čeká |

| ID | Scénář | Očekávaný výsledek | Stav |
|---|---|---|---|
| H01 | Každá C04–C06/C10–C15: HDMI1 → HDMI2 → původní vstup | obraz hry není nahrazen appkou, překryv funguje po návratu | čeká |
| H02 | Příběhový režim a standardní dialogy | konzistentní HDMI chování, story se nezastaví chybnou detekcí | čeká |
| H03 | Žlutý a oranžový náhled i bez textu | správné nezávislé oblasti, viditelné i ovládání náhledu | čeká |
| H04 | Odmítnutí/obnovení overlay oprávnění | žádný pád, pravdivý stav, po povolení funkční | čeká |
| H05 | PlayCaption obsadí 41989, restart TV služby | GameLingo použije 41990–41993 a klient ho znovu najde | čeká |
| H06 | iPhone + Android současně | oddělené párování, pouze jeden aktivní zdroj | čeká |
| H07 | Stop v telefonu, hlavním menu, službě; přepnutí na druhý telefon | prázdné heartbeat nezablokuje handover; žádné staré překryvy | čeká |
| H08 | 30+ min hry, Wi-Fi off/on, změna IP | návrat funkce nebo jasná chyba, žádné falešné potvrzení | čeká |
| H09 | Profil, barvy, velikost, nezávislá geometrie | zachováno po restartu | čeká |
| H10 | Přepnutí PS4 ↔ PS5 a více Xboxů | přesně vybraná konzole, správný typ a registrace | čeká; PS4 Android blokováno |

Další krok: CI 45347464381 → cílená oprava P0.4 lease → CI → P0.5 implementace a testy. Žádný release.

## P0.4 — následná oprava před uzavřením
- Původní run 45347464381: všech šest jobů success. Runtime testy neprovedeny. Nový samostatný commit `fix: release idle TV caption source without losing selected receiver` odstraňuje zjištěnou regresi handoveru. Android Vysilac opakuje pouze během aktivního RemotePlayService nebo omezeného náhledu; onDestroy invaliduje staré generace, vyčistí dialog/volby/oblast a zachová vybraný TV profil pro další start. Přijímač nezíská ani neprodlouží lease prázdným dialogem/volbami, skrytím rámečku nebo story readiness; clear od jiného zdroje nemůže přepsat aktuálního vlastníka. Viditelné náhledy stále mohou získat lease. Pětisekundová prodleva od posledních skutečných herních dat zůstává. Trvalý náhled mimo relaci se neopakuje bez omezení; ruční zobrazení funguje okamžitou zprávou. Přidán regresní kontrakt a instrumentation test klasifikace/oddělení zdrojů. CI čeká; HW handover, stop/restart, preview a příběhový režim nutno ověřit. P0.5 implementace PS4 a HDMI až po úspěchu tohoto commitu.

- P0.4 opravný commit `35b8c395b9d2167e3feae382e51f3cf9dbfebe43`. CI 45348404394: in_progress, bez výsledku. [Run](https://github.com/CaseyCZ/GameLingo/actions/runs/45348404394). Otevřené ověření: PCQ1 readiness druhého story telefonu po vypršení lease musí být možné bez získání vlastnictví; aktuální větev nepovoluje readiness od jiného ID, dokud je původní activePhone uložen. Prověřit a případně cíleně opravit v P0.4 před P0.5. Neoznačovat handover story za hotový. P0.5 stále čeká na úspěšný build P0.4.

## P0.4 — cílená oprava readiness příběhového režimu
- Run 45348404394: rychlé a integrační kontroly success, nativní buildy při kontrole stále běží. Samostatný navazující opravný commit v témže bodu P0.4 (nemění P0.5): ověřený PCQ1 dotaz smí po vypršení 5s lease získat odpověď, aniž změní vlastníka, obnoví lease či vykreslí text. Při aktivním jiném zdroji zůstává odmítnut; cizí clear nadále odmítnut i po vypršení lease do skutečného převzetí. Nový text se řídí dosavadní claimGameData. Přidány statické kontroly a device test (před/po vypršení lease, revision beze změny, převzetí až textem). Spuštěn samostatný CI; před P0.5 ověřit oba runy, případnou chybu opravit nebo revertovat. HW neověřeno.
- P0.5 upřesnění z upstream: Chiaki.kt 13–25 má Target.PS4_10(1000), PS5_1(1000100). RemotePlay.kt 66–67 pevný PS5_1; MainActivity.kt 1462 vybírá první PS5, 1499 předává IP/PIN bez typu. RegistEventSuccess.host.target poskytuje skutečný target. RemotePlayService.kt 472 fallback může vybrat jinou konzoli, 655 ps5=true, HEVC podmínka musí omezit na PS5; 2020 kontrola PS5/Xbox jen typ PS5. Při implementaci předávat typ při registraci, uložit target do aktivního i šifrovaného profilu, nezaměnit dvě konzole v discovery. Stávající přenos PS účtu z TV (PCS1 DATA v Propojeni.kt 110–155; MainActivity.kt 1368–1370) musí buď přenést typ kompatibilním polem bez posunu Xbox dat, nebo PS4 převzetí jasně odmítnout a vyžadovat nové párování. Nepředstírat podporu PS4 před funkčním buildem a HW testem.

- Readiness fix SHA `873299cbc4cd34d21c771ec2c41a7bfa6122e2d3`; CI [45348736716](https://github.com/CaseyCZ/GameLingo/actions/runs/45348736716): in_progress, bez výsledku. P0.4 checklist zůstává otevřený do dokončení obou buildů. P0.5 nebyl změněn aplikační kód.

## P0.5 — PS4/PS5 Android + HDMI receiver story
- P0.4 run 45348404394 zrušen při novějším push (concurrency), nikoli potvrzený úspěch. Kumulativní run 45348736716 obsahuje obě následné opravy a dokončil všech šest jobů success. Runtime instrumentation čeká.
- Samostatný P0.5 commit `fix: wire actual PS4 registration and stream target with consistent HDMI story receiver`: explicitní výběr nalezené PS4/PS5, při více konzolích žádný skrytý výběr jiné. Native Chiaki Regist dostává PS4_10/PS5_1, ukládá vrácený target, ConnectInfo.ps5 dle target a PS4 H.264. Aktivní i šifrovaný profil uchovává typ. Starší PS5 záznamy bez target default PS5_1. Volitelné PS_TARGET pole při existujícím převzetí registrace bez posunu PS klíčů nebo Xbox X: markeru. Po změně IP je nutná nová registrace (odstraněn riskantní fallback na jinou konzoli); staré PS4 firmware před PS4_10 nepokrýváme. HW zatím neověřeno.
- TV telefonní story používá stejnou HDMI policy jako normální receiver, včetně overlay ticku. Direct-on-TV zachovává Popredi detekci (známé TCL vstupy) a stávající auto-start. HDMI se nezachytává ani nepřehrává v appce. Riziko překryvu mimo HDMI při explicitním receiver režimu zůstává stejné jako u běžných titulků. Fyzické testy oprávnění, přepnutí vstupů a přímého režimu čekají.
- Přidány source contracts a kontroly vygenerovaného Android/TV kódu. Full CI po commitu. C08/C11/C14: PS4 implementace nyní čeká na CI a HW, neoznačovat za PASS. H10 čeká na výběr správného typu. P0.5 uzavřít až po CI; HW zůstává release gate. Žádný release, Master beze změny.

- P0.5 commit `3efcdc7c7af6c68b9e093747e1c6d320552d3307`, [CI run 45349556066](https://github.com/CaseyCZ/GameLingo/actions/runs/45349556066). Poslední stav: quick / verify: in_progress. Před P1.1 ověřit všech šest jobů; pokud P0.5 selže, opravit nebo revertovat. Fyzická QA C01–C15 a H01–H10 dosud neprovedena. P1.1: nejprve potvrdit stávající Android OtiskOcr a implementovat vlastní otisk posledního přečteného snímku iOS, s periodickou kontrolou, nezávisle pro dialogy/herní texty.

## P1.1 — obrazový otisk před OCR
- P0.5 run 45349556066: všech šest jobů success. P0 implementováno a sestaveno; HW QA stále čeká, žádný release.
- Android ověřen proti připnutému upstreamu: RemotePlayService.kt 846–868 porovnává otisk posledního OCR, 1381–1405 96×24 mřížka hran písma, nucená kontrola po 3 s, stabilizace 200 ms. Herní texty/volby 1741–1810 mají samostatný otisk a opakované čtení po 800 ms. OtiskOcr.kt navíc plný SHA-256 výřezu pro aplikační ochrany. Android implementace zůstává beze změny, nevytváříme duplicitní detektor.
- iOS: vlastní OCRFrameFingerprint vzorkuje maximálně 192×64 jasových bodů pouze v ROI (NV12 video/full range a BGRA), bez kopie snímku; konzervativní prahy, lokální výrazná změna okamžitě vyžaduje OCR. Neznámý pixel formát, chyba locku nebo neplatná ROI → standardní OCR. Samostatný OCRFrameGate dialogy a žlutá oblast, porovnává pouze poslední úspěšně přečtený snímek včetně prázdného výsledku, nikdy přeskočený snímek; nucené nové OCR při první příležitosti po 1.5 s / 2 s, respektuje současné minimální intervaly. Reset při změně ROI/jazyka/režimu a startu relace. Nepřidává zpoždění úmyslným čekáním; detekce v mezích stávajících intervalů. Fallback širší ROI není potlačen otiskem jiné geometrie.
- Přidán statický kontrakt a skutečný Swift executable test s umělými CVPixelBuffer snímky (NV12/BGRA, lokální změna, mimo ROI, drobný šum, pomalá kumulativní změna, reset, periodická kontrola, fail-open, nezávislé oblasti). CI na macOS test kompiluje a spustí před iOS buildem. Výsledek zatím nepotvrzen. Reálné krátké titulky, blikání, světlé scény, barevný text stejné luminance a spotřeba čekají na HW; pravidelná kontrola omezuje, ale nevylučuje ztrátu krátkého textu mezi vzorky. Neměníme OCR režimy/jazyky, překladač, cache, rozvržení ani fronty. P1.2 až po CI.

- P1.1 commit `cfec54dbed024e8b9144d20c483ee3a18bc147e3`, [CI run 45353138433](https://github.com/CaseyCZ/GameLingo/actions/runs/45353138433). Poslední stav: quick / verify: in_progress. Před P1.2 ověřit všech šest jobů včetně skutečného macOS Swift testu; případně opravit nebo revertovat P1.1 podle pravidel. HW výkon nezměřen. Další P1.2: zamezit hromadění Task per frame na MainActor, latest-only mailbox s omezeným počtem snímků; Android ověřit existující frontu, zachovat nezávislé OCR oblasti.

## P1.2 — vždy nejnovější snímek bez FIFO obrazů
- P1.1 run 45353138433: všech šest jobů success včetně skutečných syntetických Swift CVPixelBuffer testů. HW výkon/krátké titulky čekají.
- Android/TV ověřen na připnutém upstreamu: RemotePlayService.kt 45 používá jednu čtečku, 632 acquireLatestImage, 768–795 odmítá snímky při obsazeném OCR nebo intervalu; 2087–2097 Xbox před bg.post rezervuje čtečku a předá výřez, 1775–1811 žlutá oblast má vlastní busy/token. GlSnimac.kt 108–119 updateTexImage pracuje s aktuální texturou. Nejde o neomezenou aplikační FIFO snímků, ponecháno. Story CteciFronta je záměrná fronta již rozpoznaného textu, ne obrazů; neměníme ji. Android watchdog může po timeoutu znovu povolit čtení, HW zátěž ověřit v P1.7.
- iOS: LatestFrameMailbox s NSLock, jediným pending slotem a atomickým přechodem idle/draining; každá relace má dvě nezávislé schránky. Xbox i PS callback nejprve synchronně nabídnou buffer, úloha na MainActor vzniká jen při přechodu idle→drain; další snímky přepisují slot. Max. jeden zpracovávaný a jeden čekající snímek na OCR lane, nikoliv fronta. Volby mimo aktivní nastavení nepřijímají obrazy; deaktivace zahodí pending, stop uzavře obě schránky nevratně a staré callbacky nemohou obnovit relaci. Reset OCR před prvním drainem, zachovány session guards, 4 oblasti, stávající OCR intervaly, dialogový překlad mimo obrazový drain; žlutý překlad může zaměstnat vlastní lane, ale ne dialogový. frameCount nyní počítá obrazy skutečně odebrané dialogue drainem. Neřešíme souběžné síťové překlady (P1.6/P1.9), ani kodekové/transportní buffery. Přidány skutečné Swift testy (10k nabídek, paralelní producenti, reset odpovědnosti, zavření, enable/disable, uvolnění nahrazeného objektu) do rychlého CI a statické kontrakty. Výsledek CI čeká. HW ověřit bursty při 30 fps PS a Xbox, stop/start, přepnutí nastavení a dlouhou latenci překladače; měření P1.7. P1.3 až po úspěchu CI.

- P1.2 commit `d260df9665f1f56b4758334c881a1f28ddf1528f`. CI [45356393524](https://github.com/CaseyCZ/GameLingo/actions/runs/45356393524): quick / verify: failure; native: skipped. Před P1.3 ověřit všechny joby, včetně executable Swift mailbox testu, případně opravit/revertovat. P1.3: trvalá SQLite LRU cache cca 20k, klíč engine+source+target+text; nejprve prověřit upstream Android Prekladac a existující cache. HW stále neověřeno.

- P1.2 run 45356393524 selhal v jediném z 34 Python kontraktů: test_both_translation_lanes_require_verified_tv_receiver očekával původní přesný počet mezer po extrakci dvou OCR funkcí. Kontrolou skutečného kódu potvrzeny obě podmínky verifiedTelevisionOutput bez změny. Následný commit `test: make verified receiver contract independent of indentation` mění pouze test na regex vyžadující stejné podmínky a volání, nikoliv vypnutí kontroly. První Swift executable test a nativní build byly přeskočeny, čeká nový CI. P1.3 zatím nezačal.

- P1.2 opravný test commit `b34bfa906475fc347680c104188cb7cd3ae31056`. CI [45356504392](https://github.com/CaseyCZ/GameLingo/actions/runs/45356504392): quick / verify: in_progress. Před P1.3 ověřit plný kumulativní CI, zejména latest-frame Swift test a nativní build; HW neověřeno.

## P1.3 — persistent SQLite LRU
- iOS vlastní SQLite actor, Android SQLiteOpenHelper + jeden omezený cache worker (128 čekajících cache operací), limit 20 000 řádků na instalaci. Klíč engine+zdroj+ cíl+ přesný vstup (Android zachovává normalizaci anglického slangu). Po hitu aktualizace last_used; po insertu LRU trim v transakci, ne clear-all. UTF-8 a parametrizované SQL, limity vstupu 4096 / výstupu 8192 bytů; prázdný výsledek ani chyba se neukládá. Shodný validní překlad u jmen může být uložen. iOS Application Support, vyloučeno ze zálohy; Android privátní DB (aplikace má allowBackup=false). Žádná synchronizace mezi zařízeními. Lokální texty přetrvají restart, smazání dat aplikace odstraní cache; vlastní volba vymazání bude řešena s historií/diagnostikou. Databázová chyba znamená cache miss, překladače dál fungují. Není šifrovaná historie; žádné klíče/účty v DB.
- Android nahrazuje RAM LruCache 500 ve všech třech vstupech (normal/story/quick), cache před síťovými službami i ML Kit. Skutečný engine se ukládá zvlášť; jazykové snapshoty předány online providerům, při změně jazyka nepoužít starý výsledek. Ponechány existující Google/Microsoft/MyMemory/ML Kit cesty a jejich strategie. iOS odstraněna RAM clear-all ~250, pouze úspěšný Google výsledek do SQLite. Fallback originálu při chybě iOS se v P1.3 nemění, neukládá se; řeší P1.4. Bez nových překladačů nebo jazyků.
- Swift skutečný SQLite test persistence, unicode, engine/jazyk izolace, LRU a blank rejection; Android instrumentation analogický, kopírován do src/androidTest, kompilace v TV CI, runtime HW čeká. Statický kontrakt. CI a HW výkon zatím nepotvrzen. P1.4 až po CI.

- P1.2 kumulativní CI 45356504392: všech šest jobů success včetně Swift mailbox testu a všech nativních balíčků. P1.3 připraven jako kandidát a připojen k auditní větvi až po potvrzení tohoto výsledku.

- P1.3 commit `3b498db0d0736e4dfc6475a02970f2820ee03923`. CI [45357244015](https://github.com/CaseyCZ/GameLingo/actions/runs/45357244015): quick / verify: in_progress. Před P1.4 ověřit všech šest jobů, skutečný Swift SQLite test a Android patch anchors/kompilaci instrumentation; případně opravit nebo revertovat. Android runtime, kapacita 20k a reálné latence dosud neověřeny. P1.4: explicitní úspěch/chyba, zejména odstranit návrat originálu při chybě na iOS a zachovat validní shodný překlad.

## P1.4 — explicitní úspěch/chyba překladu
- P1.3 CI 45357244015: všech šest jobů success; nativní Swift SQLite test prošel, Android instrumentation zkompilována, runtime HW čeká.
- iOS: Result<String, GLTranslationFailure>; HTTP != 200, prázdná/neplatná odpověď, timeout, síť, zrušení a chybný vstup odděleny od úspěchu. Striktní JSON segmenty, cache zápis jen v success. Stejný zdrojový/cílový jazyk je legitimní success bez provideru; platný překlad shodný s originálem (jméno apod.) nezakazujeme. Nezaměňovat selhání s textem. Dialog i seznam voleb přijímají pouze success, při chybě aktuálního výsledku schovají starý překlad; u seznamu žádné částečné indexy. Diagnostika bez citlivých URL nebo provider payloadu. Po chybě může stále viditelný stejný text projít novým OCR/překladem po min. 2 s (stávající OCR gate zachován), bez retry starého revision/oblasti. P1.6 teprve společný circuit breaker a fallback.
- Android: již existující success/error callback API zachováno, doplněno odmítání prázdného vstupu/ML Kit výsledku a strukturálně neplatných Google/Microsoft/MyMemory dat. Zjištěné skutečné chyby upstream RemotePlayService: prelozVse dříve dosazoval replika při selhání normálního překladu; zpracujVolby posílal prekladyVoleb[it] ?: it. Nyní při selhání dávky žádný nový dialog (předchozí validní titulek doběhne podle původní doby), story dostane chybu, neúplné volby schovány bez přesunu indexu, chyba žlutého textu schová pouze výstup aktuální relace. Žádný nový provider, změna designu/jazykové nabídky ani cache neúspěchu. Android existující krátkodobá mapa prekladyVoleb a retry dedup TextVRamecku jsou další rizika při změně jazyka či výpadku; ověřit v P1.5/P1.9, neoznačovat za vyřešené. Test explicitního Swift výsledku s validní/invalidní/HTTP/timeout/network/cancel odpovědí v quick CI, Android statický kontrakt + nativní sestavení. HW vypnutí Wi-Fi, návrat služby, opakování a volby čekají. P1.5 až po CI.

- P1.4 commit `2b8389a89671063a09d7a1f66e86b74a4634ef54`, [CI run 45359504848](https://github.com/CaseyCZ/GameLingo/actions/runs/45359504848). Poslední stav: quick / verify: in_progress. Před P1.5 ověřit všechny joby a executable Swift test, případně opravit/revertovat P1.4. P1.5: stabilizace neúplných vět, stejné ROI a zhasnutí; ověřit stávající Android StabilitaTitulku/StabilitaTextu/TextVRamecku a iOS délku zobrazení. HW a selhání sítě v reálném provozu čekají.

## P1.5 — stabilizace a časování dialogů
- P1.4 CI 45359504848 všech šest jobů success včetně Swift testu explicitních výsledků.
- Android pinned upstream StabilitaTextu.kt a RemotePlayService.kt 847–1080 již potvrzují zkrácení, rozvíjení věty a OCR nejistotu, opravují postupné dočtení; nezavádíme druhý paralelní filtr. TextVRamecku a story ponechány. Standardní Android/TV ukazCesky trvání max 4 s místo 15 s, po potvrzeném zmizení plánované schování nejpozději 800 ms (ne za hranicí 4 s od zobrazení); skutečná latence závisí na OCR. Nemění se story stránky, samostatný dlouhý herní text ani layout.
- iOS CaptionStability: normalizace mezer/case pro dedup, krátké repliky ihned, delší neukončené nebo zkrácené čtení vyžaduje potvrzení; postupné dopisování má omezený rozpočet ~800 ms od souvisejícího kandidáta, dokončená věta může ihned projít. Podezřelé čtení a první prázdný výsledek vynutí další OCR přes image gate, nikoli mimo stávající OCR interval. Prázdný výsledek potvrdit po min. 800 ms od prvního prázdného OCR; pak clear event + nová revision, stejná replika po zmizení je znovu přípustná. P1.4 retry zachováno. Žlutá oblast má vlastní debounce prázdného výsledku, dlouhý dokument neexpiruje po 4 s. UI dialog expiry je nezávislá na snímcích, po úspěšném překladu min(zvolené trvání,4 s), kratší nastavení respektováno; explicitní clear zruší TV opakování, stop ruší timer. Vyšší zvolená délka standardního dialogu je během této auditní změny omezena bezpečnostním stropem, volba nastavení nezměněna. Skutečný clear je první dostupné čtení po debounce, nikoli garance přesně 800 ms; závisí na decoderu, OCR a zátěži. U rychle měnících se nesouvisejících nedokončených frází může filtr některou krátkou větu přeskočit; HW RDR2 a jiné hry nutné ověřit. P1.9 zůstává pro úplnou ochranu při změně nastavení/výstupu a stale race.
- Přidán executable Swift test krátké věty, dopisování, zkrácení, opakování, výpadek jednoho OCR, potvrzené zmizení, retry a dlouhý herní text. Statické kontroly Android patchů a nativní CI. Zatím neověřeno na zařízení. P1.6 až po CI.

- P1.5 commit `68e41a76d808df04c979d61d5c7d2a5e1486c237`. CI [45360814692](https://github.com/CaseyCZ/GameLingo/actions/runs/45360814692): quick / verify: in_progress. Před P1.6 ověřit všech šest jobů, executable Swift stabilization test, Android patch/build; případně opravit/revertovat. HW čitelnost a clear timing neověřeny. P1.6: circuit breaker po 2 chybách, cooldown 30 s, 1 half-open probe, zachovat cache a stávající Android fallback; iOS ověřit vhodný již používaný provider pro fallback bez změny jazyků.

## P1.6 — Circuit breaker a fallback
- P1.5 CI 45360814692: všech šest jobů success včetně executable stabilizačního testu. HW timing čeká.
- Vlastní shodná state machine Swift/Kotlin: monotónní čas, dvě po sobě dokončené neúspěšné provider operace → open 30 s od druhé chyby, cache před síťovou cestou, po cooldown právě jeden half-open probe; success resetuje počítadlo, neúspěch/cancel probe znovu 30 s, pozdní completion starší generace nemůže circuit zavřít. Nejde o automatické čekání nebo background request. Fallback se zkouší již při první chybě; primární služba se vyřadí až po druhé. Každý online provider má vlastní stav. Stav je v RAM, nezávislý na jazyce, per pipeline/translator instance. Validní cache hit neovlivňuje breaker. P1.7 doplní detailnější metriky.
- iOS Google → MyMemory (již existující provider v Android projektu), HTTPS, limit dotazu 500 UTF-8 bytů bez zkracování; MyMemory se používá pouze při selhání nebo open Google a cache miss. Validuje HTTP, responseStatus, string, prázdné texty a quota warning. Používá stejný source/target, vlastní SQLite engine key; žádné nové jazyky, API klíče, offline modely ani změny UI. Riziko: při fallbacku se rozpoznaný text odesílá také MyMemory, veřejná služba má limity a nemusí překlad poskytnout. Síťové timeouty zachovány/3s backup; nejsou absolutní wall-clock deadline. Cancel/invalid local request není provider failure. Při všech službách open explicitní providerUnavailable, nikdy raw OCR text.
- Android: Google breaker sdílený pro standardní a story cestu, Microsoft a MyMemory samostatné. Původní dvojité okamžité pokusy a nesourodé pauzy nahrazeny jednou provider operací per průchod, zachováno pořadí Google→Microsoft→MyMemory→ML Kit. Story zachovává původní omezenou Google/ML Kit cestu a deadline, jen respektuje Google breaker. Lokální ML Kit model/download chyby dál explicitní failure, není online provider; jeho fallback neblokujeme síťovým breakerem. Synthetic Google warmup vypnut, aby nepočítal testovací "ok" jako úspěch nebo neobsadil jediný half-open probe. Cache hits ani přeskočené open providery nejsou chyby. Android stav synchronizovaný mezi story/normal pracovníky. Reálné síťové chování a limity providerů nutno ověřit na zařízení.
- Přidány executable Swift testy (2 chyby, 30 s, jediný probe, reset, cancellation, staré completion, parsing zálohy), Android JVM JUnit test včetně 16 souběžných pokusů o half-open (TV CI spouští testDebugUnitTest), statické integrační kontrakty. Výsledky CI zatím nepotvrzeny. P1.7 až po CI; žádný release.

- P1.6 implementační commit `95facf766335ccdabe395ea1036fa8d5dd883b25`; CI 45362700345 první quick selhal v jediném z 38 kontraktů (starý zápis `if case .success(let translated) = outcome` po změně na switch). Opravný test commit vyžaduje přímo `case .success(let translated)` následované zápisem do cache; bezpečnostní podmínka zůstává. Swift executable a native v prvním CI dosud neproběhly. Před P1.7 ověřit opakovaný CI.

- P1.6 opravný test commit `d9c57b47be85bcfa27ece94495a5efe59d57f7f7`. CI [45362779460](https://github.com/CaseyCZ/GameLingo/actions/runs/45362779460): quick / verify: in_progress. Před P1.7 ověřit všech šest jobů včetně executable Swift a Android JVM testů circuit breaker, případně opravit nebo revertovat. P1.7: měřit OCR/translation/total latency, paměť a teplotní stav, nečekané ukončení bez telemetrie mimo zařízení; prověřit existující diagnostiku Android/iOS.

## P1.7 — lokální diagnostika výkonu
- P1.6 kumulativní CI 45362779460 všech šest jobů success včetně Swift a Android JVM circuit breaker testů. Kandidát P1.7 připojen až po plném úspěchu.
- iOS: oddělené rolling statistiky posledních 128 vzorků (průměr, P95, počet všech vzorků v relaci) dialog OCR od začátku skutečného čtení do přijetí výstupu OCR, překlad od requestu do výsledku včetně cache/fallbacku, celkem do odevzdání výstupu; žlutá oblast měří změněné čtení od vstupu do OCR funkce a zpracování seznamu/dispatch. Celkový čas není čas skutečného vykreslení na TV ani capture-to-photon. Počet neúspěšných překladů, resident memory RSS MiB a systémový thermalState (nikoli stupně C). Stávající diagnostika a technický log, aktualizace při startu/stopu a každých 30 s aktivní relace. Marker otevřené relace v UserDefaults při dalším startu indikuje neuzavřenou relaci, není důkaz pádu (OS kill, force quit, výpadek napájení apod.).
- Android/TV: existující 30s lokální log rozšířen o rolling průměr/P95/počet, standardní dialog OCR (jednotlivé pokusy), batch překlad (normal/quick a story odděleně), odhad celkem k předání dialogového výstupu (OCR casCteni + čas od vstupu do překladu, nepokrývá síť/TV render), žlutá oblast OCR a překlad. Žlutá oblast předává skutečný monotónní OCR timestamp a generation do text/choice callbacků; měří celkem od začátku OCR do předání platného neprázdného výstupu včetně cache. Paměť Debug.getPss MiB a systémový thermalStatus API29+ jinak unknown. Sběr 30s v existující statistice, žádný per-frame disk write navíc. Token relace brání promíchání statistik po ukončení služby. Persistent marker aktivní služby (SharedPreferences commit) indikuje neuzavřenou relaci, stop/onDestroy ho odstraní; interní reconnect není pád. Žádné externí odesílání metrik; stávající ruční log export zůstává. Nové metriky neobsahují text titulků, účet, adresu, token. Existující log text/IP může obsahovat a má vlastní upozornění.
- Testy Swift a Android JVM pro omezené okno, percentile a chybné hodnoty; integrace a plný CI. Skutečné hodnoty latencí, teploty, PSS/RSS, marker po ukončení a zátěž měřit na hardware, nikoli tvrdit z CI. P1.8 až po plném CI.

- P1.7 commit `cd5fe7b3d1bfd8ff8607f9125a91b3a5353b22ca`. CI [45363666595](https://github.com/CaseyCZ/GameLingo/actions/runs/45363666595): quick / verify: in_progress. Před P1.8 ověřit všech šest jobů včetně Swift/JVM performance testů a iOS Mach memory API kompilace; při chybě opravit/revertovat. P1.8: sjednotit semantiku OCR režimů a nezávislou dynamickou žlutou oblast, bez změny jazykových voleb. Fyzická měření stále čekají.
