# P2.5 — architektura offline překladu

Datum: 2026-11-24. **Aktualizace rozhodnutí uživatele:** vlastní TV offline překladač definitivně mimo rozsah. Samostatné OCR, online překlad, cache a přijímač z telefonu zachovat. Níže uvedené historické TV standalone NMT alternativy jsou zrušené, ne budoucí úkol. TV ML Kit Translation odstraněn a CI 45384321662 PASS. Žádná žádost o výjimku. Offline inference zůstává tématem pouze telefonů. Návrh bez implementace; žádná nová závislost, model, build, release ani změna jazykových voleb.

## 1. Skutečný stav

- iOS deployment target 17.0 (project.yml), Vision OCR, CaptionPipeline.translate: explicitní Result, SQLite cache, Google GTX a MyMemory přes HTTPS, oddělené circuit breakery. Bez lokálního překladového modelu. Offline umí jen cache hit nebo stejný zdroj/cíl; novou větu bez internetu nepřeloží.
- Android mobil a TV: společný připnutý upstream PlayCaption Prekladac.kt, SDK TranslateLanguage, Translator, downloadModelIfNeeded v priprav a nastavJazyk, patch wire_gamelingo_languages.py doplňuje source, SQLite a breakery. Standardně cache → Google → Microsoft → MyMemory → ML Kit. Rychlý náhled a story mají vlastní ML Kit větev. Modely se dnes mohou připravovat automaticky, neexistuje jasný offline-only režim ani kompletní uživatelský správce modelů. Přítomnost SDK a úspěšný build neprokazují model v zařízení ani překlad bez internetu.
- P2.4 slovník je lokální postprocessing úspěšného překladu. Není samostatný offline překladač a nemá měnit raw cache. Story omezení P2.4 zůstává.
- Jazyk hry na obou platformách: en/de/fr/es/it/pt. Cíl: cs/sk/en/de/pl/fr/es/it/pt. Zachovat UI a BCP-47 kódy; offline dostupnost je vlastnost engine/páru, ne důvod skrytí jazyka.

## 2. Kritický licenční nález před release

Google Usage guidelines for ML Kit on-device translation, aktualizováno 2026-11-09, omezuje on-device Translation API na osobní počítačová zařízení a vyžaduje předchozí písemné povolení Googlu pro použití v televizích a dalších embedded zařízeních. Zároveň požaduje příslušnou Google atribuci. Podmínky a soukromí ML Kit vyžadují informování uživatele o zpracování metrik SDK.

To se týká již zděděné TV překladové cesty, nejen případné nové funkce. Nemáme doklad písemného souhlasu. **Release blocker: veřejnou TV APK nepovažovat za připravenou k distribuci, dokud nebude písemně vyjasněn souhlas nebo odstraněna nepovolená TV integrace.** Pouhé označení TEST či vypnutí tlačítka nezaručuje splnění podmínek; zkontrolovat transitive dependency a všechny cesty inicializace/preload/quick/story, oddělit mobile a TV adapter/build varianty. Před další distribucí prověřit i ostatní ML Kit API podle jejich vlastních podmínek, SDK notices a AGPL-3.0. Tento auditní bod nemění kód (výslovné zadání); náprava vyžaduje samostatný odsouhlasený bezpečnostně-licenční commit a CI. Neznamená to, že Google zakazuje samotný Android TV overlay nebo veškeré OCR. Nejde o právní posudek.

## 3. Doporučené platformní řešení

| Platforma | První volba | Podmínky a alternativy |
|---|---|---|
| Android telefon | Správa stávajícího ML Kit adapteru | Podporuje všechny současné vybrané jazyky dle seznamu SDK; ověřit přesný pár v runtime. Ověřit podmínky atribuce, soukromí, redistribuce a licence. |
| iOS | PoC ML Kit Translate pro shodu češtiny/slovenštiny | Existuje iOS SDK, jazykové modely okolo 30 MB, ale Xcode/architektura, podepisování/sideload IPA, závislosti CocoaPods, velikost a iOS 17 compatibility vyžadují ověření. Nepřidávat SDK do aktuálního auditu. |
| iOS alternativně | Apple Translation framework | Novější OS, runtime availability a installed status, explicitní prepareTranslation v UI. Nezvyšovat minimum iOS 17; neslibovat češtinu ani slovenštinu bez reálné LanguageAvailability na cílovém OS/zařízení. Nepoužívat Apple Intelligence high-fidelity režim v rámci této fáze. |
| TV receiver | Překlad na telefonu (včetně offline) → stávající podepsané UDP titulky | Preferovaná cesta. TV nepotřebuje překladový model, internet ani účet překladače, ale místní spojení s telefonem musí fungovat. |
| TV standalone | Samostatný licenčně vhodný lokální NMT engine, až po PoC | Kandidát OPUS-MT/Marian en→cs (model card Helsinki-NLP) a CTranslate2 (MIT) nebo jiný mobilně kompilovatelný runtime. Ověřit samostatně model/weights/tokenizer/tréninková data, runtime/native dependencies, komerční a redistribuční podmínky, AGPL, ABI, RAM/CPU/teplotu. MIT licence runtime není automaticky licence modelu. Žádné tvrzení o hotovém Android TV portu nebo pokrytí všech párů. |

