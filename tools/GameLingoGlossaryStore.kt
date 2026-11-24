package cz.titulky.tv

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Separate local store keyed by exact Android game profile name. */
internal object GameLingoGlossaryStore {
    private val revision = AtomicLong()
    fun generation(): Long = revision.get()
    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo_glossary_v1", Context.MODE_PRIVATE)
    private fun key(name: String) = "rule:" + name
    fun active(c: Context): String? = prefs(c).getString("active", null)
    fun names(c: Context): List<String> = prefs(c).all.keys.filter { it.startsWith("rule:") }
        .map { it.removePrefix("rule:") }.sorted()
    fun activate(c: Context, name: String?) {
        if (active(c) == name) return
        prefs(c).edit().putString("active", name).apply()
        revision.incrementAndGet()
    }
    fun remove(c: Context, name: String) {
        val editor = prefs(c).edit().remove(key(name))
        if (active(c) == name) editor.remove("active")
        editor.apply()
        revision.incrementAndGet()
    }
    data class Saved(val source: String, val target: String, val text: String)
    fun read(c: Context, name: String): Saved? = runCatching {
        val o = JSONObject(prefs(c).getString(key(name), null) ?: return null)
        Saved(o.getString("source"), o.getString("target"), o.getString("text"))
    }.getOrNull()
    fun save(c: Context, name: String, source: String, target: String, text: String): Boolean {
        if (name.isBlank() || name.length > 64 || GameLingoGlossary.parse(text, source, target) == null) return false
        prefs(c).edit().putString(key(name), JSONObject().put("source", source)
            .put("target", target).put("text", text).toString()).apply()
        revision.incrementAndGet()
        return true
    }
    fun apply(c: Context, original: String, translated: String, source: String, target: String): String {
        val name = active(c) ?: return translated
        val saved = read(c, name) ?: return translated
        if (saved.source != source || saved.target != target) return translated
        return GameLingoGlossary.parse(saved.text, saved.source, saved.target)
            ?.apply(original, translated, source, target) ?: translated
    }
}
