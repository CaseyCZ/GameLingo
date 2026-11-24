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
    val engines = listOf("google-gtx-v1", "microsoft-edge-v1", "mymemory-v1", "mlkit-v1")
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(128), ThreadPoolExecutor.AbortPolicy())
    private var store: GameLingoCacheStore? = null
    private fun store(context: Context): GameLingoCacheStore =
        store ?: GameLingoCacheStore(context.applicationContext).also { store = it }

    fun lookup(context: Context?, source: String, target: String, original: String,
               callback: (String?) -> Unit) {
        if (context == null) { callback(null); return }
        try {
            worker.execute {
                val result = runCatching { store(context).lookup(engines, source, target, original) }.getOrNull()
                callback(result)
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { callback(null) }
    }

    fun remember(context: Context?, engine: String, source: String, target: String,
                 original: String, result: String) {
        if (context == null) return
        runCatching { worker.execute {
            runCatching { store(context).remember(engine, source, target, original, result) }
        } }
    }
}
