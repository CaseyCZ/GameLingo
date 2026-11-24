# GameLingo 0.5 — návod pro testery
Vývojová verze, nikoli potvrzené stabilní vydání. Používej pouze balíček předaný pro QA s uvedenou zdrojovou revizí a kontrolním součtem. [Aktuální stav a omezení](STATUS-0.5.md).

## Instalace
- iPhone: unsigned IPA vyžaduje vlastní platný podpis a kompatibilní instalační nástroj; prosté otevření IPA nestačí.
- Android telefon: mobilní TEST APK, identita cz.caseycz.gamelingo.mobile.
- Google TV / Android TV: TV TEST APK, identita cz.caseycz.gamelingo.tv. Povol instalaci z konkrétního důvěryhodného zdroje jen na dobu instalace. Debug podpis může vyžadovat reinstalaci; ta může smazat lokální nastavení. Nepřepisuj původní PlayCaption.
- Překryv na TV: povol GameLingo zobrazování přes jiné aplikace, pokud to systém nabízí. Na TCL/Android TV se cesta v nastavení liší podle firmware. Nepovoluj nesouvisející oprávnění.

## Překlad přes telefon
1. Zapni konzoli, povol Remote Play podle nastavení konzole, připoj telefon a konzoli do domácí sítě.
2. V GameLingo vyber jazyky hry a překladu, Xbox nebo PlayStation a výstup Telefon nebo Google TV. Xbox vyžaduje Microsoft přihlášení; PlayStation registraci a konzolový PIN dle průvodce. Vyber správnou konzoli.
3. Pro výstup TV otevři GameLingo na TV a režim Překlad z telefonu. Povol překryv. Vyber TV v telefonu, naskenuj její párovací QR nebo zadej šest číslic. Ověř živé spojení; uložená TV není automaticky připojená.
4. Spusť překlad. Na konzoli zapni originální titulky. V editoru nastav čtecí a výstupní oblasti. Při HDMI režimu přepni na vstup konzole; kompatibilita závisí na TV.
5. Po hraní zastav překlad; při změně konzole/TV zastav relaci a vyber nový zdroj.

## Přímý překlad na Google TV
Zvol Přímo na TV, nastav konzoli a jazyky a postupuj podle nativního průvodce. TV má vlastní OCR a online překlad z dostupné Remote Play cesty, nikoli univerzální zachytávání libovolného HDMI/chráněného videa. Nemá offline model. Bez internetu může použít existující cache, nikoli zaručeně přeložit nový text. Přímý story/modrá oblast mají otevřená omezení. Telefonní režim zůstává samostatnou cestou.

## Čtyři oblasti
| Barva | Účel |
|---|---|
| Modrá | Čtení originálních dialogů |
| Zelená | Zobrazení překladu dialogů |
| Žlutá | Čtení herních voleb, textů a nabídek |
| Oranžová | Zobrazení přeložených voleb a textů |

Změna oranžové oblasti nemá měnit žlutou OCR oblast. Nezávislé posílání oranžové geometrie z Androidu a úplná parita profilů jsou otevřené nálezy; hlaste rozdíl, neobcházejte jej změnou čtecí oblasti. Ulož profil a ověř po restartu.

## Když něco nefunguje
- Zkontroluj stejnou důvěryhodnou domácí síť, nikoli veřejnou Wi-Fi/guest izolaci. Neotvírej UDP porty do internetu. Port fallback 41989–41993 je implementován, HW čeká.
- Vytvoř čerstvý PIN, ověř IP/QR a správné zařízení. Po změně IP může být nutné nové párování. Lokální zapomenutí neznamená revokaci na TV; odeber daný telefon v TV nastavení.
- Je-li připojeno více telefonů, zastav původní zdroj, než začne vysílat druhý. TV podporuje jeden aktivní zdroj.
- Při chybě překladače ověř internet a jazyk; služby mají limity. Nepředpokládej neomezený bezplatný překlad. Žádná platforma nepřekládá nové texty offline. Již přeložené texty mohou být dostupné v lokální paměti.
- Netflix a jiný chráněný obsah nejsou ověřeny; žádný příslib OCR chráněného přehrávání. Pokud systém překryv blokuje, neobcházej ochrany.

## Bezpečnost a hlášení
Používej vlastní testovací konzoli/účty a důvěryhodnou LAN. Přenos titulků je podepsaný, ale nešifrovaný, PIN bootstrap a starší account transfer mají otevřená rizika. Preferuj samostatné přihlášení místo přenosu účtu. [Soukromí](PRIVACY-0.5.md). Do hlášení uveď model, OS, verzi/SHA, režim, kroky a výsledek, ale odstraň PIN, token, soukromé IP, identifikátory účtu a citlivý text. [QA scénáře](PHYSICAL_QA_0.5.md).
