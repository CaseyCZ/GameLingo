# GameLingo 0.5 — protokol praktického testování (bod 7)

Datum založení: 2026-11-24. Stav: **BLOCKED — čeká na testera a fyzická zařízení**, nikoli potvrzený PASS. Neexistuje přístup k reálnému iPhone, Androidu, TCL nebo herní konzoli v tomto běhu. Dřívější testy PlayCaption/CaseyCZ/Test či jiné beta verze nelze přenést na aktuální revizi.

## Testovaná revize

- Aplikační zdroj `f34348b1cd721c83074bae2590e6047b0a076303`, [CI 45384321662](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662), všech šest jobů PASS. [Balíčky a checksumy](BUILD_EVIDENCE_0.5.md). Použít pouze tuto sadu, ne směs starších beta.
- iPhone: IPA unsigned, vyžaduje vlastní podpis; SHA-256 kontrolovat na původním souboru před podpisem. Android a TV: debug APK, případná reinstalace smaže nastavení. Ponechat originální PlayCaption jako zálohu, nepřepisovat a na běžný test vypnout jeho službu.
- Cílový TV hardware: TCL C835 (konkrétní firmware, Android verze a HDMI režim doplnit). iPhone a Android: model/OS zapsat při testu, Xbox/PS4/PS5 skutečně dostupné kusy. Nedostupné kombinace označit BLOCKED, nikoli PASS.
- Testovat v důvěryhodné domácí LAN s vlastními testovacími účty a neosobním herním textem. Neotevírat porty do internetu. Neprovádět starý přenos účtu s reálnými přihlašovacími údaji; bezpečnostní scénáře odděleně se syntetickými daty.
- Nezveřejňovat QA balíčky jako release. Licence/provider/security zůstávají otevřené.

## A. První průchod — prioritní scénáře

Každý řádek spustit pro uvedené kombinace, zapsat samostatný výsledek. Při chybě uložit redigovaný snímek/krátké video, přesný krok a pokračovat na nezávislém testu. Nezapisovat PIN/QR, tokeny, PSN ID, interní IP ani citlivý text.

| ID | Kde | Postup | Očekávání | Stav |
|---|---|---|---|---|
| SM-01 | iPhone, Android, TV | Ověř SHA původního souboru, instaluj, spusť, zapiš verzi a model | Samostatné identity; žádný pád, stávající PlayCaption nepřepsán | NOT RUN |
| SM-02 | iPhone, Android | Projdi průvodce, pět záložek a nastav později; restart | Správná navigace, přetrvání volby, žádný falešný online stav | NOT RUN |
| SM-03 | iPhone + Xbox | Vyber konkrétní Xbox (pokud jsou dva, druhý), připoj, přelož 5 dialogů, Stop | Správný stream a překlad, Stop odstraní překryv | NOT RUN |
| SM-04 | TV + telefon | Povol overlay, TV režim Překlad z telefonu, spáruj čerstvý PIN/QR, přepni TV na HDMI konzole | Překlad nad skutečným HDMI obrazem, ne pouze v aplikaci | NOT RUN |
| SM-05 | iPhone→TV, Android→TV | Nastav modrou čtecí a zelenou výstupní oblast; zobraz dialog, změň barvu, velikost a pozici výstupu | Změny se projeví na TV, čtecí oblast se nemění | NOT RUN |
| SM-06 | iPhone→TV, Android→TV | Žlutá OCR oblast nad herní volbou, zobraz oranžový náhled, posuň pouze oranžovou, změň font; opakuj s druhou volbou | Oranžový výstup na zvolené pozici, žlutý vstup nezměněn, nezmizí dialogy. Známé riziko CODE-03 | NOT RUN |
| SM-07 | iPhone, Android, TV | Ulož profil A; změň podporované jazyky, fonty a oblasti; ulož B; načti A/B, restart aplikace a zopakuj | Každý profil obnoví podporovanou konfiguraci, bez záměny čtecích/výstupních oblastí. CODE-02/07 | NOT RUN |
| SM-08 | TV + telefon | Na TV zastav receiver, spusť legitimní druhý lokální receiver na výchozím portu, restartuj GameLingo; spáruj a přelož | GameLingo nabídne funkční fallback v 41989–41993, nepoužije cizí receiver; nedostupnost jasně hlášena. Pokud není druhý receiver dostupný, BLOCKED | NOT RUN |
| SM-09 | iPhone + Android + TV | Spáruj oba telefony, ukonči vysílání prvního, spusť druhý; odpáruj první na TV | Druhý funguje; první nemůže po revokaci posílat titulky. Jeden aktivní zdroj | NOT RUN |
| SM-10 | Xbox / iPhone | Během navazování Remote Play stiskni Stop, zopakuj 3×, potom normální start | Bez osiřelé relace, stale překladu a zamrznutí; CODE-01 | NOT RUN |
| SM-11 | Android + Xbox/PS4/PS5, iPhone + PS4/PS5 | Každá dostupná konzole samostatně: registrace, live video, dialog, herní text, Stop a reconnect | Skutečné snímky a překlady; zapiš výsledek pro každý model; nedostupný model BLOCKED | NOT RUN |
| SM-12 | TV přímo | Přímý režim, každá dostupná konzole; čtení originálu, online překlad, ruční modrá/story a orange výstup | Funkční samostatné OCR a online překlad; zdokumentovat omezení CODE-04 | NOT RUN |
| SM-13 | TV přímo; Android mobil | U TV odpoj pouze WAN při zachované LAN, po zahřátí cache zkus starý a nový text; obnov WAN. Mobil s již dostupným modelem samostatně | TV cache hit možný, nový text bez online služby explicitně nepřeložen; žádný TV model download. Android offline fallback samostatně ověřit | NOT RUN |
| SM-14 | iPhone→TV, Android→TV, TV přímo | 30 minut běžného hraní; krátké i dlouhé dialogy, text zmizí, dočasný výpadek Wi-Fi a návrat | Zaznamenat zpoždění, ztracené titulky, stale overlay, pády, obnovu, teplotu a P95 pokud je dostupná | NOT RUN |

