# Původ projektu a licence

GameLingo je nezávislý open-source projekt pod licencí [GNU AGPL-3.0](LICENSE).

## Původ zdrojového kódu

- [PlayCaption — AdamRuzick4](https://github.com/AdamRuzick4/PlayCaption-app/tree/633a847400b6c4bac1e7f034463e7847a53a9590): základ Android/TV, herní titulky a Remote Play (AGPL-3.0).
- [CaseyCZ/Test](https://github.com/CaseyCZ/Test): vlastní vývoj iOS Xbox OCR, párování s TV a výstupu titulků.
- [TP Play — mB14ck22](https://github.com/mB14ck22/tp-play/tree/482611539977496cad047560c9bc1ebc5809a566), [chiaki-ng](https://github.com/streetpea/chiaki-ng) a Chiaki: základ PlayStation Remote Play, nativního jádra, registrace, discovery a dekódování (AGPL-3.0; zachovat původní oznámení a příslušné licenční výjimky, včetně případné výjimky OpenSSL).

GameLingo upravuje uživatelské rozhraní, OCR, překlad, lokální párování a sestavovací postup. Odpovídající zdrojový kód zahrnuje i připnuté upstream revize a [build skripty](.github/workflows/audit-0.5-ci.yml). Původ Android/TV Chiaki binárek je doložen v upstream `native/chiaki-build.json` a `native/build_chiaki.py`; build nyní ověřuje SHA-256 všech čtyř ARM binárek proti tomuto manifestu a zaznamenává skutečné hashe knihoven v APK. Odlišné bajty v APK (například po stripování) vyžadují další posouzení; samotná shoda hashů nedokládá reprodukovatelnost ze zdrojů.

## Další komponenty

- [Font Awesome Free 6.7.2](https://github.com/FortAwesome/Font-Awesome/tree/6.7.2): Xbox a PlayStation ikony, © 2024 Fonticons, Inc., [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). SVG převedeny na VectorDrawable, upravena barva.
- [ZXing](https://github.com/zxing/zxing) 3.5.3 a [JourneyApps ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded/tree/v4.3.0) 4.3.0: Apache-2.0, mobilní QR skener.

Původní licenční texty Chiaki-TV včetně výjimky OpenSSL, Oboe, nanopb, GF-Complete, Jerasure a OpenSSL 1.1.1d jsou uloženy v `licenses/` s odkazy na přesné zdrojové revize v [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Mezivrstva PlayCaption `native/pcobraz.c` je AGPLv3; původní binárky nejsou doloženy reprodukovatelným sestavením.

Další závislosti: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Build přikládá dostupné původní licenční texty a inventář revizí. Inventář není úplný SBOM ani potvrzení oprávnění k veřejné distribuci; podmínky externích služeb, odpovídající zdroje a licence všech skutečně distribuovaných komponent musí být ověřeny.

Xbox, PlayStation, Android a Google TV jsou ochranné známky příslušných vlastníků. GameLingo není jejich oficiálním produktem ani jimi sponzorován.
