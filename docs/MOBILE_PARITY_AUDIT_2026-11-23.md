# GameLingo 0.5 — audit shody iOS a Android mobil

**Provedeno:** 23. 11. 2026. **Větev:** `feature/gamelingo-v05-platform-rebuild`. **Rozsah:** statický audit skutečných SwiftUI/Kotlin obrazovek, zdrojů nastavení a jejich návazností + kontroly sestavení. Nejde o vizuální test na telefonech ani test fyzických konzolí.

## Ověřená shoda

| Část | Stav | Doklad |
| --- | --- | --- |
| Názvy a pořadí pěti záložek | ✅ Shoda | Generováno ze `shared/mobile-ui.json` do `GameLingoUIContract.swift` a `GameLingoUIContract.kt` |
| Názvy a pořadí pěti kroků průvodce | ✅ Shoda | Oba čtou `setupSteps` |
| Sekce Nastavení | ✅ Shoda | Oba čtou `settingsSections` |
| Hlavní barevné tokeny | ✅ Shoda hodnot | `GLNeon` / `GLStyle` / `GameLingoVisualKit` používají generované `Palette` |
| Jazyky hry | ✅ Shoda | en, de, fr, es, it, pt |
| Cílové jazyky překladu | ✅ Shodná množina | cs, en, sk, de, pl, fr, es, it, pt; pořadí výběru se ještě liší |
| Konzole Xbox a PlayStation | ✅ Obě mají nativní nabídky | Existence a sestavení tras ověřeno; fyzické připojení neověřeno |
| Čtyři oblasti titulků | ✅ Stejné role oblastí | Modrá, zelená, žlutá, oranžová |
| Vypnutí falešného zeleného stavu Androidu | ✅ Opraveno v auditu | Konfigurovaná konzole není zobrazována jako potvrzené živé spojení |
| Dokončení Android průvodce bez platné konfigurace | ✅ Opraveno v auditu | Závěrečné tlačítko je nyní zakázané do splnění podmínek |

## Zbývající rozdíly — nelze tvrdit „úplně stejné“

| Priorita | Část | Skutečný rozdíl | Potřebná úprava |
| --- | --- | --- | --- |
| Vysoká | Spodní navigace | iOS mění hlavní obsah a zvýrazněnou záložku. Android v aktuálním sestavení spouští dialogy a v navigaci napevno označuje Domů. | Předělat Android na trvalý pětizáložkový kontejner se stránkami bez modálních oken |
| Vysoká | Jazyky rozhraní | iOS má cs, en, sk; Android má cs, sk. | **Nevyplňovat automaticky — uživatel zakázal změny jazyků bez schválení.** |
| Vysoká | OCR nastavení | iOS má samostatný režim OCR, filtrování menu, rozšířené hledání a reset. Android volbou OCR otevírá editor oblastí. | Samostatný funkční panel Android OCR propojený s reálnými OCR nastaveními |
| Vysoká | Vzhled titulků | iOS nabízí na telefonu velikost, barvu textu a pozadí, a zvlášť TV. Android má vlastní TV/telefon profil `tvPismo`, `tvSirka` a uzamčení; sadu barev a pozadí místních titulků neobsahuje. | Doplňovat jen s reálným napojením na místní renderer, ne kosmetickými přepínači |
| Střední | TV propojení v průvodci | iOS pro dokončení TV požaduje stav spárování; Android nyní kontroluje existence uloženého TV profilu. | Sjednotit ověřování skutečného spárování a návrat do průvodce po dokončení párování |
| Střední | Ovládání TV velikosti titulků | iOS má 70–180 % a šířku 30–95 %, Android 50–250 % a 20–100 %. | Dohodnout společný rozsah bez odebrání funkčního ovládání |
| Střední | Rozložení/pohyb pozadí | iOS používá SwiftUI barevné záře, Android nativní gradient. Barvy jsou stejné, vykreslení není identické. | Porovnat screenshoty stejných úhlopříček a upravit vykreslení Androidu |
| Střední | Průvodce | Oba mají 5 fází; Android však dovoluje skoky z úvodního panelu a při volbě TV/konzole otevírá jiné průběhy. | Sjednotit postup a uchování kroku během párování |
| Otevřené | Remote Play + OCR v praxi | Statické kontroly a sestavení neověří PSN, Microsoft, přijímané snímky, zpoždění titulků ani TV přijímač. | Fyzické testy iPhone + Android + Xbox/PS + TV |

## Potvrzené sestavení před auditem

- [Android mobil – GitHub Actions / build úspěšný](https://github.com/CaseyCZ/GameLingo/actions/runs/45249965715)
- [iOS nativní aplikace – GitHub Actions / build úspěšný](https://github.com/CaseyCZ/GameLingo/actions/runs/45250577982)

Následující změny Androidu (bezpečné zobrazení připravenosti a zakázané neplatné dokončení) vyžadují nové sestavení, aby byl audit potvrzen i pro poslední revizi.

**Závěr:** Sdílená designová specifikace už existuje a lze ji měnit společně. UI/UX ještě není 1:1. „Stejné“ se smí deklarovat až po odstranění výše označených rozdílů a po fyzickém porovnání obou aplikací. `Master`, veřejné vydání a TV rozhraní nebyly v tomto auditu měněny.
