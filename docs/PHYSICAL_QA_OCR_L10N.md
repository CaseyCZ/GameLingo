# GameLingo — akceptační test OCR a lokalizace

Datum přípravy: 2026-11-26. Větev: `feature/ocr-lab`. Kandidát zdrojového kódu: `27de878c0183ab8cde112fda3624a3cb256de338`.

**Všechny níže uvedené fyzické testy jsou NEOTESTOVÁNY.** Úspěšná kompilace a integrační testy nejsou důkazem funkčnosti na skutečném zařízení. Tento doplněk navazuje na [PHYSICAL_QA_0.5.md](PHYSICAL_QA_0.5.md).

- [Nativní sestavení kandidáta](https://github.com/CaseyCZ/GameLingo/actions/runs/45829389593)
- [Kontrola TV navigace](https://github.com/CaseyCZ/GameLingo/actions/runs/45829389643)

## Prostředí a evidence

iPhone, Android telefon, TCL C835 / Google TV, Xbox Series X; druhá konzole či PS5/PS4 podle dostupnosti. Zapisuj model, OS, commit, jazyk rozhraní, režim OCR, zdroj obrazu a výsledek PASS / FAIL / BLOCKED. Do veřejného logu nevkládej PIN, token, IP ani identifikátory zařízení. Testovací APK musí mít stejný podpis jako instalovaná předchozí testovací verze, pokud se ověřuje aktualizace. IPA je nepodepsaná a před instalací vyžaduje odpovídající podpis. Nepoužívej produkční release k ověření kandidáta.

## Akceptační scénáře

| ID | Postup | Očekávané chování | Stav |
|---|---|---|---|
| L10N-01 | Čistá instalace Android/TV v češtině, slovenštině, angličtině a nepodporovaném jazyce | Podporovaný systémový jazyk je vybrán automaticky, jinak angličtina; všechny viditelné položky odpovídají jazyku | NEOTESTOVÁNO |
| L10N-02 | Změň jazyk aplikace během nastavení, restartuj | Jazyk zůstane uložený; jazyk hry a překladu se nezmění; ikony a funkce zůstanou stejné | NEOTESTOVÁNO |
| L10N-03 | Projdi menu, dialogy, chyby, párování, nápovědu, profily, historii, cache, editor oblastí a aktualizaci v každém jazyce | Žádná neočekávaná čeština v EN/SK, žádný oříznutý text; vlastní názvy profilů a skutečný obsah překladu zůstávají beze změny | NEOTESTOVÁNO |
| UI-01 | Telefon na výšku i na šířku, TV při 720p a 1080p | Nadpisy a karty jsou vycentrované; tři řady hlavních nastavení TV se vejdou při běžné velikosti písma; nic nepřekrývá fokus | NEOTESTOVÁNO |
| UI-02 | Ovladačem projdi všechna menu, vstup do podnabídky a Zpět | Fokus je viditelný, žádné slepé uličky; návrat ze stránky neukončí aplikaci | NEOTESTOVÁNO |
| UI-03 | Čisté první spuštění bez vytvořeného profilu | Standardní OCR, žádný zbytečný výběr prázdného seznamu profilů; nastavení zařízení lze odložit | NEOTESTOVÁNO |
| UI-04 | Přepínej standardní/automatický OCR režim | Stav v kartě se aktualizuje okamžitě a přetrvá restart | NEOTESTOVÁNO |
| OCR-01 | Standardní režim na stejné scéně jako 0.5.1 | Původní chování a poloha překladu beze změny; nečte mimo vymezenou oblast | NEOTESTOVÁNO |
| OCR-02 | Zapni automatický režim | Jedna výchozí zóna; lze přidat, odebrat a vypnout další zóny; nejvýše 10 zón a 32 bloků | NEOTESTOVÁNO |
| OCR-03 | Dvě nezávislé zóny s měnícími se dialogy | Aktualizace jedné zóny nesmaže druhou, starý text zmizí, stejné bloky se neduplikují; vlastní překryv není znovu rozpoznáván | NEOTESTOVÁNO |
| OCR-04 | Uprav geometrii, vytvoř a načti profil, restartuj | Souřadnice a stav zón se zachovají; názvy a hodnoty v editoru jsou lokalizované | NEOTESTOVÁNO |
| DEV-01 | Xbox Remote Play na iPhone i Android, vyber konkrétní konzoli | Skutečný přenos obrazu a OCR; žádné označení připojeno před potvrzením, žádný požadavek na mikrofon pro OCR | NEOTESTOVÁNO |
| TV-01 | Spáruj telefon s TV přes QR a PIN, ověř připojení a odeber zařízení | Platný kód funguje, neplatný/vypršený se odmítne; odebrané zařízení se samovolně nevrací | NEOTESTOVÁNO |
| TV-02 | Xbox na HDMI, překlad z telefonu | Překryv funguje pouze na TV, která jej dovoluje; pozice odpovídá zóně telefonu, staré titulky se odstraní | NEOTESTOVÁNO |
| TV-03 | Samostatný TV OCR na podporované aplikaci a DRM zdroji | Podporovaný obraz se čte; nepřístupný/chráněný obraz vyvolá srozumitelný stav, ne falešný překlad | NEOTESTOVÁNO |
| STAB-01 | 30 minut reálného hraní, Wi-Fi výpadek, stop/start, změna hry | Bez pádu, starých titulků nebo přetečení front; zaznamenej latenci p50/p95 a případné chyby | NEOTESTOVÁNO |
| UPD-01 | Aktualizace předchozího kompatibilně podepsaného APK | Profily, jazyk, zóny a zařízení přetrvají; žádná nutnost mazat aplikaci | NEOTESTOVÁNO |

## Podmínky vydání

Neuzavírat lokalizaci jako úplnou, dokud nejsou zkontrolovány i generované obrazovky upstream aplikace, technické stavy, systémové dialogy a aktualizátor. Neměnit Master ani release 0.5.1 při této kontrole. Pro vydání musí projít nativní sestavení, navigace a příslušné fyzické scénáře. Neověřené platformy či zdroje označit výslovně.
