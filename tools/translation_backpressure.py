"""CODE-05: bounded standard network queue and current-choice request admission."""
from pathlib import Path

def once(text, before, after):
    if text.count(before) != 1:
        raise RuntimeError("Translation backpressure anchor changed: " + before[:100])
    return text.replace(before, after, 1)

def bound_translator(text):
    text = once(text, "private val sit = Executors.newSingleThreadExecutor()",
        """private val sit = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(8),
        java.util.concurrent.ThreadPoolExecutor.AbortPolicy())""")
    # Story already has its atomic single-flight guard and separate worker.
    text = once(text, """        if (runCatching { sit.execute {
            if (!currentPair(source, target)) { ui.post { chyba("Změna jazyka nebo relace") }; return@execute }""",
        """        val queuedAt = android.os.SystemClock.elapsedRealtime()
        val queuedLanguageRevision = GameLingoLanguages.revision
        if (runCatching { sit.execute {
            if (queuedLanguageRevision != GameLingoLanguages.revision || android.os.SystemClock.elapsedRealtime() - queuedAt > 5000L) {
                ui.post { chyba("Překlad čekal příliš dlouho") }
                return@execute
            }
            if (!currentPair(source, target)) { ui.post { chyba("Změna jazyka nebo relace") }; return@execute }""")
    return once(text, '} }.isFailure) chyba("překladač zavřený")',
        '} }.isFailure) chyba("Překladová fronta je plná nebo uzavřená")')

def bound_choices(text, folder):
    text = once(text, "    private val prekladyVoleb = HashMap<GameTextKey, String>()",
        """    private val prekladyVoleb = HashMap<GameTextKey, String>()
    private data class ChoiceFlightKey(val context: TranslationContext, val roi: Long, val term: String)
    private val choiceFlights = GameLingoChoiceFlights<ChoiceFlightKey>()
    private data class ChoiceOutput(val generation: Long, val revision: Long,
                                    val items: List<String>, val selected: Int)
    private var lastChoiceOutput: ChoiceOutput? = null""")
    # Restrict modifications to the choice function, not whole-text OCR.
    begin = text.index("    private fun zpracujVolby(")
    end = text.index("    private fun vyrezZGl(", begin)
    s = text[begin:end]
    s = once(s, "        val chybi = volby.filter { gameTextKey(context, it) !in prekladyVoleb }",
        """        val chybi = volby.distinct().filter { gameTextKey(context, it) !in prekladyVoleb }
        if (novy && chybi.isNotEmpty()) {
            lastChoiceOutput = null
            Propojeni.Vysilac.nastavVolby(emptyList(), -1)
        }""")
    s = once(s, """            GameLingoTranslationHistory.append("Volby", context.source, context.target, volby.joinToString("\\n"), corrected.joinToString("\\n"))
            Propojeni.Vysilac.nastavVolby(corrected, volbyVybrana)""",
        """            val output = ChoiceOutput(generation, revize, corrected, volbyVybrana)
            val previous = lastChoiceOutput
            if (output == previous) return
            lastChoiceOutput = output
            if (previous == null || previous.generation != generation || previous.revision != revize || previous.items != corrected)
                GameLingoTranslationHistory.append("Volby", context.source, context.target, volby.joinToString("\\n"), corrected.joinToString("\\n"))
            Propojeni.Vysilac.nastavVolby(corrected, volbyVybrana)""")
    s = once(s, """        var zbyva = chybi.size
        for (v in chybi) {
            prekladac.preloz(v, { cesky ->
                ui.post {""",
        """        for (v in chybi) {
            val flightKey = ChoiceFlightKey(context, revize, v)
            val ticket = choiceFlights.begin(flightKey) ?: continue
            prekladac.preloz(v, { cesky ->
                ui.post {
                    if (!choiceFlights.finish(flightKey, ticket, cesky.isNotBlank())) return@post
                    if (cesky.isBlank()) return@post""")
    s = once(s, """                    if (--zbyva == 0) {
                        performance.record(perfToken, "Herní text překlad", performance.now() - perfStart)
                        posli()
                    }""",
        """                    if (volby.all { gameTextKey(context, it) in prekladyVoleb }) {
                        performance.record(perfToken, "Herní text překlad", performance.now() - perfStart)
                        posli()
                    }""")
    s = once(s, """                ui.post { if (--zbyva == 0) {
                    performance.record(perfToken, "Herní text překlad", performance.now() - perfStart)
                    posli()
                } }""",
        """                ui.post {
                    choiceFlights.finish(flightKey, ticket, false)
                }""")
    module = Path(__file__).with_name("GameLingoChoiceFlights.kt")
    (folder/module.name).write_text(module.read_text(encoding="utf8"), encoding="utf8")
    return text[:begin] + s + text[end:]