## B. Úplná matice

První průchod nenahrazuje [PHYSICAL_QA_0.5.md](PHYSICAL_QA_0.5.md) (UI-01–04, DEV-01–04, RP-01–04, OCR-01–04, TV-01–03, STAB-01, REL-01–03, AUD-01–17) ani C01–C15/H01–H10 z REPORT. Testy opakovat pro všechny deklarované platformy, firmware a konzole; nevyvozovat podporu jiných modelů z TCL C835.

| Cesta | Xbox | PS4 | PS5 |
|---|---|---|---|
| iPhone → telefon | NOT RUN | NOT RUN | NOT RUN |
| iPhone → TCL HDMI | NOT RUN | NOT RUN | NOT RUN |
| Android → telefon | NOT RUN | NOT RUN | NOT RUN |
| Android → TCL HDMI | NOT RUN | NOT RUN | NOT RUN |
| TV přímo | NOT RUN | NOT RUN | NOT RUN |

## C. Záznam skutečného výsledku

```
ID:
Datum:
Aplikační SHA / hash původního balíčku:
Telefon / OS:
TV / firmware / HDMI režim:
Konzole / model:
Cesta (telefon / HDMI receiver / přímo TV):
Výsledek: PASS / FAIL / BLOCKED
Očekávání:
Skutečnost:
Reprodukce (počet pokusů):
Zpoždění / diagnostika (pokud měřeno):
Redigovaná příloha:
Související CODE/SEC/CLEAN:
```

PASS = skutečně provedený test a splněná kritéria; FAIL = skutečně pozorovaný nesoulad; BLOCKED = chybějící hardware, podpis, kompatibilita nebo neuzavřená předpokladová kontrola; NOT RUN = nezahájeno. Nedostupný důkaz nepřevádět na PASS. Chyby přenést do bodu 8 s vazbou na existující auditní ID, po opravě nový build a opakovaná QA. Neoznačovat bod 7 jako dokončený před skutečnými výsledky. Bod 8 může řešit již prokázané statické nálezy nezávisle, pokud uživatel schválí tento postup; fyzická QA zůstane otevřená. Žádný merge/release před splněním release gates.

## D. Stav výsledků
K založení protokolu: 14 prioritních scénářů NOT RUN, 15 cest konzole/výstup NOT RUN. Žádný reálný FAIL ani PASS zaznamenán; známé statické nálezy jsou v [CODE-AUDIT-0.5.md](CODE-AUDIT-0.5.md), ne vymyšlené výsledky zařízení. Fyzické testy čekají.
