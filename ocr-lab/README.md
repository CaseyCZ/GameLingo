# GameLingo OCR Lab (Windows)

Samostatný experiment na větvi `feature/ocr-lab`. Nemění Android, iOS, TV, verzi ani Releases. Nevyžaduje ADB, server, klíče, externí AI API ani mikrofon.

## Spuštění (Windows 10/11, PowerShell 5.1)

Python 3.11 a Tesseract:

```powershell
winget install --exact --id Python.Python.3.11
winget install --exact --id UB-Mannheim.TesseractOCR
```

Po instalaci otevři nové okno PowerShellu, stáhni větev do nové složky:

```powershell
gh repo clone CaseyCZ/GameLingo GameLingo-OCR-Lab -- --branch feature/ocr-lab --single-branch
cd GameLingo-OCR-Lab\ocr-lab
powershell -NoProfile -ExecutionPolicy Bypass -File .\start.ps1
```

ExecutionPolicy platí pouze pro nový proces PowerShellu. Skript vytváří lokální .venv, nevyžaduje správce, instaluje závislosti a spouští jednotkové testy. Pro další engine spusť stejný skript s `-Rapid` (volitelná větší instalace). Pro lokální překlad s `-Translate`; oba přepínače lze kombinovat. První stažení balíčků vyžaduje internet.

## Živý test

Pusť video, pozastav na titulcích, zvol Vybrat oblast myší, obkresli jen anglický text. Otevře se zmenšená kopie hlavního monitoru; po výběru se obraz nezapisuje na disk. Spusť OCR a video. Ovládací okno nesmí zakrývat snímanou oblast; případně jej minimalizuj. Plovoucí okno zůstává nad videem mimo oblast. Zastavení je v něm i v hlavním okně. Parametry lze měnit za běhu, engine se načítá při prvním použití. Pokud změníš volbu offline překladu, vypni a znovu zapni plovoucí okno, aby ukazovalo správný výstup.

- Tesseract (lokální angličtina, PSM 6 pro více řádků, 7 pro jeden).
- RapidOCR (ONNX Runtime, volitelný lokální model).
- Filtry raw / gray / contrast / binary; měřítko 50–150 %, interval 100–1000 ms.
- Jediný aktivní OCR požadavek; žádná fronta starých obrazů. Měříme zachycení + filtr zvlášť a OCR zvlášť. OCR zahrnuje převod do vstupního formátu, u Tesseractu i spuštění procesu.
- Počty nových, stejných a prázdných výsledků nejsou skutečné ztráty vět: bez referenčního textu nevíme, co OCR nevidělo. Pro známou referenci použij syntetický benchmark.
- Experiment s průběžnými titulky odstraňuje pouze přesný překryv alespoň dvou slov; změny generací zabraňují zobrazení staré odpovědi. Překlad je nezávislý na OCR, jedna aktivní úloha a poslední čekající text.
- CSV pouze ruční export, max. 10 000 posledních vzorků v RAM; obsahuje i rozpoznaný text. Nesnímej citlivý obsah. Žádná telemetrie, automatický upload nebo ukládání obrazovky.

### Offline překlad (nepovinný)

Argos Translate je samostatný experiment, nikoli produkční překladač GameLingo. Nainstaluj balíček pomocí `-Translate` a kompatibilní model angličtina → čeština z oficiálního katalogu https://www.argosopentech.com/argospm/index/ ; model nainstaluj lokálně pomocí `argostranslate.package.install_from_path`. Žádný model se automaticky nestahuje. Pokud model není dostupný, OCR funguje dál a aplikace oznámí chybu. Modely a závislosti mají vlastní licence; před jejich začleněním do APK musí proběhnout samostatná kontrola.

## Opakovatelný benchmark

```powershell
.\.venv\Scripts\python.exe benchmark.py --engine tesseract --duration 1500 --filter raw --output baseline.json
.\.venv\Scripts\python.exe benchmark.py --engine tesseract --duration 1500 --filter contrast --scale 75 --output contrast.json
.\.venv\Scripts\python.exe benchmark.py --engine rapid --duration 1500 --output rapid.json
```

12 syntetických vět, pevná doba 500/1000/1500/2000/3000 ms. Každá metoda dostane stejný scénář. Měří se skutečný čas OCR + předzpracování na PC, simuluje se posun časové osy bez hromadění snímků. Zvlášť se uvádí warmup, průměr, P95, rozpoznané a vynechané věty (podobnost normalizovaného textu alespoň 0,9), duplicity, chybné a pozdě dokončené nové události. Výsledek je syntetický benchmark, nikoli výkon ML Kit ani měření na TCL. Reálné titulky mají proměnlivé pozadí a časování; je nutné testovat i živé video.