ML Kit podporuje více než 50 jazyků; překlad mezi dvěma neanglickými jazyky může používat angličtinu jako mezikrok, a proto zhoršit kvalitu. Jeho jazykové modely mají přibližně 30 MB, skutečná spotřeba na disk/RAM závisí na páru, verzi SDK a modelu. Nepřevádět odhad na garantovanou velikost celého IPA/APK. Angličtina je dle dokumentace iOS SDK vestavěný model; kontrolovat stav SDK, ne mechanicky vyžadovat dvě stažení. U alternativních modelů měřit skutečná data.

## 4. Rozhraní a tok dat

Navržené interní rozhraní (pseudokód, ne implementace):
```
TranslationEngine:
    id, modelIdentity, supports(source, target)
    availability(pair) -> unsupported | missing | preparing | ready | failed
    translate(request) -> success(text, engine, modelIdentity) | failure(reason)
ModelManager:
    listInstalled(), estimate(pair), prepare(pair, consent, networkPolicy)
    cancelPreparation(), remove(model), refreshAvailability()
TranslationRequest:
    original, source, target, lane, session, revision, deadline, mode
```

- Jeden explicitní routing layer před OCR výstupem, neměnit HMAC/Keystore/port, geometry, profily nebo jazykové katalogy. Context snapshot jazyka, profilu, relace a engine režimu; generation při změně konfigurace. Rušit/potlačit opožděný výsledek. Latest-only OCR, žádná neomezená fronta model inference. Dialog a yellow lane odděleně; jedno omezené inference worker pool, fairness a přiměřené priority; využít existující diagnostics.
- Navržené volby: Auto (stávající cache/online/fallback podle platformy, pouze schválené enginy), Offline-first (připravený lokální model, online jen pokud uživatel povolí), Offline-only (žádný síťový překlad, žádný automatický model download ani probe). Nová nastavení až po schválení. Zvolený režim musí fungovat i pro story, quick a choices, ne jen hlavní dialog.
- Offline-only může použít existující lokálně uložené výsledky bez sítě, ale původní online engine zachovat v metadatech; jasně označit, že jde o uložený překlad. Cache miss → pouze připravený lokální engine. Při chybě modelu explicitní failure, nikdy raw OCR text jako překlad. Stejný jazyk je legitimní no-op success. Breaker jen pro online providery; offline-only žádné probe.
- Cache klíč engine/model identity + zdroj/cíl + originál. Stávající 20k LRU zachovat; při výměně modelu oddělit cache namespace. Pokud SDK neposkytuje přesnou revizi modelu, použít konzervativní adapter/SDK epoch a zdokumentovat omezení. Slovník až po success, nezapisovat opravený text do provider cache. Historie zůstává oddělená a opt-in.
- Překlad offline znamená bez internetu pro překladový engine, nikoli zaručené offline spuštění Xbox/PlayStation Remote Play. Přihlášení, konzole, licence hry, discovery a transport mohou mít další síťové požadavky. Netflix/protected capture není tímto řešen.

## 5. Životní cyklus modelů a soukromí

Při explicitním výběru jazykového páru zobrazit dostupnost, odhad velikosti, volné místo, povolený způsob připojení. Uživatel potvrdí přípravu před hraním. Wi-Fi default, mobilní data pouze výslovně. Nezahajovat download v OCR smyčce, při změně jazyka bez souhlasu ani při startu offline-only. V SDK-managed model download nelze garantovat skutečné zrušení/byte progress, pokud jej API neumožňuje: UI oznámí čekající/opuštěnou operaci a znovu ověří stav, neslibuje smazání částečného SDK souboru. U vlastních modelů pinned manifest, ověřený podpis/hash a velikost, atomické staging, odstranění neúplného souboru, rollback funkční verze. Žádná libovolná URL ani načtení nedůvěryhodných modelů/remote code. Nikdy neredistribuovat ML Kit model files v repu nebo APK.

