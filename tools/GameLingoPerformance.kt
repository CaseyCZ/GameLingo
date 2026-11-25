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

/** Fixed-cardinality, in-memory phase windows. No text or device IDs. */
internal class GameLingoPhaseMetrics(names: List<String>) {
    private val windows = names.distinct().associateWith { GameLingoPerformanceWindow() }
    fun record(name: String, ms: Long) { windows[name]?.record(ms) }
    fun summary(): String = windows.entries.joinToString("\n") { (name, window) -> "$name: ${window.summary()}" }
}

/** Shared process-local queue measurements for ordinary online cache misses. */
internal object GameLingoNetworkPerformance {
    private val queue = GameLingoPerformanceWindow()
    private val discarded = java.util.concurrent.atomic.AtomicLong()
    fun waitMs(value: Long) = queue.record(value)
    fun discard() { discarded.incrementAndGet() }
    fun summary() = "Síťová fronta (online, od spuštění aplikace): ${queue.summary()}; zahozeno: ${discarded.get()}"
}

/** Numeric inbound video statistics only. Internal stream ID distinguishes counter resets;
 * no addresses, raw reports or device identifiers are exposed or persisted. */
internal class GameLingoXboxNetworkMetrics {
    data class Sample(val id: String, val atMs: Long, val bytes: Double?,
                      val received: Double?, val lost: Double?, val jitterSeconds: Double?, val rttSeconds: Double?)
    private var previous: Sample? = null
    private var kbps: Double? = null
    private var loss: Double? = null
    private var lostInWindow: Double? = null
    private fun delta(a: Double?, b: Double?): Double? =
        if (a != null && b != null && a.isFinite() && b.isFinite() && a >= b) a - b else null
    @Synchronized fun record(sample: Sample) {
        val old = previous
        previous = sample
        kbps = null; loss = null; lostInWindow = null
        if (old == null || old.id != sample.id || sample.atMs <= old.atMs) return
        val elapsed = sample.atMs - old.atMs
        kbps = delta(sample.bytes, old.bytes)?.let { it * 8.0 / elapsed }
        val r = delta(sample.received, old.received)
        val l = delta(sample.lost, old.lost)
        if (r != null && l != null) {
            lostInWindow = l
            if (r + l > 0) loss = 100.0 * l / (r + l)
        }
    }
    @Synchronized fun summary(now: Long): String {
        val s = previous
        if (s == null || now < s.atMs || now - s.atMs > 15000L) return "Xbox síť: nedostupná"
        fun fmt(v: Double?, unit: String) =
            if (v != null && v.isFinite() && v >= 0) String.format(java.util.Locale.ROOT, "%.1f %s", v, unit) else "–"
        return "Xbox síť (video): ${fmt(kbps,"kb/s")} · ztráta ${fmt(loss,"%")} (${fmt(lostInWindow,"paketů")})" +
            " · jitter ${fmt(s.jitterSeconds?.times(1000),"ms")} · RTT ${fmt(s.rttSeconds?.times(1000),"ms")}"
    }
}

/** Decoder-delivered frame rate, not the OCR sampler rate or requested resolution. */
internal class GameLingoXboxVideoMetrics {
    val network = GameLingoXboxNetworkMetrics()
    private var windowStart: Long? = null
    private var frames = 0L
    private var lastFrame: Long? = null
    private var width = 0
    private var height = 0
    private var rate: Double? = null
    private var stopped = false
    @Synchronized fun frame(w: Int, h: Int, now: Long) {
        if (stopped || w <= 0 || h <= 0) return
        width = w; height = h
        val start = windowStart
        val previous = lastFrame
        lastFrame = now
        if (start == null || previous == null || now < previous || now - previous > 2000L) {
            windowStart = now; frames = 0L; rate = null; return
        }
        frames++
        val elapsed = now - start
        if (elapsed >= 1000L) {
            rate = frames * 1000.0 / elapsed
            windowStart = now; frames = 0L
        }
    }
    @Synchronized fun fps(now: Long): Double? {
        val last = lastFrame ?: return null
        return if (!stopped && now >= last && now - last <= 2000L) rate else null
    }
    @Synchronized fun stop() { stopped = true }
    @Synchronized fun summary(now: Long): String {
        val size = if (width > 0 && height > 0) "${width}×${height}" else "–"
        val fps = fps(now)?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "–"
        return "Xbox dekódovaný obraz: $size · $fps FPS" +
            if (stopped) " (poslední relace ukončena)" else "\n" + network.summary(now)
    }
}
internal object GameLingoXboxDiagnostics {
    @Volatile private var current: GameLingoXboxVideoMetrics? = null
    fun begin(metrics: GameLingoXboxVideoMetrics) { current = metrics }
    fun summary() = current?.summary(SystemClock.elapsedRealtime()) ?: "Xbox dekódovaný obraz: –"
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
        } + "; chyby=$failures] " + GameLingoNetworkPerformance.summary() + " · " + GameLingoXboxDiagnostics.summary()
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
