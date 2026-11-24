# GameLingo 0.5 — informace o soukromí (pracovní návrh)
2026-11-24. Dokument popisuje zjištěný stav kódu pro testery, nikoli finální právně schválené zásady ochrany osobních údajů. Nejde o potvrzení GDPR shody. Před veřejnou distribucí doplnit ověřené podmínky, identitu/kontakt správce tam, kde je to relevantní, právní základy, retenci a práva subjektů podle skutečného provozu. [Bezpečnostní audit](SECURITY-PRIVACY-AUDIT-0.5.md).

| Data | Účel a zpracování |
|---|---|
| Remote Play video | Dekódování a OCR na zařízení (Vision / ML Kit); kompletní absence dalších přenosů není ještě síťově ověřena |
| Rozpoznaný text a jazyky | Online překlad: iOS Google GTX/MyMemory, Android/TV také Microsoft Edge. Fallback může poslat text více poskytovatelům; poskytovatel zpracovává síťové údaje a text podle svých podmínek. |
| Přihlašovací údaje konzolí | Autentizace Sony/Microsoft. iOS tokeny v Keychain; Android credential persistence a starší account-transfer cesta ještě vyžadují úplné ověření. |
| Párování TV | IP, identita telefonu, PIN/klíč, HMAC a replay ochrana; Keychain/Keystore na ověřených cestách. UDP není šifrované; bootstrap PIN/secret nešifrovaný. |
| Cache | Trvalá SQLite s originály, výsledky, jazyky, engine a LRU; limit cca 20 000, ne časová expirace. iOS vyloučena ze zálohy; Android generated manifest ověřit. |
| Historie | Oddělená volitelná RAM-only historie; vypnutí/vymazání historie nevymaže SQLite cache. |
| Profily/slovníky | Lokální uživatelská konfigurace; úplný audit persistence a mazání čeká. |
| Diagnostika | Lokální metriky a ruční export; starší logy mohou obsahovat text/IP. Před sdílením ručně redigovat. Úplná absence telemetrie SDK není doložena. |

ML Kit OCR/language ID zůstávají na Androidu a TV; SDK může odesílat provozní metriky nebo stahovat modely. ML Kit Translation je pouze na mobilním Androidu. TV nemá samostatný offline model; iOS má cache, nikoli offline inference. Použité neoficiální online endpointy nejsou autorizací doloženy; limity a podmínky otevřené. Viz [licenční audit](LICENSE-AND-PROVIDER-AUDIT.md), [Google ML Kit privacy](https://developers.google.com/ml-kit/privacy), [Google privacy](https://policies.google.com/privacy), [Microsoft privacy](https://privacy.microsoft.com/privacystatement), [MyMemory podmínky](https://mymemory.translated.net/terms-and-conditions). Odkazy nejsou dokladem smluvního schválení ani konkrétní retence.

Oprávnění: lokální síť pro konzole a TV; kamera pro volitelné QR skenování na telefonu; overlay na TV pro vykreslení titulků. Kontrola všech skutečně zabalených oprávnění a chování po odmítnutí čeká. Nepřidávat nové oprávnění bez zdůvodnění.

Uživatel může vypnout historii, zastavit relaci, lokálně zapomenout přijímač a individuálně odpárovat telefon na TV. Návrhy samostatného mazání cache, režimu bez trvalé cache, redakce exportu a bezpečnějšího párování zatím nejsou implementované. Neuvádět, že odinstalace vždy bezpečně fyzicky vymaže všechna data nebo odvolá serverový účet. Nepoužívat na nedůvěryhodné síti ani pro citlivý text; neexportovat skutečné tokeny/PINy. Žádné nové funkce ani změna poskytovatele touto dokumentací.
