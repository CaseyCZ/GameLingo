# GameLingo – společná lokalizace rozhraní

Rozhraní iOS, Androidu a Google TV se generuje z **jednoho zdroje**.
Lokalizace rozhraní je nezávislá na jazyku rozpoznávaném OCR a cílovém
jazyku překladu. Nepoužívejte externí překladové služby ani AI API.

## Zdrojové soubory

- `shared/locales/manifest.json` – seznam nabízených jazyků, jejich nativní názvy a fallback.
- `shared/locales/cs.json` – české zdrojové popisky.
- `shared/locales/en.json` – anglické překlady, záložní jazyk.
- `shared/locales/sk.json` – slovenské překlady.
- `tools/generate_locales.py` – deterministický generátor a validátor.

Soubory `cs.json`, `en.json` a `sk.json` obsahují **stejnou množinu klíčů**.
Původní české texty zatím slouží jako klíče kvůli zpětné kompatibilitě s
existujícími obrazovkami. Výhledově je možné postupně migrovat na
sémantické identifikátory typu `nav.back`, ale taková změna musí
aktualizovat všechna místa použití.

## Přidání dalšího jazyka

1. Zkopírujte `shared/locales/en.json` např. do `shared/locales/de.json`.
   Přeložte **všechny hodnoty**; klíče se nemění.
2. Do pole `languages` v `shared/locales/manifest.json` přidejte
   `{"code": "de", "name": "Deutsch"}`.
3. Spusťte `python3 tools/generate_locales.py`.
4. Ověřte `python3 tools/generate_locales.py --check` a
   `python3 -m unittest discover -s tests -p 'test_release_localization.py' -v`.
5. Ověřte skutečné zařízení v novém jazyce, dlouhé texty, titulky,
   dialogy a návrat zpět.

Generátor z aktualizovaného manifestu připraví nový iOS
`<code>.lproj/Localizable.strings`, změní `CFBundleLocalizations` v
XcodeGen `ios/GameLingoIOS/project.yml`, aktualizuje seznam jazyků
`GLAppLanguage.allCases` a přepíše překladové mapy i výběr jazyků
v `tools/GameLingoLanguages.kt`, který se používá pro Android i Google TV.
Není potřeba přidávat ručně nové hodnoty do Swift `enum` ani
duplikovat překlady v Kotlinu.

## Pravidla

- **Upravujte pouze `shared/locales/*.json` a manifest.** Výstupy v
  `Localizable.strings`, `GameLingoAppLanguage.swift`,
  `GameLingoLanguages.kt` a `project.yml` jsou generované či částečně
  generované; ruční změny v generovaných blocích jsou odmítnuty v CI.
- Nový popisek přidejte pod *stejným* zdrojovým klíčem do všech jazyků.
  Nevkládejte české texty jako náhradu chybějícího překladu.
- Dynamické Swift řetězce lokalizujte přes `GLInterfaceText.localized(...)`;
  pro statické SwiftUI texty používejte `LocalizedStringKey` a správné
  `environment(\.locale, ...)`.
- Na Androidu a Google TV se uživatelské popisky lokalizují
  `GameLingoLanguages.ui(context, sourceString)`.
- Nezaměňujte rozhraní s OCR; jazyky hry a cílového překladu mají
  oddělené seznamy a pravidla.
- Kontrolujte bezpečné formátování dynamických hodnot a nezaměňujte
  jazykové kódy s hesly, identifikátory konzolí nebo názvy her.

## Validace a známé hranice

`tools/generate_locales.py --check` ověřuje kompletní sadu klíčů,
neprázdné překlady, unikátní názvy a přesnou shodu vygenerovaných
iOS/Android/TV souborů. CI spouští kontrolu přes
`tests/test_release_localization.py`. Kompilace a tyto testy
nenahrazují kontrolu vzhledu na iPhonu, Androidu a TV; starší
obrazovky stále mohou obsahovat doslovné popisky nepředané
lokalizačnímu helperu. Tyto případy je nutné průběžně odstraňovat
při plošném UI auditu.

## Původ sloučených překladů

Výchozí katalog vznikl sjednocením stávajících iOS `.strings` a
Android `GameLingoLanguages.kt`. U 38 klíčů byly původní překlady
mezi platformami rozdílné; u těchto překryvů byla upřednostněna
existující iOS lokalizace. Před další veřejnou verzí zkontrolujte
terminologii a reálné obrazovky ve všech třech jazycích.
