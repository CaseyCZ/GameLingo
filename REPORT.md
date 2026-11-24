# GameLingo 0.5 — audit a pracovní checkpoint
Datum: 2026-11-24. Stav: fáze 0, statická kontrola; žádné změny aplikačního kódu.

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
- [ ] P0.1 povinné HMAC všech typů titulků, legacy opt-in default off
- [ ] P0.2 ochrana proti replay (podepsaná sekvence/nonce, restart relace a ochrana pořadí)
- [ ] P0.3 Keystore na TV, bezpečná migrace nebo nové párování
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