Správce modelů ukáže nainstalované modely, aktivní pár, skutečný/odhadovaný disk pokud API dovolí, možnost odstranění neaktivního modelu a explicitní re-download. Modely používané více páry sdílet dle SDK, neodstranit aktivní model během inference. Apple system-managed download/delete mohou vyžadovat systémovou správu; nabídnout pouze operace skutečně podporované API. Omezit počet živých translator instances, close/dispose po relaci, sledovat PSS/RSS/thermal a při kritickém stavu snížit zátěž. Uvolnění prostoru nemá mazat uživatelské profily, cache nebo historii bez explicitního souhlasu.

Offline engine neposílá herní text externímu překladači. SDK může mít vlastní provozní metriky, modelové downloady a systémové síťové chování; netvrdit absolutní nulovou telemetrii bez ověření. Samostatně auditovat síťové chování SDK a uvést jej v privacy notices, zejména ML Kit Terms & Privacy. Nový cloud fallback pouze po zvolení odpovídajícího režimu.

## 6. PoC a testovací plán (vše zatím neprovedeno)

1. Nejprve vyřešit licenční TV blocker a oddělit platformní závislosti; žádný TV ML Kit translate bez doloženého oprávnění.
2. Android telefon: správce stávajících modelů a režim offline-only, všechny vstupní cesty, explicitní consent, odstranění automatického downloadu. Ověřit cold start, restart a chybějící model.
3. iOS PoC na oddělené větvi: angličtina → čeština/slovenština jako prioritní test, iOS 17 a novější podle SDK kompatibility. Porovnat Apple Translation runtime dostupnost. Teprve po PoC vybrat adapter.
4. TV receiver: telefon v režimu offline-only, TV bez WAN, zachovaná Wi-Fi/LAN; ověřit HDMI Xbox/PS4/PS5, pairing, port fallback a znovupřipojení. Samostatný TV NMT PoC až po licenci a měření na TCL C835; bez předběžného slibu výkonu.
5. Benchmark: stejné sady krátkých a delších dialogů, názvů her, voleb a dlouhých textů na všech podporovaných směrech; přirozenost češtiny/slovenštiny, překlad jmen, neanglický pivot. Nepoužívat soukromé texty bez svolení. Měřit cold/warm OCR→výstup, inference P50/P95, disk, paměť, teplotu, 30min relaci. Předběžný cíl PoC pro krátkou větu warm inference P95 do 700 ms na cílovém telefonu (návrhový cíl, ne naměřený výsledek), TV cíl až po základním měření. V režimu bez sítě prokázat překlad dosud necachované věty. Packet capture s blokovaným WAN pro ověření žádných požadavků na online providery, odděleně popsat SDK telemetry a LAN titulky.
6. Testy: model absent/corrupt/deleted, quota/low storage, Wi-Fi consent odmítnut, změna source/target A→B→A, stop/start, změna režimu v průběhu inference, stale text, cache model version, glossary, history, story, quick, obě ROI, neúspěšný překlad, low-memory a thermal. U simulovaných chyb žádný implicitní online fallback v offline-only. CI ověří routing, testy a build; fyzické offline překlady vyžadují HW.

## 7. Ověřené podklady

- https://developers.google.com/ml-kit/language/translation/translation-terms (TV omezení a atribuce, aktualizováno 2026-11-09)
- https://developers.google.com/ml-kit/terms (metriky a podmínky)
- https://developers.google.com/ml-kit/language/translation
- https://developers.google.com/ml-kit/language/translation/android
- https://developers.google.com/ml-kit/language/translation/ios
- https://developers.google.com/ml-kit/language/translation/translation-language-support
- https://developers.google.com/android/reference/com/google/mlkit/common/model/RemoteModelManager
- https://developer.apple.com/documentation/translation/languageavailability
- https://developer.apple.com/documentation/translation/translationsession
- https://huggingface.co/Helsinki-NLP/opus-mt-en-cs (kandidát, nikoli schválená licence)
- https://github.com/OpenNMT/CTranslate2/blob/master/LICENSE (runtime MIT, ne modelová licence)

Rozhodnutí: návrh P2.5 dokončen. Žádná implementace. P3 pouze návrhy AI, dabingu, Samsung/LG a závěrečný konsolidovaný report s release blockers a HW maticí.
