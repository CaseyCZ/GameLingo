"""Removal of offline Translation API on both Android variants.

Run after common language/cache/provider patches. Both variants use identical online translation policy.
The direct TV reader retains online providers, OCR and the SQLite cache.
"""
from pathlib import Path
import re

DEP = '    implementation("com.google.mlkit:translate:17.0.3")'
MOBILE_ID = 'applicationId = "cz.caseycz.gamelingo.mobile"'
TV_ID = 'applicationId = "cz.caseycz.gamelingo.tv"'
FORBIDDEN_SOURCE = re.compile(r'com\.google\.mlkit\.nl\.translate|TranslatorOptions|TranslateLanguage|downloadModelIfNeeded\s*\(')


def once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError(f"TV translation policy anchor {old[:100]!r}: {text.count(old)} occurrences")
    return text.replace(old, new, 1)


def between(text, start, end, new):
    if text.count(start) != 1 or text.count(end) != 1:
        raise RuntimeError(f"TV translation policy boundary: {start!r} / {end!r}")
    a = text.index(start)
    b = text.index(end)
    if b <= a:
        raise RuntimeError("TV translation policy reversed boundary")
    return text[:a] + new + text[b:]


def strip_dependency(gradle):
    gradle = once(gradle, '    // Překlad přímo v televizi – zdarma, bez limitu (Google ML Kit)\n' + DEP,
                  '    // Online only: no ML Kit on-device Translation dependency.')
    # Reject accidental reintroduction, including a future transitive request.
    return gradle + '''
// GameLingo: offline translation is not shipped.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if ((requested.group == "com.google.mlkit" && requested.name == "translate") ||
            (requested.group == "com.google.android.gms" &&
                requested.name.startsWith("play-services-mlkit-translate"))) {
            throw GradleException("ML Kit on-device Translation is not allowed in GameLingo")
        }
    }
}
'''


def tv_variant(root):
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf8")
    tv, mobile = TV_ID in gradle, MOBILE_ID in gradle
    if tv == mobile:
        raise RuntimeError("Expected exactly one branded GameLingo applicationId")
    return tv


def tv_translator(text):
    for name in ("com.google.mlkit.common.model.DownloadConditions",
                 "com.google.mlkit.nl.translate.TranslateLanguage",
                 "com.google.mlkit.nl.translate.Translation",
                 "com.google.mlkit.nl.translate.Translator",
                 "com.google.mlkit.nl.translate.TranslatorOptions"):
        text = once(text, "import " + name + "\n", "")
    text = between(text, "/**\n * Překlad angličtina → čeština.",
                   "class Prekladac(", """/**
 * Online providers and local translation cache only.
 * The on-device translation SDK is not shipped.
 */
""")
    text = between(text, "    private fun novyTranslator(", "    private val ui = Handler(", '''    @Volatile private var zavreno = false

    fun nastavZdroj(kod: String) { zdrojovyJazyk = kod }
    fun nastavJazyk(kod: String) { cil = kod }

''')
    text = between(text, "    /** Stáhne jazykový balíček", "    /** Přeloží anglický text", '''    /** Online path is configured; this does not prove network/provider reachability. */
    fun priprav(hotovo: () -> Unit, chyba: (String) -> Unit) {
        if (zavreno) { chyba("Překladač je zavřený"); return }
        pripraven = true
        hotovo()
    }

''')
    # Do not submit duplicate network requests for the quick preview. The
    # full translation path already runs independently; cache hit stays fast.
    text = once(text, '''        cachedOrRun(vstup, source, target, hotovo, chyba) {
            prelozVTelevizi(vstup, source, target, hotovo, chyba)
        }''', '''        cachedOrRun(vstup, source, target, hotovo, chyba) {
            chyba("Rychlý náhled není v paměti; čeká se na online překlad")
        }''')
    # The existing story coordinator can start its secondary route after
    # 2.5 seconds; use only alternative online providers, never duplicate Google.
    text = once(text, 'prelozVTelevizi(t, source, jazyk, ok, { err() })',
                'prelozOnlineMiss(t, source, jazyk, ok, { err() }, skipGoogle = true)')
    text = once(text, '''    private fun prelozOnlineMiss(vstup: String, source: String, target: String,
                                 hotovo: (String) -> Unit, chyba: (String) -> Unit) {''',
                '''    private fun prelozOnlineMiss(vstup: String, source: String, target: String,
                                 hotovo: (String) -> Unit, chyba: (String) -> Unit,
                                 skipGoogle: Boolean = false) {''')
    text = once(text, 'var vysledek = guardedGoogle(vstup, target, source)',
                'var vysledek = if (skipGoogle) null else guardedGoogle(vstup, target, source)')
    text = once(text, 'prelozZalozne(vstup, source, target, hotovo, chyba)',
                'chyba("Online překlad není dostupný")')
    text = between(text, "    /** Záloha, když nic online nejde:", "    /** Neoficiální adresa Google Překladače", "")
    text = once(text, '        runCatching { translator.close() }\n', '')
    text = once(text, '    /** Jen rychlý překlad přímo v televizi (bez internetu) – na okamžitý náhled. */',
                '    /** Rychlý náhled z lokální cache; síťový překlad běží samostatně. */')
    text = once(text, '                    // nic online nejde → překlad v televizi',
                '                    // All enabled online providers failed; no offline SDK on TV.')
    text = once(text, '    /** Odkud byl poslední překlad: "google", "tv" nebo "paměť" (pro záznam). */',
                '    /** Provider posledního úspěšného překladu (pro diagnostiku). */')
    if FORBIDDEN_SOURCE.search(text) or 'mlkit-v1' in text:
        raise RuntimeError("Restricted TV translation implementation remains")
    return text


