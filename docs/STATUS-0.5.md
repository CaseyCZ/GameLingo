# GameLingo — aktuální stav vývojové větve

Aktualizace 2026-11-26. Pracovní větev `feature/ocr-lab`, Master a veřejné vydání 0.5.1 zůstávají nedotčené. Hlavní autoritativní přehled vydání je [RELEASE_CONTROL.md](RELEASE_CONTROL.md). Zdrojová revize posuzovaná v tomto checkpointu: `968e4808b2ab433b93c57c517bc64911134922c4`. [Rychlá regrese 45844249821](https://github.com/CaseyCZ/GameLingo/actions/runs/45844249821) prošla. [TV kontrola 45844249886](https://github.com/CaseyCZ/GameLingo/actions/runs/45844249886): generovaný zdroj, navigace a inventář 112 Kotlin souborů/176 UI referencí prošly; kompilace skončila na HTTP 429 při stahování Maven závislostí před kompilací. [Úplný nativní build 45844249987](https://github.com/CaseyCZ/GameLingo/actions/runs/45844249987) v době kontroly běžel. Starší úspěšné buildy nejsou aktuální release kandidát. Fyzické testy tohoto kandidáta nemají PASS.

| Funkce | iOS | Android mobil | Google TV |
|---|---|---|---|
| Xbox / PlayStation | Implementované cesty Remote Play; kandidát čeká na HW | Implementované cesty Remote Play; kandidát čeká na HW | Přijímač z telefonu; přímé konzolové cesty čekají na HW |
| OCR | Vision | ML Kit OCR | ML Kit OCR při podporovaném přímém snímání; telefonní receiver OCR nedělá |
| Online překlad | Google GTX / Microsoft Edge / MyMemory | Stejné | Stejné |
| Offline překlad nové věty | Ne | Ne | Ne |
| Cache | Lokální SQLite s opt-out a mazáním | Stejné | Stejné |
| Režimy OCR | Standardní výchozí, automatický volitelný | Stejné | Stejné pro přímý OCR režim |
| Oblasti | Čtyři nezávislé standardní oblasti; automatické zóny | Stejné | Přijímač a samostatný režim; geometrie vyžaduje HW kontrolu |
| Párování | PIN/QR a HMAC | PIN/QR a HMAC | Přijímač s více uloženými telefony, jeden aktivní zdroj |
| Historie a slovník | Volitelná historie, slovník profilu | Stejné | Stejné; funkční parita čeká na HW |
| UI jazyky | CZ/SK/EN | CZ/SK/EN | CZ/SK/EN |
| HDMI překryv | Odesílá titulky do TV | Odesílá titulky do TV | Jen při kompatibilním firmware a povoleném překryvu |
| Samostatná TV | n/a | n/a | MediaProjection, souhlas systému, OCR dostupného obrazu; chráněný obraz může být nedostupný |

Tabulka popisuje implementaci a rozsah, nikoli potvrzení funkčnosti každé kombinace. Úplná jazyková a vizuální kontrola zůstává povinná dle [UI auditu](UI_UX_FUNCTIONAL_AUDIT.md). HMAC chrání integritu, nikoli důvěrnost; prvotní PIN/secret bootstrap zůstává nešifrovaný. Starý PCS1 import konzolového účtu byl odstraněn, přímé síťové ověření čeká; nepřenášet účty mezi zařízeními. Neoficiální online API nemají doloženou autorizaci. Bezpečnostní a licenční podmínky se neuzavírají samotným buildem. Nepřidávat nové platformy ani funkce.

## Dokumentace
- [Řízení vydání](RELEASE_CONTROL.md), [UI a funkční audit](UI_UX_FUNCTIONAL_AUDIT.md), [fyzická akceptace](PHYSICAL_QA_OCR_L10N.md).
- [Návod pro testery](USER-GUIDE-0.5.md), [soukromí (pracovní návrh)](PRIVACY-0.5.md).
- [Bezpečnost](SECURITY-PRIVACY-AUDIT-0.5.md), [licence a poskytovatelé](LICENSE-AND-PROVIDER-AUDIT.md), [atribuce](../ATTRIBUTION.md).
- [Historické výsledky sestavení](BUILD_EVIDENCE_0.5.md), [podmínky vydání](RELEASE_GATES_0.5.md), [vydávání](RELEASES.md).

Další postup: dokončit aktuální CI, uzavřít ověřitelné nálezy, připravit jediný kandidát se shodnou revizí a prokazatelnými podpisy, provést fyzické testy, získat schválení. Žádný veřejný release bez schválení.
