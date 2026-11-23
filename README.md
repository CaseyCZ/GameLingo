# GameLingo 🎮

**Hraj. Rozuměj.** Otevřený projekt českých překladů herních dialogů a textů přímo na TV.

GameLingo spojuje aplikaci pro telefon s přijímačem pro **Google TV / Android TV**. Rozpoznává obraz z konzole nebo Remote Play, překládá anglické texty a zobrazuje je na televizi.

## Podpora platforem

| Zdroj / zařízení | Stav a původ |
| --- | --- |
| **iPhone + Xbox** | Funkční základ z našeho ověřeného iOS 0.3.2 |
| **Google TV / Android TV** | Funkční příjem titulků z telefonu; testované na jednom zařízení |
| **Android telefon** | Funkční výchozí režim přebíráme z původního PlayCaptionu; samostatný GameLingo build ještě testujeme |
| **PS5** | Funkční Remote Play základ existuje v původní Android aplikaci PlayCaption; převádíme do GameLingo pro Android |
| **PC** | Plánovaná integrace, dosud nepodporováno |

> **Pozor:** PS5 zatím **nefunguje v naší iOS verzi**. Původní Android aplikace má vlastní implementaci PS5 Remote Play. Proto platformy zobrazujeme podle konkrétního telefonu, nikoli jako obecnou podporu na všech systémech.

## Přehled

- Dialogové titulky: OCR vstup **modrá** → český překlad **zelená**.
- Herní texty a volby: OCR vstup **žlutá** → vlastní český výstup **oranžová**.
- Samostatné nastavení fontu, pozice, velikosti a uživatelské profily her.
- Párování telefonu a TV šestimístným PINem v domácí síti.
- Základní design: tmavý motiv, vlastní GameLingo identita, modrá/fialová.
- Původní aplikace a funkční testovací repo zůstávají nedotčené.

## Struktura projektu

- `ios/GameLingoIOS/` – nové iOS rozhraní nad ověřenou Xbox/OCR implementací.
- `android/tv/` – přijímač pro Google TV / Android TV.
- `android/mobile/` – Android telefon a PS5 Remote Play, vycházející z originálního PlayCaptionu.
- `tools/` – reprodukovatelné build patche.
- `docs/` – roadmapa, design a pokyny.

## Licence a původ

Projekt navazuje na otevřený zdrojový kód [PlayCaption](https://github.com/AdamRuzick4/PlayCaption-app), autor Adam Ruzick4, **GNU AGPL-3.0**. Zdrojový základ je při sestavení připnutý na konkrétní commit a naše změny jsou dostupné v tomto veřejném repozitáři. Nejde o produkt společnosti OpenAI, Microsoft, Sony ani Google.

Detaily jsou v [ATTRIBUTION.md](ATTRIBUTION.md). 
