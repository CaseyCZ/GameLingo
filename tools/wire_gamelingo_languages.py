#!/usr/bin/env python3
"""Wire real Android source and target language choices into translation core.

Only changes the pinned upstream language-specific paths. It never replaces
Remote Play, subtitle placement, OAuth, Sony credentials or TV transport.
"""
from pathlib import Path
import sys

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
    t = translator.read_text(encoding="utf8")
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
            '        if (zdrojovyJazyk == "en") upravSlang(text.trim()) else text.trim()'
        ),
        (
            '.setSourceLanguage(TranslateLanguage.ENGLISH)',
            '.setSourceLanguage(TranslateLanguage.fromLanguageTag(zdrojovyJazyk) ?: TranslateLanguage.ENGLISH)'
        ),
        (
            '    /** Přepne cílový jazyk (např. po změně v aplikaci). */',
            '    /** Changes the real source of every online and offline translation. */\n'
            '    fun nastavZdroj(kod: String) {\n'
            '        if (kod == zdrojovyJazyk) return\n'
            '        val stary = translator\n'
            '        zdrojovyJazyk = kod\n'
            '        translator = novyTranslator(cil)\n'
            '        cache.evictAll()\n'
            '        runCatching { stary.close() }\n'
            '        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())\n'
            '    }\n\n'
            '    /** Přepne cílový jazyk (např. po změně v aplikaci). */'
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
    if t.count("        cache.evictAll()\n") != 2:
        raise RuntimeError("Expected two old language-cache resets")
    t=t.replace("        cache.evictAll()\n","")
    t=once(t, '    private val cache = LruCache<String, String>(500)', '''    private val cacheContext = context?.applicationContext

    private fun currentPair(source: String, target: String) =
        !zavreno && zdrojovyJazyk == source && cil == target

    private fun cachedOrRun(text: String, source: String, target: String,
                            hotovo: (String) -> Unit, chyba: (String) -> Unit,
                            miss: () -> Unit) {
        if (!currentPair(source, target)) { chyba("Změna jazyka nebo relace"); return }
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
    t=once(t,'prelozVTelevizi(t, ok, { err() })','prelozVTelevizi(t, source, jazyk, ok, { err() })',translator.name)
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
    t=once(t,"prelozZalozne(vstup, hotovo, chyba)","prelozZalozne(vstup, source, target, hotovo, chyba)",translator.name)
    t=once(t,'''        // když už Google tuhle větu znal, vrátit rovnou dobrý překlad
        cache.get(vstup)?.let { hotovo(it); return }
        if (zavreno) { chyba("překladač zavřený"); return }
        runCatching {
            translator.translate(vstup)
                .addOnSuccessListener { hotovo(it) }
                .addOnFailureListener { chyba(it.message ?: "Neznámá chyba") }
        }.onFailure { chyba(it.message ?: "překladač zavřený") }''','''        val source = zdrojovyJazyk
        val target = cil
        cachedOrRun(vstup, source, target, hotovo, chyba) {
            prelozVTelevizi(vstup, source, target, hotovo, chyba)
        }''',translator.name)
    t=once(t,'''    private fun prelozZalozne(vstup: String, hotovo: (String) -> Unit, chyba: (String) -> Unit) =
        prelozVTelevizi(vstup, hotovo, chyba)''','''    private fun prelozZalozne(vstup: String, source: String, target: String,
                               hotovo: (String) -> Unit, chyba: (String) -> Unit) =
        prelozVTelevizi(vstup, source, target, hotovo, chyba)''',translator.name)
    t=once(t,'private fun prelozVTelevizi(vstup: String, hotovo: (String) -> Unit, chyba: (String) -> Unit)', 'private fun prelozVTelevizi(vstup: String, source: String, target: String, hotovo: (String) -> Unit, chyba: (String) -> Unit)',translator.name)
    t=once(t,'''        if (zavreno) { chyba("překladač zavřený"); return }
        runCatching {
            translator.translate(vstup)
                .addOnSuccessListener { cesky -> zdroj = "tv"; hotovo(cesky) }''','''        if (!currentPair(source, target)) { chyba("Změna jazyka nebo relace"); return }
        val activeTranslator = translator
        runCatching {
            activeTranslator.translate(vstup)
                .addOnSuccessListener { cesky ->
                    if (!currentPair(source, target)) chyba("Změna jazyka nebo relace")
                    else {
                        zdroj = "tv"
                        remember("mlkit-v1", source, target, vstup, cesky)
                        hotovo(cesky)
                    }
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
    t=once(t, '''                    else {
                        zdroj = "tv"
                        remember("mlkit-v1", source, target, vstup, cesky)''', '''                    else if (cesky.isBlank()) chyba("Prázdná odpověď překladače")
                    else {
                        zdroj = "tv"
                        remember("mlkit-v1", source, target, vstup, cesky)''', translator.name)
    t=once(t, '''                val kus = kusy.optJSONArray(i) ?: continue
                sb.append(kus.optString(0, ""))''', '''                val kus = kusy.optJSONArray(i) ?: return null
                val segment = kus.opt(0) as? String ?: return null
                sb.append(segment)''', translator.name)
    t=once(t, '''        return JSONArray(json).getJSONObject(0).getJSONArray("translations")
            .getJSONObject(0).optString("text").trim().ifEmpty { null }''', '''        return (JSONArray(json).getJSONObject(0).getJSONArray("translations")
            .getJSONObject(0).opt("text") as? String)?.trim()?.ifEmpty { null }''', translator.name)
    t=once(t, '''        val preklad = json.getJSONObject("responseData").optString("translatedText").trim()''', '''        val preklad = (json.getJSONObject("responseData").opt("translatedText") as? String)?.trim()
            ?: return null''', translator.name)
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
    # progressive correction. Bound standard dialogue lifetime only;
    # story pages and long game text retain their own timing.
    s=once(s, '        ukaz(cesky, 15000, box, radku, barvyJmen)',
        '        ukaz(cesky, 4000, box, radku, barvyJmen)', service.name)
    s=once(s, '''        val minDoba = 1800L + 60L * ukazanaDelka
        val zbyva = ukazanoKdy + minDoba - System.currentTimeMillis()
        ui.removeCallbacks(schovatCesky)
        ukazanoKdy = 0L
        if (zbyva <= 0) ukaz("", 0) else ui.postDelayed(schovatCesky, zbyva)''',
        '''        val zbyva = (ukazanoKdy + 4000L - System.currentTimeMillis()).coerceIn(0L, 800L)
        ui.removeCallbacks(schovatCesky)
        ukazanoKdy = 0L
        if (zbyva <= 0) ukaz("", 0) else ui.postDelayed(schovatCesky, zbyva)''', service.name)
    # The old story and game-text timers intentionally stay independent.
    service.write_text(s, encoding="utf8")
    print("GameLingo source and target languages connected to real Android translation")


if __name__ == "__main__":
    main()
