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


def restrict_provider_transport(t):
    # The Android provider requests are constructed here, not by account
    # authentication or console code. Do not modify global networking settings.
    t = once(t, '    private fun google(text: String, cilJazyk: String = cil, sourceJazyk: String = zdrojovyJazyk)',
        '''    private fun providerConnection(address: String): HttpURLConnection {
        val url = URL(address)
        val permitted = when (url.host) {
            "translate.googleapis.com" -> url.path == "/translate_a/single"
            "edge.microsoft.com" -> url.path == "/translate/auth"
            "api-edge.cognitive.microsofttranslator.com" -> url.path == "/translate"
            "api.mymemory.translated.net" -> url.path == "/get"
            else -> false
        }
        require(url.protocol == "https" && permitted && url.userInfo == null &&
            (url.port == -1 || url.port == 443) && url.ref == null) { "Nepovolená adresa překladače" }
        return (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Cache-Control", "no-store")
        }
    }

    private fun google(text: String, cilJazyk: String = cil, sourceJazyk: String = zdrojovyJazyk)''')
    if t.count('URL(adresa).openConnection() as HttpURLConnection') != 2:
        raise RuntimeError("Expected Google and MyMemory connections")
    t = t.replace('URL(adresa).openConnection() as HttpURLConnection', 'providerConnection(adresa)')
    t = once(t, 'URL("https://edge.microsoft.com/translate/auth").openConnection() as HttpURLConnection',
        'providerConnection("https://edge.microsoft.com/translate/auth")')
    t = once(t, 'URL("https://api-edge.cognitive.microsofttranslator.com/translate?from=$source&to=$target&api-version=3.0")\n            .openConnection() as HttpURLConnection',
        'providerConnection("https://api-edge.cognitive.microsofttranslator.com/translate?from=$source&to=$target&api-version=3.0")')
    return t
