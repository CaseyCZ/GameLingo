# P2.2 — návrh průvodce instalací Google TV

Stav: návrh, bez implementace. Auditní větev, 2026-11-24.
Rozsah: pouze GameLingo Google TV / Android TV. Žádné nové funkce Samsung/LG, žádná změna transportu titulků, žádný release.

## 1. Zjištěný výchozí stav

- TV má samostatné applicationId `cz.caseycz.gamelingo.tv`; mobil `cz.caseycz.gamelingo.mobile` (tools/brand_android.py).
- Nynější auditní CI produkuje debug TEST APK a SHA256SUMS.txt jako Actions artefakty, nikoli veřejný release. Neexistuje důkaz dlouhodobě stabilního release podpisu. Nepoužívat tyto artefakty jako automatickou aktualizační distribuci.
- docs/RELEASES.md vyžaduje stabilní APK podpis, kontrolu identity, verze, kontrolního součtu a odpovídající zdrojový kód.
- P2.1 QR `GLPAIR1|IPv4|PIN` je pouze párování již nainstalovaného přijímače. Není to ADB párování, není to URL APK a neobsahuje instalační oprávnění.

## 2. Doporučené cesty (v pořadí)

| Cesta | Android mobil | iOS | Podmínka |
|---|---|---|---|
| Ruční průvodce | Ano | Ano | Oficiální release APK, stažení na TV přes prohlížeč nebo přenos souboru, potvrzení v systémovém instalátoru |
| Asistovaná instalace ADB | Kandidát po PoC | Přes dobrovolný lokální PC helper | TV skutečně nabízí Wireless debugging a uživatel schválí párování |
| Google Play | Budoucí možnost | Budoucí možnost | Publikace kompatibilního TV balíčku, pravidla obchodu; není součást auditu |

Podle aktuální dokumentace Android Developers je standardní bezdrátové ADB párování pro TV podporováno od Androidu 13 (API 33); pro telefony od Androidu 11. Výrobce může tuto možnost skrýt nebo nepodporovat. Verze TCL C835 zde není ověřena. Nezaměňovat Google TV launcher za verzi OS. Ověřit přímo v nastavení zařízení.

První implementační etapa má být multiplatformní ruční průvodce. Druhá samostatný opt-in ADB PoC na konkrétní TV, teprve poté rozhodnout o nativní integraci. Na iOS nelze předpokládat přístup k systémovému ADB nástroji nebo možnost spouštět desktopový proces. Lokální helper jen po výslovném souhlasu a fyzické kontrole na PC; žádný vzdálený cloudový ADB server, žádný veřejně dostupný port, žádný automatický SSH přístup. Případná vlastní mobilní ADB implementace vyžaduje samostatné ověření knihovny, licence a bezpečnosti. Staré neověřené ADB přes TCP/5555 není automatický fallback.

## 3. Uživatelský postup a stavy

1. **Přidat televizor → Nemám aplikaci na TV.** Zastavit probíhající překlad; nabídnout ruční průvodce a podmíněně ADB.
2. **Vybrat cíl.** Název a IP, model/OS jen pokud skutečně zjištěny. Pokud více zařízení, explicitně vybrat jedno; při nejasnosti nebo změně identity nepokračovat.
3. **Zvolit balíček.** Stabilní vydání výchozí, TEST pouze samostatný přepínač a varování. Zobrazit verzi, velikost, zdroj, očekávanou identitu a podpis. Pokud ještě není ověřený release, pouze informovat, nevymýšlet odkaz.
4. **Ověřit.** Stáhnout přes HTTPS z oficiálního povoleného zdroje do dočasného souboru s limitem velikosti; ověřit podepsaná metadata, SHA-256, APK podpis, applicationId, verzi, ABI/minSDK a případný stávající podpis. Chyba je blokující. Nezobrazovat stav Ready před ověřením.
5. **Potvrdit.** Zobrazit konkrétní TV, balíček a zda jde o první instalaci nebo aktualizaci. Zrušení nic neinstaluje.
6. **Instalace.** Ruční cesta vede do systémového instalátoru TV. ADB cesta vyžaduje uživatelem povolené Wireless debugging a samostatné systémové párování kódem; nepoužívat PIN GameLingo. ADB spojení autorizuje široká oprávnění, proto pouze explicitní opt-in a jen jedna cílová instalace. Neotevírat shell pro libovolné příkazy, nespouštět jiné APK, nevynucovat downgrade, neodinstalovat existující aplikaci, neměnit systémová oprávnění, HDMI ani nastavení TV. Za běhu instalace po odeslání systémovému instalátoru zrušení nemusí vrátit již dokončenou instalaci; ověřit konečný stav.
7. **Ověřit instalaci.** Potvrdit package/version/signature, kde je to autorizovaně dostupné. Spustit aplikaci ručně nebo explicitním tlačítkem. Stav „Nainstalováno“ není stav „Připojeno“.
8. **Připojit titulky.** Na TV povolit překryv přes systémové nastavení, otevřít Překlad z telefonu, načíst P2.1 QR nebo PIN. Úspěch až po ověřeném PING a ručním testu překryvu; při HDMI ověřit reálný vstup.
9. **Ukončit.** Uzavřít instalační relaci, smazat dočasné soubory a ephemeral autorizaci v helperu. Nabídnout vypnutí Wireless debugging a odebrání jeho párování v nastavení TV. Nezaměňovat s odpárováním GameLingo.

