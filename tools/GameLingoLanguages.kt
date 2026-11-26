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
        "Vyber způsob překladu nebo spravuj svá zařízení." to Pair("Vyber spôsob prekladu alebo spravuj svoje zariadenia.", "Choose a translation mode or manage your devices."),
        "ZPŮSOB PŘEKLADU" to Pair("SPÔSOB PREKLADU", "TRANSLATION MODE"),
        "Překlad z telefonu" to Pair("Preklad z telefónu", "Translation from phone"),
        "Příjem běží · otevřít párování a stav" to Pair("Príjem beží · otvoriť párovanie a stav", "Receiving · open pairing and status"),
        "Spustit příjem a párování telefonu." to Pair("Spustiť príjem a párovanie telefónu.", "Start receiving and pair a phone."),
        "Přímo na TV" to Pair("Priamo na TV", "Directly on TV"),
        "Xbox, PlayStation nebo aplikace přímo na televizoru." to Pair("Xbox, PlayStation alebo aplikácie priamo na televízore.", "Xbox, PlayStation or apps directly on the TV."),
        "Uložená konzole" to Pair("Uložená konzola", "Saved console"),
        "MOJE KONZOLE" to Pair("MOJE KONZOLY", "MY CONSOLES"),
        "Zatím nemáš přidanou žádnou konzoli." to Pair("Zatiaľ nemáš pridanú žiadnu konzolu.", "No console added yet."),
        "Remote Play běží" to Pair("Remote Play beží", "Remote Play running"),
        "Uložená · nepřipojena" to Pair("Uložená · nepripojená", "Saved · disconnected"),
        "Nepřidána" to Pair("Nepridaná", "Not added"),
        "Překryv titulků" to Pair("Prekrytie titulkov", "Subtitle overlay"),
        "Vyžaduje povolení" to Pair("Vyžaduje povolenie", "Permission required"),
        "Ovládání: šipky pro výběr • OK potvrdit • Zpět pro návrat" to Pair("Ovládanie: šípky na výber • OK potvrdiť • Späť na návrat", "Controls: arrows to select • OK to confirm • Back to return"),
        "Připojit Xbox" to Pair("Pripojiť Xbox", "Connect Xbox"),
        "Získej titulky z iPhonu nebo Androidu." to Pair("Získaj titulky z iPhonu alebo Androidu.", "Receive subtitles from iPhone or Android."),
        "Otevři GameLingo na telefonu" to Pair("Otvor GameLingo na telefóne", "Open GameLingo on your phone"),
        "Spáruj TV šestimístným kódem" to Pair("Spáruj TV šesťmiestnym kódom", "Pair the TV using the six-digit code"),
        "Spusť překlad a vrať se ke hře" to Pair("Spusť preklad a vráť sa k hre", "Start translation and return to the game"),
        "Titulky se zobrazí nad hrou, bez přepínání vstupu." to Pair("Titulky sa zobrazia nad hrou, bez prepínania vstupu.", "Subtitles appear over the game without switching inputs."),
        "Připojte svůj telefon" to Pair("Pripoj svoj telefón", "Connect your phone"),
        "Zadejte kód do telefonu" to Pair("Zadaj kód do telefónu", "Enter the code on your phone"),
        "Otevřete GameLingo na telefonu a zadejte tento šestimístný kód." to Pair("Otvor GameLingo na telefóne a zadaj tento šesťmiestny kód.", "Open GameLingo on your phone and enter this six-digit code."),
        "Párovací QR kód" to Pair("Párovací QR kód", "Pairing QR code"),
        "Zobrazit QR kód" to Pair("Zobraziť QR kód", "Show QR code"),
        "Vygeneruj nový PIN a ověř připojení k místní síti." to Pair("Vygeneruj nový PIN a over pripojenie k miestnej sieti.", "Generate a new PIN and check the local network connection."),
        "Nový párovací kód" to Pair("Nový párovací kód", "New pairing code"),
        "Spárované telefony" to Pair("Spárované telefóny", "Paired phones"),
        "Zatím žádný telefon." to Pair("Zatiaľ žiadny telefón.", "No phones yet."),
        "Síť" to Pair("Sieť", "Network"),
        "Zjišťuji" to Pair("Zisťujem", "Checking"),
        "Přijímač" to Pair("Prijímač", "Receiver"),
        "Čeká na telefon" to Pair("Čaká na telefón", "Waiting for phone"),
        "Titulky" to Pair("Titulky", "Subtitles"),
        "Vyzkoušet titulky" to Pair("Vyskúšať titulky", "Test subtitles"),
        "GameLingo · Ukázka titulků" to Pair("GameLingo · Ukážka titulkov", "GameLingo · Subtitle sample"),
        "Zpět do nabídky" to Pair("Späť do ponuky", "Back to menu"),
        "Zastavit příjem" to Pair("Zastaviť príjem", "Stop receiving"),
        "Podrobnosti" to Pair("Podrobnosti", "Details"),
        "Povolit titulky přes obraz hry" to Pair("Povoliť titulky cez obraz hry", "Allow subtitles over the game"),
        "Nepřipojeno" to Pair("Nepripojené", "Not connected"),
        "Port UDP obsazen" to Pair("Port UDP obsadený", "UDP port in use"),
        "Aktivní · UDP" to Pair("Aktívny · UDP", "Active · UDP"),
        "Zastaven" to Pair("Zastavený", "Stopped"),
        "Povol překryv" to Pair("Povoľ prekrytie", "Allow overlay"),
        "Telefon připojen" to Pair("Telefón pripojený", "Phone connected"),
        "Nelze přijímat: všechny UDP porty 41989–41993 jsou obsazené" to Pair("Nemožno prijímať: všetky UDP porty 41989–41993 sú obsadené", "Cannot receive: all UDP ports 41989–41993 are in use"),
        "Kód platí ještě:" to Pair("Kód platí ešte:", "Code expires in:"),
        "Spárováno:" to Pair("Spárované:", "Paired:"),
        "Připojeno:" to Pair("Pripojené:", "Connected:"),
        "Čekám na spojení" to Pair("Čakám na spojenie", "Waiting for connection"),
        "Zvol Nový párovací kód" to Pair("Vyber Nový párovací kód", "Choose New pairing code"),
        "IP televizoru:" to Pair("IP televízora:", "TV IP:"),
        "Adresa se zjišťuje" to Pair("Adresa sa zisťuje", "Detecting address"),
        "Vyber zdroj překladu" to Pair("Vyber zdroj prekladu", "Choose translation source"),
        "Nejdříve zastav překlad z konzole nebo telefonu." to Pair("Najprv zastav preklad z konzoly alebo telefónu.", "Stop translation from the console or phone first."),
        "Nejdříve zastav stávající překlad." to Pair("Najprv zastav aktuálny preklad.", "Stop the current translation first."),
        "Vyber PlayStation" to Pair("Vyber PlayStation", "Select PlayStation"),
        "Přidat PlayStation" to Pair("Pridať PlayStation", "Add PlayStation"),
        "Konzoli se nepodařilo načíst." to Pair("Konzolu sa nepodarilo načítať.", "Could not load console."),
        "Hledám konzole…" to Pair("Hľadám konzoly…", "Searching for consoles…"),
        "Seznam Xboxů se nepodařilo načíst." to Pair("Zoznam Xboxov sa nepodarilo načítať.", "Could not load Xbox list."),
        "Vyber Xbox" to Pair("Vyber Xbox", "Select Xbox"),
        "Nejprve vyber a připoj konzoli." to Pair("Najprv vyber a pripoj konzolu.", "Select and connect a console first."),
        "Kterou konzoli používáš?" to Pair("Ktorú konzolu používaš?", "Which console do you use?"),
        "Moje zařízení" to Pair("Moje zariadenia", "My devices"),
        "Vybrat konzoli" to Pair("Vybrať konzolu", "Select console"),
        "Snímání aktivní" to Pair("Snímanie aktívne", "Capture active"),
        "Nastaveno · spojení ověřím při spuštění" to Pair("Nastavené · spojenie overím pri spustení", "Configured · connection checked at start"),
        "Přidat Xbox nebo PlayStation" to Pair("Pridať Xbox alebo PlayStation", "Add Xbox or PlayStation"),
        "Titulky na telefonu" to Pair("Titulky na telefóne", "Subtitles on phone"),
        "Vyber nebo spáruj televizi" to Pair("Vyber alebo spáruj televízor", "Select or pair a TV"),
        "Televize uložena · spojení se ověří" to Pair("Televízor uložený · spojenie sa overí", "TV saved · connection will be checked"),
        "Zastavit překlad" to Pair("Zastaviť preklad", "Stop translation"),
        "Spustit překlad" to Pair("Spustiť preklad", "Start translation"),
        "Nastavení překladu" to Pair("Nastavenia prekladu", "Translation settings"),
        "Remote Play běží · TV spojení neověřeno" to Pair("Remote Play beží · spojenie TV neoverené", "Remote Play running · TV connection unverified"),
        "Připojuji / načítám snímky" to Pair("Pripájam / načítavam snímky", "Connecting / loading frames"),
        "Nejprve připoj konzoli" to Pair("Najprv pripoj konzolu", "Connect a console first"),
        "Konzole je nastavená · připojení neověřeno" to Pair("Konzola je nastavená · pripojenie neoverené", "Console configured · connection unverified"),
        "Video snímky se přijímají, titulky zpracovává OCR." to Pair("Videosnímky sa prijímajú, titulky spracováva OCR.", "Video frames are arriving; OCR processes subtitles."),
        "Uložený televizor nepotvrzuje živé přijetí titulků." to Pair("Uložený televízor nepotvrdzuje živý príjem titulkov.", "A saved TV does not confirm live subtitle reception."),
        "Uložené zařízení samo o sobě nepotvrzuje živé spojení." to Pair("Uložené zariadenie samo osebe nepotvrdzuje živé spojenie.", "A saved device alone does not confirm a live connection."),
        "Hraj. Rozuměj." to Pair("Hraj. Rozumej.", "Play. Understand."),
        "Účet PlayStation" to Pair("Účet PlayStation", "PlayStation account"),
        "Najít PlayStation" to Pair("Nájsť PlayStation", "Find PlayStation"),
        "Párovací PIN" to Pair("Párovací PIN", "Pairing PIN"),
        "Účet Microsoft" to Pair("Účet Microsoft", "Microsoft account"),
        "Najít Xbox" to Pair("Nájsť Xbox", "Find Xbox"),
        "Připojení Xbox" to Pair("Pripojenie Xbox", "Xbox connection"),
        "Verze:" to Pair("Verzia:", "Version:"),
        "Samostatná TV:" to Pair("Samostatný TV:", "Standalone TV:"),
        "OCR obrazovky: pouze MediaProjection" to Pair("OCR obrazovky: iba MediaProjection", "Screen OCR: MediaProjection only"),
        "Překryv:" to Pair("Prekrytie:", "Overlay:"),
        "Povolen" to Pair("Povolené", "Allowed"),
        "Nepovolen" to Pair("Nepovolené", "Not allowed"),
        "Zatím žádný překlad ve volitelné historii. Pro živý náhled použij Test OCR." to Pair("Zatiaľ žiadny preklad vo voliteľnej histórii. Na živý náhľad použi Test OCR.", "No translations in the optional history yet. Use Test OCR for a live preview."),
        "Povolit překryv" to Pair("Povoliť prekrytie", "Allow overlay"),
        "Další nastavení" to Pair("Ďalšie nastavenia", "More settings"),
        "Diagnostika a kompatibilita" to Pair("Diagnostika a kompatibilita", "Diagnostics and compatibility"),
        "Legacy příjem:" to Pair("Legacy príjem:", "Legacy reception:"),
        "Vymazat místní diagnostický log" to Pair("Vymazať miestny diagnostický záznam", "Clear local diagnostic log"),
        "Test OCR" to Pair("Test OCR", "Test OCR"),
        "Diagnostika TV" to Pair("Diagnostika TV", "TV diagnostics"),
        "Samostatný překlad TV" to Pair("Samostatný preklad TV", "Standalone TV translation"),
        "GameLingo · samostatná TV" to Pair("GameLingo · samostatný TV", "GameLingo · standalone TV"),
        "Pozastaveno" to Pair("Pozastavené", "Paused"),
        "Pozastavit" to Pair("Pozastaviť", "Pause"),
        "Čekám na systémový souhlas" to Pair("Čakám na systémový súhlas", "Waiting for system consent"),
        "Snímání nebylo povoleno" to Pair("Snímanie nebolo povolené", "Capture was not allowed"),
        "Souhlas udělen" to Pair("Súhlas udelený", "Consent granted"),
        "Čekám na první snímek" to Pair("Čakám na prvú snímku", "Waiting for first frame"),
        "Čekám na podporovaný zdroj" to Pair("Čakám na podporovaný zdroj", "Waiting for a supported source"),
        "Čekám na otevření videa nebo vstupu TV" to Pair("Čakám na otvorenie videa alebo vstupu TV", "Waiting for video or TV input"),
        "Aktivuji službu snímání" to Pair("Aktivujem službu snímania", "Activating capture service"),
        "Zahajuji MediaProjection" to Pair("Spúšťam MediaProjection", "Starting MediaProjection"),
        "Systém ukončil snímání" to Pair("Systém ukončil snímanie", "System stopped capture"),
        "Oblast obrazu je tmavá nebo nedostupná" to Pair("Oblasť obrazu je tmavá alebo nedostupná", "Image region is dark or unavailable"),
        "OCR titulků" to Pair("OCR titulkov", "Subtitle OCR"),
        "OCR herních textů" to Pair("OCR herných textov", "Game text OCR"),
        "Zastaveno" to Pair("Zastavené", "Stopped"),
        "Zastaveno · poslední stav:" to Pair("Zastavené · posledný stav:", "Stopped · last status:"),
        "Dialog snímání:" to Pair("Dialóg snímania:", "Capture consent dialog:"),
        "Start služby:" to Pair("Spustenie služby:", "Service startup:"),
        "Aktivace foreground služby:" to Pair("Aktivácia služby v popredí:", "Foreground service activation:"),
        "Start snímání:" to Pair("Spustenie snímania:", "Capture startup:"),
        "Chyba OCR:" to Pair("Chyba OCR:", "OCR error:"),
        "Stav:" to Pair("Stav:", "Status:"),
        "Foreground služba:" to Pair("Služba v popredí:", "Foreground service:"),
        "Souhlas / projekce:" to Pair("Súhlas / projekcia:", "Consent / projection:"),
        "Snímání:" to Pair("Snímanie:", "Capture:"),
        "zdroj:" to Pair("zdroj:", "source:"),
        "Přijaté snímky:" to Pair("Prijaté snímky:", "Received frames:"),
        "výřezy:" to Pair("výrezy:", "crops:"),
        "Chyba snímku:" to Pair("Chyba snímky:", "Frame error:"),
        "Hlídač:" to Pair("Strážca:", "Watchdog:"),
        "Překladač:" to Pair("Prekladač:", "Translator:"),
        "překlad:" to Pair("preklad:", "translation:"),
        "Rozložení:" to Pair("Rozloženie:", "Layout:"),
        "hlášená chyba" to Pair("hlásená chyba", "error reported"),
        "bez hlášené chyby" to Pair("bez hlásenej chyby", "no reported error"),
        "Překlad:" to Pair("Preklad:", "Translation:"),
        "Samostatná TV – průměr/P95 (předání není potvrzené zobrazení):" to Pair("Samostatný TV – priemer/P95 (odovzdanie nie je potvrdené zobrazenie):", "Standalone TV – average/P95 (handoff does not confirm display):"),
        "Aktivní · mediaProjection" to Pair("Aktívna · mediaProjection", "Active · mediaProjection"),
        "Zahajuji" to Pair("Spúšťam", "Starting"),
        "Povoleno" to Pair("Povolené", "Allowed"),
        "Vytvořen" to Pair("Vytvorený", "Created"),
        "Bez textu" to Pair("Bez textu", "No text"),
        "Text rozpoznán" to Pair("Text rozpoznaný", "Text recognized"),
        "Chyba OCR" to Pair("Chyba OCR", "OCR error"),
        "Překládám" to Pair("Prekladám", "Translating"),
        "Přeloženo" to Pair("Preložené", "Translated"),
        "Starý výsledek zahozen" to Pair("Starý výsledok zahodený", "Stale result discarded"),
        "Chyba překladu" to Pair("Chyba prekladu", "Translation error"),
        "Uvolněn" to Pair("Uvoľnený", "Released"),
        "Chyba při uvolnění" to Pair("Chyba pri uvoľnení", "Release error"),
        "Ukončena" to Pair("Ukončená", "Stopped"),
        "Chyba při ukončení" to Pair("Chyba pri ukončení", "Stop error"),
        "Nezahájena" to Pair("Nezačatá", "Not started"),
        "Nezahájeno" to Pair("Nezačaté", "Not started"),
        "Nevytvořen" to Pair("Nevytvorený", "Not created"),
        "Čeká" to Pair("Čaká", "Waiting"),
        "Povolit nepodepsané titulky ze starších aplikací pouze v důvěryhodné domácí síti?" to Pair("Povoliť nepodpísané titulky zo starších aplikácií iba v dôveryhodnej domácej sieti?", "Allow unsigned subtitles from older apps only on a trusted home network?"),
        "Vymazat místní diagnostické logy?" to Pair("Vymazať miestne diagnostické záznamy?", "Clear local diagnostic logs?"),
        "Zastav ostatní režimy a povol překryv." to Pair("Zastav ostatné režimy a povoľ prekrytie.", "Stop other modes and allow overlays."),
        "Snímání nelze spustit." to Pair("Snímanie nemožno spustiť.", "Could not start capture."),
        "Překlad se nepodařilo spustit." to Pair("Preklad sa nepodarilo spustiť.", "Could not start translation."),
        "Samostatná TV" to Pair("Samostatný TV", "Standalone TV"),
        "Překlad je spuštěný." to Pair("Preklad je spustený.", "Translation is running."),
        "Zastavit" to Pair("Zastaviť", "Stop"),
        "Pozastavit / pokračovat" to Pair("Pozastaviť / pokračovať", "Pause / resume"),
        "Spustit překlad TV · vybrat profil" to Pair("Spustiť preklad TV · vybrať profil", "Start TV translation · choose profile"),
        "Diagnostický výřez OCR" to Pair("Diagnostický výrez OCR", "Diagnostic OCR crop"),
        "Vypnout test překryvu" to Pair("Vypnúť test prekrytia", "Stop overlay test"),
        "Test překryvu" to Pair("Test prekrytia", "Test overlay"),
        "Nejdříve zastav ostatní režimy." to Pair("Najprv zastav ostatné režimy.", "Stop other modes first."),
        "Test překladového jádra" to Pair("Test prekladového jadra", "Test translation engine"),
        "Test překladu" to Pair("Test prekladu", "Translation test"),
        "Vypršel čas testu." to Pair("Vypršal čas testu.", "Test timed out."),
        "Překlad selhal." to Pair("Preklad zlyhal.", "Translation failed."),
        "Chyba:" to Pair("Chyba:", "Error:"),
        "Test snímání" to Pair("Test snímania", "Test capture"),
        "Po souhlasu otevři video do 10 sekund. Výřez a poslední rozpoznaný text zůstanou pouze v paměti do vymazání nebo nové relace. Po návratu sem se snímání zastaví." to Pair("Po udelení súhlasu otvor video do 10 sekúnd. Výrez a posledný rozpoznaný text zostanú iba v pamäti do vymazania alebo novej relácie. Po návrate sem sa snímanie zastaví.", "After granting consent, open a video within 10 seconds. The crop and last recognized text remain in memory only until cleared or a new session starts. Capture stops when you return here."),
        "Spustit test" to Pair("Spustiť test", "Start test"),
        "Vymazat náhled" to Pair("Vymazať náhľad", "Clear preview"),
        "Diagnostika samostatného OCR" to Pair("Diagnostika samostatného OCR", "Standalone OCR diagnostics"),
        "Služba snímání neodpověděla" to Pair("Služba snímania neodpovedala", "Capture service did not respond"),
        "Chybí souhlas nebo překryv, případně běží jiný režim" to Pair("Chýba súhlas alebo prekrytie, prípadne beží iný režim", "Consent or overlay permission is missing, or another mode is running"),
        "Aktualizace GameLingo" to Pair("Aktualizácia GameLingo", "GameLingo update"),
        "Nová verze GameLingo · O aplikaci → Aktualizace" to Pair("Nová verzia GameLingo · O aplikácii → Aktualizácia", "New GameLingo version · About → Updates"),
        "Nejprve zastav překlad a příjem." to Pair("Najprv zastav preklad a príjem.", "Stop translation and receiving first."),
        "Kontrola již probíhá. Zkus to znovu." to Pair("Kontrola už prebieha. Skús to znova.", "An update check is already running. Try again."),
        "Kontroluji vydání na GitHubu…" to Pair("Kontrolujem vydania na GitHube…", "Checking GitHub releases…"),
        "Nelze zjistit nainstalovanou verzi." to Pair("Nemožno zistiť nainštalovanú verziu.", "Could not determine the installed version."),
        "Zatím není dostupné vydání s ověřitelnými aktualizačními metadaty." to Pair("Zatiaľ nie je dostupné vydanie s overiteľnými aktualizačnými metadátami.", "No release with verifiable update metadata is available yet."),
        "Novější verze není dostupná." to Pair("Novšia verzia nie je dostupná.", "No newer version is available."),
        "Nainstalováno:" to Pair("Nainštalované:", "Installed:"),
        "Nová APK má jiný podpis. Aktualizace je zablokovaná, aby se nesmazalo nastavení. Je nutný jednorázový přechod na trvalý podpis." to Pair("Nová APK má iný podpis. Aktualizácia je zablokovaná, aby sa nastavenia nevymazali. Je potrebný jednorazový prechod na trvalý podpis.", "The new APK has a different signature. The update is blocked to preserve settings. A one-time migration to the permanent signing key is required."),
        "Nová verze GameLingo" to Pair("Nová verzia GameLingo", "New GameLingo version"),
        "Nová verze:" to Pair("Nová verzia:", "New version:"),
        "Velikost:" to Pair("Veľkosť:", "Size:"),
        "Později" to Pair("Neskôr", "Later"),
        "Stáhnout" to Pair("Stiahnuť", "Download"),
        "Kontrola aktualizace se nezdařila. Zkontroluj internet a zkus to znovu." to Pair("Kontrola aktualizácie zlyhala. Skontroluj internet a skús to znova.", "Update check failed. Check your internet connection and try again."),
        "Povolení instalace" to Pair("Povolenie inštalácie", "Installation permission"),
        "Povol instalaci z GameLingo v nastavení TV. Potom znovu otevři Aktualizace." to Pair("Povoľ inštaláciu z GameLingo v nastaveniach TV. Potom znova otvor Aktualizácie.", "Allow installation from GameLingo in TV settings. Then reopen Updates."),
        "Povol instalaci z GameLingo v nastavení zařízení. Potom znovu otevři Aktualizace." to Pair("Povoľ inštaláciu z GameLingo v nastaveniach zariadenia. Potom znova otvor Aktualizácie.", "Allow installation from GameLingo in device settings. Then reopen Updates."),
        "Otevřít nastavení" to Pair("Otvoriť nastavenia", "Open settings"),
        "Nastavení instalace není na této TV dostupné." to Pair("Nastavenia inštalácie nie sú na tomto TV dostupné.", "Installation settings are unavailable on this TV."),
        "Nastavení instalace není na tomto zařízení dostupné." to Pair("Nastavenia inštalácie nie sú na tomto zariadení dostupné.", "Installation settings are unavailable on this device."),
        "Jiná aktualizace již probíhá." to Pair("Iná aktualizácia už prebieha.", "Another update is already running."),
        "Stahování aktualizace" to Pair("Sťahovanie aktualizácie", "Downloading update"),
        "Překlad je aktivní. Aktualizaci spusť po zastavení." to Pair("Preklad je aktívny. Aktualizáciu spusti po zastavení.", "Translation is active. Start the update after stopping it."),
        "Systémový instalátor nelze otevřít." to Pair("Systémový inštalátor nemožno otvoriť.", "Could not open the system installer."),
        "Aktualizace nebyla nainstalována:" to Pair("Aktualizácia nebola nainštalovaná:", "Update not installed:"),
        "Neznámá chyba." to Pair("Neznáma chyba.", "Unknown error."),
        "Nelze vytvořit složku aktualizací." to Pair("Nemožno vytvoriť priečinok aktualizácií.", "Could not create the update directory."),
        "Stahování zrušeno." to Pair("Sťahovanie zrušené.", "Download canceled."),
        "Časový limit stahování." to Pair("Časový limit sťahovania.", "Download timed out."),
        "Neočekávaná velikost APK." to Pair("Neočakávaná veľkosť APK.", "Unexpected APK size."),
        "Kontrolní součet APK nesouhlasí." to Pair("Kontrolný súčet APK nesúhlasí.", "APK checksum mismatch."),
        "APK není čitelná." to Pair("APK nie je čitateľná.", "The APK could not be read."),
        "Podpis, identita nebo verze APK nesouhlasí." to Pair("Podpis, identita alebo verzia APK nesúhlasia.", "APK signature, identity or version mismatch."),
        "Nepovolený zdroj aktualizace." to Pair("Nepovolený zdroj aktualizácie.", "Untrusted update source."),
        "Chybí cíl přesměrování." to Pair("Chýba cieľ presmerovania.", "Missing redirect destination."),
        "Příliš mnoho přesměrování." to Pair("Príliš veľa presmerovaní.", "Too many redirects."),
        "GitHub odpověděl HTTP" to Pair("GitHub odpovedal HTTP", "GitHub returned HTTP"),
        "Zkontrolovat aktualizace" to Pair("Skontrolovať aktualizácie", "Check for updates"),
        "Stáhnout novou verzi" to Pair("Stiahnuť novú verziu", "Download new version"),
        "Modrá · čtení dialogů" to Pair("Modrá · čítanie dialógov", "Blue · read dialogue"),
        "Zelená · přeložené dialogy" to Pair("Zelená · preložené dialógy", "Green · translated dialogue"),
        "Žlutá · čtení dalších textů" to Pair("Žltá · čítanie ďalších textov", "Yellow · read other text"),
        "Oranžová · přeložené texty" to Pair("Oranžová · preložené texty", "Orange · translated text"),
        "zamčeno" to Pair("zamknuté", "locked"),
        "Náhled oblastí · zmizí za 30 s" to Pair("Náhľad oblastí · zmizne za 30 s", "Region preview · disappears in 30 s"),
        "Nejdříve povol překryv v nastavení TV." to Pair("Najprv povoľ prekrytie v nastaveniach TV.", "Allow overlays in TV settings first."),
        "Náhled nelze zobrazit" to Pair("Náhľad nemožno zobraziť", "Could not display preview"),
        "Nejdříve zastav překlad i příjem z telefonu." to Pair("Najprv zastav preklad aj príjem z telefónu.", "Stop translation and phone reception first."),
        "Rozložení oblastí" to Pair("Rozloženie oblastí", "Region layout"),
        "Posun" to Pair("Posun", "Move"),
        "Velikost" to Pair("Veľkosť", "Size"),
        "Písmo" to Pair("Písmo", "Font"),
        "Odemknout oblast" to Pair("Odomknúť oblasť", "Unlock region"),
        "Zamknout oblast" to Pair("Zamknúť oblasť", "Lock region"),
        "Čtení: ruční" to Pair("Čítanie: ručné", "Reading: manual"),
        "Čtení: automatické" to Pair("Čítanie: automatické", "Reading: automatic"),
        "Poloha: vlastní" to Pair("Poloha: vlastná", "Position: custom"),
        "Poloha: automatická" to Pair("Poloha: automatická", "Position: automatic"),
        "Další texty: zapnuto" to Pair("Ďalšie texty: zapnuté", "Other text: on"),
        "Další texty: vypnuto" to Pair("Ďalšie texty: vypnuté", "Other text: off"),
        "Typ: celý text" to Pair("Typ: celý text", "Type: full text"),
        "Typ: volby odpovědí" to Pair("Typ: voľby odpovedí", "Type: answer choices"),
        "Šířka" to Pair("Šírka", "Width"),
        "písmo" to Pair("písmo", "font"),
        "výška" to Pair("výška", "height"),
        "Zamčeno · odemkni oblast" to Pair("Zamknuté · odomkni oblasť", "Locked · unlock region"),
        "OK · posouvat šipkami" to Pair("OK · posúvať šípkami", "OK · move with arrows"),
        "OK · šířka a měřítko" to Pair("OK · šírka a mierka", "OK · width and scale"),
        "OK · změnit velikost" to Pair("OK · zmeniť veľkosť", "OK · resize"),
        "OK · velikost písma" to Pair("OK · veľkosť písma", "OK · font size"),
        "Vyber oblast a režim · v editoru stiskni OK, potom šipky · OK ukončí úpravy" to Pair("Vyber oblasť a režim · v editore stlač OK, potom šípky · OK ukončí úpravy", "Choose region and mode · press OK in editor, then arrows · OK ends editing"),
        "Režim úprav · šipky mění rámeček · OK ukončí úpravy" to Pair("Režim úprav · šípky menia rámček · OK ukončí úpravy", "Editing · arrows adjust frame · OK ends editing"),
        "Vyber další ovládací prvek šipkami" to Pair("Vyber ďalší ovládací prvok šípkami", "Use arrows to choose another control"),
        "Úpravy ukončeny · šipkami vyber další ovládací prvek" to Pair("Úpravy ukončené · šípkami vyber ďalší ovládací prvok", "Editing finished · use arrows to choose another control"),
        "Režim úprav · OK ukončí úpravy" to Pair("Režim úprav · OK ukončí úpravy", "Editing · OK ends editing"),
        "Režim" to Pair("Režim", "Mode"),
        "Typ" to Pair("Typ", "Type"),
        "Posouvat" to Pair("Posúvať", "Move"),
        "Obnovit oblast" to Pair("Obnoviť oblasť", "Reset region"),
        "Nejdřív odemkni oblast." to Pair("Najprv odomkni oblasť.", "Unlock the region first."),
        "Překlad běží nebo oblast není platná." to Pair("Preklad beží alebo oblasť nie je platná.", "Translation is running or the region is invalid."),
        "Uložit do profilu" to Pair("Uložiť do profilu", "Save to profile"),
        "Vítej v GameLingo TV" to Pair("Vitaj v GameLingo TV", "Welcome to GameLingo TV"),
        "Vyber jazyky. Potom můžeš překládat z telefonu nebo přímo na televizi." to Pair("Vyber jazyky. Potom môžeš prekladať z telefónu alebo priamo na televízore.", "Choose your languages, then translate from your phone or directly on the TV."),
        "Pokračovat na TV" to Pair("Pokračovať na TV", "Continue on TV"),
        "PŘEKLAD HRY" to Pair("PREKLAD HRY", "GAME TRANSLATION"),
        "Překlad mluvených dialogů" to Pair("Preklad hovorených dialógov", "Translate dialogue subtitles"),
        "Herní texty" to Pair("Herné texty", "Game text"),
        "Nápověda a úkoly v herním obraze" to Pair("Pomoc a úlohy v hernom obraze", "Hints and objectives in the game image"),
        "Překrýt původní titulky" to Pair("Prekryť pôvodné titulky", "Cover original subtitles"),
        "Přeložený text přímo přes originál" to Pair("Preložený text priamo cez originál", "Translated text over the original"),
        "Postupné čtení delších dialogů" to Pair("Postupné čítanie dlhších dialógov", "Sequential reading of longer dialogue"),
        "Změny nastavení překladu se projeví po novém spuštění." to Pair("Zmeny nastavení prekladu sa prejavia po novom spustení.", "Translation settings take effect after restarting translation."),
        "OCR A ROZPOZNÁVÁNÍ" to Pair("OCR A ROZPOZNÁVANIE", "OCR AND RECOGNITION"),
        "Nejprve zastav překlad." to Pair("Najprv zastav preklad.", "Stop translation first."),
        "Platí pro čtení přímo na TV. Při příjmu titulků z telefonu rozhoduje telefon." to Pair("Platí pre čítanie priamo na TV. Pri príjme titulkov z telefónu rozhoduje telefón.", "Applies to reading directly on TV. When receiving subtitles, the phone controls reading."),
        "Přímý překlad i překlad z telefonu používají internet nebo dříve uložené překlady." to Pair("Priamy preklad aj preklad z telefónu používajú internet alebo skôr uložené preklady.", "Direct translation and phone translation use internet access or previously cached translations."),
        "ZOBRAZENÍ" to Pair("ZOBRAZENIE", "DISPLAY"),
        "Titulky přes obraz hry" to Pair("Titulky cez obraz hry", "Subtitles over game image"),
        "Vyžaduje povolení překryvu" to Pair("Vyžaduje povolenie prekrytia", "Requires overlay permission"),
        "Pro čtení her přímo v TV potřebuje aplikace povolení překryvu." to Pair("Na čítanie hier priamo v TV potrebuje aplikácia povolenie prekrytia.", "Reading games directly on the TV requires overlay permission."),
        "ZABEZPEČENÍ" to Pair("ZABEZPEČENIE", "SECURITY"),
        "Legacy · staré aplikace" to Pair("Legacy · staršie aplikácie", "Legacy · older apps"),
        "Povolí nepodepsané titulky (jen důvěryhodná síť)" to Pair("Povolí nepodpísané titulky (iba dôveryhodná sieť)", "Allow unsigned subtitles (trusted networks only)"),
        "DALŠÍ" to Pair("ĎALŠIE", "MORE"),
        "Vlastní výrazy pro přímé OCR" to Pair("Vlastné výrazy pre priame OCR", "Custom terms for direct OCR"),
        "Překlady zpracované přímo na TV" to Pair("Preklady spracované priamo na TV", "Translations processed on TV"),
        "Původní autoři, zdrojový kód a GNU AGPLv3" to Pair("Pôvodní autori, zdrojový kód a GNU AGPLv3", "Original authors, source code and GNU AGPLv3"),
        "Nejdříve zastav překlad a příjem z telefonu." to Pair("Najprv zastav preklad a príjem z telefónu.", "Stop translation and phone reception first."),
        "Místní překladová paměť" to Pair("Miestna prekladová pamäť", "Local translation cache"),
        "Ukládá originály a překlady na tomto zařízení. Historie a profily jsou oddělené." to Pair("Ukladá originály a preklady na tomto zariadení. História a profily sú oddelené.", "Stores originals and translations on this device. History and profiles are separate."),
        "Vypnout paměť" to Pair("Vypnúť pamäť", "Disable cache"),
        "Zapnout paměť" to Pair("Zapnúť pamäť", "Enable cache"),
        "Vymazat paměť" to Pair("Vymazať pamäť", "Clear cache"),
        "Vymazat pouze místní překladovou cache? Profily, účty ani historii to nesmaže." to Pair("Vymazať iba miestnu prekladovú cache? Profily, účty ani históriu to nezmaže.", "Clear only the local translation cache? Profiles, accounts and history will not be deleted."),
        "Ukládá originály i překlady na tomto zařízení. Je oddělená od historie. Vypnutí může zvýšit počet online požadavků a omezit překlad bez internetu." to Pair("Ukladá originály aj preklady na tomto zariadení. Je oddelená od histórie. Vypnutie môže zvýšiť počet online požiadaviek a obmedziť preklad bez internetu.", "Stores originals and translations on this device, separate from history. Disabling may increase online requests and limit offline translation."),
        "zapnuto" to Pair("zapnuté", "on"),
        "vypnuto" to Pair("vypnuté", "off"),
        "Operace se nezdařila. Zkus ji znovu." to Pair("Operácia sa nepodarila. Skús ju znova.", "Operation failed. Try again."),
        "Cache zapnuta." to Pair("Cache zapnutá.", "Cache enabled."),
        "Cache vypnuta; uložené překlady lze vymazat zvlášť." to Pair("Cache vypnutá; uložené preklady možno vymazať zvlášť.", "Cache disabled; saved translations can be cleared separately."),
        "Místní překladová cache vymazána." to Pair("Miestna prekladová cache vymazaná.", "Local translation cache cleared."),
        "QR není dostupný. Zkontroluj místní IP a platnost PINu, nebo použij ruční párování." to Pair("QR nie je dostupný. Skontroluj miestnu IP a platnosť PINu alebo použi ručné párovanie.", "QR unavailable. Check local IP and PIN validity or use manual pairing."),
        "QR kód není dostupný." to Pair("QR kód nie je dostupný.", "QR code unavailable."),
        "Dočasný párovací QR kód" to Pair("Dočasný párovací QR kód", "Temporary pairing QR code"),
        "V mobilní aplikaci otevři Zařízení a naskenuj QR kód." to Pair("V mobilnej aplikácii otvor Zariadenia a naskenuj QR kód.", "Open Devices in the mobile app and scan the QR code."),
        "Kód již není platný. Vrať se zpět a vygeneruj nový." to Pair("Kód už nie je platný. Vráť sa späť a vygeneruj nový.", "Code expired. Go back and generate a new code."),
        "zbývá" to Pair("zostáva", "remaining"),
        "Párování pomocí QR" to Pair("Párovanie pomocou QR", "Pair using QR"),
        "Vyber svůj mobilní telefon." to Pair("Vyber svoj mobilný telefón.", "Choose your phone."),
        "IPA · instalace s podpisem" to Pair("IPA · inštalácia s podpisom", "IPA · signed installation"),
        "APK · mobilní verze" to Pair("APK · mobilná verzia", "APK · mobile version"),
        "Stáhnout GameLingo" to Pair("Stiahnuť GameLingo", "Download GameLingo"),
        "Ověřuji aktuální vydání…" to Pair("Overujem aktuálne vydanie…", "Checking current release…"),
        "QR kód ke stažení" to Pair("QR kód na stiahnutie", "Download QR code"),
        "Naskenuj QR kód fotoaparátem iPhonu. IPA je testovací balíček; před instalací vyžaduje podpis, například přes AltStore nebo Sideloadly." to Pair("Naskenuj QR kód fotoaparátom iPhonu. IPA je testovací balík; pred inštaláciou vyžaduje podpis, napríklad cez AltStore alebo Sideloadly.", "Scan with your iPhone camera. The test IPA requires signing before installation, for example with AltStore or Sideloadly."),
        "Naskenuj QR kód telefonem s Androidem, stáhni mobilní APK a potvrď instalaci. Systém může vyžadovat povolení instalace z tohoto zdroje." to Pair("Naskenuj QR kód telefónom s Androidom, stiahni mobilný APK a potvrď inštaláciu. Systém môže vyžadovať povolenie inštalácie z tohto zdroja.", "Scan with your Android phone, download the mobile APK and confirm installation. Android may require permission to install from this source."),
        "Otevři QR kód v telefonu." to Pair("Otvor QR kód v telefóne.", "Open the QR code on your phone."),
        "Znovu ověřit vydání" to Pair("Znova overiť vydanie", "Check release again"),
        "Aktuální vydání" to Pair("Aktuálne vydanie", "Current release"),
        "Vydání se nepodařilo ověřit · stránka všech vydání" to Pair("Vydanie sa nepodarilo overiť · stránka všetkých vydaní", "Could not verify release · all releases page"),
        "Načti QR kód z GameLingo TV" to Pair("Načítaj QR kód z GameLingo TV", "Scan the GameLingo TV QR code"),
        "Fotoaparát není dostupný. Použij ruční PIN." to Pair("Fotoaparát nie je dostupný. Použi ručný PIN.", "Camera unavailable. Use manual PIN entry."),
        "Neplatný GameLingo QR kód. Použij nový kód z TV." to Pair("Neplatný GameLingo QR kód. Použi nový kód z TV.", "Invalid GameLingo QR code. Use a new code from the TV."),
        "Nastavení TV" to Pair("Nastavenia TV", "TV settings"),
        "Jazyky, OCR a Překlad určují, co a jak se překládá. Rozložení nastavuje čtyři oblasti. Vzhled upravuje titulky a Profily ukládají konfiguraci pro hry, filmy a aplikace." to Pair("Jazyky, OCR a Preklad určujú, čo a ako sa prekladá. Rozloženie nastavuje štyri oblasti. Vzhľad upravuje titulky a Profily ukladajú konfiguráciu pre hry, filmy a aplikácie.", "Languages, OCR and Translation determine what is translated and how. Layout sets four regions. Appearance changes subtitles and Profiles save settings for games, films and apps."),
        "Zvol režim rozpoznávání, filtrování menu a případně rozšířené hledání. Ruční modrá oblast omezuje čtení na místo dialogů. Před změnou zastav překlad." to Pair("Zvoľ režim rozpoznávania, filtrovanie menu a prípadne rozšírené hľadanie. Ručná modrá oblasť obmedzuje čítanie na miesto dialógov. Pred zmenou zastav preklad.", "Choose recognition mode, menu filtering and optional expanded search. The manual blue region limits reading to dialogue. Stop translation before making changes."),
        "Vyber barvu a pozadí překladu. Polohu, šířku a velikost písma upravíš v Rozložení a uložíš do profilu." to Pair("Vyber farbu a pozadie prekladu. Polohu, šírku a veľkosť písma upravíš v Rozložení a uložíš do profilu.", "Choose translation color and background. Adjust position, width and font size in Layout and save to a profile."),
        "Test překryvu ověří zobrazení pevného titulku. Test překladového jádra ověří online překlad. Test snímání vyžaduje souhlas Androidu. Náhled lze vymazat." to Pair("Test prekrytia overí zobrazenie pevného titulku. Test prekladového jadra overí online preklad. Test snímania vyžaduje súhlas Androidu. Náhľad možno vymazať.", "The overlay test displays a fixed subtitle. The translation engine test checks online translation. Capture requires Android permission. You can clear the preview."),
        "Stáhnout aplikaci" to Pair("Stiahnuť aplikáciu", "Download app"),
        "Vyber iPhone nebo Android a naskenuj QR kód fotoaparátem telefonu. Odkaz vede na nejnovější dostupný mobilní balíček na GitHubu. IPA vyžaduje podpis; Android APK může vyžadovat povolení instalace z tohoto zdroje. Pokud vydání nelze ověřit, použij stránku všech vydání. Toto není párovací QR kód." to Pair("Vyber iPhone alebo Android a naskenuj QR kód fotoaparátom telefónu. Odkaz vedie na najnovší dostupný mobilný balík na GitHube. IPA vyžaduje podpis; Android APK môže vyžadovať povolenie inštalácie z tohto zdroja. Ak vydanie nemožno overiť, použi stránku všetkých vydaní. Toto nie je párovací QR kód.", "Choose iPhone or Android and scan the QR code with your phone camera. It opens the latest available mobile package on GitHub. IPA requires signing; APK may require permission to install from this source. If the release cannot be verified, use the releases page. This is not a pairing QR code."),
        "Párování telefonu" to Pair("Párovanie telefónu", "Phone pairing"),
        "Telefon a TV připoj ke stejné síti. V mobilu otevři Zařízení, vyber TV a naskenuj QR kód nebo zadej šestimístný PIN. Po vypršení platnosti vytvoř nový kód." to Pair("Telefón a TV pripoj k rovnakej sieti. V mobile otvor Zariadenia, vyber TV a naskenuj QR kód alebo zadaj šesťmiestny PIN. Po vypršaní platnosti vytvor nový kód.", "Connect phone and TV to the same network. Open Devices on the phone, choose TV and scan the QR code or enter the six-digit PIN. Generate a new code if it expires."),
        "Historie je volitelná a uchovává poslední překlady v paměti. Je oddělená od místní překladové paměti." to Pair("História je voliteľná a uchováva posledné preklady v pamäti. Je oddelená od miestnej prekladovej pamäte.", "History is optional and keeps recent translations in memory. It is separate from the local translation cache."),
        "Překladová paměť" to Pair("Prekladová pamäť", "Translation cache"),
        "Místní paměť ukládá originály a překlady pro opakované použití. Můžeš ji vypnout nebo vymazat bez odstranění profilů." to Pair("Miestna pamäť ukladá originály a preklady na opakované použitie. Môžeš ju vypnúť alebo vymazať bez odstránenia profilov.", "The local cache stores originals and translations for reuse. You can disable or clear it without deleting profiles."),
        "Slovník opravuje vlastní výrazy a jména pro zvolenou jazykovou dvojici. Před úpravou zastav překlad." to Pair("Slovník opravuje vlastné výrazy a mená pre zvolenú jazykovú dvojicu. Pred úpravou zastav preklad.", "The glossary corrects custom terms and names for the selected language pair. Stop translation before editing."),
        "1. Otevři GameLingo na telefonu.\n2. Zvol Google TV a vyber tuto televizi.\n3. Zadej šest číslic z obrazovky.\n\nTelefon a TV musí být ve stejné síti. Pokud používáš starý přijímač PlayCaption, zavři jej. Přijímač může zobrazit titulky přes HDMI obraz jen na podporovaném televizoru." to Pair("1. Otvor GameLingo na telefóne.\n2. Zvoľ Google TV a vyber tento televízor.\n3. Zadaj šesť číslic z obrazovky.\n\nTelefón a TV musia byť v rovnakej sieti. Ak používaš starý prijímač PlayCaption, zatvor ho. Prijímač môže zobraziť titulky cez HDMI obraz iba na podporovanom televízore.", "1. Open GameLingo on your phone.\n2. Choose Google TV and select this TV.\n3. Enter the six digits shown on screen.\n\nPhone and TV must be on the same network. Close the old PlayCaption receiver if it is running. Overlay over HDMI is available only on supported TVs."),
        "Oblasti" to Pair("Oblasti", "Regions"),
        "Modrá oblast čte původní titulky. Zelená určuje místo přeložených dialogů. Žlutá čte volby a herní texty, oranžová zobrazuje jejich překlad. Polohu a rozměry měň podle konkrétní hry. Oblasti se ukládají do profilů." to Pair("Modrá oblasť číta pôvodné titulky. Zelená určuje miesto preložených dialógov. Žltá číta voľby a herné texty, oranžová zobrazuje ich preklad. Polohu a rozmery meň podľa hry. Oblasti sa ukladajú do profilov.", "Blue reads original subtitles. Green sets translated dialogue position. Yellow reads choices and game text; orange displays their translation. Adjust position and size for each game. Regions are saved in profiles."),
        "OCR čte dialogy ve zvoleném zdrojovém jazyce. Samostatně lze zapnout dialogy, herní texty a volby. Při změně rychlosti nebo oblastí doporučujeme překlad nejdříve zastavit. Výchozí nastavení vychází z ověřeného základu PlayCaption." to Pair("OCR číta dialógy vo zvolenom zdrojovom jazyku. Samostatne možno zapnúť dialógy, herné texty a voľby. Pred zmenou rýchlosti alebo oblastí odporúčame zastaviť preklad. Predvolené nastavenia vychádzajú z overeného základu PlayCaption.", "OCR reads dialogue in the selected source language. Dialogue, game text and choices can be enabled separately. Stop translation before changing speed or regions. Defaults are based on the proven PlayCaption foundation."),
        "Profil ukládá aktuální OCR oblasti, volby a nastavení překladu pro konkrétní hru, film nebo aplikaci. Údaje o účtech Sony a Microsoft se do profilů neukládají." to Pair("Profil ukladá aktuálne OCR oblasti, voľby a nastavenia prekladu pre konkrétnu hru, film alebo aplikáciu. Údaje účtov Sony a Microsoft sa do profilov neukladajú.", "A profile saves current OCR regions, options and translation settings for a game, film or app. Sony and Microsoft account information is not stored in profiles."),
        "Xbox a PlayStation" to Pair("Xbox a PlayStation", "Xbox and PlayStation"),
        "Pro Xbox se přihlašuješ svým účtem Microsoft a povolíš vzdálené funkce. Pro PS5/PS4 použij Remote Play a údaje svého účtu PlayStation. Párovací PIN z konzole se liší od hesla. Do hry ani do konzole se nic neinstaluje." to Pair("Pre Xbox sa prihlás účtom Microsoft a povoľ vzdialené funkcie. Pre PS5/PS4 použi Remote Play a údaje účtu PlayStation. Párovací PIN konzoly sa líši od hesla. Do hry ani konzoly sa nič neinštaluje.", "For Xbox, sign in with your Microsoft account and enable remote features. For PS5/PS4, use Remote Play and your PlayStation account details. The console pairing PIN differs from your password. Nothing is installed on the game or console."),
        "Překlad na telefonu" to Pair("Preklad na telefóne", "Translation on phone"),
        "Standardní režim funguje bez dalšího nastavování. Automatický režim používá připravenou zónu a další můžeš přidat v Oblastech obrazovky. Pokud překládáš přes TV, telefon i TV musí být ve stejné síti." to Pair("Štandardný režim funguje bez ďalšieho nastavovania. Automatický režim používa pripravenú zónu a ďalšie môžeš pridať v Oblastiach obrazovky. Ak prekladáš cez TV, telefón aj TV musia byť v rovnakej sieti.", "Standard mode works without additional setup. Automatic mode has a ready-to-use zone; add more in Screen regions. For TV output, phone and TV must be on the same network."),
        "Vyber Xbox, PlayStation nebo samostatnou TV, připoj zařízení a spusť překlad. Kam se přeložené titulky zobrazí, nastavíš v Zařízení. Oblasti upravují místo čtení a zobrazení, Profily her ukládají vlastní konfiguraci." to Pair("Vyber Xbox, PlayStation alebo samostatnú TV, pripoj zariadenie a spusti preklad. Miesto zobrazenia preložených titulkov nastavíš v Zariadeniach. Oblasti upravujú čítanie a zobrazenie, Profily hier ukladajú konfiguráciu.", "Choose Xbox, PlayStation or standalone TV, connect your device and start translation. Set subtitle output in Devices. Regions control reading and display; Game profiles save custom configurations."),
        "Otevřít nápovědu" to Pair("Otvoriť pomoc", "Open help"),
        "Nápověda" to Pair("Pomoc", "Help"),
        "testovací sestavení" to Pair("testovacie zostavenie", "test build"),
        "Nezávislý překlad herních dialogů a textů pro Xbox, PlayStation a kompatibilní TV." to Pair("Nezávislý preklad herných dialógov a textov pre Xbox, PlayStation a kompatibilné TV.", "Independent game dialogue and text translation for Xbox, PlayStation and compatible TVs."),
        "Licence a původní autoři" to Pair("Licencie a pôvodní autori", "Licenses and original authors"),
        "GameLingo využívá open-source základy PlayCaption a Chiaki. Zdrojový kód a jeho úpravy jsou šířeny podle GNU AGPLv3 (AGPL-3.0). GameLingo není oficiální aplikací Sony, Microsoftu ani Google. Názvy Xbox a PlayStation jsou ochranné známky příslušných vlastníků." to Pair("GameLingo využíva open-source základy PlayCaption a Chiaki. Zdrojový kód a jeho úpravy sa šíria podľa GNU AGPLv3 (AGPL-3.0). GameLingo nie je oficiálnou aplikáciou Sony, Microsoftu ani Google. Xbox a PlayStation sú ochranné známky príslušných vlastníkov.", "GameLingo uses the open-source foundations of PlayCaption and Chiaki. Source code and modifications are distributed under GNU AGPLv3 (AGPL-3.0). GameLingo is not an official Sony, Microsoft or Google application. Xbox and PlayStation are trademarks of their respective owners."),
        "Otevřít odkaz" to Pair("Otvoriť odkaz", "Open link"),
        "Licenční texty v aplikaci" to Pair("Licenčné texty v aplikácii", "License texts in app"),
        "Licence třetích stran" to Pair("Licencie tretích strán", "Third-party licenses"),
        "Číst bez internetu" to Pair("Čítať bez internetu", "Read offline"),
        "Licenční soubor není v tomto sestavení dostupný." to Pair("Licenčný súbor nie je v tomto zostavení dostupný.", "The license file is not available in this build."),
        "Zdrojové kódy" to Pair("Zdrojové kódy", "Source code"),
        "GameLingo na GitHubu" to Pair("GameLingo na GitHube", "GameLingo on GitHub"),
        "Původní PlayCaption" to Pair("Pôvodný PlayCaption", "Original PlayCaption"),
        "Zatím žádný spárovaný telefon." to Pair("Zatiaľ žiadny spárovaný telefón.", "No paired phones yet."),
        "Připojen" to Pair("Pripojený", "Connected"),
        "Spárován" to Pair("Spárovaný", "Paired"),
        "Odpárovat" to Pair("Zrušiť párovanie", "Unpair"),
        "Ostatní telefony zůstanou spárované." to Pair("Ostatné telefóny zostanú spárované.", "Other phones will remain paired."),
        "Odpárování se nepodařilo uložit." to Pair("Zrušenie párovania sa nepodarilo uložiť.", "Could not save unpairing."),
        "Přejmenovat konzoli" to Pair("Premenovať konzolu", "Rename console"),
        "Konzole a zařízení" to Pair("Konzoly a zariadenia", "Consoles and devices"),
        "Přidat zařízení" to Pair("Pridať zariadenie", "Add device"),
        "Telefon · QR / PIN" to Pair("Telefón · QR / PIN", "Phone · QR / PIN"),
        "Přidat PS5 / PS4" to Pair("Pridať PS5 / PS4", "Add PS5 / PS4"),
        "Přejmenovat" to Pair("Premenovať", "Rename"),
        "Odebrat" to Pair("Odobrať", "Remove"),
        "Xbox: vyhledávám…" to Pair("Xbox: vyhľadávam…", "Xbox: searching…"),
        "Xbox: žádná konzole" to Pair("Xbox: žiadna konzola", "Xbox: no console"),
        "Připojit" to Pair("Pripojiť", "Connect"),
        "Odebrat Xbox?" to Pair("Odobrať Xbox?", "Remove Xbox?"),
        "Konzole zmizí ze seznamu GameLingo, ale zůstane na tvém Microsoft účtu. Můžeš ji později znovu zobrazit." to Pair("Konzola zmizne zo zoznamu GameLingo, ale zostane na tvojom účte Microsoft. Môžeš ju neskôr znova zobraziť.", "The console will be hidden in GameLingo but remain on your Microsoft account. You can show it again later."),
        "Vrátit odebrané Xboxy" to Pair("Vrátiť odobraté Xboxy", "Restore hidden Xbox consoles"),
        "Znovu zobrazit konzole" to Pair("Znova zobraziť konzoly", "Show consoles again"),
        "Xbox: nepřihlášeno" to Pair("Xbox: neprihlásené", "Xbox: not signed in"),
        "Přidat Xbox" to Pair("Pridať Xbox", "Add Xbox"),
        "Přihlásit Microsoft" to Pair("Prihlásiť Microsoft", "Sign in to Microsoft"),
        "Telefony" to Pair("Telefóny", "Phones"),
        "Přidat telefon" to Pair("Pridať telefón", "Add phone"),
        "Párování pomocí QR kódu nebo PINu" to Pair("Párovanie pomocou QR kódu alebo PINu", "Pair using QR code or PIN"),
        "Televize" to Pair("Televízory", "TVs"),
        "Přidat televizor" to Pair("Pridať televízor", "Add TV"),
        "Načíst QR kód z TV" to Pair("Načítať QR kód z TV", "Scan TV QR code"),
        "Párování v domácí síti" to Pair("Párovanie v domácej sieti", "Pair on home network"),
        "TV odpověděla" to Pair("TV odpovedala", "TV responded"),
        "Kód uložen, spojení neověřeno" to Pair("Kód uložený, spojenie neoverené", "Code saved, connection unverified"),
        "Spárovat šest číslic z TV" to Pair("Spárovať šesť číslic z TV", "Pair with six-digit TV code"),
        "Ověřit spojení" to Pair("Overiť spojenie", "Verify connection"),
        "TV odpověděla na ověření. Příjem titulků ještě ověř ve hře." to Pair("TV odpovedala na overenie. Príjem titulkov ešte over v hre.", "TV responded. Verify subtitle reception in a game."),
        "TV neodpovídá. Ověř IP, připojení a případně zadej nový kód." to Pair("TV neodpovedá. Over IP, pripojenie a prípadne zadaj nový kód.", "TV is not responding. Check IP, connection and enter a new code if needed."),
        "z tohoto zařízení?" to Pair("z tohto zariadenia?", "from this device?"),
        "Přístup telefonu odvolej také na TV. Místní odebrání neodvolá klíč v přijímači." to Pair("Prístup telefónu odvolaj aj na TV. Miestne odobratie neodvolá kľúč v prijímači.", "Revoke phone access on the TV too. Local removal does not revoke the receiver key."),
        "Šestimístný kód z GameLingo TV" to Pair("Šesťmiestny kód z GameLingo TV", "Six-digit GameLingo TV code"),
        "Párování televize" to Pair("Párovanie televízora", "Pair TV"),
        "Na TV otevři Překlad z telefonu. Zadej nový šestimístný kód a potvrď párování. Používej jen důvěryhodnou domácí síť; párování a titulky nejsou šifrované." to Pair("Na TV otvor Preklad z telefónu. Zadaj nový šesťmiestny kód a potvrď párovanie. Používaj iba dôveryhodnú domácu sieť; párovanie a titulky nie sú šifrované.", "Open Translation from phone on the TV. Enter a new six-digit code and confirm pairing. Use only a trusted home network; pairing and subtitles are not encrypted."),
        "Spárovat" to Pair("Spárovať", "Pair"),
        "Je nutných šest číslic" to Pair("Je potrebných šesť číslic", "Six digits are required"),
        "TV přijala kód. Ověřuji spojení…" to Pair("TV prijala kód. Overujem spojenie…", "TV accepted the code. Verifying connection…"),
        "GameLingo TV je připojená." to Pair("GameLingo TV je pripojená.", "GameLingo TV is connected."),
        "Kód uložen, ale TV zatím neodpovídá." to Pair("Kód uložený, ale TV zatiaľ neodpovedá.", "Code saved, but TV is not responding yet."),
        "TV neodpověděla. Ověř IP a síť." to Pair("TV neodpovedala. Over IP a sieť.", "TV did not respond. Check IP and network."),
        "Zadej šest číslic z TV." to Pair("Zadaj šesť číslic z TV.", "Enter six digits from the TV."),
        "Odpověď nepřišla od vybrané TV." to Pair("Odpoveď neprišla od vybranej TV.", "Response did not come from the selected TV."),
        "TV kód odmítla. Vytvoř nový kód." to Pair("TV kód odmietla. Vytvor nový kód.", "TV rejected the code. Generate a new one."),
        "Televize vrátila neplatnou odpověď." to Pair("Televízor vrátil neplatnú odpoveď.", "TV returned an invalid response."),
        "Stav místních nastavení a zahájené relace. Uložené údaje nepotvrzují živé spojení s konzolí ani TV." to Pair("Stav miestnych nastavení a začatej relácie. Uložené údaje nepotvrdzujú živé spojenie s konzolou ani TV.", "Local settings and session status. Saved data does not confirm a live console or TV connection."),
        "Stav" to Pair("Stav", "Status"),
        "Poslední překlad" to Pair("Posledný preklad", "Latest translation"),
        "Zatím žádný překlad ve volitelné historii." to Pair("Zatiaľ žiadny preklad vo voliteľnej histórii.", "No translation in optional history yet."),
        "Překladová fronta" to Pair("Prekladový rad", "Translation queue"),
        "Xbox – skutečný obraz" to Pair("Xbox – skutočný obraz", "Xbox – live image"),
        "Relace spuštěna – snímky se ověřují zvlášť" to Pair("Relácia spustená – snímky sa overujú zvlášť", "Session started – frames checked separately"),
        "Relace není spuštěna" to Pair("Relácia nie je spustená", "Session not started"),
        "Xbox – účet" to Pair("Xbox – účet", "Xbox – account"),
        "Přihlášený" to Pair("Prihlásený", "Signed in"),
        "Nepřihlášený" to Pair("Neprihlásený", "Not signed in"),
        "Xbox – volba konzole" to Pair("Xbox – výber konzoly", "Xbox – console selection"),
        "Žádná vybraná" to Pair("Žiadna vybraná", "None selected"),
        "ID konzole uloženo" to Pair("ID konzoly uložené", "Console ID saved"),
        "Uložené konzole" to Pair("Uložené konzoly", "Saved consoles"),
        "jedna vybraná" to Pair("jedna vybraná", "one selected"),
        "žádná vybraná" to Pair("žiadna vybraná", "none selected"),
        "Výstup titulků" to Pair("Výstup titulkov", "Subtitle output"),
        "Telefon" to Pair("Telefón", "Phone"),
        "Televize nevybrána" to Pair("Televízor nevybraný", "No TV selected"),
        "Párování TV" to Pair("Párovanie TV", "TV pairing"),
        "Bez uloženého profilu" to Pair("Bez uloženého profilu", "No saved profile"),
        "Nespárováno" to Pair("Nespárované", "Not paired"),
        "Ověřené spojení" to Pair("Overené spojenie", "Verified connection"),
        "Spárováno – bez čerstvého potvrzení" to Pair("Spárované – bez nového potvrdenia", "Paired – not recently verified"),
        "Jazyk hry → překlad" to Pair("Jazyk hry → preklad", "Game language → translation"),
        "Aktualizovat stav" to Pair("Aktualizovať stav", "Refresh status"),
        "Ověřit spojení s TV" to Pair("Overiť spojenie s TV", "Verify TV connection"),
        "Nejdříve vyber a spáruj TV." to Pair("Najprv vyber a spáruj TV.", "Select and pair a TV first."),
        "Spojení ověřeno." to Pair("Spojenie overené.", "Connection verified."),
        "TV neodpovídá. Zkontroluj domácí síť a přijímač." to Pair("TV neodpovedá. Skontroluj domácu sieť a prijímač.", "TV is not responding. Check the home network and receiver."),
        "Vymazat místní log překladu" to Pair("Vymazať miestny log prekladu", "Clear local translation log"),
        "Nejdříve zastav překlad a příjem." to Pair("Najprv zastav preklad a príjem.", "Stop translation and reception first."),
        "Vymazat starý i nový místní log překladu? Ostatní diagnostické soubory zůstanou." to Pair("Vymazať starý aj nový miestny log prekladu? Ostatné diagnostické súbory zostanú.", "Clear both old and new local translation logs? Other diagnostics will remain."),
        "Místní log překladu vymazán." to Pair("Miestny log prekladu vymazaný.", "Local translation log cleared."),
        "Některý log se nepodařilo vymazat." to Pair("Niektorý log sa nepodarilo vymazať.", "One or more logs could not be cleared."),
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
