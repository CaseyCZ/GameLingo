package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** TV-only, explicit system installation of a newer, same-signer GameLingo APK. */
object GameLingoTVUpdater {
    private const val ID = "cz.caseycz.gamelingo.tv"
    private const val RELEASES = "https://github.com/CaseyCZ/GameLingo/releases/download/"
    private const val API = "https://api.github.com/repos/CaseyCZ/GameLingo/releases?per_page=10"
    private const val MANIFEST = "GameLingo-TV-update.json"
    private const val MAX_APK = 150L * 1024 * 1024
    private val checked = AtomicBoolean(false)
    private val working = AtomicBoolean(false)
    private val hashPattern = Regex("[a-f0-9]{64}")
    private val tagPattern = Regex("v[0-9]+\\.[0-9]+\\.[0-9]+-test-[a-f0-9]{8}-r[0-9]+")
    private val assetPattern = Regex("GameLingo-GoogleTV-v[0-9]+\\.[0-9]+\\.[0-9]+-dev-TEST\\.apk")
    private data class Update(val name: String, val code: Long, val size: Long,
                              val hash: String, val cert: String, val url: String, val notes: String)

    private fun tr(a: Activity, key: String) = GameLingoLanguages.ui(a, key)
    private fun alive(a: Activity) = !a.isFinishing && !a.isDestroyed
    private fun busy() = RemotePlayService.aktivni || PrijemService.bezi || GameLingoScreenTranslationService.running
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun code(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
    private fun flags(): Int = if (Build.VERSION.SDK_INT >= 28)
        PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    private fun signers(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        return sigs?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }
            ?.toSet() ?: emptySet()
    }
    private fun installed(a: Activity): PackageInfo =
        a.packageManager.getPackageInfo(a.packageName, flags())

