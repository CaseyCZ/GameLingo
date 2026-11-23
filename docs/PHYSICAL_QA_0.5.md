# GameLingo 0.5 — fyzický akceptační test

**Stav všech případů: NEOTESTOVÁNO NA HARDWARU.** CI ani sestavení IPA/APK tyto případy nenahrazují. Nepoužívej skutečné přihlašovací PINy, tokeny, PSN Account ID ani identifikátory konzolí v veřejných komentářích nebo diagnostice.

Pracovní větev: `feature/gamelingo-v05-platform-rebuild`, [PR #5](https://github.com/CaseyCZ/GameLingo/pull/5). Zapisuj pro každý test datum, zařízení, sestavení / SHA, výsledek **PASS / FAIL / BLOCKED** a případně odkaz na redigovaný snímek obrazovky. Po jakékoli změně kódu zkontroluj, zda test nevyžaduje opakování.

## P0: rozhraní, průvodce, zařízení

| ID | Test / postup | Co musí nastat | Výsledek |
|---|---|---|---|
| UI-01 | Čistá instalace iPhone a Android; otevři aplikaci | Průvodce Xbox/PlayStation → Telefon/Google TV → párování; vlastní GameLingo grafika, žádné původní nabídky | NEOTESTOVÁNO |
| UI-02 | V průvodci zvol „Nastavit později“, ukonči a spusť aplikaci znovu | Průvodce se neopakuje, Domů ukazuje nenastavená zařízení a nabídku Přidat | NEOTESTOVÁNO |
| UI-03 | Přihlas účet, přidej konzoli, ukonči a spusť aplikaci znovu | Výchozí konzole i výstup se zachovají, ale „Nastaveno“ není předstírané „Připojeno“ | NEOTESTOVÁNO |
| UI-04 | Projdi všech pět záložek na iPhone a Android | Domů, Oblasti, Profily, Zařízení, O aplikaci ve stejném pořadí; tlačítka vedou do skutečných obrazovek | NEOTESTOVÁNO |
| DEV-01 | Přidej dva Xboxy a vyber druhý | Home i Remote Play používá **druhý** Xbox, nikoli první ve výpisu; výběr přetrvá restart | NEOTESTOVÁNO |
| DEV-02 | Přidej dvě PlayStation; vyber jednu, odeber druhou, obnov | Odebraná se neobjeví znovu, klíče odstraněné PS se nedají znovu použít, novou lze explicitně znovu spárovat | NEOTESTOVÁNO |
| DEV-03 | Přidej dvě Google TV, vyber druhou, odeber první, restartuj | Zvolená TV zůstává; odstraněná se nevrátí ze staré uložené IP | NEOTESTOVÁNO |
| DEV-04 | Během běžícího překladu zkus změnit nebo odebrat konzoli | Změna se bezpečně odmítne, proud dat nekončí neočekávaně, žádná jiná konzole se samovolně nespustí | NEOTESTOVÁNO |

## P1: skutečný Remote Play, OCR, překlad a TV

| ID | Test / postup | Co musí nastat | Výsledek |
|---|---|---|---|
| RP-01 | iPhone + Xbox: Microsoft device-code login, připojení a odebrání relace | Žádné uvíznutí přihlašování; stream snímků a OCR začnou, ruční Stop je okamžitý | NEOTESTOVÁNO |
| RP-02 | iPhone + PS5: registrace přes Sony a osmimístný registrační PIN | PS5 se spáruje, uloží klíče do Keychain a nabídne zdroj videa; nikdy ji neoznačí za spojenou před přijetím streamu | NEOTESTOVÁNO |
| RP-03 | iPhone + PS4: připojení a přehrávání | H.264 dekódování, přijaté snímky, překlad dialogů | NEOTESTOVÁNO |
| RP-04 | Android + PS5/PS4 a Android + Xbox | Dřívější funkční připojení zůstává funkční po redesignu; výběr více konzolí správný | NEOTESTOVÁNO |
| OCR-01 | Na jedné scéně zapni všechny čtyři oblasti | Modrá čte dialogy, zelená zobrazuje jejich český překlad; žlutá čte herní texty, oranžová je vykresluje | NEOTESTOVÁNO |
| OCR-02 | Změň pozice, velikost, zamknutí; ulož a načti profil | Všechny oblasti zůstanou nezávislé a respektují uložené nastavení | NEOTESTOVÁNO |
| OCR-03 | Přepni výstup na telefon, pak na TV; přepni dvě TV | Telefon neposílá staré titulky na TV; při změně TV se staré překryvy smažou | NEOTESTOVÁNO |
| OCR-04 | Znovu spusť překlad rychle po předchozí relaci | Asynchronní překlad z předchozí konzole se neobjeví v nové relaci | NEOTESTOVÁNO |
| TV-01 | Google TV / Android TV: přepínej obě úvodní volby šipkami ovladače | Viditelný focus se pohybuje šipkami vlevo/vpravo a Enter spustí správný režim | NEOTESTOVÁNO |
| TV-02 | Režim Překlad z telefonu: spáruj šest číslic, spusť hru přes HDMI | Skutečné české titulky se zobrazují nad obrazem HDMI; potvrzení spojení, nikoli jen UDP send | NEOTESTOVÁNO |
| TV-03 | Reinstaluj testovací TV APK a zopakuj párování | Párování se obnoví až po novém ověření, bez porušení originálního PlayCaption přijímače | NEOTESTOVÁNO |
| STAB-01 | Hraj nepřetržitě alespoň 30 minut, přerušuj Wi-Fi a návrat z pozadí | Aplikace nepadá, umí bezpečně zastavit/restartovat a hlásí reálný stav, nikoli falešné „online“ | NEOTESTOVÁNO |

## P2: instalace, nezávislost a vydání

| ID | Test / postup | Co musí nastat | Výsledek |
|---|---|---|---|
| REL-01 | Vedle originálních PlayCaption IPA/APK nainstaluj GameLingo | Vlastní bundle/application ID, originál není přepsaný | NEOTESTOVÁNO |
| REL-02 | Ověř všechna tři APK/IPA z jednoho commitu, checksum a verze | Shodné SHA, soubory otevřitelné, appName GameLingo, Android debug APK označená TEST | NEOTESTOVÁNO |
| REL-03 | Zkontroluj práva, logy a závislosti | Žádné Xbox/PSN tokeny v plain textu či veřejných logách; AGPL-3.0 a zdrojové kódy dostupné | NEOTESTOVÁNO |

## Jak zaznamenat výsledek

Příklad: `TV-02 | FAIL | TCL C835 / HDMI 2 | v0.5.0-dev / SHA ... | 6místné párování OK, ale overlay po přepnutí vstupu zmizel | redigovaný snímek / GitHub issue`.

**Release gate:** žádné „hotovo“, žádný merge do Master, žádné stabilní vydání, dokud povinné případy nejsou PASS nebo explicitně dokumentovaná nepodporovaná konfigurace. Ověření pouze skriptem označit `CI PASS / HW NEOTESTOVÁNO`.
