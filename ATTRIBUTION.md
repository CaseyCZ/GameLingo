# Původ projektu a licence

GameLingo je samostatně vyvíjený open-source projekt navazující na:

- **PlayCaption**, původní Android aplikace pro překlad herních titulků a Remote Play, [AdamRuzick4/PlayCaption-app](https://github.com/AdamRuzick4/PlayCaption-app), konkrétně commit [633a847400b6c4bac1e7f034463e7847a53a9590](https://github.com/AdamRuzick4/PlayCaption-app/commit/633a847400b6c4bac1e7f034463e7847a53a9590).
- **CaseyCZ/Test**, vlastní vývojová sada pro iOS Xbox OCR, lokální párování s TV a oranžové výstupy, [CaseyCZ/Test](https://github.com/CaseyCZ/Test).
- **TP Play / chiaki-ng / Chiaki**, otevřený základ nativního PlayStation 4/5 Remote Play. iOS komponenty (C jádro, registrace konzolí, zjišťování v síti, dekódování videa a zvuku a přehrávání) jsou odvozené z [mB14ck22/tp-play](https://github.com/mB14ck22/tp-play) na připnuté revizi [`482611539977496cad047560c9bc1ebc5809a566`](https://github.com/mB14ck22/tp-play/commit/482611539977496cad047560c9bc1ebc5809a566), jehož sdílené jádro vychází z [chiaki-ng](https://github.com/streetpea/chiaki-ng). Tyto části zůstávají pod **AGPL-3.0** včetně příslušných původních oznámení o autorství a případné doplňující výjimky k OpenSSL v původním adresáři LICENSES.

**Licence původního PlayCaptionu: GNU AGPL-3.0.** Text licence je v [LICENSE](LICENSE). Licence se vztahuje na kód, který z PlayCaptionu odvozujeme; změna jména ani grafiky jej nepřevádí na proprietární kód. Při distribuci odvozených binárních balíčků musí být zpřístupněn odpovídající zdrojový kód včetně relevantních build skriptů a informace o úpravách.

Cílem je mít v tomto repozitáři GameLingo vlastní UI, vlastní build skripty, aktuální stav implementace a transparentní cestu k původnímu zdroji. V počátečním období mohou buildy pro reprodukovatelnost používat externí připnuté revize uvedené výše. Neprezentujeme cizí kód jako originálně vlastní.

**Názvy Xbox, PlayStation, Android a Google TV označují kompatibilní produkty.** GameLingo s jejich výrobci není spojen ani jimi sponzorován.

Veškeré vizuální prvky GameLingo mají být autorské, nikoliv kopie GlossaSub.

## Stav ověření

Sestavení nativního PlayStation jádra a aplikace iOS v CI **nenahrazuje** test na reálné konzoli. Před stabilním vydáním musí být ověřeno párování PS4/PS5 s vlastním účtem, VideoToolbox snímky v OCR, překlad na obrazovce telefonu a na Google TV, obnovení spojení a ukončování relace. Nativní soubory ve složce `ios/GameLingoIOS/App/PlayStation/` a [sestavovací postup](.github/workflows/build-gamelingo.yml) jsou součástí odpovídajícího zdrojového kódu.

## QR knihovny
- [ZXing](https://github.com/zxing/zxing) core 3.5.3 (již v Android upstreamu) a [JourneyApps ZXing Android Embedded 4.3.0](https://github.com/journeyapps/zxing-android-embedded/tree/v4.3.0) pouze mobilní kamerový skener. Apache-2.0, původní licence a oznámení zachovat při distribuci. iOS používá systémové VisionKit/AVFoundation.

## Inventář závislostí a distribuce 0.5
Podrobný [licenční audit a překladové služby](docs/LICENSE-AND-PROVIDER-AUDIT.md) rozlišuje přímé deklarace, nativní závislosti, modely, externí služby a dosud neověřené tranzitivní součásti. Android/TV původní Chiaki binary provenance je v pinned PlayCaption native/chiaki-build.json a build_chiaki.py; před vydáním ověřit zabalené hashe a odpovídající zdroj. [Kredit ikon](docs/TV_ICON_CREDITS.md): Font Awesome Free 6.7.2, © Fonticons, Inc., CC BY 4.0, SVG převedeny na VectorDrawable. Tento seznam nenahrazuje úplné THIRD_PARTY_NOTICES, source bundle a provider attribution. Samostatný TV offline Translation SDK byl odstraněn; ML Kit obecné podmínky platí pro zbývající OCR/language ID a mobilní Translation.

## Licenční texty v testovacích balíčcích 0.5
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) obsahuje platformní inventář a otevřené podmínky. Společný build nově generuje `GameLingoNotices.txt` a `GameLingoLicenseInventory.json` z aktuálních Git revizí a dostupných původních licenčních souborů; balíčky se kontrolují před uploadem. O aplikaci umožňuje číst soubor offline. Tato implementace ještě neprošla novým buildem a nenahrazuje úplný SBOM, binární notices, odpovídající zdroj ani právní clearance.
