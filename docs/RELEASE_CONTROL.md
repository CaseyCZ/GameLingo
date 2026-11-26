# GameLingo — řízení veřejného vydání

Pracovní větev: `feature/ocr-lab`. Master a stabilní 0.5.1 zůstávají beze změny. Tento dokument je hlavní přehled akceptace; detailní QA a auditní dokumenty zůstávají podklady.

## Rozsah a odpovědnost
- Majitel produktu určuje směr a schvaluje release. Technické vedení koordinuje vývoj, UX, lokalizaci, bezpečnost, testy, dokumentaci a distribuci bez schvalování každé drobné opravy.
- Zmrazit rozsah před vydáním: žádné nové funkce. Standardní OCR zachovat podle 0.5.1; automatický režim volitelný, jedna zóna ve výchozím stavu, další zóny na požádání. Žádné audio ani mikrofon.
- Dostupnost platformy musí odpovídat důkazu; neověřené PS/PC funkce neinzerovat jako hotové.

## Ověřený výchozí stav (2026-11-26)
- Zdrojová revize před tímto checkpointem: `2727094b4df1d6d69c7906168387177c3cc99bfc`.
- [Build 45830812794](https://github.com/CaseyCZ/GameLingo/actions/runs/45830812794) na `961a5566`: integrační kontrakty, Android a TV prošly; iOS přerušen; validace balíčků přeskočena. Není release kandidát.
- [iOS kontrola 45831114842](https://github.com/CaseyCZ/GameLingo/actions/runs/45831114842) na `265ded86`: geometrie a parse úspěšné, nikoli kompletní IPA build.
- [TV kontrola 45831376394](https://github.com/CaseyCZ/GameLingo/actions/runs/45831376394) na `2727094b`: úspěšná.
- Nové změny diagnostiky/notifikací vyžadují nové CI; technické metriky a zděděná UI dále podléhají auditu.

## Návazný checkpoint (2026-11-26)
- [Úplná nativní sestava 45836614280](https://github.com/CaseyCZ/GameLingo/actions/runs/45836614280) na `8b7acf63`: všech pět jobs úspěšných, 108 integračních testů, IPA/APK/TV APK a validace balíčků. Jde o starší revizi, ne o schváleného kandidáta.
- [Rychlá regrese 45837753310](https://github.com/CaseyCZ/GameLingo/actions/runs/45837753310) na `46197126`: všechny kroky úspěšné, včetně kontroly slovníků, syntaxe Python/Swift a nativních unit testů. [TV navigace 45837268966](https://github.com/CaseyCZ/GameLingo/actions/runs/45837268966) na `4aec2785`: úspěšná.
- `b6ea5e1487baad7280185d2920d67697f9f82a43`: lokalizován generovaný TV dashboard, příjem telefonu, PIN/QR a výběr zdroje/konzolí, zachovány interní stavy, protokol a navigační akce. Doplněna kontrola slovníku a aktualizovány testy skutečného generovaného TV zdroje. 713 klíčů Android/TV; iOS 474 v každém z EN/SK slovníků. Počty klíčů nejsou důkazem úplné lokalizace.
- [Google TV navigation checks 45838722440](https://github.com/CaseyCZ/GameLingo/actions/runs/45838722440) na `b6ea5e14`: in_progress.
- [GameLingo • quick regression checks 45838722416](https://github.com/CaseyCZ/GameLingo/actions/runs/45838722416) na `b6ea5e14`: success.
- Další technický krok: ověřit CI této revize, audit zbývajících dynamických textů a zděděných obrazovek iOS/Android/TV, zkontrolovat pravdivou dokumentaci a release metadata, teprve poté úplný build zmrazeného kandidáta. G1–G7 se zatím neuzavírají; fyzické výsledky neexistují.

## Fyzická zpětná vazba TCL (2026-11-26)
- Majitel produktu potvrdil správnou velikost párovací obrazovky na dřívějším dev sestavení; jiné obrazovky (zejména O aplikaci / Licence) mají odlišné měřítko a obsah je mimo výšku. Jde o konkrétní vizuální důkaz, nikoli potvrzení funkčnosti párování ani nového kandidáta. Fotografie ani údaje o síti/PIN neukládat do repozitáře.
- Oprava: společné kompaktní TV jednotky (0,66), konzistentní velikost prvků/písma, About v jednotném TV kontejneru se stálým návratem a scrollováním dlouhých licencí. Telefonní měřítko 1,0, OCR geometrie a skutečné titulky nedotčeny. Ověřit výsledný vzhled všech stránek na TCL; G1/G2 zůstávají otevřené do akceptace.

- Rozšíření schválené majitelem produktu: v Pokročilém nastavení TV jednotná volba velikosti 80/90/100/110/120 % (100 % = původní kompaktní 0,66). Uložená lokálně mimo OCR/profily; menu se znovu vykreslí bez restartu aplikace. Nutno ověřit všechny stránky včetně podstránek, dialogů, D-pad a krajních hodnot. Systémové dialogy, klávesnice a externí weby nejsou součástí vlastního rozhraní.

## Audit rozhraní a funkčnosti (26. 11. 2026)
Závazný návrh a akceptační matice: [UI_UX_FUNCTIONAL_AUDIT.md](UI_UX_FUNCTIONAL_AUDIT.md). Fyzická zpětná vazba potvrdila další vady: slabý/neviditelný fokus, příliš široké karty, zbytečný scroll a nekonzistentní Back/Help. Jde o blokátory G1/G2, nikoli kosmetické požadavky. Zdrojové opravy napříč platformami a regresní kontroly jsou v této větvi; vizuální průchod a nativní build aktuální revize zůstávají povinné.

## Povinné brány vydání
| Brána | Akceptační důkaz | Stav |
|---|---|---|
| G1 Architektura, navigace | Inventář obrazovek, zachování standardního režimu, návraty v menu, bez regresí | OTEVŘENO |
| G2 UI a lokalizace | Všechny obrazovky/dialogy/chyby CZ/SK/EN, změna jazyka, systémový fallback, layout a TV fokus | OTEVŘENO |
| G3 Funkce a výkon | Xbox, párování, více zařízení, profily, oba OCR režimy, latence, čištění titulků, HDMI | OTEVŘENO |
| G4 Bezpečnost a licence | Tokeny, oprávnění, podpisy, aktualizace, licence, TV bez offline překladače | OTEVŘENO |
| G5 Nativní build | Integrační testy, IPA, mobilní APK, TV APK, společná kontrola balíčků a SHA-256 ze stejného commitu | OTEVŘENO |
| G6 Fyzická akceptace | Protokoly skutečných zařízení; upgrade bez ztráty nastavení | BLOKOVÁNO do fyzických testů |
| G7 Distribuce | Podpisy, instalace, README, changelog, schválení majitele, návrat ke stabilní verzi | OTEVŘENO |

## Pravidla řízení
1. Dokončit audit a opravy G1–G4; evidence u konkrétních commitů, nikoli odhad procent. Průběžné rychlé testy.
2. Zmrazit commit a spustit jediný úplný workflow Native GameLingo candidate. Další kandidátní build může předchozí zrušit; do konce neposouvat build checkpoint. Zrušený/selhaný build není PASS.
3. Z jedné revize předat sadu IPA/APK pro fyzické testy. Pokud chyba, opravit, zopakovat regresi i kompletní validaci nového kandidáta.
4. Veřejné vydání až po G1–G7 a schválení. Nepřidávat nové funkce.

Detailní podklady: [fyzická QA OCR a lokalizace](PHYSICAL_QA_OCR_L10N.md), [obecná fyzická QA](PHYSICAL_QA_0.5.md), [release gates](RELEASE_GATES_0.5.md), [licence](LICENSE-AND-PROVIDER-AUDIT.md), [bezpečnost](SECURITY-PRIVACY-AUDIT-0.5.md).

Metodika: agency-roster — projektové řízení (priority, rizika, definition of done), testování (regrese, evidence), lokalizace (oddělení textů od interních hodnot). Bez dalších placených služeb a externího AI API.
