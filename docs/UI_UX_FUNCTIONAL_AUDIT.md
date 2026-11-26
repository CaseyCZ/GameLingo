# GameLingo — audit návrhu, ovládání a funkčnosti před veřejným vydáním

Datum: 26. 11. 2026. Rozsah: iOS, Android telefon, Google/Android TV, včetně vlastních podstránek a zděděných generovaných obrazovek. Referenční TV: párovací menu schválené majitelem produktu z hlediska měřítka; nikoli funkční akceptace. Pracovní větev `feature/ocr-lab`, stabilní 0.5.1 beze změny.

## 1. Závazný design a navigace

- Jednotné GameLingo barvy, typografie, ikony, rozestupy, karty a tlačítka. Jedna sada design tokenů a komponent na platformu, společná sémantika. Nezavádět nový vzhled v každé podnabídce.
- Každá vlastní podstránka: Zpět **vlevo nahoře**, nadpis uprostřed, kontextová nápověda vpravo nahoře. Hlavička mimo scroll. Nativní iOS Back u push navigace; stejné umístění u sheetů. Úvodní/root obrazovky bez fiktivního Back. Potvrzení a uložení zůstávají samostatné; Back nesmí aktivovat uložení, ukončit relaci ani odebrat zařízení. Neuložený editor má explicitní Zrušit na stejném místě.
- TV: každá akce musí mít viditelný fokus (kontrastní cyan obrys), odlišný od trvalého výběru a disabled stavu, být dostupná přes D-pad/OK, bez slepých uliček. Vnořený dialog obnoví fokus rodiče. Textový obsah čitelný z běžné vzdálenosti.
- TV 100 % = schválené kompaktní měřítko (0,66), ostatní volby 80–120 %. Jedna lokální preference; žádný vliv na titulky, OCR souřadnice, uložené profily, síťové služby nebo telefon. Při zvětšení se prvky nepřekrývají. Kompaktní mřížky 2–3 sloupce, ne zbytečně široké řádky. Krátké nabídky bez posunu; u dlouhého obsahu (licence, historie, seznamy) je posun legitimní, jen uvnitř obsahu. Bez oříznutí nebo zmenšení písma pod čitelnost.
- Telefon: bezpečné okraje, portrait/landscape podle funkce, systémové velikosti a přístupnost, dotykové cíle; modal i push navigace mají stejné významy. Nápověda popisuje aktuální obrazovku, ne obecný text všude.
- Všude CZ/SK/EN, první jazyk podle systému jinak EN; skutečné jméno zařízení, text hry, diagnostické technické hodnoty a licence se nepřepisují. Systémové souhlasy, klávesnice, OAuth/web jsou výjimky z vlastního layoutu, ne z funkčního testování.

## 2. Zjištěné vady a změny

| ID | Závažnost | Důkaz / příčina ve zdroji | Oprava nebo další kontrola |
|---|---|---|---|
| UX-01 | P0 | Fotografie TCL: karta licence nemá jasný fokus. `GameLingoVisualKit.deviceCard` měla kliknutí bez focus stylu. | Sdílený focus overlay neodstraňuje původní background ani trvalé selected stavy; vlastní karty a potomci společných stránek jej používají. Nutná fyzická kontrola všech ovladačů. |
| UX-02 | P1 | Fotografie TCL: široké řádky, posun skrývá hlavičku. About měla vlastní scroll a čtyři velké karty. | Kompaktní 2×2 mřížka, společná pevná hlavička, dlouhé licence samostatně. Ověřit 80/100/120 %, všechny jazyky a různé poměry stran. |
| UX-03 | P0 | Rozdílný Back/Help v `GameLingoTVUi.page`, TV nastavení, jazycích, překladu, OCR, správě zařízení, párování, editoru a iOS sheetech. | Sdílená symetrická hlavička a mountPage, iOS GLBackButton. Přesunutí Back v párování nemění explicitní Stop. Ověřit všechny cesty Back a nápovědy. |
| UX-04 | P1 | Android průvodce měl Back ve footeru a celý obsah včetně hlavičky scrolloval. | Fixní hlavička a footer, posouvá se jen tělo. Ověřit malý displej a klávesnici. |
| UX-05 | P0 | `GameLingoTVProfiles.show` zavírala seznam před potvrzením mazání i před pokusem o načtení. Zrušení vracelo jinam, chybějící profil mohl vyhodit výjimku. | Seznam zůstává až do potvrzené změny, bezpečné optJSONObject, editor vrací do seznamu. Ověřit zrušení/smazání/načtení/obnovu profilu a slovníku. |
| UX-06 | P1 | Vlastní podstránky mají různá měřítka, mnoho zděděných Android/TV obrazovek se generuje až při buildu. | Sdílené menuScale a statické kontroly generovaného zdroje; zbývá inventář a vizuální průchod skutečného APK/IPA. |
| UX-07 | P1 | iOS `GLAutomaticZonesView` používá List/Form; část pomoci je uvnitř karet a některé nativní sheet/PS/QR cesty mají jiné UI. | Sjednocené akce Back v auditovaných sheetech; zbývá ověřit navazující editory, VoiceOver, texty a zachování skutečného uložení. |

