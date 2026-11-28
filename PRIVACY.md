# GameLingo – Zásady ochrany osobních údajů / Privacy notice

Aktualizováno / Updated: 2026-11-28. Informace pro aktuální testovací distribuci z GitHubu. / Information for the current GitHub test distribution.

## Čeština

GameLingo je nezávislý open-source projekt CaseyCZ. Kontakt k projektu: [profil CaseyCZ](https://github.com/CaseyCZ) a [web projektu](https://caseycz.github.io/). Neposílejte osobní údaje, přihlašovací údaje ani snímky do veřejných Issues. Před vydáním v obchodech je nutné doplnit potvrzenou totožnost a neveřejný kontaktní kanál správce, právní základy, podmínky příjemců a případná mezinárodní předávání.

### Co aplikace zpracovává a proč

- Obraz z kamery, Remote Play nebo systémem povoleného snímání TV: místní OCR pro zjištění textu. Aplikace nepřekládá mikrofon ani zvuk. Diagnostický výřez může zůstat v paměti do vymazání nebo ukončení příslušné relace.
- Rozpoznaný text, zdrojový a cílový jazyk: podle režimu a dostupnosti se posílají přes HTTPS do Google, Microsoft nebo MyMemory pro překlad. Poskytovatelé při síťových požadavcích mohou získat IP adresu a provozní údaje; jejich skutečné doby uchování, právní role a případné předávání nejsou dosud ověřeny. Android mobil obsahuje také lokální ML Kit překlad, který může stahovat modely. TV offline překlad neobsahuje.
- Přihlášení Xbox/PlayStation: identifikátory účtu, autentizační a registrační údaje pro připojení ke konzoli. Nastavení, spárovaná zařízení a profily jsou uloženy místně; odpojení účtu a odebrání zařízení jsou dostupné v příslušných nabídkách. Přihlášení k externím účtům se řídí pravidly jejich poskytovatelů.
- Spárovaná TV: titulky, parametry zobrazení a údaje nutné k přenosu. Přenos je autentizovaný, nikoli šifrovaný; používejte pouze důvěryhodnou síť.
- Aktualizace: aplikace kontaktuje GitHub pro ověření a stažení vydání; GitHub při tom může zpracovávat síťové údaje.

### Místní uložení a ovládání

Překladová cache může uložit až 20 000 původních a přeložených textů; nejméně používané položky se postupně nahrazují. Není nastaven pevný časový limit. V Nastavení lze cache vypnout a odděleně vymazat. Vypnutí samo o sobě nemusí odstranit již uložené položky. Volitelná historie je ve výchozím stavu vypnutá, má nejvýše 200 záznamů a zůstává v paměti procesu. Vypnutí historie ji vymaže. Nastavení a profily zůstávají do změny či odstranění. Technická diagnostika je oddělená od historie a cache.

Snímání a překlad lze zastavit; používejte jen požadovanou oblast a vyhněte se citlivému obsahu. Pokud nechcete odeslat text externím službám, nepoužívejte online překlad. Místní mazání neodstraňuje případné záznamy u externích poskytovatelů.

### Externí služby a práva

[Google Privacy](https://policies.google.com/privacy) · [ML Kit terms](https://developers.google.com/ml-kit/terms) · [Microsoft Privacy](https://privacy.microsoft.com/privacystatement) · [MyMemory](https://mymemory.translated.net/) · [Sony privacy](https://www.playstation.com/legal/privacy-policy/) · [GitHub privacy](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

Podle použitelného práva můžete mít právo na přístup, opravu, výmaz, omezení, přenositelnost, námitku nebo odvolání souhlasu, pokud je zpracování založeno na souhlasu, a stížnost u dozorového úřadu ([ÚOOÚ](https://uoou.gov.cz/)). Pro údaje vedené externími poskytovateli využijte jejich kontakty. Tento dokument popisuje technicky zjištěné chování testovací verze, není potvrzením právní shody ani dokončenou store deklarací.

## English

GameLingo is an independent open-source project by CaseyCZ. Project contact: [CaseyCZ profile](https://github.com/CaseyCZ) and [project website](https://caseycz.github.io/). Do not post personal information, credentials or captures in public issues. Verified controller identity and private contact channel, legal bases, recipient terms and international transfer arrangements must be completed before store distribution.

- Camera, Remote Play and system-authorized TV capture: on-device OCR for text recognition. GameLingo does not translate microphone input or audio. A diagnostic crop may remain in memory until cleared or the relevant session ends.
- Online translation may send recognized text and language choices over HTTPS to Google, Microsoft and MyMemory. Network requests may expose IP addresses and operational metadata; provider retention, legal roles and transfers have not yet been verified. Android mobile also supports local ML Kit translation and may download models. The TV variant has no offline translation model.
- Xbox/PlayStation sign-in uses account identifiers, authentication and registration data to connect to consoles. Settings, paired devices and profiles are local; manage accounts and devices in their respective menus. External accounts are subject to their providers' terms.
- Paired TV subtitle transport carries subtitles and display/connection parameters. It is authenticated but not encrypted; use a trusted local network.
- Update checks and downloads contact GitHub, which may process network data.

The persistent cache holds up to 20 000 original/translated text entries and evicts least recently used items; no fixed time limit is configured. It can be disabled and cleared separately in Settings; disabling alone does not necessarily delete existing entries. Optional history is off by default, limited to 200 entries in process memory, and cleared when disabled. Settings and profiles remain until changed or removed. Technical diagnostics are separate.

Stop capture at any time and avoid sensitive content. Do not use online translation if you do not want text sent to external services. Local deletion does not remove records external providers may hold. Provider links are listed above. Depending on applicable law, users may have rights of access, correction, deletion, restriction, portability, objection, withdrawal of consent when applicable, and complaint to a supervisory authority. This notice describes observed behavior of the test distribution, not verified legal compliance or a completed store declaration.
