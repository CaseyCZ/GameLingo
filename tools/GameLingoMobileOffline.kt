package cz.titulky.tv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** Only included in mobile APK. All mutable state and task callbacks use the main looper. */
internal class GameLingoMobileOffline(private val context: Context?) {
    private val main = Handler(Looper.getMainLooper())
    private var active: Client? = null
    private var closed = false

    private class Client(val source: String, val target: String, val translator: Translator) {
        var ready = false
        var downloading = false
        var retryAt = 0L
        var pending = 0
    }

    companion object {
        private const val MAX_PENDING = 4
        private const val RETRY_MS = 30_000L
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == main.looper) action() else main.post { action() }
    }

    private fun allowed(): Boolean {
        val c = context ?: return false
        if (c.packageName != "cz.caseycz.gamelingo.mobile") return false
        val mode = c.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val pm = c.packageManager
        return mode != Configuration.UI_MODE_TYPE_TELEVISION &&
            mode != Configuration.UI_MODE_TYPE_CAR &&
            mode != Configuration.UI_MODE_TYPE_WATCH &&
            !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    private fun discard() {
        val previous = active
        active = null
        previous?.let { runCatching { it.translator.close() } }
    }

    private fun client(source: String, target: String): Client? {
        if (closed || !allowed() || source == target) return null
        active?.let { if (it.source == source && it.target == target) return it }
        discard()
        val from = TranslateLanguage.fromLanguageTag(source) ?: return null
        val to = TranslateLanguage.fromLanguageTag(target) ?: return null
        return runCatching {
            Client(source, target, Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build()
            )).also { active = it }
        }.getOrNull()
    }

    private fun ensureModel(client: Client) {
        if (client.ready || client.downloading || SystemClock.elapsedRealtime() < client.retryAt) return
        client.downloading = true
        fun failed() {
            client.downloading = false
            client.retryAt = SystemClock.elapsedRealtime() + RETRY_MS
        }
        runCatching {
            client.translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
                .addOnCompleteListener { task ->
                    onMain {
                        if (active !== client || closed) return@onMain
                        client.downloading = false
                        if (task.isSuccessful) client.ready = true else failed()
                    }
                }
        }.onFailure { failed() }
    }

    fun prepare(source: String, target: String) = onMain {
        client(source, target)?.let { ensureModel(it) }
    }

    fun translate(text: String, source: String, target: String, done: (String?) -> Unit) = onMain {
        val client = client(source, target)
        if (client == null || text.isBlank() || text.length > 4096) {
            done(null)
            return@onMain
        }
        ensureModel(client)
        // Never stall a live caption waiting for a first-time model download.
        if (!client.ready || client.pending >= MAX_PENDING) {
            done(null)
            return@onMain
        }
        client.pending++
        runCatching {
            client.translator.translate(text).addOnCompleteListener { task ->
                onMain {
                    client.pending--
                    if (active !== client || closed) done(null)
                    else if (task.isSuccessful) done(task.result?.takeIf { it.isNotBlank() })
                    else {
                        client.ready = false
                        client.retryAt = SystemClock.elapsedRealtime() + RETRY_MS
                        done(null)
                    }
                }
            }
        }.onFailure {
            client.pending--
            done(null)
        }
    }

    fun close() = onMain {
        closed = true
        discard()
    }
}
