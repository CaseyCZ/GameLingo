# GameLingo 0.5.0 — evidence sestavení

## Aktuální auditní build — 24. 11. 2026

- Zdroj: `f34348b1cd721c83074bae2590e6047b0a076303`, větev `feature/audit-0.5`.
- [CI 45384321662](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662): šest jobů PASS, iOS/Android/TV, kontrakty, balení/SHA-256, TV DEX absence a mobilní presence ML Kit Translation.
- Historické checksumy níže neplatí pro novější balíčky. Aktuální jednotlivé hodnoty je nutné převzít z artefaktů daného runu a ověřit v bodě 6. HW čeká, žádný nový veřejný release.

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
