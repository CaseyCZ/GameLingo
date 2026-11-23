# Vydávání GameLingo

Tento dokument popisuje, co patří do **GitHub Releases** projektu [GameLingo](https://github.com/CaseyCZ/GameLingo/releases).

## Typy vydání

- **Pre-release** (např. `v0.1.0-beta.1`): testovací sestavení s výslovnými omezeními; nejsou považována za stabilní.
- **Stable** (např. `v1.0.0`): až po skutečném testu na podporovaných zařízeních, stálém podpisu Androidu a kontrole instalace/aktualizace.

## Přílohy vydání

| Balíček | Soubor | Důležité |
| --- | --- | --- |
| iPhone | `GameLingo-iOS-vX.Y.Z-unsigned.ipa` | Nepodepsaná IPA. Je nutné ji podepsat vlastním Apple účtem / kompatibilním nástrojem; není určena pro App Store. |
| Android telefon | `GameLingo-Android-vX.Y.Z.apk` | Podpora Android telefonu a původních Android Remote Play režimů včetně PlayStation podle ověřené kompatibility. |
| Google TV / Android TV | `GameLingo-GoogleTV-vX.Y.Z.apk` | Přijímač titulků z telefonu; musí podporovat překryv přes vstup HDMI. |
| Kontrola integrity | `SHA256SUMS.txt` | SHA-256 pro **všechny** přiložené balíčky. |
| Změny | `RELEASE_NOTES.md` či text GitHub release | Novinky, chyby, kompatibilita, instalační postup, původní upstream commit. |
| Zdroj | GitHub automaticky přiloží ZIP a TAR.GZ tagu | U odvozeného AGPL-3.0 kódu musí být k dispozici veškerý odpovídající zdroj i reprodukovatelné úpravy. |

Volitelně: balíček `GameLingo-Android-TV-universal` nebo jiné platformy **až tehdy**, když takový výstup reálně sestavujeme a podporujeme. Nepřikládat neúplné ani záměrně prázdné ZIP soubory.

## Kontrola před zveřejněním

- [ ] Všechny binární balíčky byly sestaveny z jednoho dokumentovaného release tagu a identifikovaných upstream revizí.
- [ ] iOS používá vlastní název **GameLingo** a samostatný bundle ID, aby nepřepsal funkční starší PlayCaption.
- [ ] Android telefon a Google TV mají vlastní rozlišitelné application ID a popisky, bez názvu konkrétní značky TV v uživatelském rozhraní.
- [ ] APK/IPA skutečně obsahují správný kód a správnou verzi (ověření Android `aapt dump badging`, Apple `Info.plist`).
- [ ] Kontrolní součty SHA-256 souhlasí s publikovanými soubory.
- [ ] Je ověřeno párování telefonu a TV, příjem titulků, čtyři oblasti, oddělený oranžový text.
- [ ] Xbox zůstává použitelný a výběr více Xboxů funguje.
- [ ] Android telefon se spouští a pro PlayStation/PS5 je jasně doložený stav testu.
- [ ] Android APK má **stabilní podpis** pro aktualizace. Případné debug APK je výslovně označené `TEST` a varuje před nutnou reinstalací.
- [ ] **AGPL-3.0** licence, původní autor, zdrojové kódy, změny a build skripty jsou dostupné.
- [ ] Pre-release má jasně popsaná omezení; neoznačujeme neotestované platformy jako plně funkční.

## Důležité k dosavadnímu vývoji

Poslední vyzkoušený iOS / Xbox / TV základ vznikl v [CaseyCZ/Test](https://github.com/CaseyCZ/Test). Konkrétně při úvodní migraci používáme ověřený iOS revizní bod `a7d18f372f90803096129de54ac75c4d4ef6c8d0` a TV revizní bod `61389ab353c96e68675a80da5b79f34c7352aa2d`. Android PlayStation/telefonní implementace vychází z upstream [PlayCaption](https://github.com/AdamRuzick4/PlayCaption-app) revize `633a847400b6c4bac1e7f034463e7847a53a9590`.

**Dosavadní IPA a APK PlayCaptionu se automaticky nestávají vydáním GameLingo.** Nejdříve musí projít rebrandingem, změnou instalační identity, sestavením, bezpečnostní kontrolou a testem.

## Web

Veřejný web GameLingo se připraví až později. README v kořeni repozitáře je zatím primární informační stránka.
