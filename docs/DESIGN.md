# GameLingo – vizuální styl

Schválená identita vychází z [CaseyCZ](https://caseycz.github.io/), [iOS-Hub](https://github.com/CaseyCZ/iOS-Hub) a výchozího **manager-dark** motivu [GameS Calendar](https://github.com/CaseyCZ/GameS-Calendar-Website), nikoli z grafiky GlossaSub.

## Barevná paleta

| Účel | Hodnota |
| --- | --- |
| Pozadí (dark) | `#070B14` |
| Druhé pozadí | `#0B1020` |
| Karta | `#111827` |
| Zvýrazněná karta | `#172033` |
| Formulář | `#0B1220` |
| Okraj | `#2A3850` |
| Hlavní text | `#F8FAFC` |
| Vedlejší text | `#94A3B8` |
| Akcent – světle modrá | `#38BDF8` |
| Akce – sytě modrá | `#0284C7` |
| Úspěch / připojeno | `#86EFAC` |
| Značková fialová (CTA gradient) | `#884BF8` |

- Zaoblené karty, přehledné rozestupy, nativní tlačítka, velké dotykové plochy a bílé nadpisy.
- Primární akce používá modro-fialový gradient GameLingo; běžné ovládání je modrobílé. Stav připojení vždy používá samostatnou zelenou nebo neutrální barvu.
- Čtyři barvy oblastí OCR (modrá, zelená, žlutá a oranžová) mají **funkční význam** a nesmí se zaměňovat s vizuální identitou.
- Na iOS používat SF / nativní typografii, na Android TV čitelnou typografii a navigaci dálkovým ovladačem.
- Dynamické stavy spojení jsou důležitější než dekorativní efekty.

## Hlavní obrazovky

### iPhone / Android telefon
Pět společných záložek na iOS a Android telefonu, ve stejném pořadí:
1. **Domů** – aktuálně vybraná konzole, výstup, skutečný stav a jediné hlavní tlačítko Spustit překlad.
2. **Oblasti** – čtení dialogů, výstup titulků, čtení herního textu, výstup herního textu.
3. **Profily** – samostatné nastavení podle hry.
4. **Zařízení** – více Xboxů, PlayStationů a Google TV / Android TV; výběr, přidání a odstranění.
5. **O aplikaci** – aktuální verze, zdrojové kódy a právní atribuce.

**Nastavení** je samostatná obrazovka dostupná z horní lišty. Pro iOS i Android platí: uložená konzole není totéž co aktivní Remote Play; během běžícího překladu nelze konzoli nenápadně zaměnit.

### Google TV / Android TV
- GameLingo TV logo a připojovací stav, šestimístný PIN, síťová diagnostika, náhled překladu.
- Plně ovladatelné dálkovým ovladačem, oba režimy („Překlad z telefonu“ a „Přímo na TV“) mají zřetelný stav zaměření a sousední směr navigace.
- Překryv nepřebíjí herní obraz tam, kde to není potřeba.
- Žádné označení testované značky televizoru v popiscích.

## Stav

Větev `feature/gamelingo-v05-platform-rebuild` obsahuje rozpracované nové iOS/Android/TV obrazovky. Přesnou implementaci a ověřený stav všech devíti auditních bodů průběžně eviduje [WORK_CHECKPOINT_0.5.md](WORK_CHECKPOINT_0.5.md). Funkce, které dosud neprošly fyzickým testem, nejsou považovány za dokončené.
