package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Three distinct settings: app UI, OCR/game source, translated output.
 * English is the fallback selection for unsupported system languages.
 * Legacy upstream screens may still use Czech until localized.
 */
object GameLingoLanguages {
    data class Language(val code: String, val title: String)
    val appLanguages = listOf(
        Language("cs", "Čeština"), Language("sk", "Slovenština"), Language("en", "English"))
    val gameLanguages = listOf(
        Language("en", "English"), Language("de", "Deutsch"),
        Language("fr", "Français"), Language("es", "Español"),
        Language("it", "Italiano"), Language("pt", "Português"))
    val targetLanguages = listOf(
        Language("cs", "Čeština"), Language("sk", "Slovenština"),
        Language("en", "English"), Language("de", "Deutsch"),
        Language("pl", "Polski"), Language("fr", "Français"),
        Language("es", "Español"), Language("it", "Italiano"),
        Language("pt", "Português"))

    // App-owned static UI only; never translate OCR results, identifiers or user text.
    private val uiLabels = mapOf(
        "Profil hry změň před spuštěním překladu." to Pair("Profil hry zmeň pred spustením prekladu.", "Change the game profile before starting translation."),
        "Rozumím" to Pair("Rozumiem", "Got it"),
        "Název hry" to Pair("Názov hry", "Game name"),
        "Uložit profil" to Pair("Uložiť profil", "Save profile"),
        "Zadej název hry (max. 64 znaků)" to Pair("Zadaj názov hry (max. 64 znakov)", "Enter a game name (max. 64 characters)"),
        "Přepsat profil" to Pair("Prepísať profil", "Overwrite profile"),
        "Přepsat" to Pair("Prepísať", "Overwrite"),
        "Nepoužívat herní slovník" to Pair("Nepoužívať herný slovník", "Do not use a game glossary"),
        "Použít" to Pair("Použiť", "Apply"),
        "Smazat" to Pair("Zmazať", "Delete"),
        "Smazat profil" to Pair("Zmazať profil", "Delete profile"),
        "Zatím nemáš uložený žádný profil." to Pair("Zatiaľ nemáš uložený žiadny profil.", "No saved profiles yet."),
        "Zavřít" to Pair("Zavrieť", "Close"),
        "Vybrat profil" to Pair("Vybrať profil", "Choose profile"),
        "Aktuální nastavení" to Pair("Aktuálne nastavenia", "Current settings"),
        "Profil nelze načíst" to Pair("Profil nemožno načítať", "Could not load profile"),
        "Název hry nebo aplikace" to Pair("Názov hry alebo aplikácie", "Game or app name"),
        "Profil uložen" to Pair("Profil uložený", "Profile saved"),
        "Zadej název profilu." to Pair("Zadaj názov profilu.", "Enter a profile name."),
        "Uprav aktuální nastavení. Tlačítkem Uložit změny do profilu přepíšeš uložený profil." to Pair("Uprav aktuálne nastavenia. Tlačidlom Uložiť zmeny do profilu prepíšeš uložený profil.", "Adjust current settings. Save changes to profile overwrites the saved profile."),
        "Rozložení" to Pair("Rozloženie", "Layout"),
        "Uložit změny do profilu" to Pair("Uložiť zmeny do profilu", "Save changes to profile"),
        "Upravit profil" to Pair("Upraviť profil", "Edit profile"),
        "Uložit aktuální nastavení" to Pair("Uložiť aktuálne nastavenia", "Save current settings"),
        "Zatím žádné profily" to Pair("Zatiaľ žiadne profily", "No profiles yet"),
        "Aktivní profil" to Pair("Aktívny profil", "Active profile"),
        "Uložený profil" to Pair("Uložený profil", "Saved profile"),
        "Upravit rozložení" to Pair("Upraviť rozloženie", "Edit layout"),
        "Upravit další nastavení" to Pair("Upraviť ďalšie nastavenia", "Edit other settings"),
        "Profil načten" to Pair("Profil načítaný", "Profile loaded"),
        "Profily her a aplikací" to Pair("Profily hier a aplikácií", "Game and app profiles"),
        "Nejdřív zastav probíhající překlad." to Pair("Najprv zastav prebiehajúci preklad.", "Stop the current translation first."),
        "Jazyky slovníku" to Pair("Jazyky slovníka", "Glossary languages"),
        "Max. 50 řádků: originál | chybný překlad | správný překlad. Prázdný prostředek opraví jen celý přesný originál. Rozlišuje velikost písmen." to Pair("Max. 50 riadkov: originál | chybný preklad | správny preklad. Prázdny stred opraví iba celý presný originál. Rozlišuje veľkosť písmen.", "Max. 50 lines: original | incorrect translation | correct translation. An empty middle field corrects only the exact full original. Case-sensitive."),
        "Nový slovník pro aktuální jazyky" to Pair("Nový slovník pre aktuálne jazyky", "New glossary for current languages"),
        "Neplatný formát, duplicita originálu nebo překročený limit." to Pair("Neplatný formát, duplicita originálu alebo prekročený limit.", "Invalid format, duplicate original or limit exceeded."),
        "Bez slovníku" to Pair("Bez slovníka", "No glossary"),
        "Nový herní slovník" to Pair("Nový herný slovník", "New game glossary"),
        "aktivní" to Pair("aktívny", "active"),
        "Smazat slovník" to Pair("Zmazať slovník", "Delete glossary"),
        "Čtyři nezávislé oblasti pro rozpoznávání a překlad." to Pair("Štyri nezávislé oblasti na rozpoznávanie a preklad.", "Four independent regions for recognition and translation."),
        "Čtení dialogových titulků" to Pair("Čítanie dialógových titulkov", "Read dialogue subtitles"),
        "Zelená" to Pair("Zelená", "Green"),
        "Zobrazení přeložených titulků" to Pair("Zobrazenie preložených titulkov", "Display translated subtitles"),
        "Čtení herních textů a voleb" to Pair("Čítanie herných textov a volieb", "Read game text and choices"),
        "Oranžová" to Pair("Oranžová", "Orange"),
        "Překlad herních textů" to Pair("Preklad herných textov", "Translate game text"),
        "Otevřít editor oblastí" to Pair("Otvoriť editor oblastí", "Open region editor"),
        "Zavřít vzhled titulků" to Pair("Zavrieť vzhľad titulkov", "Close subtitle appearance"),
        "Vlastní vzhled titulků pro telefon a nastavení zelené oblasti na TV. Přesná poloha rámečků zůstává v Oblastech obrazovky." to Pair("Vlastný vzhľad titulkov pre telefón a nastavenia zelenej oblasti na TV. Presná poloha rámčekov zostáva v Oblastiach obrazovky.", "Customize phone subtitles and the green TV region. Edit exact frame positions in Screen regions."),
        "NÁHLED" to Pair("NÁHĽAD", "PREVIEW"),
        "TITULKY NA TELEFONU" to Pair("TITULKY NA TELEFÓNE", "PHONE SUBTITLES"),
        "Velikost písma" to Pair("Veľkosť písma", "Font size"),
        "Barva textu" to Pair("Farba textu", "Text color"),
        "Tyrkysová" to Pair("Tyrkysová", "Cyan"),
        "Fialová" to Pair("Fialová", "Purple"),
        "Bílá" to Pair("Biela", "White"),
        "Tmavé" to Pair("Tmavé", "Dark"),
        "Průhledné" to Pair("Priehľadné", "Translucent"),
        "Žádné" to Pair("Žiadne", "None"),
        "Pozadí" to Pair("Pozadie", "Background"),
        "TITULKY NA TV" to Pair("TITULKY NA TV", "TV SUBTITLES"),
        "Velikost písma TV" to Pair("Veľkosť písma TV", "TV font size"),
        "Šířka titulků" to Pair("Šírka titulkov", "Subtitle width"),
        "Zamknout polohu a vzhled" to Pair("Zamknúť polohu a vzhľad", "Lock position and appearance"),
        "Zamknout polohu a vzhled titulků" to Pair("Zamknúť polohu a vzhľad titulkov", "Lock subtitle position and appearance"),
        "Náhled na TV" to Pair("Náhľad na TV", "Preview on TV"),
        "Odeslat ukázkový překlad do vybrané TV" to Pair("Odoslať ukážkový preklad do vybranej TV", "Send a sample translation to the selected TV"),
        "Změny se ukládají pro aktuálně vybranou televizi nebo telefon. Pro přesné umístění rámečků otevři Oblasti obrazovky." to Pair("Zmeny sa ukladajú pre aktuálne vybraný televízor alebo telefón. Na presné umiestnenie rámčekov otvor Oblasti obrazovky.", "Changes are saved for the selected TV or phone. Open Screen regions to position frames precisely."),
        "Ukázka přeložených titulků" to Pair("Ukážka preložených titulkov", "Sample translated subtitles"),
        "Neprůhlednost pozadí" to Pair("Nepriehľadnosť pozadia", "Background opacity"),
        "Pozadí titulků" to Pair("Pozadie titulkov", "Subtitle background"),
        "Povol překryv v nastavení TV." to Pair("Povoľ prekrytie v nastaveniach TV.", "Allow overlays in TV settings."),
        "Velikost a poloha titulků" to Pair("Veľkosť a poloha titulkov", "Subtitle size and position"),
        "Překládat dialogy" to Pair("Prekladať dialógy", "Translate dialogue"),
        "Titulky pro rozhovory ve hrách." to Pair("Titulky pre rozhovory v hrách.", "Subtitles for in-game conversations."),
        "Zobrazit i předchozí repliku" to Pair("Zobraziť aj predchádzajúcu repliku", "Show previous line too"),
        "V běžném režimu bez překrývání originálu; nemění příběhovou frontu." to Pair("V bežnom režime bez prekrývania originálu; nemení príbehový rad.", "In normal mode without covering the original; does not change the story queue."),
        "Dvě předchozí repliky" to Pair("Dve predchádzajúce repliky", "Two previous lines"),
        "Pokud je zapnutý předchozí dialog, zobrazí až dva nedávné překlady místo jednoho." to Pair("Ak je zapnutý predchádzajúci dialóg, zobrazí až dva nedávne preklady namiesto jedného.", "If previous dialogue is enabled, show up to two recent translations instead of one."),
        "Skrýt po zmizení originálu" to Pair("Skryť po zmiznutí originálu", "Hide when source disappears"),
        "Volitelně ukončí zobrazení běžného dialogu po potvrzeném zmizení zdrojového textu." to Pair("Voliteľne ukončí zobrazenie bežného dialógu po potvrdenom zmiznutí zdrojového textu.", "Optionally hide normal dialogue after the source text is confirmed gone."),
        "Herní texty a volby" to Pair("Herné texty a voľby", "Game text and choices"),
        "Samostatná žlutá oblast OCR." to Pair("Samostatná žltá oblasť OCR.", "Separate yellow OCR region."),
        "Dopisy a zprávy" to Pair("Listy a správy", "Letters and messages"),
        "Překládat celý text místo seznamu voleb." to Pair("Prekladať celý text namiesto zoznamu volieb.", "Translate full text instead of a list of choices."),
        "Nápověda a úkoly" to Pair("Pomoc a úlohy", "Hints and objectives"),
        "Překládat herní nápovědu." to Pair("Prekladať hernú pomoc.", "Translate in-game hints."),
        "Překrývat originál" to Pair("Prekrývať originál", "Cover original"),
        "Překlad překryje původní titulky." to Pair("Preklad prekryje pôvodné titulky.", "Translation covers the original subtitles."),
        "Příběhový režim" to Pair("Príbehový režim", "Story mode"),
        "Delší přeložené dialogy." to Pair("Dlhšie preložené dialógy.", "Longer translated dialogue."),
        "Ruční OCR oblast" to Pair("Ručná OCR oblasť", "Manual OCR region"),
        "Vlastní modrý rámeček." to Pair("Vlastný modrý rámček.", "Custom blue frame."),
        "Doba zobrazení" to Pair("Doba zobrazenia", "Display duration"),
        "Pevná doba titulku" to Pair("Pevná doba titulku", "Fixed subtitle duration"),
        "Nastavení v sekundách jako na iOS; příběhová fronta zachovává vlastní časování." to Pair("Nastavenie v sekundách ako na iOS; príbehový rad zachováva vlastné časovanie.", "Set seconds as on iOS; the story queue keeps its own timing."),
        "Doba titulku" to Pair("Doba titulku", "Subtitle duration"),
        "Přeskočit starý překlad" to Pair("Preskočiť starý preklad", "Skip stale translation"),
        "Zahodí výsledek, který překročil maximální stáří." to Pair("Zahodí výsledok, ktorý prekročil maximálny vek.", "Discard results older than the maximum age."),
        "Maximální stáří" to Pair("Maximálny vek", "Maximum age"),
        "Prodleva po zmizení" to Pair("Oneskorenie po zmiznutí", "Delay after disappearance"),
        "Hned" to Pair("Hneď", "Immediately"),
        "Oblasti na obrazovce" to Pair("Oblasti na obrazovke", "Screen regions"),
        "Rychlost čtení, filtrování menu a skutečná oblast snímání. Během spuštěné relace nejprve zastav překlad." to Pair("Rýchlosť čítania, filtrovanie menu a skutočná oblasť snímania. Počas spustenej relácie najprv zastav preklad.", "Reading speed, menu filtering and capture region. Stop translation before changing an active session."),
        "Automatický · vyvážený" to Pair("Automatický · vyvážený", "Automatic · balanced"),
        "Krátké dialogy · rychlý" to Pair("Krátke dialógy · rýchly", "Short dialogue · fast"),
        "Původní · stabilní" to Pair("Pôvodný · stabilný", "Original · stable"),
        "Režim OCR" to Pair("Režim OCR", "OCR mode"),
        "Stejný jako modrá" to Pair("Rovnaký ako modrá", "Same as blue"),
        "Žlutá: režim OCR" to Pair("Žltá: režim OCR", "Yellow: OCR mode"),
        "Filtrovat menu hry" to Pair("Filtrovať menu hry", "Filter game menus"),
        "Modrá oblast: omezí velké nadpisy, tlačítka a krátké sloupce menu. Žluté oblasti se netýká." to Pair("Modrá oblasť: obmedzí veľké nadpisy, tlačidlá a krátke stĺpce menu. Žltej oblasti sa netýka.", "Blue region: reduces large headings, buttons and short menu columns. Does not affect yellow."),
        "Skrýt pokročilé filtry" to Pair("Skryť pokročilé filtre", "Hide advanced filters"),
        "Ignorované řádky, minimální výška a filtry obou oblastí." to Pair("Ignorované riadky, minimálna výška a filtre oboch oblastí.", "Ignored lines, minimum height and filters for both regions."),
        "Žlutá: filtrovat pokyny ovladače" to Pair("Žltá: filtrovať pokyny ovládača", "Yellow: filter controller prompts"),
        "Volitelně vynechá anglické pokyny jako Press A nebo Hold R2. Běžné volby ponechá." to Pair("Voliteľne vynechá anglické pokyny ako Press A alebo Hold R2. Bežné voľby ponechá.", "Optionally omit prompts like Press A or Hold R2. Keep ordinary choices."),
        "Žlutá" to Pair("Žltá", "Yellow"),
        "Modrá" to Pair("Modrá", "Blue"),
        "Ignorované řádky" to Pair("Ignorované riadky", "Ignored lines"),
        "Počet" to Pair("Počet", "Count"),
        "Jeden přesný výraz na řádek" to Pair("Jeden presný výraz na riadok", "One exact phrase per line"),
        "Modrá: minimální výška textu" to Pair("Modrá: minimálna výška textu", "Blue: minimum text height"),
        "Modrá: velikost textu" to Pair("Modrá: veľkosť textu", "Blue: text size"),
        "Žlutá: minimální výška textu" to Pair("Žltá: minimálna výška textu", "Yellow: minimum text height"),
        "Žlutá: velikost textu" to Pair("Žltá: veľkosť textu", "Yellow: text size"),
        "Automaticky" to Pair("Automaticky", "Automatic"),
        "Obnovit filtry modré oblasti" to Pair("Obnoviť filtre modrej oblasti", "Reset blue region filters"),
        "Bez změny rámečků a žluté oblasti." to Pair("Bez zmeny rámčekov a žltej oblasti.", "Keep frames and the yellow region unchanged."),
        "Obnovit filtry žluté oblasti" to Pair("Obnoviť filtre žltej oblasti", "Reset yellow region filters"),
        "Bez změny rámečků a modré oblasti." to Pair("Bez zmeny rámčekov a modrej oblasti.", "Keep frames and the blue region unchanged."),
        "Rozšířit hledání" to Pair("Rozšíriť hľadanie", "Expand search"),
        "Při automatickém režimu zahrne širší část obrazu." to Pair("Pri automatickom režime zahrnie širšiu časť obrazu.", "Include a wider part of the image in automatic mode."),
        "Ruční oblast OCR" to Pair("Ručná oblasť OCR", "Manual OCR region"),
        "Použít modrý rámeček místo automatického výběru." to Pair("Použiť modrý rámček namiesto automatického výberu.", "Use the blue frame instead of automatic selection."),
        "Výchozí oblast" to Pair("Predvolená oblasť", "Default region"),
        "Původní automatika" to Pair("Pôvodná automatika", "Original automatic detection"),
        "Úzká – pouze titulky" to Pair("Úzka – iba titulky", "Narrow – subtitles only"),
        "Střed – doporučeno" to Pair("Stred – odporúčané", "Center – recommended"),
        "Široká – původní" to Pair("Široká – pôvodná", "Wide – original"),
        "Upravit modrý, zelený, žlutý a oranžový rámeček." to Pair("Upraviť modrý, zelený, žltý a oranžový rámček.", "Edit the blue, green, yellow and orange frames."),
        "Výchozí nastavení OCR" to Pair("Predvolené nastavenia OCR", "Default OCR settings"),
        "Obnovit původní režim, filtrování a automatické čtení." to Pair("Obnoviť pôvodný režim, filtrovanie a automatické čítanie.", "Restore original mode, filtering and automatic reading."),
        "Zpět do Nastavení" to Pair("Späť do Nastavení", "Back to Settings"),
        "Pouze v paměti do ukončení aplikace · max. 200 položek. Vypnutí historii vymaže. Technický log a cache jsou oddělené." to Pair("Iba v pamäti do ukončenia aplikácie · max. 200 položiek. Vypnutie históriu vymaže. Technický log a vyrovnávacia pamäť sú oddelené.", "In memory until the app closes · max. 200 entries. Turning off clears history. Technical log and cache are separate."),
        "Uchovávat historii" to Pair("Uchovávať históriu", "Keep history"),
        "Zatím žádné překlady." to Pair("Zatiaľ žiadne preklady.", "No translations yet."),
        "Historie je vypnutá." to Pair("História je vypnutá.", "History is off."),
        "Vymazat celou historii?" to Pair("Vymazať celú históriu?", "Clear all history?"),
        "vybráno" to Pair("vybrané", "selected"),
        "Domů" to Pair("Domov", "Home"),
        "Obrazovka" to Pair("Obrazovka", "Screen"),
        "Konzole" to Pair("Konzoly", "Consoles"),
        "O aplikaci" to Pair("O aplikácii", "About"),
        "HRAJ. ROZUMĚJ." to Pair("HRAJ. ROZUMEJ.", "PLAY. UNDERSTAND."),
        "Překlad přímo při hraní" to Pair("Preklad priamo pri hraní", "Translation while playing"),
        "Titulky a překlady her v reálném čase" to Pair("Titulky a preklady hier v reálnom čase", "Real-time game subtitles and translation"),
        "Standardní" to Pair("Štandardný", "Standard"),
        "Automatický" to Pair("Automatický", "Automatic"),
        "Pokročilé nastavení OCR" to Pair("Pokročilé nastavenia OCR", "Advanced OCR settings"),
        "Režim překladu" to Pair("Režim prekladu", "Translation mode"),
        "Nejdřív zastav překlad." to Pair("Najprv zastav preklad.", "Stop translation first."),
        "Přidat zónu" to Pair("Pridať zónu", "Add zone"),
        "Obnovit výchozí" to Pair("Obnoviť predvolené", "Reset to default"),
        "Oblasti překladu" to Pair("Oblasti prekladu", "Translation regions"),
        "Vypnout" to Pair("Vypnúť", "Disable"),
        "Zapnout" to Pair("Zapnúť", "Enable"),
        "Upravit" to Pair("Upraviť", "Edit"),
        "Odstranit" to Pair("Odstrániť", "Remove"),
        "Zóna" to Pair("Zóna", "Zone"),
        "Dialogy" to Pair("Dialógy", "Dialogue"),
        "Obnovit výchozí zóny?" to Pair("Obnoviť predvolené zóny?", "Reset zones to default?"),
        "Obnovit" to Pair("Obnoviť", "Reset"),
        "Název" to Pair("Názov", "Name"),
        "Vlevo" to Pair("Vľavo", "Left"),
        "Nahoře" to Pair("Hore", "Top"),
        "Vpravo" to Pair("Vpravo", "Right"),
        "Dole" to Pair("Dole", "Bottom"),
        "Upravit zónu" to Pair("Upraviť zónu", "Edit zone"),
        "Uložit" to Pair("Uložiť", "Save"),
        "Časování, historie a diagnostika" to Pair("Časovanie, história a diagnostika", "Timing, history and diagnostics"),
        "Slovník, historie a diagnostika" to Pair("Slovník, história a diagnostika", "Glossary, history and diagnostics"),
        "Jazyk aplikace" to Pair("Jazyk aplikácie", "App language"),
        "Jazyk hry" to Pair("Jazyk hry", "Game language"),
        "Jazyk překladu" to Pair("Jazyk prekladu", "Translation language"),
        "Jazyky GameLingo" to Pair("Jazyky GameLingo", "GameLingo languages"),
        "Zrušit" to Pair("Zrušiť", "Cancel"),
        "Hotovo" to Pair("Hotovo", "Done"),
        "Jazyk nabídky GameLingo" to Pair("Jazyk ponuky GameLingo", "GameLingo interface language"),
        "Původní jazyk dialogů a herního textu" to Pair("Pôvodný jazyk dialógov a herného textu", "Original language of dialogue and game text"),
        "Do jakého jazyka překládat" to Pair("Do akého jazyka prekladať", "Language to translate into"),
        "Vítej v GameLingo" to Pair("Vitaj v GameLingo", "Welcome to GameLingo"),
        "Jazyky a překlad" to Pair("Jazyky a preklad", "Languages and translation"),
        "Vyber zařízení" to Pair("Vyber zariadenie", "Choose devices"),
        "Překládej dialogy a herní texty přímo během hraní." to Pair("Prekladaj dialógy a herné texty priamo počas hrania.", "Translate dialogue and game text while playing."),
        "Překládat do" to Pair("Prekladať do", "Translate into"),
        "Tyto jazyky platí pro dialogy i herní texty. Změnit je můžeš později v Nastavení." to Pair("Tieto jazyky platia pre dialógy aj herné texty. Zmeniť ich môžeš neskôr v Nastaveniach.", "These languages apply to dialogue and game text. You can change them later in Settings."),
        "Vybraná konzole je uložená. Spojení ověříme při spuštění." to Pair("Vybraná konzola je uložená. Spojenie overíme pri spustení.", "Console saved. Connection will be checked when you start."),
        "Po výběru můžeš konzoli připojit v posledním kroku." to Pair("Po výbere môžeš konzolu pripojiť v poslednom kroku.", "Choose a console, then connect it below."),
        "Na telefonu" to Pair("Na telefóne", "On your phone"),
        "Spárovat TV" to Pair("Spárovať TV", "Pair TV"),
        "Připojit konzoli" to Pair("Pripojiť konzolu", "Connect console"),
        "Uložené zařízení neznamená aktivní Remote Play. Živé spojení a překlad se ověří až při spuštění." to Pair("Uložené zariadenie neznamená aktívne Remote Play. Živé spojenie a preklad sa overia až pri spustení.", "A saved device does not mean Remote Play is active. Connection and translation are checked when you start."),
        "Nastavit později" to Pair("Nastaviť neskôr", "Set up later"),
        "Zpět" to Pair("Späť", "Back"),
        "Pokračovat" to Pair("Pokračovať", "Continue"),
        "Dokončit nastavení" to Pair("Dokončiť nastavenie", "Finish setup"),
        "Vybraná televize nepodporuje šestimístné párování GameLingo TV. Pro tento výstup vyber Android TV nebo telefon." to Pair("Vybraný televízor nepodporuje šesťmiestne párovanie GameLingo TV. Pre tento výstup vyber Android TV alebo telefón.", "This TV does not support six-digit GameLingo TV pairing. Choose Android TV or your phone."),
        "Nastavení" to Pair("Nastavenia", "Settings"),
        "Zavřít nastavení" to Pair("Zavrieť nastavenia", "Close settings"),
        "JAZYKY" to Pair("JAZYKY", "LANGUAGES"),
        "ROZPOZNÁVÁNÍ A PŘEKLAD" to Pair("ROZPOZNÁVANIE A PREKLAD", "RECOGNITION AND TRANSLATION"),
        "ZOBRAZENÍ A PROFILY" to Pair("ZOBRAZENIE A PROFILY", "DISPLAY AND PROFILES"),
        "OBECNÉ" to Pair("VŠEOBECNÉ", "GENERAL"),
        "Jazyky" to Pair("Jazyky", "Languages"),
        "OCR a rozpoznávání" to Pair("OCR a rozpoznávanie", "OCR and recognition"),
        "Překlad" to Pair("Preklad", "Translation"),
        "Režimy, dialogy a časování" to Pair("Režimy, dialógy a časovanie", "Modes, dialogue and timing"),
        "Oblasti obrazovky" to Pair("Oblasti obrazovky", "Screen regions"),
        "Automatické zóny" to Pair("Automatické zóny", "Automatic zones"),
        "Čtyři nezávislé oblasti" to Pair("Štyri nezávislé oblasti", "Four independent regions"),
        "Standardní oblasti" to Pair("Štandardné oblasti", "Standard regions"),
        "Vzhled titulků" to Pair("Vzhľad titulkov", "Subtitle appearance"),
        "Velikost, šířka a poloha titulků" to Pair("Veľkosť, šírka a poloha titulkov", "Subtitle size, width and position"),
        "Profily her" to Pair("Profily hier", "Game profiles"),
        "Vlastní nastavení pro každou hru" to Pair("Vlastné nastavenia pre každú hru", "Custom settings for each game"),
        "Zařízení" to Pair("Zariadenia", "Devices"),
        "Historie překladů" to Pair("História prekladov", "Translation history"),
        "Poslední dialogy a herní texty · volitelné" to Pair("Posledné dialógy a herné texty · voliteľné", "Recent dialogue and game text · optional"),
        "Překladová cache" to Pair("Prekladová vyrovnávacia pamäť", "Translation cache"),
        "Ukládání a vymazání místních překladů" to Pair("Ukladanie a vymazanie miestnych prekladov", "Store and clear local translations"),
        "Diagnostika" to Pair("Diagnostika", "Diagnostics"),
        "Stav relace a uložených zařízení" to Pair("Stav relácie a uložených zariadení", "Session and saved device status"),
        "O aplikaci / Licence" to Pair("O aplikácii / Licencie", "About / Licenses"),
        "GameLingo, zdroje a AGPLv3" to Pair("GameLingo, zdroje a AGPLv3", "GameLingo, sources and AGPLv3"),
        "Pokročilé nastavení" to Pair("Pokročilé nastavenia", "Advanced settings"),
        "Citlivost, režim a časování" to Pair("Citlivosť, režim a časovanie", "Sensitivity, mode and timing"),
        "Nastavení GameLingo TV" to Pair("Nastavenia GameLingo TV", "GameLingo TV settings"),
        "Aplikace, hra, překlad" to Pair("Aplikácia, hra, preklad", "App, game, translation"),
        "Dialogy, herní texty, Story" to Pair("Dialógy, herné texty, Story", "Dialogue, game text, Story"),
        "Čtyři oblasti" to Pair("Štyri oblasti", "Four regions"),
        "Barvy, pozadí a velikost" to Pair("Farby, pozadie a veľkosť", "Colors, background and size"),
        "Hry, video a aplikace TV" to Pair("Hry, video a aplikácie TV", "Games, video and TV apps"),
        "Konzole a telefony" to Pair("Konzoly a telefóny", "Consoles and phones"),
        "Herní slovník" to Pair("Herný slovník", "Game glossary"),
        "Vlastní výrazy" to Pair("Vlastné výrazy", "Custom terms"),
        "Překlady na TV" to Pair("Preklady na TV", "Translations on TV"),
        "Ukládání a vymazání" to Pair("Ukladanie a vymazanie", "Storage and clearing"),
        "Příjem, oprávnění, verze" to Pair("Príjem, oprávnenia, verzia", "Receiver, permissions, version"),
        "Verze a licence" to Pair("Verzia a licencie", "Version and licenses"),
        "Nejdřív zastav překlad a příjem." to Pair("Najprv zastav preklad a príjem.", "Stop translation and receiving first."),
        "Povolit" to Pair("Povoliť", "Allow"),
        "Vymazat" to Pair("Vymazať", "Clear")
    )
    fun ui(c: Context, cs: String): String = when (app(c)) {
        "sk" -> uiLabels[cs]?.first ?: cs
        "en" -> uiLabels[cs]?.second ?: cs
        else -> cs
    }

