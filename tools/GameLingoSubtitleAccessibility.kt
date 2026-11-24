package cz.titulky.tv

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context

/**
 * Optional, read-only subtitle source. No gestures, audio, screenshots, history,
 * notification access or reading other apps. The session chooses one player.
 */
class GameLingoSubtitleAccessibility : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun read(player: String, roi: FloatArray): Reading {
        val root = rootInActiveWindow ?: return Reading(false, "")
        try {
            if (root.packageName?.toString() != player) return Reading(false, "")
            val screen = resources.displayMetrics
            val area = Rect((roi[0]*screen.widthPixels).toInt(), (roi[1]*screen.heightPixels).toInt(),
                (roi[2]*screen.widthPixels).toInt(), (roi[3]*screen.heightPixels).toInt())
            val lines = linkedSetOf<String>()
            var visited = 0
            fun walk(node: AccessibilityNodeInfo, inherited: Boolean, depth: Int) {
                if (++visited > 240 || depth > 16 || node.packageName?.toString() != player ||
                    !node.isVisibleToUser || node.isPassword || node.isEditable) return
                val id = node.viewIdResourceName.orEmpty().substringAfterLast('/').lowercase()
                val klass = node.className?.toString().orEmpty().lowercase()
                // A generic TextView, title, menu, description or button is not evidence of a subtitle.
                val controls = node.isClickable || node.isCheckable || node.isFocusable ||
                    listOf("button","menu","setting","toggle","option","control","selector","track","language","list")
                        .any { id.contains(it) || klass.contains(it) }
                if (controls) return
                val marked = inherited || id.contains("subtitle") || id.contains("caption") ||
                    klass.endsWith("subtitleview") || klass.endsWith("captionview")
                if (marked) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val raw = node.text?.toString()?.trim().orEmpty()
                    if (raw.length in 2..500 && raw.any { it.isLetter() } &&
                        !bounds.isEmpty && Rect.intersects(bounds, area) &&
                        raw.lowercase() !in setOf("subtitles","captions","closed captions","cc","off","on","titulky","vypnuto","zapnuto"))
                        lines.add(raw)
                }
                for (i in 0 until minOf(node.childCount, 40)) {
                    if (visited >= 240 || lines.size >= 4) break
                    val child = node.getChild(i) ?: continue
                    try { walk(child, marked, depth+1) } finally { @Suppress("DEPRECATION") child.recycle() }
                }
            }
            walk(root,false,0)
            return Reading(true,lines.joinToString("\n").take(500))
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }

    companion object {
        data class Reading(val inPlayer: Boolean, val text: String)
        // Only known playback apps. No global UI text collection.
        val players = linkedMapOf(
            "com.google.android.youtube.tv" to "YouTube",
            "com.netflix.ninja" to "Netflix",
            "com.disney.disneyplus" to "Disney+",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.wbd.stream" to "Max / HBO Max",
            "com.max" to "Max",
            "org.videolan.vlc" to "VLC",
            "org.xbmc.kodi" to "Kodi",
            "com.stremio.one" to "Stremio",
            "com.plexapp.android" to "Plex",
            "org.smarttube.stable" to "SmartTube",
            "com.liskovsoft.smarttubetv.beta" to "SmartTube Beta",
            "com.liskovsoft.videomanager.tv" to "SmartTube"
        )
        @Volatile private var instance: GameLingoSubtitleAccessibility? = null
        fun connected() = instance != null
        fun read(player: String, roi: FloatArray): Reading {
            if (player !in players || !GameLingoScreenTranslationService.running) return Reading(false, "")
            return runCatching { instance?.read(player,roi) }.getOrNull() ?: Reading(false,"")
        }
        fun available(c: Context) = players.filterKeys {
            c.packageManager.getLeanbackLaunchIntentForPackage(it) != null ||
                c.packageManager.getLaunchIntentForPackage(it) != null
        }
    }
}
