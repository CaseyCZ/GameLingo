# GameLingo – společné překlady uživatelského rozhraní

**Jediným zdrojem textů jsou** `shared/locales/*.json`. Tento katalog spravuje **jazyk aplikace**, nikoli původní jazyk hry, rozpoznávání OCR ani cílový jazyk překladu her.

## Podporované jazyky

Seznam jazyků a jejich názvy jsou v `manifest.json`. Výchozí jazyk se odvozuje od nastavení systému (je-li podporován); při neznámém jazyku se použije angličtina. Stávající instalace uchovávají vlastní výběr jazyka.

- `cs.json` – české referenční texty. Zatím se používá původní český text jako klíč.
- `en.json` – angličtina, záložní jazyk.
- `sk.json` – slovenština.

**Pozor:** Toto jsou překlady **rozhraní**, nikoli herní slovník uživatele. Herní slovník má nezávislá pravidla pro nahrazování textu.

## Jak upravit text

1. Upravte klíč a jeho hodnotu v českém `cs.json` a stejný klíč ve všech překladových JSON souborech. Při změně českého klíče je nutné současně změnit všechny odkazy v kódu.
2. Spusťte `python3 tools/generate_locales.py`.
3. Ověřte `python3 tools/generate_locales.py --check` a `python3 -m unittest discover -s tests -p test_release_localization.py -v`.
4. Commitujte **společný katalog i vygenerované soubory**. CI odmítne chybějící klíče, duplicitní položky a neaktuální generované soubory.

Generátor vytváří soubory pro iOS (`App/<jazyk>.lproj/Localizable.strings`), společnou překladovou mapu `tools/GameLingoLanguages.kt` pro Android i Google TV, nabídky jazyků pro oba systémy a `CFBundleLocalizations` v `ios/GameLingoIOS/project.yml`. Nevyžaduje internet, další placené služby ani externí API.

## Jak přidat jazyk

1. Do `manifest.json` vložte například `{"code": "de", "name": "Deutsch"}`.
2. Zkopírujte `en.json` jako `de.json` **a přeložte všechny hodnoty**. Klíče musí být přesně totožné s `cs.json`.
3. Spusťte generátor a testy výše.
4. Ověřte texty, délky, fokus, ovládání a přepínání jazyka na iOS, mobilním Androidu i Google TV.

Generátor doplní nabídku a systémové jazykové soubory. **Nové jazyky se nesmí publikovat s anglickými zástupnými překlady** jen proto, aby prošel test počtu klíčů. Úplnost klíčů zaručuje CI; kvalita překladu vyžaduje jazykovou revizi a test na zařízení.

## Dynamické texty

- SwiftUI statické `Text("Klíč")`, `Label("Klíč", …)` apod. se lokalizují přes vygenerované `.strings` a aplikací nastavenou `Locale`.
- Dynamické texty: `GLInterfaceText.localized("Klíč")` a až následně připojit hodnoty zařízení/čísla.
- Android/Google TV: `GameLingoLanguages.ui(context, "Klíč")`. Obě aplikace používají totožný generovaný katalog.
- Nikdy nepřekládejte jména konzolí, uživatelský obsah, OCR výsledky, síťové identifikátory ani údaje v profilech.

Poznámka k migraci: první společný katalog slučuje historické texty z iOS a Androidu. Stále je nutné postupně odstranit staré přímé řetězce používané bez lokalizačního volání a provést vizuální kontrolu všech obrazovek. Pouhé úspěšné sestavení tuto kontrolu nenahrazuje.
