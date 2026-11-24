package cz.titulky.tv

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.ceil
import kotlin.math.roundToLong

internal class GameLingoPerformanceWindow(private val capacity: Int = 128) {
    private val samples = LongArray(capacity.coerceIn(1, 1024))
    private var cursor = 0
    private var size = 0
    private var count = 0L
    @Synchronized fun record(ms: Long) {
        if (ms < 0) return
        samples[cursor] = ms
        cursor = (cursor + 1) % samples.size
        size = minOf(size + 1, samples.size)
        count++
    }
    @Synchronized fun summary(): String {
        if (size == 0) return "–"
        val sorted = samples.copyOf(size).sorted()
        val rank = (ceil(size * 0.95).toInt() - 1).coerceAtLeast(0)
        return "${sorted.average().roundToLong()} / ${sorted[rank]} ms (n=$count)"
    }
}

internal class GameLingoPerformance {
    private val windows = linkedMapOf<String, GameLingoPerformanceWindow>()
    private var generation = 0L
    private var running = false
    private var failures = 0L
    private fun prefs(context: Context) = context.getSharedPreferences("gamelingo_performance", Context.MODE_PRIVATE)
    fun now() = SystemClock.elapsedRealtime()

    @Synchronized fun begin(context: Context): Boolean {
        if (running) return false
        val previous = prefs(context).getBoolean("session_open", false)
        prefs(context).edit().putBoolean("session_open", true).commit()
        generation++
        windows.clear()
        failures = 0
        running = true
        return previous
    }
    @Synchronized fun end(context: Context) {
        if (!running) return
        running = false
        generation++
        prefs(context).edit().remove("session_open").commit()
    }
    @Synchronized fun token() = generation
    @Synchronized fun record(token: Long, name: String, ms: Long) {
        if (!running || generation != token) return
        windows.getOrPut(name) { GameLingoPerformanceWindow() }.record(ms)
    }
    @Synchronized fun failure(token: Long) { if (running && generation == token) failures++ }
    @Synchronized fun summary(): String {
        val names = listOf("Dialog OCR", "Dialog překlad", "Dialog celkem k výstupu",
            "Herní text OCR", "Herní text překlad", "Herní text celkem k výstupu", "Story překlad")
        return "[Diagnostika průměr/P95: " + names.joinToString("; ") {
            "$it=${windows[it]?.summary() ?: "–"}"
        } + "; chyby=$failures]"
    }
    fun device(context: Context): String {
        val pss = runCatching { Debug.getPss() / 1024 }.getOrNull()
        val thermal = if (Build.VERSION.SDK_INT >= 29) runCatching {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus
        }.getOrNull() else null
        val state = when (thermal) {
            0 -> "none"; 1 -> "light"; 2 -> "moderate"; 3 -> "severe"
            4 -> "critical"; 5 -> "emergency"; 6 -> "shutdown"; else -> "unknown"
        }
        return "[Diagnostika paměť: ${pss?.let { "$it MiB PSS" } ?: "nedostupná"}; thermal=$state]"
    }
}