Stavy: Idle → SelectingTarget → FetchingManifest → Downloading → Verifying → AwaitingConsent → Installing → VerifyingInstalled → AwaitingGameLingoPairing → Ready. Vedlejší stavy Cancelled, Unsupported, Failed, OutcomeUnknown. Stav OutcomeUnknown po výpadku vyžaduje nové ověření, ne automatickou druhou instalaci.

## 4. Návrh modulů (neimplementovat)

- `InstallationCatalog`: ověřený manifest, explicitní stable/test kanál. Budoucí manifest schemaVersion, platform, packageId, versionName/versionCode, minSdk, ABI, artifact URL, size, SHA256, cert SHA256, source commit; podpis manifestu proti zabudovanému důvěryhodnému veřejnému klíči. Hash ze stejného neověřeného zdroje sám nestačí jako důkaz autenticity. Rotace klíčů a release signing oddělená od klientského kódu.
- `TvInstallTarget`: explicitní zařízení, schopnosti, autorizovaná session identita, případný package stav. Discovery pouze lokální a uživatelem vyvolané; nepřistupovat k nevybraným hostům.
- `InstallTransport`: ManualGuide a případně AuthorizedAdbAdapter; žádné generické shell rozhraní. Instalace pouze předem ověřeného GameLingo TV APK, očekávaný balíček; explicitní target binding. Žádný server na Oracle ani Tailscale automaticky.
- `InstallCoordinator`: jedna cancellable relace, bounded download, timeout, dočasný soubor, verifikace před souhlasem, kontrola targetu i bezprostředně před instalací, fail closed, lokální redigovaný log bez PIN/ADB klíčů. Žádná automatická aktualizace na pozadí.
- `GameLingoPairing`: stávající P2.1 a HMAC; žádné sdílení ADB klíčů nebo portů s GameLingo.

## 5. Bezpečnostní a release podmínky

Android vyžaduje odpovídající package identity a podpis při aktualizaci (případně podporovaný podpisový lineage). Pokud podpis debug TEST nesouhlasí, instalaci zablokovat a nabídnout vysvětlení; automatické odinstalování by zničilo profily a párování. Vývojářský podpis/privátní klíč nikdy nesmí být v aplikaci, repozitáři ani veřejném logu. Stabilní podpis a distribuce musí být rozhodnuty před implementací katalogu. Uživatel může odmítnout instalaci i oprávnění; systémové ochrany a verifikaci aplikací neobcházet.

## 6. Akceptační matice budoucí implementace

Vše zatím neprovedeno, protože P2.2 je pouze návrh.

| Test | Očekávání |
|---|---|
| TCL C835, verze OS a dostupnost Wireless debugging | Skutečně ověřit; unsupported → ruční průvodce |
| Google TV API 33+ s Wireless debugging | Uživatelem autorizované párování, správný cíl, jediný ověřený APK |
| Starší TV / chybějící ADB / izolovaná Wi-Fi | Ruční cesta, žádný neautorizovaný pokus |
| Dvě TV, změna IP, přerušení sítě | Žádná instalace na jiném zařízení, OutcomeUnknown a recheck |
| Zrušené párování nebo instalace | Žádná další akce bez potvrzení |
| Poškozený download, cizí APK, neplatný manifest, jiný certifikát | Blokace před instalací |
| Update shodného certifikátu, nižší verze, debug/release konflikt | Zachovat data nebo blokovat; žádný nucený downgrade/uninstall |
| Bez overlay oprávnění, QR expirován, přijímač nereaguje | Nainstalováno, ale nepřipojeno; systémová/manual recovery |
| HDMI Xbox / PS4 / PS5 | Ruční test překryvu, zachovat vstup TV |
| iOS/Android ruční cesta a PC helper | Ověřit reálné platformní možnosti, consent, bez cloudového tunelu |

## 7. Oficiální podklady

Ověřeno 2026-11-24:
- Android Debug Bridge: https://developer.android.com/tools/adb
- Run apps on a hardware device: https://developer.android.com/studio/run/device
- How app updates work: https://developer.android.com/google/play/app-updates
- Sign your app: https://developer.android.com/studio/publish/app-signing
- Publish your app (instalace z jiných zdrojů): https://developer.android.com/studio/publish
- PackageInstaller: https://developer.android.com/reference/android/content/pm/PackageInstaller

Rozhodnutí: **dokončený návrh není implementace ani ověřená kompatibilita.** Žádný nový ADB kód, oprávnění, build dependency, artefakt ani release v tomto bodě. P2.3 je historie překladů.
