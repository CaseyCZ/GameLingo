#!/usr/bin/env python3
"""Wire real Android source and target language choices into translation core.

Only changes the pinned upstream language-specific paths. It never replaces
Remote Play, subtitle placement, OAuth, Sony credentials or TV transport.
"""
from pathlib import Path
import re
import sys
from tv_translation_policy import tv_variant, strip_offline_translator, tv_translator, finalize_tv
from direct_tv_mode import patch_direct_tv
from translation_backpressure import bound_translator, bound_choices
from translation_privacy import protect_cache_writes, restrict_service_log, restrict_provider_transport

SRC = Path("app/src/main/java/cz/titulky/tv")


def once(text, old, new, file):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{file}: language integration anchor {old[:90]!r} occurred {count} times")
    return text.replace(old, new, 1)


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: wire_gamelingo_languages.py <upstream-checkout>")
    folder = Path(sys.argv[1]) / SRC
    translator = folder / "Prekladac.kt"
    t = strip_offline_translator(translator.read_text(encoding="utf8"))
    patch = [
        (
            'class Prekladac(context: android.content.Context? = null, cilovy: String = "cs") {',
            'class Prekladac(context: android.content.Context? = null, cilovy: String = "cs", zdrojovy: String = "en") {'
        ),
        (
            '    @Volatile var cil: String = cilovy\n        private set',
            '    @Volatile var cil: String = cilovy\n        private set\n'
            '    @Volatile var zdrojovyJazyk: String = zdrojovy\n        private set\n\n'
            '    private fun upravVstup(text: String): String =\n'
            '        if (zdrojovyJazyk == "en" && zdrojovyJazyk != cil) upravSlang(text.trim()) else text.trim()'
        ),
        (
            'val vstup = upravSlang(text.trim())',
            'val vstup = upravVstup(text)'
        ),
        (
            'https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=$cilJazyk',
            'https://translate.googleapis.com/translate_a/single?client=gtx&sl=$zdrojovyJazyk&tl=$cilJazyk'
        ),
        (
            'https://api-edge.cognitive.microsofttranslator.com/translate?from=en&to=$cil&api-version=3.0',
            'https://api-edge.cognitive.microsofttranslator.com/translate?from=$zdrojovyJazyk&to=$cil&api-version=3.0'
        ),
        (
            'https://api.mymemory.translated.net/get?langpair=en%7C$cil&q=',
            'https://api.mymemory.translated.net/get?langpair=$zdrojovyJazyk%7C$cil&q='
        )
    ]
    for old, new in patch:
        if old == 'val vstup = upravSlang(text.trim())':
            if t.count(old) != 3:
                raise RuntimeError("Expected exactly three translation entry points")
            t = t.replace(old, new)
        else:
            t = once(t, old, new, translator.name)
    # P1.3: bounded persistent cache, shared by all translator instances.
    # Apply after language integration so every request uses a stable pair.
    for name in ("GameLingoTranslationCache.kt",):
        (folder/name).write_text(Path(__file__).with_name(name).read_text(encoding="utf8"),encoding="utf8")
    # folder is app/src/main/java/cz/titulky/tv; src/androidTest is a sibling of main.
    test_dir=folder.parents[4]/"androidTest/java/cz/titulky/tv"
    test_dir.mkdir(parents=True, exist_ok=True)
    (test_dir/"GameLingoTranslationCacheTest.kt").write_text(
        Path(__file__).with_name("GameLingoTranslationCacheTest.kt").read_text(encoding="utf8"),encoding="utf8")
    t=t.replace("import android.util.LruCache\n","")
    t=once(t, '    private val cache = LruCache<String, String>(500)', '''    private val cacheContext = context?.applicationContext

    private fun currentPair(source: String, target: String) =
        !zavreno && zdrojovyJazyk == source && cil == target

    private fun cachedOrRun(text: String, source: String, target: String,
                            hotovo: (String) -> Unit, chyba: (String) -> Unit,
                            miss: () -> Unit) {
        if (!currentPair(source, target)) { chyba("Změna jazyka nebo relace"); return }
        if (text.isBlank()) { chyba("Prázdný vstup"); return }
        if (source == target) { zdroj = "originál"; hotovo(text); return }
        GameLingoTranslationCache.lookup(cacheContext, source, target, text) { cached ->
            ui.post {
                if (!currentPair(source, target)) chyba("Změna jazyka nebo relace")
                else if (cached != null) { zdroj = "paměť"; hotovo(cached) }
                else miss()
            }
        }
    }

    private fun remember(engine: String, source: String, target: String, text: String, result: String) {
        GameLingoTranslationCache.remember(cacheContext, engine, source, target, text, result)
    }''', translator.name)
    t=once(t, '''        val jazyk = cil
        cache.get(vstup)?.let { hotovo(it); return }
        if (zavreno) { chyba("Překladač je zavřený"); return }
        val strategie''', '''        val jazyk = cil
        val source = zdrojovyJazyk
        cachedOrRun(vstup, source, jazyk, hotovo, chyba) {
            prelozPribehMiss(vstup, source, jazyk, hotovo, chyba)
        }
    }

    private fun prelozPribehMiss(vstup: String, source: String, jazyk: String,
                                 hotovo: (String) -> Unit, chyba: (String) -> Unit) {
        val strategie''',translator.name)
    t=once(t, 'google(t, jazyk)', 'google(t, jazyk, source)',translator.name)
    t=once(t, 'if (!vysledek.isNullOrBlank() && cil == jazyk && !zavreno) cache.put(t, vysledek)\n                        callback(vysledek)', '''if (!currentPair(source, jazyk)) { callback(null); return@post }
                        if (!vysledek.isNullOrBlank()) remember("google-gtx-v1", source, jazyk, t, vysledek)
                        callback(vysledek)''',translator.name)
    t=once(t,'prelozOnlineMiss(t, ok, { err() })','prelozOnlineMiss(t, source, jazyk, ok, { err() }, skipGoogle = true)',translator.name)
    t=once(t,'if (zavreno || cil != jazyk) chyba("Změna jazyka nebo relace")','if (!currentPair(source, jazyk)) chyba("Změna jazyka nebo relace")',translator.name)
    t=once(t, '''        cache.get(vstup)?.let { zdroj = "paměť"; hotovo(it); return }
        // služba mezitím skončila (překladač zavřený) → nic nedělat, hlavně nespadnout
        if (zavreno) { chyba("překladač zavřený"); return }

        if (runCatching { sit.execute {''', '''        val source = zdrojovyJazyk
        val target = cil
        cachedOrRun(vstup, source, target, hotovo, chyba) {
            prelozOnlineMiss(vstup, source, target, hotovo, chyba)
        }
    }

    private fun prelozOnlineMiss(vstup: String, source: String, target: String,
                                 hotovo: (String) -> Unit, chyba: (String) -> Unit) {
        if (runCatching { sit.execute {
            if (!currentPair(source, target)) { ui.post { chyba("Změna jazyka nebo relace") }; return@execute }''',translator.name)
    if t.count("google(vstup)") != 2 or t.count("edge(vstup)") != 2:
        raise RuntimeError("Expected online provider retry calls")
    t=t.replace("google(vstup)","google(vstup, target, source)").replace("edge(vstup)","edge(vstup, target, source)")
    t=once(t,"myMemory(vstup)","myMemory(vstup, target, source)",translator.name)
    t=once(t, '''                if (!hotovyText.isNullOrBlank()) {
                    zdroj = odkud
                    cache.put(vstup, hotovyText)
                    hotovo(hotovyText)''', '''                if (!currentPair(source, target)) { chyba("Změna jazyka nebo relace"); return@post }
                if (!hotovyText.isNullOrBlank()) {
                    zdroj = odkud
                    val engine = when (odkud) {
                        "google" -> "google-gtx-v1"
                        "microsoft" -> "microsoft-edge-v1"
                        else -> "mymemory-v1"
                    }
                    remember(engine, source, target, vstup, hotovyText)
                    hotovo(hotovyText)''',translator.name)
    t=once(t,'''        cache.get(vstup)?.let { hotovo(it); return }
        chyba("Rychlý náhled není v paměti; čeká se na online překlad")''','''        val source = zdrojovyJazyk
        val target = cil
        cachedOrRun(vstup, source, target, hotovo, chyba) {
            chyba("Rychlý náhled není v paměti; čeká se na online překlad")
        }''',translator.name)
    t=once(t,'private fun google(text: String, cilJazyk: String = cil)', 'private fun google(text: String, cilJazyk: String = cil, sourceJazyk: String = zdrojovyJazyk)',translator.name)
    t=once(t,'&sl=$zdrojovyJazyk&tl=$cilJazyk','&sl=$sourceJazyk&tl=$cilJazyk',translator.name)
    t=once(t,'private fun edge(text: String)', 'private fun edge(text: String, target: String, source: String)',translator.name)
    t=once(t,'?from=$zdrojovyJazyk&to=$cil&api-version','?from=$source&to=$target&api-version',translator.name)
    t=once(t,'private fun myMemory(text: String)', 'private fun myMemory(text: String, target: String, source: String)',translator.name)
    t=once(t,'?langpair=$zdrojovyJazyk%7C$cil&q=','?langpair=$source%7C$target&q=',translator.name)
    if "cache.get(" in t or "cache.put(" in t or "LruCache" in t:
        raise RuntimeError("Old text-only cache not fully removed")
    # P1.4: malformed provider responses and empty model results are failures.
    old = 'if (vstup.isEmpty()) { hotovo(""); return }'
    if t.count(old) != 2:
        raise RuntimeError("Expected two blank-input success paths")
    t = t.replace(old, 'if (vstup.isEmpty()) { chyba("Prázdný vstup"); return }')
    t=once(t, '''        val jazyk = cil
        val source = zdrojovyJazyk
        cachedOrRun''', '''        if (vstup.isEmpty()) { chyba("Prázdný vstup"); return }
        val jazyk = cil
        val source = zdrojovyJazyk
        cachedOrRun''', translator.name)
    t=once(t, '''                val kus = kusy.optJSONArray(i) ?: continue
                sb.append(kus.optString(0, ""))''', '''                val kus = kusy.optJSONArray(i) ?: return null
                val segment = kus.opt(0) as? String ?: return null
                sb.append(segment)''', translator.name)
    t=once(t, '''        return JSONArray(json).getJSONObject(0).getJSONArray("translations")
            .getJSONObject(0).optString("text").trim().ifEmpty { null }''', '''        return (JSONArray(json).getJSONObject(0).getJSONArray("translations")
            .getJSONObject(0).opt("text") as? String)?.trim()?.ifEmpty { null }''', translator.name)
    t=once(t, '''        val preklad = json.getJSONObject("responseData").optString("translatedText").trim()''', '''        val preklad = (json.getJSONObject("responseData").opt("translatedText") as? String)?.trim()
            ?: return null''', translator.name)
    # P1.6: same two-failure / 30-second / one-probe policy on all
    # online providers, shared between standard and story requests.
    (folder/"GameLingoCircuitBreaker.kt").write_text(
        Path(__file__).with_name("GameLingoCircuitBreaker.kt").read_text(encoding="utf8"),encoding="utf8")
    unit_dir=folder.parents[4]/"test/java/cz/titulky/tv"
    unit_dir.mkdir(parents=True, exist_ok=True)
    (unit_dir/"GameLingoCircuitBreakerTest.kt").write_text(
        Path(__file__).with_name("GameLingoCircuitBreakerTest.kt").read_text(encoding="utf8"),encoding="utf8")
    t=once(t, '''    @Volatile private var googlePauzaDo = 0L
    @Volatile private var edgePauzaDo = 0L
    @Volatile private var myMemoryPauzaDo = 0L''', '''    private val googleBreaker = GameLingoCircuitBreaker()
    private val edgeBreaker = GameLingoCircuitBreaker()
    private val myMemoryBreaker = GameLingoCircuitBreaker()

    private fun guardedProvider(breaker: GameLingoCircuitBreaker, call: () -> String?): String? {
        val ticket = breaker.acquire(android.os.SystemClock.elapsedRealtime()) ?: return null
        var success: Boolean? = null
        try {
            val result = runCatching { call() }.getOrNull()?.takeIf { it.isNotBlank() }
            success = result != null
            return result
        } finally {
            breaker.complete(ticket, success, android.os.SystemClock.elapsedRealtime())
        }
    }

    private fun guardedGoogle(text: String, target: String, source: String): String? =
        guardedProvider(googleBreaker) {
            val result = runCatching { google(text, target, source) }.getOrNull()
            if (result.isNullOrBlank()) chybGooglu++
            result
        }''', translator.name)
    t=once(t, 'try { runCatching { google(t, jazyk, source) }.getOrNull() }',
        'try { guardedGoogle(t, jazyk, source) }', translator.name)
    t=once(t, '''            val ted = System.currentTimeMillis()
            var vysledek: String? = null
            var odkud = ""
            // 1) Google – 2 pokusy (udržované spojení mohl Google mezitím zavřít)
            if (ted >= googlePauzaDo) {
                vysledek = runCatching { google(vstup, target, source) }.getOrNull() ?: runCatching { google(vstup, target, source) }.getOrNull()
                if (vysledek.isNullOrBlank()) { googlePauzaDo = ted + 30_000L; chybGooglu++ } else odkud = "google"
            }
            // 2) Microsoft (překladač z prohlížeče Edge) – bez registrace
            if (vysledek.isNullOrBlank() && ted >= edgePauzaDo) {
                vysledek = runCatching { edge(vstup, target, source) }.getOrNull() ?: runCatching { edgeToken = null; edge(vstup, target, source) }.getOrNull()
                if (vysledek.isNullOrBlank()) edgePauzaDo = ted + 60_000L else odkud = "microsoft"
            }
            // 3) MyMemory – veřejná služba zdarma, bez registrace (denní limit)
            if (vysledek.isNullOrBlank() && ted >= myMemoryPauzaDo) {
                vysledek = runCatching { myMemory(vstup, target, source) }.getOrNull()
                if (vysledek.isNullOrBlank()) myMemoryPauzaDo = ted + 5 * 60_000L else odkud = "mymemory"
            }''', '''            var vysledek = guardedGoogle(vstup, target, source)
            var odkud = if (vysledek != null) "google" else ""
            if (vysledek.isNullOrBlank()) {
                vysledek = guardedProvider(edgeBreaker) {
                    val result = runCatching { edge(vstup, target, source) }.getOrNull()
                    if (result.isNullOrBlank()) edgeToken = null
                    result
                }
                if (!vysledek.isNullOrBlank()) odkud = "microsoft"
            }
            if (vysledek.isNullOrBlank() && vstup.toByteArray(Charsets.UTF_8).size <= 500) {
                vysledek = guardedProvider(myMemoryBreaker) { myMemory(vstup, target, source) }
                if (!vysledek.isNullOrBlank()) odkud = "mymemory"
            }''', translator.name)
    # Synthetic warmup must not consume the only probe or reset real failures.
    t=once(t, '''        if (System.currentTimeMillis() < googlePauzaDo) return
        if (zavreno) return
        runCatching { sit.execute { runCatching { google("ok") } } }''', '''        // The real next caption is the only half-open probe.
        // Avoid synthetic requests that would reset real failure counts.
        return''', translator.name)
    if any(name in t for name in ("googlePauzaDo", "edgePauzaDo", "myMemoryPauzaDo")):
        raise RuntimeError("Old per-provider pause bypass remains")
    root = Path(sys.argv[1])
    tv_variant(root)
    t = tv_translator(t)
    t = bound_translator(t)
    t = protect_cache_writes(t)
    t = restrict_provider_transport(t)
    translator.write_text(t, encoding="utf8")

    service = folder / "RemotePlayService.kt"
    s = service.read_text(encoding="utf8")
    patches = [
        (
            'Prekladac(this, nast.jazykNeboCs)',
            'Prekladac(this, GameLingoLanguages.target(this), GameLingoLanguages.game(this))'
        ),
        (
            '${nast.jazykNeboCs}:${rucniOblast()?',
            '${GameLingoLanguages.game(this)}:${GameLingoLanguages.target(this)}:${rucniOblast()?'
        ),
        (
            'val jazyk = nast.jazykNeboCs',
            'val jazyk = GameLingoLanguages.target(this)'
        ),
        (
            'jazyk == nast.jazykNeboCs',
            'jazyk == GameLingoLanguages.target(this@RemotePlayService)'
        ),
        (
            'prekladac.nastavJazyk(jazyk)',
            'prekladac.nastavZdroj(GameLingoLanguages.game(this@RemotePlayService))\n'
            '                    prekladac.nastavJazyk(jazyk)'
        ),
        (
            'val cil = nast.jazykNeboCs',
            'val cil = GameLingoLanguages.target(this)'
        ),
        (
            '        prekladac.nastavJazyk(cil)',
            '        prekladac.nastavZdroj(GameLingoLanguages.game(this))\n'
            '        prekladac.nastavJazyk(cil)'
        ),
        (
            'if (nast.jazykNeboCs != cil || oblast !=',
            'if (GameLingoLanguages.target(this) != cil || oblast !='
        )
    ]
    for old, new in patches:
        s = once(s, old, new, service.name)
    old = 'prekladac.nastavJazyk(nast.jazykNeboCs)'
    if s.count(old) != 2:
        raise RuntimeError("Expected game-text and option-text translation entry points")
    s = s.replace(old,
        'prekladac.nastavZdroj(GameLingoLanguages.game(this))\n'
        '        prekladac.nastavJazyk(GameLingoLanguages.target(this))')
    # P1.4: no raw OCR text in the success/render path, even if a
    # multi-line translation or an individual choice failed.
    s=once(s, '''                if (pribeh && selhalo) chybaPribehu()
                else if (!(rychle && selhalo)) hotovo(vysledky.filterNotNull().joinToString("\\n"))''', '''                if (selhalo) {
                    if (pribeh) chybaPribehu()
                } else hotovo(vysledky.filterNotNull().joinToString("\\n"))''', service.name)
    s=once(s, '''            val chyba: (String) -> Unit = { selhalo = true; vysledky[i] = replika; hotovaJedna() }''', '''            val chyba: (String) -> Unit = {
                selhalo = true
                vysledky[i] = null
                zapis("[chyba překladu dialogu]")
                hotovaJedna()
            }''', service.name)
    s=once(s, '''            Propojeni.Vysilac.nastavVolby(volby.map { prekladyVoleb[it] ?: it }, volbyVybrana)''', '''            val prelozene = volby.mapNotNull { prekladyVoleb[it]?.takeIf { value -> value.isNotBlank() } }
            if (prelozene.size != volby.size) {
                Propojeni.Vysilac.nastavVolby(emptyList(), -1)
                zapis("[chyba překladu voleb: neúplný seznam]")
                return
            }
            Propojeni.Vysilac.nastavVolby(prelozene, volbyVybrana)''', service.name)
    s=once(s, '''        }, { e -> zapis("[text: chyba překladu $e]") })''', '''        }, { _ ->
            ui.post {
                if (bezi && relace == relaceCteni.get() && Volby.zapnuto && Volby.rezimText &&
                    Volby.revize == revize && textVRamecku.text == text) {
                    Propojeni.Vysilac.nastavVolby(emptyList(), -1)
                    zapis("[text: chyba překladu]")
                }
            }
        })''', service.name)
    # P1.5: retain the pinned Android two-read stabilization and
    # progressive correction. Bound ordinary dialogue lifetime; use the same
    # monotonic start on display and on source disappearance. Story is independent.
    s=once(s, '        if (ukazanoKdy == 0L || System.currentTimeMillis() - ukazanoKdy > 400) ukazanoKdy = System.currentTimeMillis()',
        '        ukazanoKdy = android.os.SystemClock.elapsedRealtime()', service.name)
    s=once(s, '        ukaz(cesky, 15000, box, radku, barvyJmen)',
        '        ukaz(cesky, 4000, box, radku, barvyJmen)', service.name)
    s=once(s, '''        val minDoba = 1800L + 60L * ukazanaDelka
        val zbyva = ukazanoKdy + minDoba - System.currentTimeMillis()
        ui.removeCallbacks(schovatCesky)
        ukazanoKdy = 0L
        if (zbyva <= 0) ukaz("", 0) else ui.postDelayed(schovatCesky, zbyva)''',
        '''        val zbyva = if (ukazanoKdy == 0L) 0L else
            (ukazanoKdy + 4000L - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        ui.removeCallbacks(schovatCesky)
        // Keep the original display start even on repeated empty observations.
        if (zbyva <= 0) ukaz("", 0) else ui.postDelayed(schovatCesky, zbyva)''', service.name)
    # The old story and game-text timers intentionally stay independent.
    # P1.7: bounded rolling performance statistics in the existing local log.
    (folder/"GameLingoPerformance.kt").write_text(
        Path(__file__).with_name("GameLingoPerformance.kt").read_text(encoding="utf8"),encoding="utf8")
    unit_dir=folder.parents[4]/"test/java/cz/titulky/tv"
    (unit_dir/"GameLingoPerformanceWindowTest.kt").write_text(
        Path(__file__).with_name("GameLingoPerformanceWindowTest.kt").read_text(encoding="utf8"),encoding="utf8")
    # Xbox metrics observe decoded frames before any OCR sampling.
    xbox = folder/"XboxSpojeni.kt"
    xbox_text = xbox.read_text(encoding="utf8")
    xbox_text = once(xbox_text, '        private val h = android.os.Handler(vlakno.looper)',
        '        private val h = android.os.Handler(vlakno.looper)\n'
        '        private val videoMetrics = GameLingoXboxVideoMetrics()', xbox.name)
    xbox_text = once(xbox_text, '        fun spust() {\n            bezi = true',
        '        fun spust() {\n            GameLingoXboxDiagnostics.begin(videoMetrics)\n            bezi = true', xbox.name)
    xbox_text = once(xbox_text, '            sirka = f.rotatedWidth; vyska = f.rotatedHeight',
        '            sirka = f.rotatedWidth; vyska = f.rotatedHeight\n'
        '            videoMetrics.frame(sirka, vyska, android.os.SystemClock.elapsedRealtime())', xbox.name)
    xbox_text = once(xbox_text, '            konec = true\n            bezi = false',
        '            konec = true\n            videoMetrics.stop()\n            bezi = false', xbox.name)
    # Non-blocking native WebRTC reports every 5 s, scoped to this Xbox session.
    xbox_text = once(xbox_text, '        private val videoMetrics = GameLingoXboxVideoMetrics()',
        '''        private val videoMetrics = GameLingoXboxVideoMetrics()
        private val statsLock = Any()
        private var statsPending = false

        private fun startNetworkStats(peer: PeerConnection) {
            val poll = object : Runnable {
                override fun run() {
                    if (konec) return
                    synchronized(statsLock) {
                        if (!konec && pc === peer && !statsPending) {
                            statsPending = true
                            runCatching {
                                peer.getStats { report ->
                                    h.post {
                                        statsPending = false
                                        if (!konec && pc === peer) readNetworkStats(report)
                                    }
                                }
                            }.onFailure { statsPending = false }
                        }
                    }
                    if (!konec) h.postDelayed(this, 5000L)
                }
            }
            h.postDelayed(poll, 5000L)
        }
        private fun readNetworkStats(report: org.webrtc.RTCStatsReport) {
            val all = report.statsMap
            fun number(m: Map<String, Any>, key: String) = (m[key] as? Number)?.toDouble()
            val video = all.values.filter { it.type == "inbound-rtp" &&
                (it.members["kind"] ?: it.members["mediaType"]) == "video" }
                .maxByOrNull { number(it.members,"bytesReceived") ?: -1.0 } ?: return
            val v = video.members
            val transport = (v["transportId"] as? String)?.let { all[it] }
            val pairId = transport?.members?.get("selectedCandidatePairId") as? String
            val pair = pairId?.let { all[it] } ?: all.values.filter { it.type == "candidate-pair" &&
                it.members["state"] == "succeeded" && it.members["nominated"] == true }.singleOrNull()
            videoMetrics.network.record(GameLingoXboxNetworkMetrics.Sample(video.id,
                android.os.SystemClock.elapsedRealtime(), number(v,"bytesReceived"), number(v,"packetsReceived"),
                number(v,"packetsLost"), number(v,"jitter"), pair?.members?.let { number(it,"currentRoundTripTime") }))
        }
''', xbox.name)
    xbox_text = once(xbox_text, '            pc = p\n            // kanály jako webový přehrávač Xboxu',
        '            pc = p\n            startNetworkStats(p)\n            // kanály jako webový přehrávač Xboxu', xbox.name)
    xbox_text = once(xbox_text, '                runCatching { pc?.close() }',
        '                synchronized(statsLock) { runCatching { pc?.close() } }', xbox.name)
    xbox_text = once(xbox_text, '                runCatching { pc?.dispose() }\n                pc = null',
        '                synchronized(statsLock) { runCatching { pc?.dispose() }; pc = null }', xbox.name)
    xbox.write_text(xbox_text, encoding="utf8")
    s=once(s, '    private var stOd = 0L',
        '    private val performance = GameLingoPerformance()\n    private var stOd = 0L', service.name)
    s=once(s, '        ui.post { zapis(zprava) }\n        stCteni = 0;',
        '        val performanceText = performance.summary()\n'
        '        val deviceText = performance.device(this)\n'
        '        ui.post { zapis(zprava); zapis(performanceText); zapis(deviceText) }\n        stCteni = 0;', service.name)
    s=once(s, '                if (!aktivni) obrazStav.zacni()\n                automat = true',
        '''                if (!aktivni) {
                    obrazStav.zacni()
                    if (performance.begin(this)) zapis("[Diagnostika: předchozí relace nebyla řádně uzavřena; příčina neznámá]")
                }
                automat = true''', service.name)
    s=once(s, '    override fun onDestroy() {\n        zanikla = true',
        '    override fun onDestroy() {\n        zapis(performance.summary())\n        performance.end(this)\n        zanikla = true', service.name)
    s=once(s, '        val otiskObrazu = OtiskOcr.sedy(vyrez.jas, vyrez.w, vyrez.h)',
        '        val perfToken = performance.token()\n        val perfOcrStart = performance.now()\n'
        '        val otiskObrazu = OtiskOcr.sedy(vyrez.jas, vyrez.w, vyrez.h)', service.name)
    s=once(s, '                // Dialog nenalezen, ale je tu nápověda',
        '                performance.record(perfToken, "Dialog OCR", performance.now() - perfOcrStart)\n'
        '                // Dialog nenalezen, ale je tu nápověda', service.name)
    s=once(s, '        var dobryHotov = false\n        fun ukazJestli',
        '        val perfToken = performance.token()\n        val perfStart = performance.now()\n'
        '        val perfOcrMs = casCteni.coerceAtLeast(0L)\n        var dobryHotov = false\n        fun ukazJestli', service.name)
    s=once(s, '            ukazCesky(cesky, box, radku, barvyJmen)',
        '            performance.record(perfToken, "Dialog celkem k výstupu", perfOcrMs + performance.now() - perfStart)\n'
        '            ukazCesky(cesky, box, radku, barvyJmen)', service.name)
    s=once(s, '        val vysledky = arrayOfNulls<String>(repliky.size)',
        '        val perfToken = performance.token()\n        val perfStart = performance.now()\n'
        '        val vysledky = arrayOfNulls<String>(repliky.size)', service.name)
    s=once(s, '            if (zbyva == 0) {\n                if (selhalo) {',
        '            if (zbyva == 0) {\n'
        '                performance.record(perfToken, if (pribeh) "Story překlad" else "Dialog překlad", performance.now() - perfStart)\n'
        '                if (selhalo) performance.failure(perfToken)\n'
        '                if (selhalo) {', service.name)
    s=once(s, '        cteckaVoleb.prectiVolby(bmp, { radky ->',
        '        val perfToken = performance.token()\n        val perfOcrStart = performance.now()\n'
        '        cteckaVoleb.prectiVolby(bmp, { radky ->', service.name)
    s=once(s, '            volbyCtu = false\n            val boxy = radky.map',
        '            volbyCtu = false\n            performance.record(perfToken, "Herní text OCR", performance.now() - perfOcrStart)\n'
        '            val boxy = radky.map', service.name)
    s=once(s, '        prekladac.preloz(text, { cesky ->',
        '        val perfToken = performance.token()\n        val perfStart = performance.now()\n'
        '        prekladac.preloz(text, { cesky ->\n'
        '            performance.record(perfToken, "Herní text překlad", performance.now() - perfStart)', service.name)
    s=once(s, '        }, { _ ->\n            ui.post {\n                if (bezi && relace == relaceCteni.get() && Volby.zapnuto && Volby.rezimText',
        '        }, { _ ->\n            performance.failure(perfToken)\n            ui.post {\n'
        '                if (bezi && relace == relaceCteni.get() && Volby.zapnuto && Volby.rezimText', service.name)
    s=once(s, '        var zbyva = chybi.size\n        for (v in chybi) {',
        '        val perfToken = performance.token()\n        val perfStart = performance.now()\n'
        '        var zbyva = chybi.size\n        for (v in chybi) {', service.name)
    s=once(s, '                    if (--zbyva == 0) posli()',
        '                    if (--zbyva == 0) {\n'
        '                        performance.record(perfToken, "Herní text překlad", performance.now() - perfStart)\n'
        '                        posli()\n                    }', service.name)
    s=once(s, '                ui.post { if (--zbyva == 0) posli() }',
        '                performance.failure(perfToken)\n'
        '                ui.post { if (--zbyva == 0) {\n'
        '                    performance.record(perfToken, "Herní text překlad", performance.now() - perfStart)\n'
        '                    posli()\n                } }', service.name)
    # Propagate the yellow-area OCR timestamp and generation into the
    # existing callback paths so total latency is tied to the actual read.
    s=once(s, 'if (rezimText) zpracujText(radky.map { it.first }, otiskTextu) else zpracujVolby(radky.map { it.first }, vybrana)',
        'if (rezimText) zpracujText(radky.map { it.first }, otiskTextu, perfToken, perfOcrStart) '
        'else zpracujVolby(radky.map { it.first }, vybrana, perfToken, perfOcrStart)', service.name)
    s=once(s, 'private fun zpracujText(radky: List<String>, otiskObrazu: String?)',
        'private fun zpracujText(radky: List<String>, otiskObrazu: String?, readToken: Long, readStarted: Long)', service.name)
    s=once(s, 'private fun zpracujVolby(surove: List<String>, vybrana: Int)',
        'private fun zpracujVolby(surove: List<String>, vybrana: Int, readToken: Long, readStarted: Long)', service.name)
    s=once(s, '        prekladyVoleb[text]?.let { Propojeni.Vysilac.nastavVolby(listOf(it), Volby.VYBRANA_TEXT); return }',
        '''        prekladyVoleb[text]?.let {
            Propojeni.Vysilac.nastavVolby(listOf(it), Volby.VYBRANA_TEXT)
            performance.record(readToken, "Herní text celkem k výstupu", performance.now() - readStarted)
            return
        }''', service.name)
    s=once(s, '''                    Volby.revize == revize && textVRamecku.text == text)
                    Propojeni.Vysilac.nastavVolby(listOf(cesky), Volby.VYBRANA_TEXT)''', '''                    Volby.revize == revize && textVRamecku.text == text) {
                    Propojeni.Vysilac.nastavVolby(listOf(cesky), Volby.VYBRANA_TEXT)
                    performance.record(readToken, "Herní text celkem k výstupu", performance.now() - readStarted)
                }''', service.name)
    s=once(s, '            Propojeni.Vysilac.nastavVolby(prelozene, volbyVybrana)',
        '            Propojeni.Vysilac.nastavVolby(prelozene, volbyVybrana)\n'
        '            performance.record(readToken, "Herní text celkem k výstupu", performance.now() - readStarted)', service.name)
    # P1.8: independently paced yellow-area OCR; keep the busy/token gate.
    s=once(s, '    private fun ctiVolby(sirka: Int, vyska: Int, precti: (Int, Int, Int, Int) -> IntArray) {',
        '    @Volatile private var gameTextOcrCostMs = 0L\n\n'
        '    private fun ctiVolby(sirka: Int, vyska: Int, precti: (Int, Int, Int, Int) -> IntArray) {', service.name)
    s=once(s, '        volbyDalsi = ted + 250',
        '        volbyDalsi = ted + GameLingoOCRConfig.gameTextIntervalMs(Volby.rezimText, !telefon && uspora, gameTextOcrCostMs)', service.name)
    s=once(s, '            performance.record(perfToken, "Herní text OCR", performance.now() - perfOcrStart)',
        '            val readCost = (performance.now() - perfOcrStart).coerceAtLeast(0L)\n'
        '            gameTextOcrCostMs = if (gameTextOcrCostMs == 0L) readCost else (gameTextOcrCostMs * 3 + readCost) / 4\n'
        '            performance.record(perfToken, "Herní text OCR", readCost)', service.name)
    s=once(s, '        stabilitaTitulku.reset()\n        textVRamecku.reset()',
        '        gameTextOcrCostMs = 0L\n        stabilitaTitulku.reset()\n        textVRamecku.reset()', service.name)
    # Street mode must not reject a two-letter subtitle in the service after
    # the native reader accepted it. Existing sentence/ROI checks stay active.
    if s.count('text.length < 4') != 3:
        raise RuntimeError("Expected three dialogue length checks")
    s=s.replace('text.length < 4', 'text.length < GameLingoOCRConfig.minimumDialogueLength()')
    (unit_dir/"GameLingoOCRCadenceTest.kt").write_text(
        Path(__file__).with_name("GameLingoOCRCadenceTest.kt").read_text(encoding="utf8"),encoding="utf8")
    (folder/"GameLingoDialogueText.kt").write_text(
        Path(__file__).with_name("GameLingoDialogueText.kt").read_text(encoding="utf8"),encoding="utf8")
    (unit_dir/"GameLingoDialogueTextTest.kt").write_text(
        Path(__file__).with_name("GameLingoDialogueTextTest.kt").read_text(encoding="utf8"),encoding="utf8")
    (folder/"GameLingoFrameGate.kt").write_text(
        Path(__file__).with_name("GameLingoFrameGate.kt").read_text(encoding="utf8"),encoding="utf8")
    (unit_dir/"GameLingoFrameGateTest.kt").write_text(
        Path(__file__).with_name("GameLingoFrameGateTest.kt").read_text(encoding="utf8"),encoding="utf8")
    # P1.9: context snapshots at OCR/translation boundaries. The old
    # text-only game-choice map must never mix source or target languages.
    transport = folder/"Propojeni.kt"
    p = transport.read_text(encoding="utf8")
    p = once(p, '        @Volatile private var generace = 0L',
        '        @Volatile private var generace = 0L\n        fun outputGeneration(): Long = generace', transport.name)
    transport.write_text(p, encoding="utf8")
    s=once(s, '    private val prekladyVoleb = HashMap<String, String>()',
        '''    private data class TranslationContext(val language: Long, val source: String, val target: String,
                                              val output: Long, val session: Long)
    private fun translationContext() = TranslationContext(GameLingoLanguages.revision,
        GameLingoLanguages.game(this), GameLingoLanguages.target(this),
        Propojeni.Vysilac.outputGeneration(), relaceCteni.get())
    private fun currentTranslation(context: TranslationContext) =
        bezi && context == translationContext()
    private data class GameTextKey(val source: String, val target: String, val text: String)
    private fun gameTextKey(context: TranslationContext, text: String) =
        GameTextKey(context.source, context.target, text)
    private val prekladyVoleb = HashMap<GameTextKey, String>()
    private var gameTextContext: TranslationContext? = null
    private var gameTextGeneration = 0L
    private fun updateGameTextContext(context: TranslationContext) {
        if (gameTextContext == context) return
        gameTextContext = context
        gameTextGeneration++
        textVRamecku.reset()
        volbyPosledni = emptyList()
        Propojeni.Vysilac.nastavVolby(emptyList(), -1)
    }''', service.name)
    s=once(s, '        val token = platnostPrekladu.novy()\n        val oblast =',
        '        val translationContext = translationContext()\n        val token = platnostPrekladu.novy()\n        val oblast =', service.name)
    s=once(s, '            if (!bezi || !nast.dialogy || !platnostPrekladu.plati(token)) return',
        '            if (!currentTranslation(translationContext) || !nast.dialogy || !platnostPrekladu.plati(token)) return', service.name)
    s=once(s, '        val klic = rucni?.joinToString(",") ?: "auto"',
        '        val klic = (rucni?.joinToString(",") ?: "auto") + "|" + translationContext().toString()', service.name)
    # Capture the language/output/session before the yellow OCR begins.
    s=once(s, '        val rezimText = Volby.rezimText\n        val o = Volby.oblast.copyOf()',
        '        val context = translationContext()\n        val rezimText = Volby.rezimText\n        val o = Volby.oblast.copyOf()', service.name)
    s=once(s, 'Volby.revize != revize || relace != relaceCteni.get()) return@post\n                if (rezimText)',
        'Volby.revize != revize || relace != relaceCteni.get() || !currentTranslation(context)) return@post\n'
        '                updateGameTextContext(context)\n                if (rezimText)', service.name)
    s=once(s, 'zpracujText(radky.map { it.first }, otiskTextu, perfToken, perfOcrStart)',
        'zpracujText(radky.map { it.first }, otiskTextu, perfToken, perfOcrStart, context)', service.name)
    s=once(s, 'zpracujVolby(radky.map { it.first }, vybrana, perfToken, perfOcrStart)',
        'zpracujVolby(radky.map { it.first }, vybrana, perfToken, perfOcrStart, context)', service.name)
    s=once(s, 'private fun zpracujText(radky: List<String>, otiskObrazu: String?, readToken: Long, readStarted: Long)',
        'private fun zpracujText(radky: List<String>, otiskObrazu: String?, readToken: Long, readStarted: Long, context: TranslationContext)', service.name)
    s=once(s, 'private fun zpracujVolby(surove: List<String>, vybrana: Int, readToken: Long, readStarted: Long)',
        'private fun zpracujVolby(surove: List<String>, vybrana: Int, readToken: Long, readStarted: Long, context: TranslationContext)', service.name)
    s=once(s, '        if (text.isEmpty()) {\n            volbyPosledni = emptyList()',
        '        val generation = ++gameTextGeneration\n        if (text.isEmpty()) {\n            volbyPosledni = emptyList()', service.name)
    s=once(s, '        prekladyVoleb[text]?.let {',
        '        val cacheKey = gameTextKey(context, text)\n        prekladyVoleb[cacheKey]?.let {', service.name)
    s=once(s, '''                prekladyVoleb[text] = cesky
                if (bezi && relace == relaceCteni.get() && Volby.zapnuto && Volby.rezimText &&
                    Volby.revize == revize && textVRamecku.text == text) {''', '''                if (currentTranslation(context) && generation == gameTextGeneration &&
                    relace == relaceCteni.get() && Volby.zapnuto && Volby.rezimText &&
                    Volby.revize == revize && textVRamecku.text == text) {
                    prekladyVoleb[cacheKey] = cesky''', service.name)
    s=once(s, '''                if (bezi && relace == relaceCteni.get() && Volby.zapnuto && Volby.rezimText &&
                    Volby.revize == revize && textVRamecku.text == text) {
                    Propojeni.Vysilac.nastavVolby(emptyList(), -1)
                    zapis("[text: chyba překladu]")''', '''                if (currentTranslation(context) && generation == gameTextGeneration &&
                    relace == relaceCteni.get() && Volby.zapnuto && Volby.rezimText &&
                    Volby.revize == revize && textVRamecku.text == text) {
                    Propojeni.Vysilac.nastavVolby(emptyList(), -1)
                    zapis("[text: chyba překladu]")
                    ui.postDelayed({
                        if (currentTranslation(context) && generation == gameTextGeneration &&
                            Volby.revize == revize && textVRamecku.text == text) textVRamecku.reset()
                    }, 2000L)''', service.name)
    s=once(s, '                    volbyPosledni = emptyList()\n                    Propojeni.Vysilac.nastavVolby(emptyList(), -1)',
        '                    gameTextGeneration++\n                    volbyPosledni = emptyList()\n                    Propojeni.Vysilac.nastavVolby(emptyList(), -1)', service.name)
    s=once(s, '        val novy = volby != volbyPosledni\n        volbyPosledni = volby',
        '        val novy = volby != volbyPosledni\n        if (novy) gameTextGeneration++\n        val generation = gameTextGeneration\n        volbyPosledni = volby', service.name)
    s=once(s, '        val chybi = volby.filter { it !in prekladyVoleb }',
        '        val chybi = volby.filter { gameTextKey(context, it) !in prekladyVoleb }', service.name)
    s=once(s, '            if (!bezi || relace != relaceCteni.get() || !Volby.zapnuto || Volby.rezimText ||',
        '            if (!currentTranslation(context) || generation != gameTextGeneration || relace != relaceCteni.get() || !Volby.zapnuto || Volby.rezimText ||', service.name)
    s=once(s, 'volby.mapNotNull { prekladyVoleb[it]?.takeIf',
        'volby.mapNotNull { prekladyVoleb[gameTextKey(context, it)]?.takeIf', service.name)
    s=once(s, '                    prekladyVoleb[v] = opraveno',
        '''                    if (!currentTranslation(context) || generation != gameTextGeneration ||
                        Volby.revize != revize || !Volby.zapnuto || Volby.rezimText ||
                        volbyPosledni != volby) return@post
                    prekladyVoleb[gameTextKey(context, v)] = opraveno''', service.name)
    # P2.3: local opt-in process history; only accepted current results.
    for name in ("GameLingoTranslationHistory.kt", "GameLingoHistoryUi.kt"):
        (folder/name).write_text(Path(__file__).with_name(name).read_text(encoding="utf8"),encoding="utf8")
    (unit_dir/"GameLingoTranslationHistoryTest.kt").write_text(
        Path(__file__).with_name("GameLingoTranslationHistoryTest.kt").read_text(encoding="utf8"),encoding="utf8")
    s=once(s, '            ukazCesky(cesky, box, radku, barvyJmen)',
        '            if (dobry) GameLingoTranslationHistory.append("Dialog", translationContext.source, translationContext.target, repliky.joinToString("\\n"), cesky)\n'
        '            ukazCesky(cesky, box, radku, barvyJmen)', service.name)
    s=once(s, '            Propojeni.Vysilac.nastavVolby(listOf(it), Volby.VYBRANA_TEXT)',
        '            GameLingoTranslationHistory.append("Herní text", context.source, context.target, text, it)\n'
        '            Propojeni.Vysilac.nastavVolby(listOf(it), Volby.VYBRANA_TEXT)', service.name)
    s=once(s, '                    prekladyVoleb[cacheKey] = cesky',
        '                    prekladyVoleb[cacheKey] = cesky\n'
        '                    GameLingoTranslationHistory.append("Herní text", context.source, context.target, text, cesky)', service.name)
    s=once(s, '            Propojeni.Vysilac.nastavVolby(prelozene, volbyVybrana)',
        '            GameLingoTranslationHistory.append("Volby", context.source, context.target, volby.joinToString("\\n"), prelozene.joinToString("\\n"))\n'
        '            Propojeni.Vysilac.nastavVolby(prelozene, volbyVybrana)', service.name)
    # P2.4: local postprocessing, provider cache and raw choice map stay clean.
    for name in ("GameLingoGlossary.kt", "GameLingoGlossaryStore.kt", "GameLingoGlossaryUi.kt"):
        (folder/name).write_text(Path(__file__).with_name(name).read_text(encoding="utf8"),encoding="utf8")
    (unit_dir/"GameLingoGlossaryTest.kt").write_text(
        Path(__file__).with_name("GameLingoGlossaryTest.kt").read_text(encoding="utf8"),encoding="utf8")
    s=once(s, 'val output: Long, val session: Long)',
        'val output: Long, val session: Long, val glossary: Long)', service.name)
    s=once(s, 'Propojeni.Vysilac.outputGeneration(), relaceCteni.get())',
        'Propojeni.Vysilac.outputGeneration(), relaceCteni.get(), GameLingoGlossaryStore.generation())', service.name)
    s=once(s, '''            if (dobry) GameLingoTranslationHistory.append("Dialog", translationContext.source, translationContext.target, repliky.joinToString("\\n"), cesky)
            ukazCesky(cesky, box, radku, barvyJmen)''',
        '''            val original = repliky.joinToString("\\n")
            val corrected = GameLingoGlossaryStore.apply(this, original, cesky, translationContext.source, translationContext.target)
            if (dobry) GameLingoTranslationHistory.append("Dialog", translationContext.source, translationContext.target, original, corrected)
            ukazCesky(corrected, box, radku, barvyJmen)''', service.name)
    s=once(s, '''            GameLingoTranslationHistory.append("Herní text", context.source, context.target, text, it)
            Propojeni.Vysilac.nastavVolby(listOf(it), Volby.VYBRANA_TEXT)''',
        '''            val corrected = GameLingoGlossaryStore.apply(this, text, it, context.source, context.target)
            GameLingoTranslationHistory.append("Herní text", context.source, context.target, text, corrected)
            Propojeni.Vysilac.nastavVolby(listOf(corrected), Volby.VYBRANA_TEXT)''', service.name)
    s=once(s, '''                    prekladyVoleb[cacheKey] = cesky
                    GameLingoTranslationHistory.append("Herní text", context.source, context.target, text, cesky)
                    Propojeni.Vysilac.nastavVolby(listOf(cesky), Volby.VYBRANA_TEXT)''',
        '''                    prekladyVoleb[cacheKey] = cesky
                    val corrected = GameLingoGlossaryStore.apply(this, text, cesky, context.source, context.target)
                    GameLingoTranslationHistory.append("Herní text", context.source, context.target, text, corrected)
                    Propojeni.Vysilac.nastavVolby(listOf(corrected), Volby.VYBRANA_TEXT)''', service.name)
    s=once(s, '''            GameLingoTranslationHistory.append("Volby", context.source, context.target, volby.joinToString("\\n"), prelozene.joinToString("\\n"))
            Propojeni.Vysilac.nastavVolby(prelozene, volbyVybrana)''',
        '''            val corrected = prelozene.mapIndexed { index, value ->
                GameLingoGlossaryStore.apply(this, volby[index], value, context.source, context.target)
            }
            GameLingoTranslationHistory.append("Volby", context.source, context.target, volby.joinToString("\\n"), corrected.joinToString("\\n"))
            Propojeni.Vysilac.nastavVolby(corrected, volbyVybrana)''', service.name)
    # A malformed saved OCR area or tiny decoder frame must not reach
    # coerceIn with a negative upper bound, nor allocate an invalid bitmap.
    # This applies to all frame sources, including the new direct-TV YUV path.
    s=once(s, '        val o = Volby.oblast.copyOf()\n        val x =',
        '''        val o = Volby.oblast.copyOf()
        if (sirka < 16 || vyska < 16 || o.size != 4 ||
            !o.all { it.isFinite() && it in 0f..1f } ||
            o[2] <= o[0] || o[3] <= o[1]) return
        val x =''', service.name)
    s = bound_choices(s, folder)
    s = restrict_service_log(s)
    service.write_text(s, encoding="utf8")
    if tv_variant(root):
        patch_direct_tv(folder)
        install_tv_theme(root)
    finalize_tv(root, folder)
    print("GameLingo source and target languages connected to real Android translation")


