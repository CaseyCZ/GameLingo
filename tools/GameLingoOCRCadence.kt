package cz.titulky.tv

internal object GameLingoOCRCadence {
    fun dialogue(mode: String, phone: Boolean, lowPower: Boolean): Long {
        if (lowPower && !phone) return 600L
        return when (mode) {
            "automatic" -> if (phone) 85L else 200L
            "street" -> if (phone) 70L else 150L
            else -> if (phone) 100L else 250L
        }
    }
    /** Automatic zones share one reader. TV uses a conservative capture floor. */
    fun automaticZones(mode: String, phone: Boolean): Long =
        if (phone) dialogue(mode, true, false)
        else when (mode) {
            "automatic" -> 300L
            "street" -> 250L
            else -> 350L
        }

    /** Two dialogue opportunities per game-text opportunity, with bounded
     * yellow starvation under slow capture/OCR. A single reader still owns the work. */
    fun standaloneRegion(blue: Boolean, yellow: Boolean, blueSinceYellow: Int, yellowAgeMs: Long): Int {
        if (!yellow) return 1
        if (!blue) return 2
        return if (blueSinceYellow >= 2 || (blueSinceYellow > 0 && yellowAgeMs >= 1200L)) 2 else 1
    }

    // Direct-on-TV uses a single shared reader. This is a minimum gap,
    // not a guarantee that OCR can complete at this rate.
    fun standaloneYellowInterval(mode: String): Long = when (mode) {
        "classic" -> 800L
        "automatic" -> 500L
        "street" -> 250L
        else -> 0L
    }

    fun gameText(mode: String, asText: Boolean, lowPower: Boolean, recentCostMs: Long): Long {
        val base = when (mode) { "automatic" -> 200L; "street" -> 150L; else -> 250L }
        val floor = maxOf(if (lowPower) 600L else 0L, if (asText) base * 5 / 4 else base)
        return maxOf(floor, (recentCostMs.coerceIn(0L, 1000L) * 3 / 2))
    }
}

/** Only reports absence or a slow operation; it never releases an active reader. */
internal object GameLingoCaptureWatchdog {
    fun state(frameAgeMs: Long, busy: Boolean, ocrAgeMs: Long): String = when {
        busy && ocrAgeMs >= 15000L -> "OCR bez odpovědi – další čtení nespouštím"
        frameAgeMs >= 5000L -> "Nepřicházejí snímky (obraz může být neměnný)"
        busy && ocrAgeMs >= 5000L -> "OCR trvá déle než 5 s"
        else -> "Bez zjištěného výpadku"
    }
}
