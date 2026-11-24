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
- [ ] P0.4 více telefonů a jednotlivé odpojení
- [ ] P0.5 konzole + HDMI; doplnit ruční QA matici
- [ ] P1.1 obrazový otisk posledního OCR, periodická kontrola a krátké věty
- [ ] P1.2 nejnovější snímek, omezená fronta
- [ ] P1.3 SQLite LRU ~20k, engine/jazyky
- [ ] P1.4 explicitní success/error, žádný falešný překlad
- [ ] P1.5 stabilizace, 800 ms / max ~4 s při stejném obrazu
- [ ] P1.6 circuit breaker (2 chyby/30 s/1 pokus)
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