    // Only HTTPS and GitHub's own release download hosts; redirects are explicit and bounded.
    private fun trusted(url: URL): Boolean {
        if (url.protocol != "https" || url.userInfo != null || (url.port != -1 && url.port != 443)) return false
        return when (url.host.lowercase()) {
            "api.github.com" -> url.toString() == API
            "github.com" -> url.toString().startsWith(RELEASES)
            "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> true
            else -> false
        }
    }
    private fun <T> read(url: String, consume: (InputStream) -> T): T {
        var next = URL(url)
        repeat(6) {
            require(trusted(next)) { "Nepovolený zdroj aktualizace." }
            val connection = next.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "GameLingo-TV-Updater")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: error("Chybí cíl přesměrování.")
                    next = URL(next, location)
                } else {
                    require(status == 200) { "GitHub odpověděl HTTP " + status }
                    return connection.inputStream.use(consume)
                }
            } finally { connection.disconnect() }
        }
        error("Příliš mnoho přesměrování.")
    }
    private fun json(url: String, limit: Int): String = read(url) { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= limit) { "Příliš velká metadata." }
            out.write(buffer, 0, n)
        }
        out.toString("UTF-8")
    }
    private fun asset(assets: JSONArray, tag: String, name: String): JSONObject? {
        val expected = RELEASES + tag + "/" + name
        return (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.singleOrNull {
            it.optString("name") == name && it.optString("browser_download_url") == expected &&
                it.optString("state") == "uploaded"
        }
    }
    private fun find(): Update? {
        val releases = JSONArray(json(API, 2 * 1024 * 1024))
        val candidates = mutableListOf<Update>()
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            val tag = release.optString("tag_name")
            if (release.optBoolean("draft") || !tagPattern.matches(tag)) continue
            val assets = release.optJSONArray("assets") ?: continue
            val meta = asset(assets, tag, MANIFEST) ?: continue
            require(meta.optLong("size") in 1L..32768L) { "Neplatná metadata aktualizace." }
            val m = JSONObject(json(RELEASES + tag + "/" + MANIFEST, 32768))
            require(m.getInt("schema") == 1 && m.getString("package") == ID &&
                m.getString("tag") == tag) { "Neplatná identita aktualizace." }
            val file = m.getString("file")
            val version = m.getString("versionName")
            val versionCode = m.getLong("versionCode")
            val size = m.getLong("size")
            val hash = m.getString("sha256")
            val cert = m.getString("certSha256")
            val semver = tag.substringAfter("v").substringBefore("-test-")
            require(assetPattern.matches(file) && file == "GameLingo-GoogleTV-v" + semver + "-dev-TEST.apk" &&
                version.startsWith(semver + "-dev.") && versionCode in 1L..2100000000L &&
                size in 1L..MAX_APK && hashPattern.matches(hash) && hashPattern.matches(cert)) {
                "Neplatné údaje APK."
            }
            val apk = asset(assets, tag, file) ?: error("Vydání nemá očekávanou APK.")
            require(apk.optLong("size") == size) { "Velikost APK nesouhlasí." }
            candidates.add(Update(version, versionCode, size, hash, cert,
                RELEASES + tag + "/" + file, release.optString("body").take(2000)))
        }
        return candidates.maxByOrNull { it.code }
    }
    private fun updateError(a: Activity, error: Throwable): String {
        val detail = error.message.orEmpty()
        val known = setOf("Nelze vytvořit složku aktualizací.",
            "Stahování zrušeno.",
            "Časový limit stahování.",
            "Neočekávaná velikost APK.",
            "Kontrolní součet APK nesouhlasí.",
            "APK není čitelná.",
            "Podpis, identita nebo verze APK nesouhlasí.",
            "Nepovolený zdroj aktualizace.",
            "Chybí cíl přesměrování.",
            "Příliš mnoho přesměrování.")
        return when {
            detail in known -> tr(a, detail)
            detail.startsWith("GitHub odpověděl HTTP ") -> tr(a, "GitHub odpověděl HTTP") + " " + detail.substringAfterLast(' ')
            else -> tr(a, "Neznámá chyba.")
        }
    }
    private fun message(a: Activity, text: String) {
        if (alive(a)) AlertDialog.Builder(a).setTitle(tr(a, "Aktualizace GameLingo"))
            .setMessage(text).setPositiveButton("OK", null).show()
    }

    /** Check once after the foreground window is ready; never interrupt translation. */
    fun checkAtStartup(a: Activity) {
        fun attempt(remaining: Int) {
            if (!alive(a) || checked.get()) return
            if (!a.hasWindowFocus() || busy() || !working.compareAndSet(false, true)) {
                if (remaining > 0) a.window.decorView.postDelayed({ attempt(remaining - 1) }, 1000L)
                return
            }
            Thread {
                val result = runCatching { find() }
                working.set(false)
                a.runOnUiThread {
                    if (!alive(a)) return@runOnUiThread
                    if (!a.hasWindowFocus() || busy() || result.isFailure) {
                        if (remaining > 0) a.window.decorView.postDelayed({ attempt(remaining - 1) }, 3000L)
                        return@runOnUiThread
                    }
                    if (!checked.compareAndSet(false, true)) return@runOnUiThread
                    val next = result.getOrNull() ?: return@runOnUiThread
                    val current = runCatching { installed(a) }.getOrNull() ?: return@runOnUiThread
                    if (next.code <= code(current)) return@runOnUiThread
                    if (signers(current) != setOf(next.cert)) {
                        message(a, tr(a, "Nová APK má jiný podpis. Aktualizace je zablokovaná, aby se nesmazalo nastavení. Je nutný jednorázový přechod na trvalý podpis."))
                        return@runOnUiThread
                    }
                    AlertDialog.Builder(a).setTitle(tr(a, "Nová verze GameLingo"))
                        .setMessage(tr(a, "Nainstalováno:") + " " + current.versionName + "\n" +
                            tr(a, "Nová verze:") + " " + next.name)
                        .setNegativeButton(tr(a, "Později"), null)
                        .setPositiveButton(tr(a, "Stáhnout")) { _, _ -> download(a, next) }.show()
                }
            }.apply { name = "gamelingo-tv-update-check"; isDaemon = true }.start()
        }
        a.window.decorView.postDelayed({ attempt(5) }, 1800L)
    }

    fun show(a: Activity) {
        if (!alive(a)) return
        if (!working.compareAndSet(false, true)) { message(a, tr(a, "Kontrola již probíhá. Zkus to znovu.")); return }
        val status = AlertDialog.Builder(a).setTitle(tr(a, "Aktualizace GameLingo"))
            .setMessage(tr(a, "Kontroluji vydání na GitHubu…")).setNegativeButton(tr(a, "Zrušit"), null).show()
        Thread {
            val result = runCatching { find() }
            working.set(false)
            a.runOnUiThread {
                if (!alive(a) || !status.isShowing) return@runOnUiThread
                status.dismiss()
                result.fold(onSuccess = { next ->
                    val current = runCatching { installed(a) }.getOrNull()
                    when {
                        current == null -> message(a, tr(a, "Nelze zjistit nainstalovanou verzi."))
                        next == null -> message(a, tr(a, "Zatím není dostupné vydání s ověřitelnými aktualizačními metadaty."))
                        next.code <= code(current) -> message(a, tr(a, "Novější verze není dostupná.") + "\n" + tr(a, "Nainstalováno:") + " " + current.versionName)
                        signers(current) != setOf(next.cert) -> message(a, tr(a, "Nová APK má jiný podpis. Aktualizace je zablokovaná, aby se nesmazalo nastavení. Je nutný jednorázový přechod na trvalý podpis."))
                        else -> AlertDialog.Builder(a).setTitle(tr(a, "Nová verze GameLingo"))
                            .setMessage(tr(a, "Nainstalováno:") + " " + current.versionName + "\n" + tr(a, "Nová verze:") + " " + next.name +
                                "\n" + tr(a, "Velikost:") + " " + (next.size / (1024 * 1024)) + " MB\n\n" + next.notes)
                            .setNegativeButton(tr(a, "Později"), null)
                            .setPositiveButton(tr(a, "Stáhnout")) { _, _ -> download(a, next) }.show()
                    }
                }, onFailure = { message(a, tr(a, "Kontrola aktualizace se nezdařila. Zkontroluj internet a zkus to znovu.")) })
            }
        }.apply { name = "gamelingo-tv-update-check"; isDaemon = true }.start()
    }

    /** Resume the verified download after Android's unknown-app-source permission screen. */
    private fun install(a: Activity, apk: File) {
        if (!alive(a)) return
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(a).setTitle(tr(a, "Povolení instalace"))
                .setMessage(tr(a, "Povol instalaci z GameLingo v nastavení TV. Po návratu bude možné pokračovat bez dalšího stahování."))
                .setNegativeButton(tr(a, "Zrušit")) { _, _ -> apk.delete() }
                .setPositiveButton(tr(a, "Otevřít nastavení")) { _, _ ->
                    val observer = object : android.app.Application.ActivityLifecycleCallbacks {
                        private var paused = false
                        override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) {}
                        override fun onActivityStarted(activity: Activity) {}
                        override fun onActivityPaused(activity: Activity) {
                            if (activity === a) paused = true
                        }
                        override fun onActivityResumed(activity: Activity) {
                            if (activity !== a || !paused) return
                            a.application.unregisterActivityLifecycleCallbacks(this)
                            a.window.decorView.post {
                                if (!alive(a)) return@post
                                if (a.packageManager.canRequestPackageInstalls()) {
                                    confirmInstall(a, apk)
                                } else {
                                    message(a, tr(a, "Instalace nebyla povolena. APK je stažená; otevři aktualizaci znovu."))
                                }
                            }
                        }
                        override fun onActivityStopped(activity: Activity) {}
                        override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) {}
                        override fun onActivityDestroyed(activity: Activity) {
                            if (activity === a) a.application.unregisterActivityLifecycleCallbacks(this)
                        }
                    }
                    a.application.registerActivityLifecycleCallbacks(observer)
                    runCatching {
                        a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + a.packageName)))
                    }.onFailure {
                        a.application.unregisterActivityLifecycleCallbacks(observer)
                        message(a, tr(a, "Systémové nastavení instalace nelze otevřít."))
                    }
                }.show()
            return
        }
        confirmInstall(a, apk)
    }

    /** The system package installer handles process shutdown; no invisible receiver lock. */
    private fun confirmInstall(a: Activity, apk: File) {
        if (!alive(a)) return
        runCatching {
            require(apk.isFile && apk.length() in 1L..MAX_APK)
            val archive = a.packageManager.getPackageArchiveInfo(apk.path, flags())
                ?: error("APK není čitelná.")
            val current = installed(a)
            require(a.packageName == ID && archive.packageName == ID &&
                code(archive) > code(current) &&
                signers(archive).isNotEmpty() && signers(archive) == signers(current)) {
                "Podpis, identita nebo verze APK nesouhlasí."
            }
            val uri = FileProvider.getUriForFile(a, a.packageName + ".updates", apk)
            a.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure {
            message(a, tr(a, "Systémový instalátor nelze otevřít.") + " " + updateError(a, it))
        }
    }

    private fun download(a: Activity, next: Update) {
        if (!alive(a)) return
        if (!working.compareAndSet(false, true)) { message(a, tr(a, "Jiná aktualizace již probíhá.")); return }
        val cancelled = AtomicBoolean(false)
        val progress = AlertDialog.Builder(a).setTitle(tr(a, "Stahování aktualizace"))
            .setMessage("0 %").setNegativeButton(tr(a, "Zrušit")) { _, _ -> cancelled.set(true) }.show()
        progress.setOnCancelListener { cancelled.set(true) }
        Thread {
            var file: File? = null
            val result = runCatching {
                val dir = File(a.cacheDir, "gamelingo-updates")
                require(dir.isDirectory || dir.mkdirs()) { "Nelze vytvořit složku aktualizací." }
                dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86400000L }?.forEach { it.delete() }
                val apk = File(dir, "verified-" + next.hash + ".apk")
                file = apk
                // Reuse a previously downloaded APK only after checking its full digest.
                if (apk.isFile && apk.length() == next.size) {
                    val cachedDigest = MessageDigest.getInstance("SHA-256")
                    apk.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            require(!cancelled.get() && alive(a)) { "Stahování zrušeno." }
                            val n = input.read(buffer)
                            if (n < 0) break
                            cachedDigest.update(buffer, 0, n)
                        }
                    }
                    if (hex(cachedDigest.digest()) == next.hash) {
                        val archive = a.packageManager.getPackageArchiveInfo(apk.path, flags())
                        val current = installed(a)
                        if (archive != null && archive.packageName == ID && a.packageName == ID &&
                            code(archive) == next.code && archive.versionName == next.name &&
                            code(archive) > code(current) &&
                            signers(archive) == setOf(next.cert) && signers(current) == setOf(next.cert)) {
                            return@runCatching apk
                        }
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                val start = android.os.SystemClock.elapsedRealtime()
                var total = 0L
                var shown = -1
                read(next.url) { input ->
                    apk.outputStream().use { out ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            require(!cancelled.get() && alive(a)) { "Stahování zrušeno." }
                            require(android.os.SystemClock.elapsedRealtime() - start < 300000L) { "Časový limit stahování." }
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= next.size && total <= MAX_APK) { "Neočekávaná velikost APK." }
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            val pct = (total * 100 / next.size).toInt()
                            if (pct != shown) {
                                shown = pct
                                a.runOnUiThread { if (progress.isShowing) progress.setMessage(pct.toString() + " %") }
                            }
                        }
                    }
                }
                require(total == next.size && hex(digest.digest()) == next.hash) { "Kontrolní součet APK nesouhlasí." }
                val archive = a.packageManager.getPackageArchiveInfo(apk.path, flags())
                    ?: error("APK není čitelná.")
                val current = installed(a)
                require(archive.packageName == ID && a.packageName == ID &&
                    code(archive) == next.code && archive.versionName == next.name &&
                    code(archive) > code(current) &&
                    signers(archive) == setOf(next.cert) && signers(current) == setOf(next.cert)) {
                    "Podpis, identita nebo verze APK nesouhlasí."
                }
                apk
            }
            a.runOnUiThread {
                working.set(false)
                if (!alive(a) || cancelled.get() || !progress.isShowing) { file?.delete(); return@runOnUiThread }
                progress.dismiss()
                result.fold(onSuccess = { apk ->
                    install(a, apk)
                }, onFailure = { file?.delete(); message(a, tr(a, "Aktualizace nebyla nainstalována:") + " " + updateError(a, it)) })
            }
        }.apply { name = "gamelingo-tv-update-download"; isDaemon = true }.start()
    }
}
