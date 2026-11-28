# DECISIONS — Fáze 0 (2026-11-28)

Stav: tři rozhodnutí zaznamenána, čekají na potvrzení vlastníka. Pouze dokumentace; žádná implementace, změna licence, přejmenování repozitáře ani spuštění CI. Nadále platí compliance požadavky v REWRITE_PLAN.md. Fáze 1 nesmí začít před schválením této fáze.

## ADR-001: Licence a původ
**Doporučená strategie:** nový, skutečně nezávislý produkt s možností proprietární (uzavřené) licence k vlastnímu kódu; konkrétní licenční podmínky schválí vlastník po kontrole práv. Alternativa: vlastní nezávislý kód pod Apache-2.0, pokud vlastník zvolí open source.
- Stávající AGPL-3.0 projekt a již udělené licence se nemění. Nepřebírat původní kód PlayCaption, jeho patchy, TP Play/Chiaki nebo jiné neprověřené části do proprietárního produktu. Samostatný adresář nebo přejmenování neřeší copyleft.
- Stávající větev feature/native-rewrite slouží zatím pouze k plánování; obsahuje AGPL historii. Pokud vlastník zvolí uzavřený produkt, nový zdrojový strom založit odděleně, v novém soukromém repozitáři bez importu AGPL historie, až po jeho schválení. Starý projekt a zálohy zachovat. Nepovažovat nové autorství za doložené pouze tvrzením agenta, který už pracoval se starým kódem. Oddělit funkční specifikaci od nezávislé implementace a zajistit nezávislou kontrolu původu.
- Preferovat prověřené MIT/BSD/Apache-2.0 knihovny a nativní systémová API; licence, patenty, notices, transitive dependencies, telemetrie a distribuční práva kontrolovat před integrací. AGPL komponenty nezařadit do uzavřeného produktu bez odpovídajícího samostatného oprávnění či právně ověřené kompatibility.
- Xbox a PlayStation vyžadují prověřený způsob připojení a práva k externím službám. Nezávislý přepis sám o sobě nezajistí povolení ani funkční náhradu Chiaki/TP Play. Pokud náhrada chybí, P0 zůstává blokované; žádné předstírání parity či skryté odebrání funkce.
- AGPL §§6,10 (stávající LICENSE); Apple App Review Guidelines 5.2 a Developer Program License Agreement 4.1: https://developer.apple.com/app-store/review/guidelines/ ; https://developer.apple.com/support/terms/apple-developer-program-license-agreement/ . Konkrétní kompatibilitu obchodů a vlastnictví neprohlašovat za právně ověřené. Doporučena právní kontrola před změnou licence.

## ADR-002: Název
**Rozhodnutí vlastníka (2026-11-28): CaseySub.** Nový produkt ponese přesně tento název, s vizuální identitou CaseyCZ (modro-bílý styl ostatních projektů). Historický GameLingo, Master, zálohy, release a identifikátory aplikací zatím beze změny.

Předběžná rešerše CaseySub a Casey Sub (2026-11-28): veřejné vyhledávání včetně dotazů omezených na App Store, Google Play, GitHub a ochranné známky neukázalo přesnou kolizi v oblasti překladového softwaru. Přímé vyhledávání v TMview a USPTO nebylo dostupné v čitelné podobě, WIPO vyžaduje ověření, ÚPV vrátil chybu přesměrování. Přímé dotazy na katalog Apple a Google Play nebyly dostupné. Název je schválen vlastníkem pro návrh produktu, nikoli právně prověřený pro distribuci. Prověřit i podobné známky CASEY a relevantní třídy a území před vydáním; neoznačovat název za právně volný.

Důvody opuštění původního názvu:
Rešerše 2026-11-28 ukázala kolize ve stejné či související kategorii:
- App Store: Game Lingo, https://apps.apple.com/gb/app/game-lingo/id6808388524
- Překlad PC her: https://gamelingo.io/
- Game Text Translator: https://github.com/GameLingoApp/GameLingo
- PyPI: https://pypi.org/project/game-lingo/
- Historická Android aplikace: https://www.appbrain.com/app/gamelingo-game%C2%A0translator-fu/tw.com.lolatlab.gamelingo (současná dostupnost v Google Play nepotvrzena).
Nejde o zjištění porušení registrované ochranné známky. Úplná rešerše konkrétních známek není hotová; před veřejným vydáním vyhledat shodné i podobné názvy v cílových zemích, zejména třídách 9 a 42 dle skutečných produktů/služeb, v EUIPO/TMview, WIPO a příslušných národních registrech, znovu v obchodech, doménách a repozitářích. https://www.euipo.europa.eu/en/trade-marks/before-applying/availability ; https://www.wipo.int/en/web/global-brand-database/index . Ověření rezervace názvu v App Store Connect/Play Console dosud neprovedeno. Nezakládat účet, nekupovat doménu ani neměnit package ID bez vlastníka.

## ADR-003: Technologie
**Rozhodnutí:** nativní Swift/SwiftUI pro iOS, Kotlin pro Android a Google TV se společnými Android moduly. Ne Flutter ani KMP v této fázi. Společná funkční specifikace, schémata, lokalizační klíče, testovací vektory a výkonnostní kritéria napříč platformami; bez duplicity telefon/TV. Důvod: přímý přístup k životnímu cyklu kamery, dekódování, OCR, MediaProjection, overlay, D-pad, Keychain/Keystore a řízení paměti. Cena: dvě implementace platformně specifických částí, nutné parity testy. Přepis nezaručuje rychlost: měřit původní a nový tok za srovnatelných podmínek.
- Apple Vision/AVFoundation kandidáti pro OCR/kameru (https://developer.apple.com/documentation/vision/recognizing-text-in-images ; https://developer.apple.com/documentation/avfoundation). Android MediaProjection pro povolené snímání (https://developer.android.com/media/grow/media-projection), OCR SDK až po licenční/privacy kontrole. Výběr jednotlivých knihoven není schválen tímto ADR.
- Zachovat modrobílou identitu a ovládání, čtyři barevné oblasti, nezávislé dialogy/herní text, více konzolí, profily a automatický port dle následné specifikace. CS/EN/SK (dosavadní slovenštinu neodstraňovat). Bez mikrofonu, bez placených služeb a bez přidávání AI překladu/dabingu; budoucí P2/P3 jen na samostatné schválení.

## Řízení fází
Použít pořadí 0–8 dle plánu vlastníka: rozhodnutí, SPEC.md (schválení vlastníkem), základ repa, bezpečný TV protokol, překladové jádro, UI/funkce, testy, obchody, vydání. Každá fáze má samostatnou odpovědnost, evidence a výstupní bránu; nepřecházet, dokud není doloženo „hotovo když“. Při návrhu protokolu nestačí samotný HMAC pro důvěrnost: prověřit autentizované šifrování, ochranu proti replay, jednorázové párování, omezení pokusů o PIN a klíče per zařízení; bez legacy režimu. Licence, bezpečnost, privacy a pravidla obchodů platí průběžně již od fáze 0. Fáze 0: dokument zapsán, název CaseySub schválen vlastníkem, licenční rozhodnutí a úplná rešerše známek zůstávají otevřené. Žádný kód nezačínat.
