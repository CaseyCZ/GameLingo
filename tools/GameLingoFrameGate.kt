package cz.titulky.tv

/** Exact-content fingerprint of the copied ROI, including its dimensions.
 * Only a real successful OCR records a signature; skips never extend the window.
 * The service additionally requires a settled, nonempty result. UI-thread owned. */
internal class GameLingoFrameGate(private val maxSkipMs: Long) {
    private var signature: Long? = null
    private var readAt = 0L
    fun record(value: Long, now: Long) { signature = value; readAt = now }
    fun reset() { signature = null; readAt = 0L }
    fun canSkip(value: Long, now: Long): Boolean =
        signature == value && now >= readAt && now - readAt < maxSkipMs
    companion object {
        fun seed(width: Int, height: Int): Long = mix(mix(-3750763034362895579L, width), height)
        fun mix(hash: Long, pixel: Int): Long = (hash xor pixel.toLong()) * 1099511628211L
    }
}
