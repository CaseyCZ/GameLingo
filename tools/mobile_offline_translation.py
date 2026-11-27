"""Mobile-only offline fallback after common Android translation safety patches."""
from pathlib import Path
from tv_translation_policy import tv_variant, DEP, once

def install_mobile_translator(t, folder):
    helper = Path(__file__).with_name("GameLingoMobileOffline.kt")
    (folder / helper.name).write_text(helper.read_text(encoding="utf8"), encoding="utf8")
    cache = folder / "GameLingoTranslationCache.kt"
    cache.write_text(once(cache.read_text(encoding="utf8"),
        'listOf("google-gtx-v1", "microsoft-edge-v1", "mymemory-v1")',
        'listOf("google-gtx-v1", "microsoft-edge-v1", "mymemory-v1", "mlkit-v1")'), encoding="utf8")
    t = once(t, '    private val cacheContext = context?.applicationContext',
        '    private val cacheContext = context?.applicationContext\n    private val offline = GameLingoMobileOffline(cacheContext)')
    t = once(t, '''        pripraven = true
        hotovo()
    }''', '''        offline.prepare(zdrojovyJazyk, cil)
        pripraven = true
        hotovo()
    }''')
    t = once(t, '''        val queuedAt = android.os.SystemClock.elapsedRealtime()''',
        '''        offline.prepare(source, target)
        val queuedAt = android.os.SystemClock.elapsedRealtime()''')
    t = once(t, '''                    chyba("Online překlad není dostupný")''',
        '''                    offlineResult(vstup, source, target, cacheRevision, true, hotovo, chyba)''')
    t = once(t, '''            chyba("Rychlý náhled není v paměti; čeká se na online překlad")''',
        '''            offlineResult(vstup, source, target, cacheRevision, false, hotovo, chyba)''')
    t = once(t, '''        val source = zdrojovyJazyk
        val target = cil
        cachedOrRun(vstup, source, target, hotovo, chyba) {
            offlineResult''',
        '''        val source = zdrojovyJazyk
        val target = cil
        val cacheRevision = GameLingoTranslationCache.revision()
        cachedOrRun(vstup, source, target, hotovo, chyba) {
            offlineResult''')
    t = once(t, '    fun zavri() {\n        zavreno = true',
        '    fun zavri() {\n        zavreno = true\n        offline.close()')
    t = once(t, '    private fun providerConnection(address: String): HttpURLConnection {',
        '''    // Local fallback shares request language and cache epochs with the online pipeline.
    private fun offlineResult(vstup: String, source: String, target: String, cacheRevision: Long,
                              cacheResult: Boolean, hotovo: (String) -> Unit, chyba: (String) -> Unit) {
        if (!currentPair(source, target)) { chyba("Změna jazyka nebo relace"); return }
        val languageRevision = GameLingoLanguages.revision
        val protected = GameLingoControllerSymbols(vstup)
        offline.translate(protected.input, source, target) { raw ->
            val result = raw?.let { protected.restore(it) }
            if (!currentPair(source, target) || GameLingoLanguages.revision != languageRevision) {
                chyba("Změna jazyka nebo relace")
            } else if (result.isNullOrBlank()) {
                chyba("Offline překlad není připraven nebo dostupný")
            } else {
                zdroj = "mlkit"
                if (cacheResult) remember("mlkit-v1", source, target, vstup, result, cacheRevision)
                hotovo(result)
            }
        }
    }

    private fun providerConnection(address: String): HttpURLConnection {''')
    return t

def finalize_mobile(root, folder):
    if tv_variant(root):
        raise AssertionError("Offline model must never be installed in TV source")
    service = folder / "RemotePlayService.kt"
    t = once(service.read_text(encoding="utf8"),
        '            prekladac.priprav(hotovo = { prekladac.preloz("Hello, friend.", {}, {}) }, chyba = {})',
        '            prekladac.priprav(hotovo = {}, chyba = {}) // prepare local model without synthetic online request')
    service.write_text(t, encoding="utf8")
    dev = folder / "VyvojActivity.kt"
    t = once(dev.read_text(encoding="utf8"),
        'Překladač: stahuji český jazyk (jen poprvé, ~30 MB)…',
        'Překladač: připravuji offline model přes Wi-Fi…')
    t = once(t, 'Překladač: připraven ✔', 'Překladač: online režim připraven, offline model se připravuje')
    t = once(t, 'Překladač: chyba při stahování ✘', 'Překladač: chyba nastavení ✘')
    dev.write_text(t, encoding="utf8")
    assert_mobile_source(root)

def assert_mobile_source(root):
    if tv_variant(root):
        raise AssertionError("Mobile offline policy requires mobile variant")
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf8")
    if DEP not in gradle or "ML Kit on-device Translation is not allowed in GameLingo" in gradle:
        raise AssertionError("Mobile offline dependency missing or incorrectly prohibited")
    folder = root / "app/src/main/java/cz/titulky/tv"
    helper = (folder / "GameLingoMobileOffline.kt").read_text(encoding="utf8")
    translator = (folder / "Prekladac.kt").read_text(encoding="utf8")
    for token in ("Translation.getClient", "requireWifi()", "FEATURE_LEANBACK", "MAX_PENDING"):
        if token not in helper:
            raise AssertionError("Mobile offline implementation missing: " + token)
    for token in ("offlineResult", "offline.close()", "cacheRevision", "guardedGoogle", "providerConnection"):
        if token not in translator:
            raise AssertionError("Mobile translation contract missing: " + token)