    private const val PREFS = "gamelingo_languages"
    private val changes = java.util.concurrent.atomic.AtomicLong()
    val revision: Long get() = changes.get()

    private fun setTranslationLanguage(c: Context, key: String, code: String) {
        val old = if (key == "game") game(c) else target(c)
        if (old == code) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, code).apply()
        changes.incrementAndGet()
    }
    private fun systemLanguage(c: Context): String = c.resources.configuration.locales[0].language
    fun systemApp(c: Context): String = systemLanguage(c).takeIf { code -> appLanguages.any { it.code == code } } ?: "en"
    private fun systemTarget(c: Context): String = systemLanguage(c).takeIf { code -> targetLanguages.any { it.code == code } } ?: "en"
    fun app(c: Context) = Nastaveni(c).jazyk?.takeIf { code -> appLanguages.any { it.code == code } } ?: systemApp(c)
    fun game(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("game", "en")?.takeIf { code ->
                gameLanguages.any { it.code == code }
            } ?: "en"
    fun target(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("target", null)?.takeIf { code ->
                targetLanguages.any { it.code == code }
            } ?: systemTarget(c)
    /** Restore only validated, existing translation languages; never change UI language. */
    fun restoreProfile(c: Context, source: String?, target: String?) {
        if (source != null && gameLanguages.any { it.code == source }) setTranslationLanguage(c, "game", source)
        if (target != null && targetLanguages.any { it.code == target }) setTranslationLanguage(c, "target", target)
    }
    fun name(choices: List<Language>, code: String) =
        choices.firstOrNull { it.code == code }?.title ?: code

    enum class Kind { APP, GAME, TARGET }

    /** Same one-field selection behavior as the iOS SwiftUI pickers. */
    fun choose(activity: Activity, kind: Kind, onChanged: () -> Unit = {}) {
        val choices = when (kind) {
            Kind.APP -> appLanguages
            Kind.GAME -> gameLanguages
            Kind.TARGET -> targetLanguages
        }
        val title = when (kind) {
            Kind.APP -> ui(activity, "Jazyk aplikace")
            Kind.GAME -> ui(activity, "Jazyk hry")
            Kind.TARGET -> ui(activity, "Jazyk překladu")
        }
        val current = when (kind) {
            Kind.APP -> app(activity)
            Kind.GAME -> game(activity)
            Kind.TARGET -> target(activity)
        }
        val index = choices.indexOfFirst { it.code == current }.coerceAtLeast(0)
        fun applyChoice(choice: Int) {
            val selected = choices[choice].code
            when (kind) {
                Kind.APP -> Nastaveni(activity).jazyk = selected
                Kind.GAME -> setTranslationLanguage(activity, "game", selected)
                Kind.TARGET -> setTranslationLanguage(activity, "target", selected)
            }
            onChanged()
        }
        if (GameLingoTVUi.isTV(activity)) {
            GameLingoTVUi.select(activity, title, choices.map { it.title }.toTypedArray(), index, ::applyChoice)
        } else AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(choices.map { it.title }.toTypedArray(), index) { picker, choice ->
                picker.dismiss(); applyChoice(choice)
            }.setNegativeButton(ui(activity, "Zrušit"), null).show()
    }

    fun show(activity: Activity, onChanged: () -> Unit = {}) {
        if (activity.isFinishing) return
        if (GameLingoTVUi.isTV(activity)) {
            val dialog = android.app.Dialog(activity)
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(GameLingoTVUi.dp(activity,20),GameLingoTVUi.dp(activity,20),
                    GameLingoTVUi.dp(activity,20),GameLingoTVUi.dp(activity,20))
                background = android.graphics.drawable.ColorDrawable(Color.parseColor("#080E1B"))
            }
            body.addView(GameLingoTVUi.title(activity,ui(activity, "Jazyky GameLingo"),28f))
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (kind in Kind.values()) {
                val label = when (kind) {
                    Kind.APP -> ui(activity, "Jazyk aplikace")
                    Kind.GAME -> ui(activity, "Jazyk hry")
                    Kind.TARGET -> ui(activity, "Jazyk překladu")
                }
                val value = when (kind) {
                    Kind.APP -> name(appLanguages,app(activity))
                    Kind.GAME -> name(gameLanguages,game(activity))
                    Kind.TARGET -> name(targetLanguages,target(activity))
                }
                row.addView(GameLingoTVUi.tile(activity,"◎",label,value) {
                    choose(activity,kind) { dialog.dismiss(); onChanged(); show(activity,onChanged) }
                }, LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
                    .apply { rightMargin = GameLingoTVUi.dp(activity,10) })
            }
            body.addView(row)
            body.addView(GameLingoTVUi.action(activity,ui(activity, "Hotovo")) { dialog.dismiss() })
            dialog.setContentView(body); dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .75f).toInt(),ViewGroup.LayoutParams.WRAP_CONTENT)
            row.getChildAt(0).requestFocus()
            return
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = GameLingoVisualKit.dp(activity, 21)
            setPadding(pad, pad, pad, pad)
        }
        val parent = AlertDialog.Builder(activity)
            .setTitle(ui(activity, "Jazyky GameLingo"))
            .setView(container)
            .setPositiveButton(ui(activity, "Hotovo"), null)
            .create()

        fun addPicker(title: String, caption: String, languages: List<Language>,
                      selected: () -> String, onSelect: (String) -> Unit) {
            val heading = TextView(activity).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
            }
            container.addView(heading)
            container.addView(TextView(activity).apply {
                text = caption
                gravity = Gravity.CENTER
                textSize = 12.5f
                setTextColor(Color.parseColor("#9FB1D4"))
                setPadding(0, 2, 0, 8)
            })
            val current = TextView(activity).apply {
                text = name(languages, selected()) + "    ▾"
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minHeight = GameLingoVisualKit.dp(activity, 55)
                background = GameLingoVisualKit.card(activity)
                val pad = GameLingoVisualKit.dp(activity, 13)
                setPadding(pad, 0, pad, 0)
                isClickable = true
                isFocusable = true
            }
            current.setOnClickListener {
                val selectedIndex = languages.indexOfFirst {
                    it.code == selected()
                }.coerceAtLeast(0)
                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setSingleChoiceItems(languages.map { it.title }.toTypedArray(),
                        selectedIndex) { picker, index ->
                        onSelect(languages[index].code)
                        current.text = languages[index].title + "    ▾"
                        picker.dismiss()
                        onChanged()
                        if (languages === appLanguages && !activity.isFinishing && !activity.isDestroyed) {
                            parent.dismiss()
                            show(activity, onChanged)
                        }
                    }.setNegativeButton(ui(activity, "Zrušit"), null).show()
            }
            container.addView(current,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = GameLingoVisualKit.dp(activity, 20)
                })
        }

        addPicker(ui(activity, "Jazyk aplikace"), ui(activity, "Jazyk nabídky GameLingo"),
            appLanguages, { app(activity) }) { code ->
            Nastaveni(activity).jazyk = code
        }
        addPicker(ui(activity, "Jazyk hry"), ui(activity, "Původní jazyk dialogů a herního textu"),
            gameLanguages, { game(activity) }) { code ->
            setTranslationLanguage(activity, "game", code)
        }
        addPicker(ui(activity, "Jazyk překladu"), ui(activity, "Do jakého jazyka překládat"),
            targetLanguages, { target(activity) }) { code ->
            setTranslationLanguage(activity, "target", code)
        }
        parent.show()
        parent.window?.setBackgroundDrawableResource(android.R.color.background_dark)
        parent.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
