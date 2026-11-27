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
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageInstaller
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
        if (busy()) { message(a, tr(a, "Nejdříve zastav překlad.")); return }
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

    // The PackageInstaller callback is explicit and private to this app. A persisted
    // session/nonce lets a final result survive replacement of the old app process.
    private const val RESULT_ACTION = "cz.titulky.tv.GAMELINGO_INSTALL_RESULT"
    private fun installPrefs(c: Context) = c.getSharedPreferences("gamelingo_install_session", Context.MODE_PRIVATE)
    private class InstallAttempt(val activity: Activity, val progress: AlertDialog) {
        val cancelled = AtomicBoolean(false)
        @Volatile var sessionId = -1
        @Volatile var foreground = true
        var confirmationStarted = false
        lateinit var observer: android.app.Application.ActivityLifecycleCallbacks
    }
    private var activeInstall: InstallAttempt? = null // main thread only

    private fun finishAttempt(attempt: InstallAttempt) {
        if (activeInstall !== attempt) return
        activeInstall = null
        attempt.activity.application.unregisterActivityLifecycleCallbacks(attempt.observer)
        if (attempt.progress.isShowing) attempt.progress.dismiss()
        working.set(false)
    }

    private fun abandon(c: Context, id: Int) {
        if (id >= 0) runCatching { c.packageManager.packageInstaller.abandonSession(id) }
    }

    private fun clearSession(c: Context, id: Int) {
        val prefs = installPrefs(c)
        if (prefs.getInt("session", -1) == id) prefs.edit().remove("session").remove("nonce").apply()
    }

    private fun install(a: Activity, apk: File) {
        if (!alive(a)) return
        if (busy()) { message(a, tr(a, "Nejdříve zastav překlad.")); return }
        // Do not reject a verified APK merely because canRequestPackageInstalls()
        // is false. The native installer owns the permission and consent flow.
        confirmInstall(a, apk)
    }

    private fun confirmInstall(a: Activity, apk: File) {
        if (!alive(a)) return
        if (busy()) { message(a, tr(a, "Nejdříve zastav překlad.")); return }
        if (!working.compareAndSet(false, true)) { message(a, tr(a, "Jiná aktualizace již probíhá.")); return }
        val app = a.applicationContext
        val progress = AlertDialog.Builder(a).setTitle(tr(a, "Aktualizace GameLingo"))
            .setMessage(tr(a, "Čekám na systémový souhlas"))
            .setNegativeButton(tr(a, "Zrušit"), null).create()
        val attempt = InstallAttempt(a, progress)
        activeInstall = attempt
        attempt.observer = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) { if (activity === a) attempt.foreground = true }
            override fun onActivityPaused(activity: Activity) {
                if (activity !== a) return
                attempt.foreground = false
                if (!attempt.confirmationStarted) attempt.cancelled.set(true)
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity !== a) return
                if (!attempt.confirmationStarted) attempt.cancelled.set(true)
                finishAttempt(attempt)
            }
        }
        a.application.registerActivityLifecycleCallbacks(attempt.observer)
        progress.setOnCancelListener { attempt.cancelled.set(true) }
        progress.show()
        progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            attempt.cancelled.set(true)
            progress.dismiss()
        }
        Thread {
            var id = -1
            var committed = false
            val result = runCatching {
                require(apk.isFile && apk.length() in 1L..MAX_APK)
                val expectedHash = apk.name.removePrefix("verified-").removeSuffix(".apk")
                require(hashPattern.matches(expectedHash) && apk.name == "verified-" + expectedHash + ".apk")
                val archive = a.packageManager.getPackageArchiveInfo(apk.path, flags()) ?: error("APK není čitelná.")
                val current = installed(a)
                require(a.packageName == ID && archive.packageName == ID &&
                    code(archive) > code(current) && signers(archive).isNotEmpty() && signers(archive) == signers(current)) {
                    "Podpis, identita nebo verze APK nesouhlasí."
                }
                val installer = app.packageManager.packageInstaller
                val prefs = installPrefs(app)
                val previous = prefs.getInt("session", -1)
                // A new explicit attempt may discard an orphaned, unconfirmed session.
                // Do not replace a session still known to this process.
                if (previous >= 0) {
                    if (installer.getSessionInfo(previous) != null) abandon(app, previous)
                    clearSession(app, previous)
                }
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(ID)
                    setSize(apk.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                id = installer.createSession(params)
                attempt.sessionId = id
                val nonce = java.util.UUID.randomUUID().toString()
                check(prefs.edit().putInt("session", id).putString("nonce", nonce).commit())
                installer.openSession(id).use { session ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val size = apk.length()
                    var copied = 0L
                    apk.inputStream().use { input ->
                        session.openWrite("base.apk", 0, size).use { out ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                require(!attempt.cancelled.get() && alive(a)) { "Stahování zrušeno." }
                                val n = input.read(buffer)
                                if (n < 0) break
                                copied += n
                                require(copied <= size && copied <= MAX_APK)
                                digest.update(buffer, 0, n)
                                out.write(buffer, 0, n)
                            }
                            require(copied == size && hex(digest.digest()) == expectedHash) { "Kontrolní součet APK nesouhlasí." }
                            session.fsync(out)
                        }
                    }
                    require(!attempt.cancelled.get() && alive(a) && !busy()) { "Stahování zrušeno." }
                    val callback = Intent(app, GameLingoInstallReceiver::class.java).apply {
                        action = RESULT_ACTION
                        data = Uri.parse("gamelingo-install://" + ID + "/" + id + "/" + nonce)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getBroadcast(app, id, callback, flags)
                    session.commit(status.intentSender)
                    committed = true
                }
            }
            if (!committed) {
                abandon(app, id)
                clearSession(app, id)
                a.runOnUiThread {
                    finishAttempt(attempt)
                    if (alive(a) && !attempt.cancelled.get()) {
                        message(a, tr(a, "Aktualizace nebyla nainstalována:") + " " +
                            updateError(a, result.exceptionOrNull() ?: IllegalStateException()))
                    }
                }
            }
        }.apply { name = "gamelingo-native-install"; isDaemon = true }.start()
    }

    internal fun installResult(context: Context, intent: Intent) {
        if (intent.action != RESULT_ACTION) return
        val prefs = installPrefs(context)
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val nonce = prefs.getString("nonce", null) ?: return
        if (id < 0 || prefs.getInt("session", -1) != id ||
            intent.data != Uri.parse("gamelingo-install://" + ID + "/" + id + "/" + nonce)) return
        val attempt = activeInstall?.takeIf { it.sessionId == id }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            }
            // Only open the system confirmation while the initiating activity is
            // actively visible; otherwise retain the APK for a new explicit attempt.
            if (attempt != null && alive(attempt.activity) && attempt.foreground &&
                !attempt.cancelled.get() && !busy() && confirmation != null &&
                (attempt.activity.hasWindowFocus() || attempt.progress.window?.decorView?.hasWindowFocus() == true)) {
                attempt.confirmationStarted = true
                attempt.progress.dismiss()
                if (runCatching { attempt.activity.startActivity(confirmation) }.isSuccess) return
            }
            abandon(context, id)
            clearSession(context, id)
            attempt?.let {
                finishAttempt(it)
                if (alive(it.activity) && it.foreground && !it.cancelled.get())
                    message(it.activity, tr(it.activity, "Systémový instalátor nelze otevřít."))
            }
            return
        }
        clearSession(context, id)
        // Self-updates can kill the old process before this callback. Keep a
        // non-sensitive status for the next start, not an assumption of success.
        prefs.edit().putInt("last_status", status).apply()
        if (attempt == null) return
        val a = attempt.activity
        finishAttempt(attempt)
        if (!alive(a)) return
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(a, tr(a, "Nainstalováno:") + " " + runCatching { installed(a).versionName }.getOrDefault(""), Toast.LENGTH_LONG).show()
        } else if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls() && attempt.foreground) {
            AlertDialog.Builder(a).setTitle(tr(a, "Povolení instalace"))
                .setMessage(tr(a, "Instalace nebyla povolena. APK je stažená; otevři aktualizaci znovu."))
                .setNegativeButton(tr(a, "Zrušit"), null)
                .setPositiveButton(tr(a, "Otevřít nastavení")) { _, _ ->
                    runCatching { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName))) }
                        .onFailure { message(a, tr(a, "Systémové nastavení instalace nelze otevřít.")) }
                }.show()
        } else if (attempt.foreground) {
            message(a, tr(a, "Aktualizace nebyla nainstalována:") + " (" + status + ")")
        }
    }

    private fun download(a: Activity, next: Update) {
        if (!alive(a)) return
        if (busy()) { message(a, tr(a, "Nejdříve zastav překlad.")); return }
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

/** System callback only; non-exported in both generated manifests. */
class GameLingoInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GameLingoTVUpdater.installResult(context, intent)
    }
}
