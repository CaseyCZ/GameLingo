# GameLingo 0.5 — společné iPhone / Android rozhraní

Historický snímek implementace vývojové větve `feature/gamelingo-v05-platform-rebuild` (23. 11. 2026).

## Změny realizované společně

| Oblast | iPhone | Android mobil | Poznámka |
| --- | --- | --- | --- |
| Spodní navigace | SwiftUI stránkování | Trvalý vnořený `pageHost`, aktivní záložka | Pět stejných cílů ze `shared/mobile-ui.json` |
| Domů | Hero, konzole/TV, CTA, čtyři zkratky | Stejná struktura s nativními widgety | Karta konzole nemůže tvrdit, že je online, pouze podle uloženého profilu |
| Obrazovka | Čtyři barevné oblasti, editor | Čtyři barevné oblasti, skutečný editor | Role oblastí sjednocené |
| Konzole | Správce Xbox/PS/TV | Stejný skutečný správce jako obsah záložky | Mobil nepoužívá modální kopii hlavní stránky; přihlašovací kroky zůstávají nativní |
| Nastavení | Jazyky, OCR, Překlad, Oblasti, Vzhled, Profily, Zařízení, Diagnostika, Licence | Stejné členění a funkční podstránky | SwiftUI a Android Views jsou stále oddělené |
| OCR | Původní/vyvážený/rychlý režim, filtr menu a rozšíření | Stejná volba režimu, filtr a rozšíření napojené na nativní čtečku | Vlastní intervaly obou dekodérů se liší, nelze je doslova sjednotit bez testů výkonu |
| Titulky na telefonu | 70–180 %, pět barev, žádné/tmavé/průhledné pozadí | Totéž, napojeno na `TelefonActivity` | Android TextView s lokálním zaobleným pozadím |
| TV titulky | 70–180 %, šířka 30–95 %, zámek | Stejná rozsahová nastavení | Skutečné vykreslení ověřit na televizoru |
| Pozadí / barvy | SwiftUI neonové vrstvy | Nativní Android vrstvené radiální přechody | Společné barevné tokeny |
| Úvodní průvodce | Pět fází | Pět fází | Nedokončí konfiguraci bez vybrané konzole a výstupu |

## Výjimky, které nejsou vyřešené pouhým UI refaktorem

- **Jazyky rozhraní:** iOS aktuálně nabízí `cs/en/sk`, Android `cs/sk`. Seznamy jazyků nebyly změněny, protože uživatel výslovně požaduje neměnit je bez souhlasu. Jazyky hry a překladové cíle zůstaly také beze změn.
- **Protokol Google TV (aktualizace 24. 11.):** iOS i Android používají šestímístný PIN/QR, HMAC podepsané titulky a PING/PONG, freshness/replay ochranu. Keystore/Keychain. Legacy unsigned je explicitní opt-in, výchozí off; zvláštní account-transfer výjimka zůstává rizikem. UDP není šifrované. HW čeká.
- **Více telefonů (aktualizace 24. 11.):** přijímač podporuje až 32 identit a jednotlivou revokaci, pouze jeden aktivní zdroj. HW čeká.
- **Funkční parita:** audit CODE-02/03/04/07 odhalil neúplné Android profily a nezávislé oranžové odesílání, přímý TV story/modrou oblast a paused profile lifecycle. Viz [audit kódu](CODE-AUDIT-0.5.md). Nejde o potvrzenou 1:1 funkční shodu.
- **Další nastavení překladu:** Android i iOS mají odlišnou implementaci časování/starých překladů. Uvádět 1:1 bez ověřeného napojení by bylo nesprávné.
- **Pixelová / funkční shoda:** společné tokeny a struktura nestačí k prokázání shody na všech rozměrech obrazovky. Vyžadují se odpovídající screenshoty a fyzické testy připojení Xbox/PlayStation a ověření OCR.

**Udržování:** nejdříve upravit `shared/mobile-ui.json`, potom vygenerovat oba kontrakty pomocí `python3 tools/sync_mobile_ui.py`. Funkční podstránky se mění v nativních komponentách obou platforem a ověřují regresními testy a sestavením.

**Zábrany:** Žádné publikování nové Release před ověřením a schválením. `Master` i televizní aplikace zůstávají nedotčené.

Aktuální stav auditní větve: [STATUS-0.5.md](STATUS-0.5.md). Ostatní popis 23. 11. je historický.
