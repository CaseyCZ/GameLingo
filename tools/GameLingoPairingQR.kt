package cz.titulky.tv

/** A QR contains only a local address and short-lived PIN. */
internal data class GameLingoPairingQR(val ip: String, val pin: String) {
    companion object {
        fun parse(raw: String): GameLingoPairingQR? {
            if (raw.length > 64) return null
            val fields = raw.split('|')
            if (fields.size != 3 || fields[0] != "GLPAIR1" ||
                !fields[2].matches(Regex("[0-9]{6}"))) return null
            val parts = fields[1].split('.')
            if (parts.size != 4) return null
            val octets = parts.map {
                if (!it.matches(Regex("[0-9]{1,3}"))) return null
                val n = it.toIntOrNull() ?: return null
                if (n !in 0..255 || n.toString() != it) return null
                n
            }
            val a = octets[0]; val b = octets[1]
            val local = a == 10 || (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) || (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
            if (!local || octets.last() in listOf(0, 255)) return null
            return GameLingoPairingQR(fields[1], fields[2])
        }
        fun encode(ip: String, pin: String): String? {
            val raw = "GLPAIR1|$ip|$pin"
            return raw.takeIf { parse(it) != null }
        }
    }
}
