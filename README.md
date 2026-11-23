<div align="center">

# 🎮 GameLingo

### Hraj. Rozuměj.

**České titulky a překlady herních dialogů, voleb a textů – přímo na obrazovce.**

[![iOS](https://img.shields.io/badge/iPhone-iOS-38BDF8?style=flat-square&logo=apple&logoColor=white)](#podporované-platformy)
[![Android](https://img.shields.io/badge/Android-telefon-0284C7?style=flat-square&logo=android&logoColor=white)](#podporované-platformy)
[![Google TV](https://img.shields.io/badge/Google%20TV-Android%20TV-38BDF8?style=flat-square&logo=googletv&logoColor=white)](#podporované-platformy)
[![Open Source](https://img.shields.io/badge/licence-AGPL--3.0-0F2B45?style=flat-square)](LICENSE)

**[📦 Vydání / Releases](https://github.com/CaseyCZ/GameLingo/releases)** · **[🎯 Funkce](#co-gamelingo-umí)** · **[🛠️ Stav vývoje](#stav-projektu)** · **[📖 Jak začít](#jak-to-funguje)** · **[🎨 Design](docs/DESIGN.md)**

</div>

---

## Co je GameLingo?

**GameLingo** je open-source projekt pro překlad herních titulků a dalších textů při hraní na konzoli. Telefon získává obraz hry, rozpoznává anglický text (OCR), překládá jej do češtiny a posílá české titulky na kompatibilní obrazovku.

Na konzoli ani do hry není potřeba nic instalovat. Pro zobrazení titulků **přes obraz hry na televizi** slouží samostatný přijímač pro **Google TV / Android TV**. Telefon i TV jsou ve stejné domácí síti.

> **GameLingo je nová značka a nově připravované samostatné aplikace.** Vycházíme z otestovaného iOS Xbox → Google TV prototypu a z původního open-source PlayCaptionu. Aktuální dostupnost konkrétních sestavení je vždy uvedena v [Releases](https://github.com/CaseyCZ/GameLingo/releases). Neoznačujeme funkci jako hotovou ve verzi GameLingo, dokud není sestavená a vyzkoušená.

## Co GameLingo umí?

| 🎮 Funkce | Popis |
| --- | --- |
| **Živé české titulky** | OCR rozpozná herní dialogy a odešle jejich překlad na TV. |
| **Herní texty a volby** | Oddělené rozpoznávání nabídnutých odpovědí, zpráv a dalších herních textů. |
| **Nastavení oblasti** | V iPhonu lze řídit čtyři nezávislé oblasti pro čtení a zobrazení. |
| **Umístění titulků** | Poloha, šířka, velikost písma a náhled podle oblasti na obrazovce. |
| **Google TV / Android TV** | Samostatný přijímač se zobrazením českých textů přes HDMI obraz hry. |
| **Párování** | Šestimístný kód v lokální síti; následné zapamatování propojení. |
| **Více konzolí** | Možnost výběru konkrétního Xboxu, je-li pod účtem více konzolí. |
| **Profily her** | Uložené nastavení oblastí a OCR pro jednotlivé hry. |

### Čtyři oblasti – dvě samostatné cesty překladu

| Barva | Funkce |
| :--- | :--- |
| 🔵 **Čtení dialogů** | Odkud rozpoznáváme původní anglické titulky. |
| 🟢 **České titulky** | Kam se zobrazí přeložené dialogy. |
| 🟡 **Čtení herních textů** | Odkud čteme volby, dopisy, zprávy a jiné texty. |
| 🟠 **Překlad herních textů** | Kam se přeložené herní texty zobrazí – nezávisle na titulcích. |

Tyto čtyři oblasti umožňují například ponechat dialogy dole a překlady voleb zobrazovat blíž k původním volbám. Neodmítáme automaticky názvy na autech ani jiné texty ve scéně.

## Podporované platformy

| Zařízení / zdroj | Současný stav |
| --- | --- |
| **iPhone + Xbox Series / One** | ✅ Ověřený funkční základ v předchozím testovacím iOS projektu. Převádí se do GameLingo. |
| **Google TV / Android TV** | ✅ Původní přijímač i testovací párování s iPhonem fungují. Samostatná GameLingo TV aplikace je ve vývoji. |
| **Android telefon** | 🔧 Funkční základ telefonu je v původním open-source PlayCaptionu; připravujeme samostatnou GameLingo APK. |
| **PS5 / PS4** | 🔧 Remote Play je implementované v původní **Android** aplikaci PlayCaption; začleňujeme do Android verze GameLingo. |
| **PS5 na iPhonu** | 🧪 Vyžaduje samostatnou iOS implementaci a reálný test. Android kód nelze jednoduše použít ve Swiftu. |
| **PC** | 🗓️ Budoucí rozšíření; zatím nevydáno. |

> **Google TV / Android TV ≠ každá televize s Android boxem.** Aby překryv zůstával nad HDMI obrazem konzole, musí jej podporovat samotný televizor a jeho systém. Externí box běžně nemůže překrýt obraz na jiném HDMI vstupu televize.

## Jak to funguje?

1. Spusť hru normálně na Xboxu (případně v podporovaném Remote Play režimu).
2. Na telefonu spusť odpovídající verzi GameLingo a vyber herní zařízení.
3. Na Google TV / Android TV otevři přijímač GameLingo TV a případně povol překryv.
4. Spáruj telefon a TV pomocí šestimístného kódu.
5. Zvol oblast čtení a polohu překladů – a **spusť překlad**.

Telefon musí mít přístup k obrazu hry a při překladu zůstávat aktivní; podle použité metody a překladače může být nutné internetové připojení. Přesnost a zpoždění OCR závisí na kvalitě přenosu, textu a hře.

## Stažení a vydání

**[➡️ Otevřít GameLingo Releases](https://github.com/CaseyCZ/GameLingo/releases)** · [Jak připravujeme vydání](docs/RELEASES.md)

Do budoucích vydání chceme přikládat tyto **skutečně sestavené a otestované** soubory:

| Soubor | Určení | Instalace |
| --- | --- | --- |
| `GameLingo-iOS-…-unsigned.ipa` | iPhone | Vyžaduje vlastní podpis / sideloading. |
| `GameLingo-Android-…apk` | Android telefon včetně režimů podporovaných jeho verzí | Instalace APK; podpis a aktualizace dle typu vydání. |
| `GameLingo-GoogleTV-…apk` | Google TV / Android TV | Instalace na TV a povolení překryvu. |
| `SHA256SUMS.txt` | Ověření integrity souborů | Kontrolní součty každého přiloženého balíčku. |

**Žádné prázdné nebo přejmenované IPA/APK:** nový soubor se objeví v Releases až po úspěšném sestavení a kontrole balíčku. Zkušební sestavení budou označena jako **Pre-release**. Vývojové Android APK mohou být podepsány testovacím klíčem, a nemusí proto podporovat aktualizaci předchozí instalace. Při veřejném vydání zajistíme stabilní podpis.

## Stav projektu

- ✅ Schválena značka **GameLingo** a modrobílá vizuální identita CaseyCZ.
- ✅ Ověřen původní iPhone / Xbox → Google TV přenos a oddělený překlad herních textů.
- 🔧 Probíhá převod do samostatného repozitáře a nové produktové UI.
- 🔧 Připravujeme reprodukovatelné buildy iOS, Android telefonu a Google TV.
- 🗓️ Vlastní web GameLingo připravíme později.
- 🗓️ PC a případná další rozšíření jsou v plánu, ne v hotové verzi.

## Zdrojový kód, licence a poděkování

Projekt vychází z open-source [PlayCaption](https://github.com/AdamRuzick4/PlayCaption-app), který je zveřejněn pod **GNU Affero General Public License v3.0 (AGPL-3.0)**. Nová značka, vlastní UI a pozdější změny na tom nic nemění: při distribuci odvozeného kódu zachováme copyright, licenci a dostupnost odpovídajících zdrojů.

Ověřený vývojový základ iPhonu a samostatného TV přijímače pochází z [CaseyCZ/Test](https://github.com/CaseyCZ/Test). Původní a testovací projekty neměníme. Podrobnosti: [původ zdrojů a licence](ATTRIBUTION.md).

**GameLingo je nezávislý komunitní projekt a není oficiálně spojený se Sony, Microsoftem, Googlem ani původním vývojářem PlayCaptionu.**

---

<div align="center">

### GameLingo · Hraj. Rozuměj.

Vyvíjí **[CaseyCZ](https://github.com/CaseyCZ)** · [Kód](https://github.com/CaseyCZ/GameLingo) · [Vydání](https://github.com/CaseyCZ/GameLingo/releases)

</div>
