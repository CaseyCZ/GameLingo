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
