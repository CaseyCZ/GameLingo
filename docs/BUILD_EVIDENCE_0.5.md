# GameLingo 0.5.0 — evidence sestavení

## Aktuální auditní build — 24. 11. 2026

- Zdroj: `f34348b1cd721c83074bae2590e6047b0a076303`, větev `feature/audit-0.5`.
- [CI 45384321662](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662): šest jobů PASS, iOS/Android/TV, kontrakty, balení/SHA-256, TV DEX absence a mobilní presence ML Kit Translation.
- Historické checksumy níže neplatí pro novější balíčky. Aktuální hodnoty a kontrola skutečných souborů jsou níže v bodě 6. HW čeká, žádný nový veřejný release.

## Historický build — 23. 11. 2026

Toto je doklad **úspěšného sestavení**, nikoli potvrzení funkčnosti na skutečné PS5, Xboxu, Androidu nebo Google TV. Zákazníci a testeři nesmějí dostat dojem, že CI znamená funkční Remote Play, párování nebo HDMI překryv.

- **Zdrojová revize:** `12e0bcb3736054c4f37a6b3328f0f9b1ecaa0341`
- **Workflow:** [GitHub Actions #34, run 45211607965](https://github.com/CaseyCZ/GameLingo/actions/runs/45211607965)
- **Celkový výsledek:** `completed / success`
- **Ověřené joby:** integration contracts = PASS; iPhone unsigned IPA = PASS; Android phone APK = PASS; Google TV APK = PASS; packaging & SHA256 = PASS.
- **Vydání:** zatím pouze soukromý/pracovní QA kandidát jako GitHub Actions artefakt; žádný tag Release, žádný merge do Master. Debug Android APK jsou TEST a mohou vyžadovat reinstalaci, iPhone IPA je unsigned.

## Kandidátní soubory a SHA-256

| Soubor | SHA-256 |
|---|---|
| `GameLingo-iOS-v0.5.0-dev-unsigned.ipa` | `42a7c0abb04daca508e69368983afb27ca3e609a0a6d8e0230e3f054674ffa56` |
| `GameLingo-Android-v0.5.0-dev-TEST.apk` | `1cd15fbd93c80ac7defffaf6ba2428899c8680721b1734b94bc5bcccc8c54c7d` |
| `GameLingo-GoogleTV-v0.5.0-dev-TEST.apk` | `a58a78e0eccb9e9acae3beb6197367fbce92ef1ce45047aec5bdaff4541a98e2` |

Kontrolní součty pocházejí z `Validate all packages and checksums` v uvedeném jobu. Společný artifact `GameLingo-v0.5.0-dev-all-installers` obsahuje všechny tři soubory a `SHA256SUMS.txt`. Artefakty mají omezenou retenci GitHub Actions; pokud vyprší, sestav je znovu ze stejného zdrojového commitu a ulož novou sadu checksumů.

## Další závazná kontrola

Před označením GameLingo 0.5 jako dokončené projít scénáře v [PHYSICAL_QA_0.5.md](PHYSICAL_QA_0.5.md): skutečný Xbox a PS4/PS5 Remote Play, více konzolí, iPhone OCR, dialogy a texty, šestímístné párování TV, HDMI overlay, vypnutí/obnovení streamu, zabezpečení a bezproblémové přepínání. Tyto scénáře jsou stále **NEOTESTOVÁNO NA HARDWARU**.

**Pravidlo obnovy po přerušení:** Před pokračováním zkontroluj [WORK_CHECKPOINT_0.5.md](WORK_CHECKPOINT_0.5.md) a aktuální head větve. Bude-li od `12e0bcb` změněn zdrojový kód aplikací, tento doklad se nesmí použít jako build finálního SHA. Při čistě dokumentačních změnách zůstává zdroj sestavených binárních souborů identický.

Aktuální obnovovací rozcestník: [STATUS-0.5.md](STATUS-0.5.md) a [REPORT.md](../REPORT.md).

## Bod 6 — kontrola skutečných stažených balíčků (24. 11. 2026)

Zdrojový build `f34348b1cd721c83074bae2590e6047b0a076303`, [Actions 45384321662](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662). Všech šest jobů znovu ověřeno success, všechny čtyři artifacts neexpirované. Porovnání s auditní větví před bodem 6: 19 následných commitů, pouze dokumentace/atribuce, žádná změna aplikačních zdrojů ani build workflow. Proto se použily skutečné existující soubory ze stejného runu místo zbytečného rebuild. [Společný ZIP](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662/artifacts/14916013048) 84 869 938 B, expirace 24. 12. 2026 11:41 UTC. Stažený ZIP SHA-256 odpovídá metadata digest: `70489864322c2a2b1512246c9937367c8c52446ee917dc0a19b04bd6a12db4b9`. CRC ZIP a všech tří vnitřních balíčků PASS; žádné poškozené entries. SHA-256 vypočteny lokálně a odpovídají souboru SHA256SUMS.txt i packaging job logu.

| Soubor | Bajtů (rozbalený instalátor) | SHA-256 |
|---|---:|---|
| GameLingo-iOS-v0.5.0-dev-unsigned.ipa | 7 512 330 | `021c63f9a966d2742427c7eaa590243e5f9d52f9dddb603088f2ca531008ff66` |
| GameLingo-Android-v0.5.0-dev-TEST.apk | 86 629 997 | `5ff518cd7e8c021149bf385fa8fab3f1032f08b9d3bf110b4b9d6108d3b24fc2` |
| GameLingo-GoogleTV-v0.5.0-dev-TEST.apk | 58 238 113 | `22399e9668e4bef3163c80fa14d43f1d5abcada1bade3ef5689b9fe2b82f5b52` |

Samostatné Actions ZIP artefakty (komprimovaná velikost, nikoli APK velikost): [iOS](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662/artifacts/14917105406) 7 492 604 B, [Android](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662/artifacts/14914834061) 45 483 495 B, [TV](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662/artifacts/14915184274) 31 893 561 B.

### Obsah a podpisy
- iOS: skutečný Info.plist: cz.caseycz.gamelingo.ios, GameLingo, 0.5.0 (5), iOS 17.0 min; bez embedded.mobileprovision a _CodeSignature. Unsigned kandidát, před instalací vyžaduje podpis. WebRTC framework a jeho PrivacyInfo.xcprivacy přítomny, Xcode job log resolved stasel/WebRTC 154.0.0.
- Mobil: skutečný binary AndroidManifest: cz.caseycz.gamelingo.mobile, 0.5.0-dev, code 505, min SDK 26, target SDK 34, debuggable=true, allowBackup=false.
- TV: skutečný binary AndroidManifest: cz.caseycz.gamelingo.tv, 0.5.0-dev-tv, code 505, min SDK 26, target SDK 34, debuggable=true, allowBackup=false. CAMERA pouze mobil, oba APK deklarují SYSTEM_ALERT_WINDOW, foreground services včetně media projection, notifications, internet, boot, usage stats a síťové/wake permissions. Úplná kontrola exported komponent, skutečné potřeby oprávnění a denial runtime není dokončena.
- Oba APK: lokální ověření APK Signature Scheme v2 (RSA PKCS#1 v1.5/SHA-256, cert public key, podpis signed data a chunked content digest). Android Debug certifikáty různé: mobile SHA-256 `5ea911cdf009964b366cf824a3d59b6389018216d04332e9b3317a33fcf53cf8`, TV `6192fa962b7b9fa93ee3e25ea7a9cb08d90449f33c99afc4edfb1fcd6bc463d8`. Nejde o stabilní release podpis; aktualizace staršího debug buildu může vyžadovat odinstalaci a ztrátu dat. Android SDK apksigner nebyl v lokálním prostředí dostupný; použit vlastní lokální parser v2 s cryptography, nenahrazuje úplný apksigner verifier ani instalaci.
- DEX lokálně: mobile 5 DEX, ML Kit Translation markery přítomny; TV 4 DEX, stejné markery nepřítomny. Mobile obsahuje libtranslate_jni.so pro arm64-v8a a armeabi-v7a (27 969 856 B dohromady), TV žádnou. Oba mají libmlkit_google_ocr_pipeline.so pro obě ABI. TV bez samostatné offline inference, nikoli bez OCR.
- Native Android: obě ABI, všechna čtyři libchiaki-jni.so/libchiaki-pc.so v každém APK odpovídají SHA-256 pinned upstream `native/chiaki-build.json`. Není důkaz reprodukovatelnosti všech submodulů nebo úplné licenční clearance.
- CLEAN-04/05 potvrzen přímo v obou APK: assets/lg (33 741 B raw, receiver.ipk), assets/samsung (15 260 B raw, receiver.wgt), assets/testy (583 759 B, 4 JPG). Jejich souhrn v každém APK cca 0,63 MB raw; odstranění samo o sobě nezmenší APK o desítky MB. JSch notices přítomny; tflite_langid.tflite.jpg 315 520 B je oddělený model, nikoli herní JPG, neodstraňovat jen podle přípony. TV APK o 28 391 884 B menší než mobilní APK, různé varianty, nikoli kontrolovaný before/after stejného TV buildu.

### Stav
QA balíčky dostupné, integrita a identita ověřeny; žádné nové veřejné vydání ani přepis stávajících. Před veřejným release zůstává úplný resolved SBOM/notice bundle, odpovídající zdroj a práva k assets, security/provider terms, stabilní podpis a fyzické testy. IPA ZIP obsahuje WebRTC privacy manifest, nikoli kompletní third-party notices. Bod 6 uzavřen pro přípravu testovacích instalátorů, ne pro právní nebo runtime clearance. Další bod 7: fyzické testování na skutečných zařízeních dle PHYSICAL_QA_0.5.md; čeká na výsledky testera. Nepublikovat release.