def install_tv_theme(root):
    """TV-only theme: native confirmations, input dialogs and loading windows
    must use our palette even when opened by inherited screens."""
    res = root / "app/src/main/res"
    for kind in ("values", "drawable"):
        (res/kind).mkdir(parents=True, exist_ok=True)
    (res/"values/gamelingo_tv_theme.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="GameLingoTVTheme" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">#080E1B</item>
        <item name="android:colorBackground">#080E1B</item>
        <item name="android:colorAccent">#02D6FF</item>
        <item name="android:colorControlActivated">#02D6FF</item>
        <item name="android:colorControlNormal">#A2B4D6</item>
        <item name="android:textColorPrimary">#FFFFFF</item>
        <item name="android:textColorSecondary">#A2B4D6</item>
        <item name="android:alertDialogTheme">@style/GameLingoTVAlert</item>
        <item name="android:dialogTheme">@style/GameLingoTVDialog</item>
        <item name="android:buttonStyle">@style/GameLingoTVButton</item>
    </style>
    <style name="GameLingoTVFullscreen" parent="@style/GameLingoTVTheme">
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowNoTitle">true</item>
    </style>
    <style name="GameLingoTVDialog" parent="@android:style/Theme.Material.Dialog.NoActionBar">
        <item name="android:windowBackground">@drawable/gamelingo_tv_dialog</item>
        <item name="android:colorBackground">#101A2C</item>
        <item name="android:colorAccent">#02D6FF</item>
        <item name="android:colorControlActivated">#02D6FF</item>
        <item name="android:textColorPrimary">#FFFFFF</item>
        <item name="android:textColorSecondary">#A2B4D6</item>
        <item name="android:alertDialogTheme">@style/GameLingoTVAlert</item>
        <item name="android:buttonStyle">@style/GameLingoTVButton</item>
    </style>
    <style name="GameLingoTVAlert" parent="@android:style/Theme.Material.Dialog.Alert">
        <item name="android:windowBackground">@drawable/gamelingo_tv_dialog</item>
        <item name="android:colorBackground">#101A2C</item>
        <item name="android:colorAccent">#02D6FF</item>
        <item name="android:colorControlActivated">#02D6FF</item>
        <item name="android:colorControlNormal">#A2B4D6</item>
        <item name="android:colorControlHighlight">#5530CFFF</item>
        <item name="android:textColorPrimary">#FFFFFF</item>
        <item name="android:textColorSecondary">#A2B4D6</item>
        <item name="android:buttonBarButtonStyle">@style/GameLingoTVButton</item>
        <item name="android:buttonStyle">@style/GameLingoTVButton</item>
        <item name="android:listChoiceBackgroundIndicator">@drawable/gamelingo_tv_focus</item>
        <item name="android:listSelector">@drawable/gamelingo_tv_focus</item>
        <item name="android:windowMinWidthMajor">42%</item>
        <item name="android:windowMinWidthMinor">70%</item>
    </style>
    <style name="GameLingoTVButton" parent="@android:style/Widget.Material.Button">
        <item name="android:background">@drawable/gamelingo_tv_focus</item>
        <item name="android:textColor">#FFFFFF</item>
        <item name="android:textAllCaps">false</item>
        <item name="android:minHeight">44dp</item>
        <item name="android:paddingLeft">12dp</item>
        <item name="android:paddingRight">12dp</item>
    </style>
</resources>
""", encoding="utf8")
    (res/"drawable/gamelingo_tv_dialog.xml").write_text("""<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#101A2C"/>
    <stroke android:width="1dp" android:color="#34445D"/>
    <corners android:radius="18dp"/>
</shape>
""",encoding="utf8")
    (res/"drawable/gamelingo_tv_focus.xml").write_text("""<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true"><shape><solid android:color="#203B55"/><stroke android:width="3dp" android:color="#02D6FF"/><corners android:radius="12dp"/></shape></item>
    <item android:state_pressed="true"><shape><solid android:color="#254A60"/><corners android:radius="12dp"/></shape></item>
    <item><shape><solid android:color="#142033"/><stroke android:width="1dp" android:color="#34445D"/><corners android:radius="12dp"/></shape></item>
</selector>
""",encoding="utf8")
    manifest=root/"app/src/main/AndroidManifest.xml"
    source=manifest.read_text(encoding="utf8")
    source, count = re.subn(r'(<application\b[^>]*\bandroid:theme=")[^"]+(")', r'\1@style/GameLingoTVTheme\2', source, count=1)
    if count != 1: raise RuntimeError("TV application theme anchor missing")
    manifest.write_text(source,encoding="utf8")
    # Explicit theme for every application-owned AlertDialog, including
    # inherited PlayStation/Xbox, login, pairing and developer screens.
    folder=root/"app/src/main/java/cz/titulky/tv"
    total=0
    for path in folder.rglob("*.kt"):
        source=path.read_text(encoding="utf8")
        updated, count = re.subn(r'(?<![\w.])(?:android\.app\.)?AlertDialog\.Builder\(([^(),\n]+)\)',r'android.app.AlertDialog.Builder(\1, R.style.GameLingoTVAlert)',source)
        # Explicit legacy activity/dialog themes otherwise bypass the app theme.
        updated = re.sub(r'(\bDialog\([^,\n]+,\s*)android\.R\.style\.Theme_(?:Material_NoActionBar_Fullscreen|DeviceDefault_NoActionBar)(?=\s*\))',
                         r'\1R.style.GameLingoTVFullscreen', updated)
        # The approved language picker owns its colors/layout; do not replace
        # its custom content or navigation. Other ordinary Dialogs inherit the
        # app dialogTheme, and all AlertDialogs are explicitly styled above.
        if updated != source:
            path.write_text(updated,encoding="utf8")
        total+=count
    if total < 20: raise RuntimeError("Unexpectedly few themed TV dialogs")
    print(f"GameLingo TV: themed {total} inherited and branded dialogs")


if __name__ == "__main__":
    main()