## Kontrola

`python -m unittest -v test_core` testuje mezery, opakování, generace, překryv slov a známou referenci. `python -m compileall -q .` kontroluje syntaxi (spouštěj mimo .venv nebo explicitně soubory).
První etapa: experimentální Windows nástroj, ne náhrada GameLingo. Finální rozhodnutí o algoritmu až po porovnání a měření na TCL C835.

## Etapa 2 – měření skutečného překladu

Spusť `powershell -NoProfile -ExecutionPolicy Bypass -File .\start.ps1 -PrepareModel` pro explicitní instalaci Argos EN-CS a ověření zkušebního překladu. Zapni Offline překlad v GUI. Příště stačí start bez přepínače. Závislosti a model se stahují při přípravě; pro striktní offline test po přípravě odpoj internet a používej lokální video. Nepřidává se žádný online překladový provider. GameLingo TV používá jinou (online) cestu, proto nejde o její benchmark.

CSV nově obsahuje OCR vzorky a události translation_completed, translation_stale a ui_displayed. Měří čekání, překlad, dobu od začátku zachycení snímku do výsledku a předání textu Tk UI (nikoli čas fyzického vykreslení nebo skutečného nástupu zdrojového titulku). Cold měření zahrnuje první inicializaci modelu; warm P95 je bez něj. Jedna aktivní úloha, jeden nejnovější čekající text. Výsledek může být zobrazen i při novějším OCR, maximálně do 3 sekund stáří, ne po potvrzeném prázdném snímku ani změně konfigurace. Počty jsou za relaci; pro nové nastavení spustit novou relaci. Zahozené/nahrazené OCR kandidáty nelze bez známé reference vydávat za vynechané věty. Při zastavení může poslední úloha zůstat nedokončená. Windows OCR a OneOCR zůstávají další etapou.

## Etapa 3 – okamžitý výstup a volitelná stabilizace

Překlad běží v samostatném workeru s jedním nejnovějším čekajícím kandidátem. GUI čte hotový výsledek každých 25 ms nezávisle na OCR. První zkušební překlad před snímáním se měří jako model_warmup; není součástí latence jednotlivých titulků. Při změně nastavení/konci textu se staré výsledky invalidují. Volitelná stabilizace potvrzuje změnu dvěma přesně shodnými OCR výsledky, nepovažuje podobné věty automaticky za stejné. Výchozí je vypnutá; může vynechat velmi krátký nebo neustále se měnící text. CSV zaznamenává raw_source a held. Měřená latence snímek → UI u stabilizace začíná až potvrzujícím snímkem, nezahrnuje čekání na druhé potvrzení. Pro srovnání ztrát je nutná známá reference. Windows OCR a další obrazové filtry nejsou v této etapě implementovány.

## Etapa 4 – živá laboratoř nastavení

Aktualizace: `git pull --ff-only`, poté `powershell -NoProfile -ExecutionPolicy Bypass -File .\\start.ps1 -WindowsOCR -Rapid`. Argos již nainstalovaný zůstává; model není potřeba znovu stahovat. Windows OCR vyžaduje anglický OCR jazyk Windows; pokud chybí, zvol jiný engine. Žádné externí AI API.

Záložky OCR a obraz / Text a stabilizace / Překlad a výstup. Filtry raw, gray, contrast, binary, white, outline (MIT SubLens), adaptive, darkbox, sharpen; volitelný okraj, inverze, práh jasu. Engine Tesseract, RapidOCR, Windows OCR. Geometrie a confidence (Windows OCR důvěru neposkytuje, proto se tam ignoruje); jemné odstranění samostatných symbolů. Stabilizace off, exact (původní), adaptive, rolling. Adaptive/rolling čeká nejvýše nastavený čas plus dobu do dalšího OCR výsledku; může stále přijmout šum nebo přeskočit krátký text. Image gate přeskočí pouze přesně stejný filtrovaný obraz, nejpozději po 700 ms znovu OCR. Předvolby jsou experimentální a nemění engine ani zapnutí překladu. Měň nastavení živě; každý úsek má v CSV settings a segment (změna konfigurace resetuje OCR statistiky, souhrn překladu je za relaci). frame_to_ui_ms měří potvrzující snímek, first_seen_to_ui_ms zahrnuje čekání na potvrzení pro adaptive/rolling, nikoli přesný nástup titulku. Doba zobrazení a velikost písma jsou samostatné volby UI. Plovoucí okno mění výšku a nesmí zasahovat do ROI. Při nedostatku místa se skryje. Přesnost bez známé reference nelze vyčíslit. OneOCR a EasyOCR nejsou zahrnuty: proprietární součásti/velká dodatečná závislost, zatím neověřené. Viz ATTRIBUTION.md.
