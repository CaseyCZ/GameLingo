package cz.titulky.tv

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class GameLingoCacheStore(context: Context, name: String = "gamelingo_translations_v1.db",
                                  capacity: Int = 20_000) : SQLiteOpenHelper(context, name, null, 1) {
    private val limit = capacity.coerceIn(1, 20_000)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE translations (
            engine TEXT NOT NULL, source TEXT NOT NULL, target TEXT NOT NULL,
            original TEXT NOT NULL, result TEXT NOT NULL, last_used INTEGER NOT NULL,
            PRIMARY KEY(engine,source,target,original))""")
        db.execSQL("CREATE INDEX translation_lru ON translations(last_used)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This is a regenerable cache, never user profiles or credentials.
        db.execSQL("DROP TABLE IF EXISTS translations")
        onCreate(db)
    }
    private fun valid(text: String, limit: Int) = text.isNotBlank() &&
        text.toByteArray(Charsets.UTF_8).size <= limit && !text.contains('\u0000')
    private fun key(engine: String, source: String, target: String, original: String) =
        valid(engine, 64) && valid(source, 32) && valid(target, 32) && valid(original, 4096)

    @Synchronized fun lookup(engines: List<String>, source: String, target: String, original: String): String? =
        runCatching {
            val db = writableDatabase
            db.beginTransaction()
            try {
                var result: String? = null
                for (engine in engines) {
                    if (!key(engine, source, target, original)) continue
                    val args = arrayOf(engine, source, target, original)
                    result = db.rawQuery("SELECT result FROM translations WHERE engine=? AND source=? AND target=? AND original=?", args)
                        .use { if (it.moveToFirst()) it.getString(0) else null }
                    if (result != null) {
                        db.execSQL("""UPDATE translations SET last_used=(SELECT COALESCE(MAX(last_used),0)+1 FROM translations)
                            WHERE engine=? AND source=? AND target=? AND original=?""", args)
                        break
                    }
                }
                db.setTransactionSuccessful()
                result
            } finally { db.endTransaction() }
        }.getOrNull()

    @Synchronized fun remember(engine: String, source: String, target: String, original: String, result: String) {
        if (!key(engine, source, target, original) || !valid(result, 8192)) return
        runCatching {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("""INSERT OR REPLACE INTO translations(engine,source,target,original,result,last_used)
                    VALUES(?,?,?,?,?,(SELECT COALESCE(MAX(last_used),0)+1 FROM translations))""",
                    arrayOf(engine, source, target, original, result))
                db.execSQL("""DELETE FROM translations WHERE rowid IN (
                    SELECT rowid FROM translations ORDER BY last_used DESC,rowid DESC LIMIT -1 OFFSET ?)""",
                    arrayOf(limit))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
}

/** Shared per app process; never perform SQLite operations on the UI thread.
 * Cache overload, unavailable storage or a corrupt database are cache misses. */
internal object GameLingoTranslationCache {
    val engines = listOf("google-gtx-v1", "microsoft-edge-v1", "mymemory-v1")
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(128), ThreadPoolExecutor.AbortPolicy())
    private var store: GameLingoCacheStore? = null
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var changing = false
    fun revision(): Long = epoch.get()
    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo_cache_privacy", Context.MODE_PRIVATE)
    fun enabled(c: Context) = !prefs(c).getBoolean("disabled", false)
    private fun allowed(c: Context, revision: Long) =
        !changing && enabled(c) && revision == epoch.get()

    /** Mutations are serialized with SQLite jobs. Never wipe anything other than
     * the translation database; deleteDatabase handles SQLite sidecars. */
    @Synchronized fun manage(c: Context, enable: Boolean?, callback: (Boolean) -> Unit) {
        if (changing) { callback(false); return }
        changing = true
        epoch.incrementAndGet()
        val app = c.applicationContext
        try {
            worker.execute {
                val success = runCatching {
                    if (enable != null) {
                        prefs(app).edit().putBoolean("disabled", !enable).commit()
                    } else {
                        store?.close()
                        store = null
                        val name = "gamelingo_translations_v1.db"
                        val path = app.getDatabasePath(name)
                        app.deleteDatabase(name)
                        listOf("", "-wal", "-shm", "-journal").none {
                            java.io.File(path.path + it).exists()
                        }
                    }
                }.getOrDefault(false)
                changing = false
                callback(success)
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            changing = false
            callback(false)
        }
    }
    private fun store(context: Context): GameLingoCacheStore =
        store ?: GameLingoCacheStore(context.applicationContext).also { store = it }

    fun lookup(context: Context?, source: String, target: String, original: String,
               callback: (String?) -> Unit) {
        val revision = revision()
        if (context == null || !allowed(context, revision)) { callback(null); return }
        try {
            worker.execute {
                val result = if (allowed(context, revision))
                    runCatching { store(context).lookup(engines, source, target, original) }.getOrNull()
                    else null
                callback(if (allowed(context, revision)) result else null)
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { callback(null) }
    }

    fun remember(context: Context?, engine: String, source: String, target: String,
                 original: String, result: String, revision: Long = epoch.get()) {
        if (context == null || !allowed(context, revision)) return
        runCatching { worker.execute {
            if (allowed(context, revision))
                runCatching { store(context).remember(engine, source, target, original, result) }
        } }
    }
}

/** Shared mobile/TV settings, D-pad reachable. No changes to account or history. */
internal object GameLingoCachePrivacyUi {
    private fun busy() = RemotePlayService.aktivni || PrijemService.bezi
    fun show(a: android.app.Activity) {
        if (busy()) {
            android.app.AlertDialog.Builder(a).setMessage("Nejdříve zastav překlad a příjem z telefonu.")
                .setPositiveButton("OK", null).show()
            return
        }
        val enabled = GameLingoTranslationCache.enabled(a)
        if (GameLingoTVUi.isTV(a)) {
            val body=android.widget.LinearLayout(a).apply { orientation=android.widget.LinearLayout.VERTICAL }
            body.addView(GameLingoTVUi.title(a,"Místní překladová paměť · "+if(enabled)"zapnuto" else "vypnuto",22f))
            body.addView(GameLingoTVUi.title(a,"Ukládá originály a překlady na tomto zařízení. Historie a profily jsou oddělené.",18f,true))
            var page: android.app.Dialog?=null
            body.addView(GameLingoTVUi.action(a,if(enabled)"Vypnout paměť" else "Zapnout paměť") {
                if(!busy()){page?.dismiss();change(a,!enabled)}
            })
            body.addView(GameLingoTVUi.action(a,"Vymazat paměť") {
                android.app.AlertDialog.Builder(a).setMessage("Vymazat pouze místní překladovou cache? Profily, účty ani historii to nesmaže.")
                    .setNegativeButton("Zrušit",null).setPositiveButton("Vymazat"){_,_->if(!busy()){page?.dismiss();change(a,null)}}.show()
            })
            page=GameLingoTVUi.page(a,"Překladová paměť",body,helpTopic="cache")
            return
        }
        android.app.AlertDialog.Builder(a)
            .setTitle("Překladová cache")
            .setMessage("Ukládá originály i překlady na tomto zařízení. Je oddělená od historie. Vypnutí může zvýšit počet online požadavků a omezit překlad bez internetu. Stav: " + if (enabled) "zapnuto" else "vypnuto")
            .setNeutralButton(if (enabled) "Vypnout" else "Zapnout") { _, _ ->
                if (!busy()) change(a, !enabled)
            }
            .setPositiveButton("Vymazat") { _, _ ->
                android.app.AlertDialog.Builder(a)
                    .setMessage("Vymazat pouze místní překladovou cache? Profily, účty ani historii to nesmaže.")
                    .setNegativeButton("Zrušit", null)
                    .setPositiveButton("Vymazat") { _, _ -> if (!busy()) change(a, null) }.show()
            }
            .setNegativeButton("Zavřít", null).show()
    }
    private fun change(a: android.app.Activity, value: Boolean?) {
        GameLingoTranslationCache.manage(a, value) { success ->
            a.runOnUiThread {
                if (!a.isFinishing && !a.isDestroyed) android.app.AlertDialog.Builder(a)
                    .setMessage(if (!success) "Operace se nezdařila. Zkus ji znovu." else when (value) {
                        true -> "Cache zapnuta."
                        false -> "Cache vypnuta; uložené překlady lze vymazat zvlášť."
                        null -> "Místní překladová cache vymazána."
                    }).setPositiveButton("OK", null).show()
            }
        }
    }
}