def finalize_tv(root, folder):
    tv_variant(root)  # validate branded variant
    cache = folder / "GameLingoTranslationCache.kt"
    text = cache.read_text(encoding="utf8")
    text = once(text, 'listOf("google-gtx-v1", "microsoft-edge-v1", "mymemory-v1", "mlkit-v1")',
                'listOf("google-gtx-v1", "microsoft-edge-v1", "mymemory-v1")')
    cache.write_text(text, encoding="utf8")
    service = folder / "RemotePlayService.kt"
    text = service.read_text(encoding="utf8")
    text = once(text, '            prekladac.priprav(hotovo = { prekladac.preloz("Hello, friend.", {}, {}) }, chyba = {})',
                '            // TV: no model download or synthetic provider request.')
    service.write_text(text, encoding="utf8")
    dev = folder / "VyvojActivity.kt"
    text = dev.read_text(encoding="utf8")
    text = once(text, 'Překladač: stahuji český jazyk (jen poprvé, ~30 MB)…',
                'Překladač: nastavuji online služby…')
    text = once(text, 'Překladač: připraven ✔', 'Překladač: online režim (spojení ověří překlad)')
    text = once(text, 'Překladač: chyba při stahování ✘', 'Překladač: chyba nastavení ✘')
    dev.write_text(text, encoding="utf8")
    assert_tv_source(root)


def assert_tv_source(root):
    tv_variant(root)  # validate branded variant
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf8")
    if re.search(r'["\']com\.google\.mlkit:translate:', gradle):
        raise AssertionError("TV Gradle still declares ML Kit Translation")
    if 'ML Kit on-device Translation is not allowed in GameLingo' not in gradle:
        raise AssertionError("TV dependency resolution guard missing")
    for path in (root / "app/src").rglob("*"):
        if path.is_file() and path.suffix in (".kt", ".java"):
            if FORBIDDEN_SOURCE.search(path.read_text(encoding="utf8")):
                raise AssertionError(f"Restricted translation API remains in {path}")
    translator = root / "app/src/main/java/cz/titulky/tv/Prekladac.kt"
    for token in ("guardedGoogle", "edgeBreaker", "myMemoryBreaker", "cachedOrRun", "skipGoogle = true"):
        if token not in translator.read_text(encoding="utf8"):
            raise AssertionError(f"TV online path missing: {token}")
