"""Privacy hooks applied after platform-specific translator policy."""
def once(s, a, b):
    if s.count(a) != 1:
        raise RuntimeError("Privacy anchor changed: " + a[:100])
    return s.replace(a, b, 1)

def protect_cache_writes(t, tv):
    # Capture epoch at the public request, pass it through all fallbacks.
    # The cache worker checks it again when the write is actually performed.
    anchor = '        cachedOrRun(vstup, source, '
    if t.count(anchor) != 3:
        raise RuntimeError("Expected three translation entry points")
    t = t.replace(anchor, '        val cacheRevision = GameLingoTranslationCache.revision()\n' + anchor)
    t = once(t, 'private fun remember(engine: String, source: String, target: String, text: String, result: String)',
        'private fun remember(engine: String, source: String, target: String, text: String, result: String, cacheRevision: Long)')
    t = once(t, 'GameLingoTranslationCache.remember(cacheContext, engine, source, target, text, result)',
        'GameLingoTranslationCache.remember(cacheContext, engine, source, target, text, result, cacheRevision)')
    for name, args in (
        ('prelozPribehMiss', 'vstup, source, jazyk, hotovo, chyba'),
        ('prelozOnlineMiss', 'vstup, source, target, hotovo, chyba'),
    ):
        t = once(t, name+'('+args+')', name+'('+args+', cacheRevision)')
    t = once(t, 'private fun prelozPribehMiss(vstup: String, source: String, jazyk: String,\n                                 hotovo: (String) -> Unit, chyba: (String) -> Unit)',
        'private fun prelozPribehMiss(vstup: String, source: String, jazyk: String,\n                                 hotovo: (String) -> Unit, chyba: (String) -> Unit, cacheRevision: Long)')
    t = once(t, 'remember("google-gtx-v1", source, jazyk, t, vysledek)',
        'remember("google-gtx-v1", source, jazyk, t, vysledek, cacheRevision)')
    t = once(t, 'remember(engine, source, target, vstup, hotovyText)',
        'remember(engine, source, target, vstup, hotovyText, cacheRevision)')
    if tv:
        t = once(t, 'chyba: (String) -> Unit,\n                                 skipGoogle: Boolean = false)',
            'chyba: (String) -> Unit, cacheRevision: Long,\n                                 skipGoogle: Boolean = false)')
        t = once(t, 'prelozOnlineMiss(t, source, jazyk, ok, { err() }, skipGoogle = true)',
            'prelozOnlineMiss(t, source, jazyk, ok, { err() }, cacheRevision, skipGoogle = true)')
    else:
        t = once(t, 'private fun prelozOnlineMiss(vstup: String, source: String, target: String,\n                                 hotovo: (String) -> Unit, chyba: (String) -> Unit)',
            'private fun prelozOnlineMiss(vstup: String, source: String, target: String,\n                                 hotovo: (String) -> Unit, chyba: (String) -> Unit, cacheRevision: Long)')
        t = once(t, 'prelozVTelevizi(t, source, jazyk, ok, { err() })',
            'prelozVTelevizi(t, source, jazyk, ok, { err() }, cacheRevision)')
        t = once(t, 'prelozZalozne(vstup, source, target, hotovo, chyba)',
            'prelozZalozne(vstup, source, target, hotovo, chyba, cacheRevision)')
        t = once(t, 'private fun prelozZalozne(vstup: String, source: String, target: String,\n                               hotovo: (String) -> Unit, chyba: (String) -> Unit)',
            'private fun prelozZalozne(vstup: String, source: String, target: String,\n                               hotovo: (String) -> Unit, chyba: (String) -> Unit, cacheRevision: Long)')
        if t.count('prelozVTelevizi(vstup, source, target, hotovo, chyba)') != 2:
            raise RuntimeError("Expected preview and fallback cache propagation")
        t = t.replace('prelozVTelevizi(vstup, source, target, hotovo, chyba)',
            'prelozVTelevizi(vstup, source, target, hotovo, chyba, cacheRevision)')
        t = once(t, 'private fun prelozVTelevizi(vstup: String, source: String, target: String, hotovo: (String) -> Unit, chyba: (String) -> Unit)',
            'private fun prelozVTelevizi(vstup: String, source: String, target: String, hotovo: (String) -> Unit, chyba: (String) -> Unit, cacheRevision: Long)')
        t = once(t, 'remember("mlkit-v1", source, target, vstup, cesky)',
            'remember("mlkit-v1", source, target, vstup, cesky, cacheRevision)')
    return t

def restrict_service_log(s):
    start = s.index("    @Synchronized private fun zapis(radek: String) {")
    end = s.index("\n    }", start) + len("\n    }")
    return s[:start] + '''    @Synchronized private fun zapis(radek: String) {
        if (radek.isBlank()) return
        // Never retain arbitrary OCR, URLs, network identifiers, credentials,
        // device names, exception messages or native protocol payloads.
        val safe = when {
            radek.startsWith("[CHYBA") -> "[chyba zpracování]"
            radek.startsWith("[OCR") -> "[OCR událost]"
            radek.startsWith("[text") || radek.startsWith("[volby") -> "[herní text: událost]"
            radek.startsWith("[Xbox") -> "[Xbox: událost relace]"
            radek.startsWith("[PS5") -> "[PlayStation: událost relace]"
            radek.startsWith("[obraz:") -> "[dekodér: událost]"
            else -> "[technická událost]"
        }
        if (safe == posledniZapis) return
        posledniZapis = safe
        runCatching {
            // New file avoids displaying pre-upgrade unredacted text as a new log.
            val f = File(filesDir, "titulky-private-v2.log")
            if (f.length() > 300_000) f.writeText("")
            val cas = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            f.appendText("$cas $safe\\n")
        }
    }''' + s[end:]
