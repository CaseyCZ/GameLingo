# GameLingo 0.5 — společné mobilní rozhraní iOS a Android

**Cíl:** Stejná informační architektura, pořadí navigace, průvodce, barvy a pojmenování na iOS a Androidu. Neznamená to sdílený spustitelný kód: iOS je SwiftUI, Android jsou nativní Kotlin views. Google TV má samostatné dálkové rozhraní a **není** součástí mobilního kontraktu.

## Jediný zdroj pro společné prvky

`shared/mobile-ui.json` určuje:
- pět záložek: Domů / Obrazovka / Konzole / Nastavení / O aplikaci;
- pět kroků úvodního průvodce;
- čtyři sekce Nastavení;
- společné barvy značky (tmavá plocha, cyan, modrá, fialová, text).

Po schválení společné změny:
1. Uprav `shared/mobile-ui.json`.
2. Spusť `python3 tools/sync_mobile_ui.py`.
3. Změny zapracuj do příslušných nativních komponent podle potřeby:
   - iOS: `ios/GameLingoIOS/App/GameLingoHome.swift`, `GameLingoSetupView.swift`, `SettingsHomeView.swift`, `GameLingoVisualSystem.swift`.
   - Android mobil: `tools/GameLingoNavigation.kt`, `GameLingoFirstRun.kt`, `GameLingoSettings.kt`, `GameLingoVisualKit.kt`, `GameLingoRegions.kt`, `GameLingoDeviceCenter.kt`.
4. Spusť `python3 tools/sync_mobile_ui.py --check` a regresní testy. Náhodná ruční úprava jednoho generovaného souboru se v CI odhalí.
5. Sestav obě mobilní aplikace. Nativní kompilace a reálný test na telefonech zůstávají nutné.

## Společné obrazovky a provozní pravidla

| Zóna | iPhone | Android | Poznámka |
| --- | --- | --- | --- |
| Domů | GameLingoHome | redesign_gamelingo.py + GameLingoVisualKit | Hero, vybraná konzole/TV, spuštění, zkratky, živý stav |
| Obrazovka | SubtitleSettingsView | GameLingoRegions + TelefonActivity | Modrá/zelená/žlutá/oranžová, skutečný OCR editor |
| Konzole | GameLingoDevicesPanel | GameLingoDeviceCenter | Xbox, PlayStation, TV; přepínat až po zastavení relace |
| Nastavení | SettingsHomeView | GameLingoSettings | Jazyky, OCR, Překlad, Oblasti, Vzhled, Profily, Zařízení, Diagnostika, Licence |
| O aplikaci | GameLingoHome + LicenseView | GameLingoAbout | Licence AGPL a původní autoři |
| První spuštění | GameLingoSetupView | GameLingoFirstRun | Vítej → Jazyky → Konzole → Výstup → Kontrola |

**Jazyky:** Seznamy původních/ cílových jazyků nejsou tímto refaktorem měněny. Technické chování jejich překladových pipeline musí být ověřeno samostatně.

**Nefalšovat stav:** Uložení konzole není živé Remote Play; uložená TV nemusí být připojená; aktivní relace bez video snímku není potvrzené OCR. Soubory APK a IPA jsou testovací kandidáti do doby fyzických testů.

**Záměrná výjimka:** iOS a Android používají nativní technologie ovládání (SwiftUI / Android Views). Identické barvy a pořadí prvků nezaručují pixelovou shodu na různých zařízeních. Před veřejným vydáním vždy vizuálně porovnej odpovídající obrazovky na skutečných telefonech a proveď scénáře v `PHYSICAL_QA_0.5.md`.
