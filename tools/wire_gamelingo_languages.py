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
    service.write_text(s, encoding="utf8")
    print("GameLingo source and target languages connected to real Android translation")


if __name__ == "__main__":
    main()
