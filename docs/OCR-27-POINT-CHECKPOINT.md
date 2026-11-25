# GameLingo — navazující 27bodový OCR audit (25. 11. 2026)

## Stav a původ rozsahu
Body 1–16 byly ověřeny kumulativním CI 45596056826 (766c26c). Bod 17 prošel všemi joby CI 45606570163 (084c790).
Přesný původní seznam bodů 19–27 nebyl dostupný z předchozího chatu. Níže je **rekonstruovaný implementační rozsah**, nikoli doslovný původní seznam. Změny jsou ve větvi Master; žádný nový Release.

| Bod | Rozsah | Commit |
|---|---|---|
| 18 | Nezávislý opt-in filtr žluté oblasti pro explicitní anglické pokyny ovladače | a4d584b |
| 19 | Přesné ignorované řádky v každé OCR oblasti, bez podřetězcového mazání vět | b450775 |
| 20 | Minimální výška rozpoznaného textu, modrá | ded170b |
| 21 | Minimální výška rozpoznaného textu, žlutá | 8bf1b21 |
| 22 | Volitelná nezávislá kadence žluté oblasti, výchozí dědění | 2d93e71 |
| 23 | Samostatné obnovení filtrů bez změny geometrie | 18baca3 |
| 24 | Opt-in předchozí replika pouze na výstupu, nikoliv v cache nebo historii | 26d6484 |
| 25 | Jedna/dvě předchozí repliky, max. stáří 12 s při výběru, limit 300 znaků předchozího kontextu | c6ed5ea |
| 26 | Opt-in schování běžného titulku při potvrzeném zmizení zdroje | 0563144 |
| 27 | Prodleva 0/1/2 s, nikdy neprodlužuje původní deadline | 26aa16f |

Nová nastavení jsou uložena do profilů, starší profily mají výchozí hodnoty. V režimu přijímače TV se OCR zpracovává na odesílateli. Kontext předchozích replik není podporován v příběhové frontě, při překrývání originálu na Android/TV ani v automatickém výstupu samostatné TV. Více řádků může vyžadovat úpravu zelené oblasti. Prahová velikost textu závisí na vstupní výšce a použitém OCR engine; Vision a ML Kit mohou mít odlišné výsledky. Nepřebírali jsme kód GlossaSub.

## Stav kontroly
- 18: quick a integrační kontroly, Android a iOS build success; TV a balíčky zrušeny novějším sestavením.
- 19–21: rychlé kontroly prošly, kumulativní nativní sestavení zrušeno novějším.
- 23: rychlé a integrační kontroly prošly; nativní sestavení při poslední kontrole probíhalo.
- 24–26: statické kontrakty odhalily očekávání starého tvaru větví a pořadí příkazů; opraveno v navazujících commitech při zachování výchozího časování.
- Kumulativní sestavení aktuálního Masteru musí projít všemi joby. Fyzické QA zatím nebylo provedeno. **Nevydávat Release bez ověření a schválení.**

## Ruční QA před publikací
- iOS/Android/Xbox/PS: krátké a dlouhé dialogy, oprava OCR, opakovaný zdroj, nový jazyk, přepnutí výstupu a relace.
- TCL/Google TV: telefonní HDMI overlay, samostatný režim, vlastní zelený výstup, žluté volby, změny profilů, návrat na výchozí filtry.
- Porovnat režimy OCR bez překrytí a s ním, nastavit velikost 0/1/5 %, ověřit ignorované řádky proti delším větám a výběr správné volby.
- Ověřit 1/2 předchozí repliky, korekci bez duplikace, limit stáří a délky, žádný přenos kontextu mezi relacemi.
- Ověřit vypnuté/hned/1/2 s mizení a nezkracování výchozího času při vypnuté volbě, rychlé A–B–A, chybný překlad, přerušení spojení.