P0 = brání použití/navigaci nebo může ztratit funkci, P1 = blokuje kvalitu vydání. Stav opravy ve zdroji není fyzická akceptace.

## 3. Inventář akceptace po stránkách a funkcích

Každý níže uvedený řádek vyžaduje skutečný průchod v CZ/SK/EN, správnou navigaci, fokus/dotyk, čitelnost a funkční výsledek. Pro TV 80/100/120 %; pro telefony alespoň běžný a menší displej, podporované orientace. Neexistuje fyzický PASS tohoto kandidáta.

| Oblast / podstránky | TV zdroj | Android zdroj | iOS zdroj | Povinný test |
|---|---|---|---|---|
| První spuštění, výběr platformy/jazyků/výstupu, přeskočení | TVUi.showWelcome, generovaný MainActivity | GameLingoFirstRun | GameLingoSetupView | Bez falešného připojení, návrat kroku, nastavit později |
| Domů, stav, začít/zastavit | polish_all_screens, direct_tv_mode | redesign_gamelingo, MainActivity | GameLingoHome | Start a stop, chyby, skutečný stav relace |
| Zařízení, více konzolí, výběr/přejmenování/odebrání | GameLingoDeviceCenter | GameLingoDeviceCenter | GameLingoDevicesPanel | Uchování všech zařízení, zrušení bez změny |
| Xbox přihlášení, seznam, připojení | direct_tv_mode, android_multi_xbox | android_multi_xbox | GameLingoXboxScreen, XboxAuthorization | Úspěch i chyba přihlášení, více Xboxů, Remote Play |
| PS nastavení a registrace | zděděná MainActivity/PS, konzole | zděděné PS flow | GameLingoPlayStationSetup | Stav dostupnosti pravdivý; netvrdit podporu bez testu |
| Telefon→TV, PIN, QR, seznam telefonů, odpojit, test překryvu | redesign_gamelingo, IPhoneTVPairing, QRDisplay, tv_navigation | GameLingoTVPairing, QRScanner | GameLingoTVSetupScreen, GameLingoQRScanner, PlayCaptionTV | Platnost PIN, QR, chybný PIN, druhý telefon, HDMI, Back ≠ Stop |
| TV samotná, souhlas, zdroje, běh, pauza, diagnostika | GameLingoScreenTranslationService | n/a | n/a | MediaProjection na podporovaném zdroji, DRM omezení, stop a uvolnění prostředků |
| Nastavení a jazyky | GameLingoTVSettings, Languages | GameLingoSettings, Languages | SettingsHomeView, GameLingoAppLanguage | Okamžitá změna jazyka, preference po restartu |
| OCR standard/auto, pokročilé filtry a časování | AutomaticConfig, OCRSettings, Settings | stejné + automatic_android | GLAutomaticZonesView, SettingsHomeView, CaptionPipeline | Výchozí standard, jedna automatická zóna, nezávislé filtry |
| Oblasti a editory, náhledy a ukládání | DirectTVLayout, AutomaticConfig, OrangeOutput | Regions, SubtitleAppearance, upstream TelefonActivity | SubtitleSettingsView, GLAutomaticZonesView | Posun, velikost, zamknutí, přidání/odebrání zón, zrušení, náhled |
| Vzhled titulků/rozhraní | TVCaptionStyle, TVUi | SubtitleAppearance, PhoneSubtitleStyle | GameLingoSubtitleAppearance, SettingsHomeView | UI měřítko bez změny subtitle scale, barvy, pozice |
| Profily, slovník, načtení, mazání, editace | TVProfiles, GlossaryUi | Profiles, GlossaryUi | SettingsHomeView, GameGlossary | Uložit/načíst/odstranit/zrušit, bez účtů v profilech |
| Historie, cache, diagnostika, testy překryvu | HistoryUi, TranslationCache, ScreenTranslationService | HistoryUi, TranslationCache, Diagnostics | SettingsHomeView, LocalDeviceDiagnostics | Volitelnost, čištění, bezpečné logy, chyby sítě |
| Nápovědy, About, licence, aktualizace, stažení | Help, About, TVUpdater, QRDisplay | Help, About, wire_mobile_update | GLHelpButton, GameLingoUpdates, GameLingoLicenseView | Fokus, návrat, dostupnost licence offline, ověření podpisu/checksum, upgrade |
| Systémové výzvy, oprávnění, klávesnice, OAuth | generated activities + system | generated activities + system | system sheets | Zamítnutí, zrušení, retry, žádné uváznutí |

