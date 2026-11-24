# GameLingo 0.5 – blokující kontroly před veřejným vydáním

Tento soubor je pracovní kontrolní seznam. **CI build není potvrzení funkčnosti herní konzole ani kvality UI.** Dokud všechny povinné položky nemají záznam z reálného zařízení, zůstává 0.5 ve vývojové větvi; nemergovat do `Master` ani nevydávat jako stabilní Release.

## Vlastní identita a kompletní UI
- [ ] iPhone: úvodní průvodce volí Xbox / PS5 / PS4 a výstup Telefon / Google TV.
- [ ] iPhone: Domů, Zařízení, Přidat / Odebrat / Přejmenovat / Použít, Xbox, Sony, Google TV, OCR, 4 oblasti, profily, titulky, diagnostika a Nápověda vizuálně odpovídají GameLingo.
- [ ] Android: stejná struktura a značkový vzhled na telefonu; po prvním spuštění výběr platformy a výstupu.
- [ ] Google TV: ovládání dálkovým ovladačem, bezpečné focus stavy, režim z telefonu / přímo na TV, párovací PIN a diagnostika.
- [ ] Bez starých log PlayCaption/TCL v uživatelském UI kromě požadované právní atribuce a pojmenované kompatibility.
- [ ] Běžná UI bez dlouhých vysvětlení; pomoc pod otazníkem (?) dostupná a srozumitelná.

## Skutečná funkčnost konzolí
- [ ] Xbox iOS: Microsoft login, seznam všech Xboxů, výběr konkrétního zařízení, připojení, snímky do OCR, odpojení bez zamrznutí.
- [ ] Xbox Android: funguje výběr více Xboxů a používá správně zvolený, nikoliv první nalezený.
- [ ] PS5 iPhone: Sony přihlášení, párování osmimístným PINem, klíče v Keychain, nalézání PS5, VideoToolbox vykreslí validní snímky do OCR, přeložené věty ve hře.
- [ ] PS4 iPhone: totéž se správným H.264 profilem.
- [ ] PlayStation iPhone: případný uživatelský PIN, přerušení spojení, restart relace, odchod na pozadí, stavové hlášky.
- [ ] PS5/PS4 Android: stávající Chiaki engine, přihlášení a párování zachované; více konzolí bezpečně ukládá šifrované klíče.
- [ ] Konzole spárovaná, ale offline, se nikdy neoznačuje jako online ani „připravena“.

## Překlad a oblast obrazovky
- [ ] Čtyři oblasti: modrá vstup dialogů, zelená výstup dialogů, žlutá vstup herních textů, oranžová výstup herních textů.
- [ ] Překlad dialogů i herních textů současně, bez ztráty krátkých vět, bez automatických agresivních filtrů.
- [ ] Výstup na iPhone: jak české dialogy, tak přeložené volby/texty, bez nevyžádaného odesílání na TV.
- [ ] Výstup na Google TV: normální bílé titulky na černém podkladu i nezávislý oranžový blok; polohy, velikosti písma, zamknutí a uložení do profilů.
- [ ] Párování přes šest číslic, obnovení po přeinstalaci TV APK, změna IP, párování více TV, odpojení a znovupřipojení.
- [ ] Překryv titulků nad HDMI ověřen na podporované TV; neslibovat univerzální podporu všech Android TV boxů.

## Stabilita, bezpečnost a distribuce
- [ ] CI iOS IPA, Android APK, Google TV APK a SHA-256 bez chyb na **téže revizi**.
- [ ] Testy modelů zařízení, přípustných stavů, přepínání výstupů a absence falešných placeholderů.
- [ ] Test během delšího hraní: OCR zpoždění, spotřeba baterie, paměť a příjem titulků.
- [ ] Přihlašovací klíče a Sony/Microsoft tokeny se neukládají do běžných nastavení ani nevypisují do logů.
- [ ] Zdrojové kódy nativního Chiaki jádra, build postup a AGPL atribuce zůstávají veřejně dohledatelné.
- [ ] Nezávislá instalační ID; původní PlayCaption 0.3.2 a TV Orange V5 zůstávají nedotčené.
- [ ] Technická dokumentace mimo veřejné README; README pouze s přehledem funkcí a pravdivě označenou dostupností.

## Pravidlo vydávání

Vývojový základ: `feature/gamelingo-v05-platform-rebuild`; aktuální audit: `feature/audit-0.5`. Nešířit jako hotové aplikace pouze proto, že proběhl build. Testovací APK/IPA určené k fyzické kontrole smí být označeny jako **dev/QA**, ne jako stabilní verze. Po dokončení povinných kontrol navázat na aktuální společný zdrojový commit, vygenerovat všechny tři instalační soubory a až potom provést release.

## Dodatečné blokující body z auditu F0–P3
- [ ] Vyjasněn předchozí písemný souhlas Google pro použití ML Kit on-device Translation na TV nebo odstraněna nepovolená TV integrace (včetně preload/quick/story a závislostí). Viz [P2.5](OFFLINE-TRANSLATION-DESIGN.md). Do té doby nepublikovat TV APK ani jako veřejný pre-release.
- [ ] Ověřeny podmínky, soukromí a atribuce online providerů a ostatních SDK, odpovídající AGPL zdroj a notices. Stabilní release podpis, identita, aktualizace a kontrolní součty.
- [ ] Fyzická kontrola HMAC/replay, QR/Keystore, více telefonů, odpárování, port fallback, story ACK a účtové cesty. Neuvádět šifrované titulky; rozhodnout o riziku plaintext pairing v důvěryhodné LAN před širší distribucí.
- [ ] Fyzická kontrola všech P1/P2 implementovaných funkcí na téže revizi, doplněné případy v [PHYSICAL_QA_0.5.md](PHYSICAL_QA_0.5.md). Auditní P2.4 CI 45379906749 PASS, hardware čeká. Návrhy P2.2/P2.5/P3 nejsou vydávané funkce.

### Aktualizace — TV Translation SDK
- [ ] Dodatečná náprava v auditní větvi: odstraněna TV ML Kit Translation deklarace a lokální kód, zaveden build/DEX guard. Ověřit plný CI včetně skutečného TV a mobilního APK a zapsat výsledek do REPORT.md. Po CI zůstává kontrola dalších SDK, práv a HW; žádný veřejný release automaticky.
