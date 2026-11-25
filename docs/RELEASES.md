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
| Google TV / Android TV | `GameLingo-GoogleTV-vX.Y.Z.apk` | Přijímač titulků z telefonu nebo vlastní OCR/online překlad; HDMI překryv musí být ověřen na cílové TV. Bez offline modelu. |
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

## Aktuální pravidla 0.5 (auditní větev)
[STATUS-0.5.md](STATUS-0.5.md) a [RELEASE_GATES_0.5.md](RELEASE_GATES_0.5.md) jsou závazný stav. QA balíčky pouze z jedné společné revize, s novými checksumy a označením dev/TEST. Nepoužívat historické hardcoded Beta2/Beta3 workflow ani automaticky mazat starší releases. Před veřejnou distribucí uzavřít bezpečnostní/licenční nálezy a fyzické testy. Release až po explicitním souhlasu. 

## Current versioning procedure

The authoritative version is `shared/version.json`. For a new release, run `python3 tools/sync_version.py --bump patch` (or minor/major). Commit only that version file together with intentional changes. CI loads the version and build counter directly, creates versioned IPA/APK/artifact names, prepares iOS metadata in the build checkout, and validates the resulting versions. Android reads the same metadata. To verify the contracts without building, run `python3 tools/sync_version.py --check`.

Publication remains explicit: dispatch `publish-gamelingo-current.yml` on Master after the matching full audit CI succeeds. It checks the exact source SHA and checksums before replacing older test releases. No automatic release on ordinary commits. Physical QA and provider/licensing gates still apply.

## Aktualizace přímo na TV

GameLingo TV kontroluje veřejná vydání jednou po spuštění a ručně v O aplikaci → Zkontrolovat aktualizace. Používá manifest GameLingo-TV-update.json vytvořený z konkrétní APK při publikaci. Stahování vyžaduje souhlas, kontroluje velikost, SHA-256, identitu, versionCode a shodu certifikátu s nainstalovanou aplikací; instalaci potvrzuje systémový instalátor. Žádné odinstalování ani automatický downgrade. Přechod z dočasného debug podpisu může vyžadovat jednorázovou ruční instalaci s novým podpisem. Fyzická QA ještě čeká.

Publikace se zastaví, pokud nejsou nastaveny soukromé GitHub Actions secrets GAMELINGO_TV_KEYSTORE_B64 a GAMELINGO_TV_KEYSTORE_PASSWORD nebo pokud jejich certifikát nesouhlasí s konkrétní TV APK. Vývojová CI nadále smí sestavit dočasně podepsaný testovací artefakt, ale nelze jej publikovat tímto workflow. Soukromý klíč nepatří do repozitáře ani do chatu; vlastník jej musí trvale a bezpečně uložit a zálohovat. Aktualizaci testovat přes dvě po sobě jdoucí verze se stejným klíčem, s odlišným podpisem, poškozenou APK, přerušeným stažením a odmítnutým instalačním oprávněním. Není nutný další server ani telefon.
