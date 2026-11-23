# GameLingo 0.5 — kontrolní bod vývoje (obnovitelné po přerušení)

**Závazné zadání:** audit z 23. 11. 2026 (priorita P0–P2). Práce probíhá pouze ve větvi `feature/gamelingo-v05-platform-rebuild`, PR [#5](https://github.com/CaseyCZ/GameLingo/pull/5). **Master ani veřejná vydání neměnit, dokud neprojde plný build a reálný test na zařízeních.**

**Jak navázat po přerušení:** Otevři tento soubor a PR #5, přečti poslední commit větve, pokračuj od prvního řádku se stavem **ROZPRACOVÁNO** nebo **NEOVĚŘENO**. Po dokončení každého bodu vytvoř samostatný commit, doplň odkaz na commit a důkaz o testu. Nikdy neoznač funkci za ověřenou pouze díky statickému testu.

## Úkoly podle původního auditu

| # | Priorita | Úkol | Kritérium dokončení | Stav |
|---|---|---|---|---|
| 1 | P0 | Vlastní struktura GameLingo | Žádné původní nabídky PlayCaptionu v uživatelském rozhraní iOS/Android/TV | iOS staré menu odstraněno; finální UI Android/TV čeká fyzický audit |
| 2 | P0 | Úvodní průvodce | První spuštění: výběr Xbox/PlayStation, výstupu a párování; po dokončení se neopakuje | Kód obou telefonů upraven; přihlášení a párování čeká fyzický test |
| 3 | P0 | Správa zařízení | Více Xboxů, PlayStationů a TV; přidávání, výběr, přejmenování/odebrání; bezpečné během relace | Opravy iOS/Android uloženy, regresní kontroly prošly; HW test chybí |
| 4 | P0 | Sjednotit Android a iOS UI | Stejné názvy, navigace, ikonografie a způsob ovládání; ne jen barvy | Pět společných záložek v nativní Kotlin navigaci; Android APK QA probíhá |
| 5 | P1 | Samostatné nové TV UI | Přehledné ovládání ovladačem na velké obrazovce; dvě volby režimu a přístup k párování | Režimy mají D-pad focus a viditelné zvýraznění; APK QA probíhá, HW test chybí |
| 6 | P1 | Zachovat skutečné funkce | Xbox, PS4/PS5 Android, iOS PS4/PS5 port, OCR, 4 oblasti, Orange V5; žádné fake indikátory | NEOVĚŘENO NA ZAŘÍZENÍ |
| 7 | P1 | Testy | Skutečné přihlášení, více konzolí, párování, titulky v reálné hře; kompletní build všech balíčků | NEOVĚŘENO NA ZAŘÍZENÍ |
| 8 | P2 | Úklid kódu | Odstranit duplicitní UI a křehké textové záplaty; zachovat upstream autorství/licenci | Odstraněna stará iOS obrazovka a Android textová navigace; další upstream patche zatím zůstávají |
| 9 | P2 | README a Master | Dokumentace odpovídá ověřené verzi; Master až po přijetí PR a všech ověřeních | DESIGN sjednocen; README stále popisuje zveřejněnou 0.4; Master neměněn |

## Co je už skutečně uložené

- iOS: nativní PlayStation bridge a OCR z dekódovaných snímků, profilované oblasti, prvotní průvodce, správa zařízení. Funkčnost na fyzické PS5 zatím **nepotvrzena**.
- iOS: odstraněna stará alternativní úvodní obrazovka a duplicitní výběr Xboxu; nový `GameLingoXboxScreen` sdílený průvodcem a dashboardem.
- iOS: opraveno přepínání Google TV/telefonu včetně mazání předešlých překryvů.
- Android/TV: součástí repozitáře jsou skripty přestavby UI nad připnutými upstream základy. Je nutné otestovat výslednou APK, ne jen zdrojové skripty.
- Rychlé regresní testy: `.github/workflows/fast-gamelingo-checks.yml`; kompletní matice: `.github/workflows/build-gamelingo.yml`.

### Nové kontrolní body pro audit P0–P2

- **P0/2 iOS průvodce:** `7911bb7` – dokončení skutečně vyžaduje registraci a TV párování; „Nastavit později“ ukládá první spuštění bez předstírání spojení.
- **P0/2 Android průvodce:** `e113bf3` – „Nastavit později“ v každém kroku, pravdivé rozlišení volby a aktivního připojení.
- **P0/3 iOS zařízení:** `046b326`, `017e748`, `39b03ac` – filtrace odebraných zařízení, opakované PS párování, zrušené TV se neobnovují ze staré IP.
- **P0/3 Android zařízení:** `7f30f74` – zákaz přepínání/odebírání během relace; potvrzení odebrání TV.
- **P0/4 Android/iOS navigace:** `62655ba`, `43a214a`, `89ea1bc`, `a9b9534`, `08c785c`, `e54cd75` – pět stejných destinací a samostatná komponenta `GameLingoNavigation.kt`; původní inline spodní navigace odstraněna.
- **P1/5 TV ovladač:** `9c92325` – zvýraznění obou režimů při D-pad focus a propojení šipka vlevo/vpravo.
- **P1/7 regrese:** `7a4d73b`, `80fa35c`, `509be99` – kontrola 9 bodů doplněna, rychlá CI na `509be99` **PASS**. Kompletní Android/TV QA: [run 45209393131](https://github.com/CaseyCZ/GameLingo/actions/runs/45209393131), výsledek zkontrolovat před uzavřením.
- **P2/9 design:** `d6697e2` – zapsaný jednotný pětizáložkový design, žádné falešné „online“.

**Nejbližší přerušitelný krok:** zkontrolovat výsledek Android/TV QA 45209393131, opravit případné Kotlin chyby, pak spustit plnou matici iOS/Android/TV na témže SHA a zaznamenat výsledky. Bez fyzických testů Xbox/PS5/TV P1/6–7 nadále zůstávají neověřené.

## Evidence práce a další jednotlivé kroky

| Krok | Důkaz / commit | Stav |
|---|---|---|
| P0/1: Sjednotit iOS Xbox a odstranit staré iOS UI | `1411dca`, `4f85eb0`, `d8f0f71`, `78432b3` | Změny uloženy, rychlé kontroly prošly |
| P0/3: Neumožnit přepnout aktivní konzoli za běhu | `9d8dc93`, `85cc0fd` | Změny uloženy, rychlé kontroly prošly |
| Další: zkontrolovat a dokončit P0/1 až P0/4 také na Androidu/TV, včetně nastavení prvního spuštění | — | DALŠÍ |
| Poté: P1/5 samostatné TV UI a P1/6 zachování protokolů | — | ČEKÁ |
| Poté: P1/7 CI build + fyzický test PS5/Xbox/TCL C835 | — | ČEKÁ |
| Poté: P2/8 vyčistit duplicity, P2/9 synchronizovat README a zvážit merge | — | ČEKÁ |

**Bezpečnost a vydání:** Nepřepínat Master; nesdělovat privátní Xbox/PSN tokeny ani klíče; nepublikovat tajné přihlašovací údaje, debug APK označovat TEST, iOS IPA unsigned; zachovat AGPL-3.0 a plné atribuce. Build nebo statická CI kontrola nikdy není důkaz funkčního HDMI overlaye, OAuth, připojení k PS5 či OCR.
