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
| 6 | P1 | Zachovat skutečné funkce | Xbox, PS4/PS5 Android, iOS PS4/PS5 port, OCR, 4 oblasti, Orange V5; žádné fake indikátory | OCR relace izolovány, status snímků pravdivější; hardware NEOVĚŘEN |
| 7 | P1 | Testy | Skutečné přihlášení, více konzolí, párování, titulky v reálné hře; kompletní build všech balíčků | Android/Google TV QA APK PASS; iOS full matrix čeká, fyzické testy NEOTESTOVÁNY |
| 8 | P2 | Úklid kódu | Odstranit duplicitní UI a křehké textové záplaty; zachovat upstream autorství/licenci | Odstraněna stará iOS obrazovka a Android textová navigace; další upstream patche zatím zůstávají |
| 9 | P2 | README a Master | Dokumentace odpovídá ověřené verzi; Master až po přijetí PR a všech ověřeních | DESIGN a README aktuální: 0.4 veřejná, 0.5 vývojová; Master neměněn |

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

**Výsledek Android/TV QA:** [run 45209393131](https://github.com/CaseyCZ/GameLingo/actions/runs/45209393131) — Android mobile **PASS**, Google TV **PASS** včetně skutečného Gradle sestavení. Zdrojové UI zůstalo od tohoto runu nezměněné; následné zdrojové opravy se týkají iOS.

**Plná matice:** [run 45211607965](https://github.com/CaseyCZ/GameLingo/actions/runs/45211607965) na SHA `12e0bcb` = všechny tři platformy a checksum **PASS**. Další upravené kódy po tomto buildu zůstávají mimo jeho důkaz; konečné sestavení musí použít aktuální společné SHA.

**Nejbližší přerušitelný krok:** ověřit nejnovější rychlou CI a výsledky iOS buildu; na finální revizi spustit plnou iOS/Android/TV matici, podle logu opravit případné chybné sestavení a uložit testovací balíčky. Poté provést konkrétní testy v [PHYSICAL_QA_0.5.md](PHYSICAL_QA_0.5.md). Bez těchto fyzických testů jsou P1/6–7 nadále **NEOVĚŘENÉ**.

### Nové důkazy po sestavení Androidu / TV

- **P1/6 připojení/obraz:** `f7fcd6b`, `f6365ef` – vynulování počtu snímků po novém startu, chybová zpráva Xboxu se neztrácí, „Snímání aktivní“ až po přijetí skutečného snímku.
- **P1/6 bezpečná asynchronní OCR relace:** `623b11a`, `183ee5a`, `331d33f` – překlady z předchozího streamu nesmí přepsat novou konzoli; regresní test po korekci testovacího rozsahu [PASS](https://github.com/CaseyCZ/GameLingo/actions/runs/45211163072).
- **P0/4 P2/8 sdílený iOS vzhled:** `0428590` – dashboard bere barvy, písmo a gradient ze společného GLStyle místo duplikované palety.
- **P1/7 fyzické testovací scénáře:** `3664aba` – [PHYSICAL_QA_0.5.md](PHYSICAL_QA_0.5.md), všechny fyzické scénáře mají pravdivý stav NEOTESTOVÁNO.
- **P2/9 README:** `ed04ad1` – poslední veřejná verze 0.4.0 a nedokončená 0.5.0 výslovně odlišeny.
- **P1/7 Android/TV QA:** [run 45209393131](https://github.com/CaseyCZ/GameLingo/actions/runs/45209393131) = obě APK úspěšně sestaveny.

### Další checkpoint po úspěšné kompletní sestavovací matici

- **Úplný build tří platforem na SHA `12e0bcb`:** [GitHub Actions 45211607965](https://github.com/CaseyCZ/GameLingo/actions/runs/45211607965) **PASS** — nativní iPhone unsigned IPA, Android telefon APK, Google TV APK a kontrolní SHA-256. **Pozor: po tomto SHA následovaly další UI změny, takže pro finální kandidát je nutný nový build.**
- **P0/3 Android multi-Xbox:** `a53aa92`, `ca2ff34` — při nalezení více konzolí se použije již zvolený Xbox ze správy zařízení; dialog až při prvním výběru.
- **P0/1 P2/8 iOS Nastavení:** `732799f`, `47b27f2`, `48afd13` — obrazovky Nastavení, OCR, Překlad, Vzhled a Diagnostika už nepoužívají interní staré názvy PlayCaption.
- **P0/1 P0/2 Android průvodce:** `54f694d`, `64fe409` — vlastní celostránkový branded průvodce namísto plovoucího okna přes původní úvodní stránku.
- **Nový cílový stav:** po poslední úpravě zdroje vytvořit jediný finální build iOS + Android + TV z jednoho SHA, ověřit artefakty a ponechat PR jako draft do dokončení [fyzických testů](PHYSICAL_QA_0.5.md).

### Navázání po přerušení — další úpravy 23. 11. 2026

- **P0/1 iOS PlayStation přihlašovací UI:** `6176cf1`, `1a1e15d` – odstraněno duplicitní PIN pole na hlavní obrazovce, zůstává jeden nativní PIN dialog a regresní test.
- **P0/3 Android Xboxy:** `4acba8c`, `e6990ff` – možnost odebrat Xbox z místního seznamu bez odhlášení Microsoft účtu, perzistentní skrytí a obnovení, zablokováno za běžícího překladu. Regresní test zahrnuje odstranění vybraného ID.
- **P0/3 TV změna za běhu:** `1e30445`, `29b0a0d`, `65ae20a`, `8d5d68e`, `3c4b665`, `c6ed723` – sdílený iOS TV výběr a mazání starých překryvů na *původní* IP před přepnutím; testy nového chování.
- **P1/7 dřívější plná matice:** [run 45212887689](https://github.com/CaseyCZ/GameLingo/actions/runs/45212887689) **PASS** na `796a5be`: iPhone IPA, Android APK, Google TV APK, checksumy. **Po tomto buildu následovaly další změny kódu, proto tyto artefakty nejsou poslední kandidát.**
- **P1/7 novější Android/TV QA:** [run 45213686310](https://github.com/CaseyCZ/GameLingo/actions/runs/45213686310) spuštěn ze zdrojů obsahujících `4acba8c` (odebrání Xboxů) a výběr TV. Výsledek je nutné zkontrolovat.
- **Nová rychlá CI:** [run 45214058201](https://github.com/CaseyCZ/GameLingo/actions/runs/45214058201) **PASS** na `c6ed723`; statické kontrakty a Swift parse nejsou fyzické testy.

**Další jasný krok pro pokračování:** 1) zkontroluj Android/TV QA run 45213686310 a oprav případné Kotlin chyby; 2) zkontroluj poslední SHA větve, 3) spusť úplnou sestavovací matici iOS/Android/TV na nejnovějším SHA (workflow `.github/workflows/build-gamelingo.yml` běží při explicitní revizi), 4) zaznamenej výsledné SHA256 do nového dokladu, 5) fyzicky otestuj body v [PHYSICAL_QA_0.5.md](PHYSICAL_QA_0.5.md). Nemergovat do Master a nepovažovat Real Remote Play/HDMI titulky za otestované bez skutečné konzole/TV.

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
