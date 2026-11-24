package cz.titulky.tv

/** Local deterministic, non-cascading glossary; no cache mutation. */
internal data class GameLingoGlossary(val source: String, val target: String, val rules: List<Rule>) {
    data class Rule(val source: String, val wrong: String, val preferred: String)
    companion object {
        fun parse(text: String, source: String, target: String): GameLingoGlossary? {
            if (text.length > 25_000) return null
            val rules = ArrayList<Rule>()
            val seen = HashSet<String>()
            for (line in text.lineSequence()) {
                if (line.isBlank()) continue
                val fields = line.split('|').map { it.trim() }
                if (fields.size != 3 || fields[0].isEmpty() || fields[2].isEmpty() ||
                    fields.any { it.length > 160 || it.contains('\u0000') } ||
                    !seen.add(fields[0]) || rules.size >= 50) return null
                rules.add(Rule(fields[0], fields[1], fields[2]))
            }
            return GameLingoGlossary(source, target, rules)
        }
        private fun pattern(text: String) = Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(text) + "(?![\\p{L}\\p{N}_])")
    }
    fun apply(original: String, translated: String, from: String, to: String): String {
        if (source != from || target != to || translated.isEmpty() ||
            original.length > 8192 || translated.length > 8192) return translated
        rules.firstOrNull { it.source == original.trim() }?.let { return it.preferred }
        val hits = ArrayList<Pair<IntRange, String>>()
        for (rule in rules.filter { it.wrong.isNotEmpty() && pattern(it.source).containsMatchIn(original) }
            .sortedByDescending { it.wrong.length }) {
            for (match in pattern(rule.wrong).findAll(translated)) {
                val range = match.range
                if (hits.none { range.first <= it.first.last && range.last >= it.first.first })
                    hits.add(range to rule.preferred)
                if (hits.size >= 256) break
            }
            if (hits.size >= 256) break
        }
        val result = StringBuilder(translated)
        for ((range, value) in hits.sortedByDescending { it.first.first })
            result.replace(range.first, range.last + 1, value)
        return if (result.length <= 8192) result.toString() else translated
    }
}