Přesné zděděné Android/TV obrazovky se inventarizují z build artefaktu, ne pouze z názvů skriptů. `audit_tv_ui.py` kontroluje generované entry pointy a dialogy, nikoli vykreslený obraz. Dosavadní testy nejsou kompletní UI automatizací.

## 4. Důkaz a pravidlo vydání

- Výchozí revize tohoto auditu: `b7776cbf6f8433d0b319c0d1902fe967fe7d788e`. [Rychlá regrese 45843616034](https://github.com/CaseyCZ/GameLingo/actions/runs/45843616034) prošla; nativní sestava 45843616071 byla při kontrole stále rozpracovaná. Předchozí revize aa3308eb/ad5876ac neprošly starými zdrojovými asercemi o umístění nápovědy a full-screen režimu; aktualizované aserce ověřují novou společnou navigaci. To nenahrazuje test skutečného obrazu.
- Nová revize musí znovu projít CI včetně kompilace a validace balíčků. Jakákoli odlišná revize není aktuální kandidát. G1/G2/G3 neuzavírat bez průchodu matice; G4/G5/G6/G7 dle RELEASE_CONTROL.md. Veřejný release pouze se schválením.
- Použity postupy agency-roster UX architect (společné tokeny a API komponent), UI designer (konzistence, čitelnost), accessibility auditor (fokus, obnova fokusu, ruční test), test engineer (regrese, scénáře a evidence). Webové frameworky a placené služby nejsou pro nativní aplikaci vhodné; nepoužity.

## Doplňující cílená revize profilů a editoru zón (26. 11. 2026)
- Android mobil: stránka profilů používala vlastní scroll včetně hlavičky a Zavřít dole. Nahrazena společným mountPage s fixní hlavičkou Zpět/Nápověda; nečitelný JSON profilu hlásí lokalizovanou chybu.
- TV: výběr profilu při startu nyní kontroluje busy stav, index a aktuální JSON bezpečně, místo getJSONObject nad starým snapshotem. Poškozený nebo mezitím odebraný profil nespustí relaci.
- iOS: editor automatické zóny má inline nadpis a kontextovou nápovědu, nativní push Back zůstává zachován. Uložení nadále explicitní.
- Regresní aserce pokrývají tyto kontrakty, nikoli vykreslení; nové CI a fyzické ověření zůstávají nutné. Metodika agency-roster code reviewer, UX architect, test engineer.
