import Foundation
import SQLite3

/// Private, persistent, bounded LRU. A separate actor serializes disk access
/// without occupying the Vision OCR actor while a cache lookup is pending.
actor GLTranslationCache {
    private var db: OpaquePointer?
    private var generation: UInt64 = 0
    private var enabled = !UserDefaults.standard.bool(forKey: "translationCacheDisabled")

    func revision() -> UInt64 { generation }
    func setEnabled(_ value: Bool) {
        generation &+= 1
        enabled = value
        UserDefaults.standard.set(!value, forKey: "translationCacheDisabled")
        if !value { close() }
    }

    /// Logical removal of the local regenerable database and SQLite sidecars.
    /// Never delete a profile, history or credential store.
    func purge() -> Bool {
        generation &+= 1
        close()
        do {
            let location: URL
            if let url { location = url }
            else {
                // A never-used cache has nothing to remove; do not create a directory.
                guard let directory = FileManager.default.urls(for: .applicationSupportDirectory,
                    in: .userDomainMask).first else { return false }
                location = directory.appendingPathComponent("GameLingoTranslationCache/translations-v1.sqlite")
            }
            for suffix in ["", "-wal", "-shm", "-journal"] {
                let file = URL(fileURLWithPath: location.path + suffix)
                if FileManager.default.fileExists(atPath: file.path) {
                    try FileManager.default.removeItem(at: file)
                }
            }
            return true
        } catch { return false }
    }
    private let url: URL?
    private let capacity: Int
    private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    init(url: URL? = nil, capacity: Int = 20_000) {
        self.url = url
        self.capacity = max(1, min(capacity, 20_000))
    }

    deinit { if let db { sqlite3_close(db) } }

    func close() {
        if let db { sqlite3_close(db); self.db = nil }
    }

    private func execute(_ db: OpaquePointer, _ sql: String) -> Bool {
        sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK
    }

    private func open() -> OpaquePointer? {
        guard enabled else { return nil }
        if let db { return db }
        do {
            let location: URL
            if let url { location = url }
            else {
                var dir = try FileManager.default.url(for: .applicationSupportDirectory,
                    in: .userDomainMask, appropriateFor: nil, create: true)
                    .appendingPathComponent("GameLingoTranslationCache", isDirectory: true)
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                var values = URLResourceValues()
                values.isExcludedFromBackup = true
                try dir.setResourceValues(values)
                try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: dir.path)
                location = dir.appendingPathComponent("translations-v1.sqlite")
            }
            var handle: OpaquePointer?
            let result = sqlite3_open_v2(location.path, &handle,
                SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nil)
            guard result == SQLITE_OK, let handle else {
                if let handle { sqlite3_close(handle) }
                return nil
            }
            sqlite3_busy_timeout(handle, 1000)
            guard execute(handle, """
                CREATE TABLE IF NOT EXISTS translations (
                    engine TEXT NOT NULL, source TEXT NOT NULL, target TEXT NOT NULL,
                    original TEXT NOT NULL, result TEXT NOT NULL, last_used INTEGER NOT NULL,
                    PRIMARY KEY(engine, source, target, original)
                );
                CREATE INDEX IF NOT EXISTS translation_lru ON translations(last_used);
                """) else { sqlite3_close(handle); return nil }
            db = handle
            return handle
        } catch { return nil }
    }

    private func valid(_ text: String, limit: Int) -> Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            text.utf8.count <= limit && !text.utf8.contains(0)
    }

    private func key(_ engine: String, _ source: String, _ target: String, _ original: String) -> [String]? {
        guard valid(engine, limit: 64), valid(source, limit: 32),
              valid(target, limit: 32), valid(original, limit: 4096) else { return nil }
        return [engine, source, target, original]
    }

    private func prepare(_ db: OpaquePointer, _ sql: String, _ bindings: [String]) -> OpaquePointer? {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK,
              let statement else { if let statement { sqlite3_finalize(statement) }; return nil }
        for (index, value) in bindings.enumerated() {
            guard sqlite3_bind_text(statement, Int32(index + 1), value, -1, transient) == SQLITE_OK else {
                sqlite3_finalize(statement)
                return nil
            }
        }
        return statement
    }

    func lookup(engine: String, source: String, target: String, original: String, revision: UInt64? = nil) -> String? {
        guard enabled, revision == nil || revision == generation else { return nil }
        guard let bindings = key(engine, source, target, original), let db = open(),
              execute(db, "BEGIN IMMEDIATE") else { return nil }
        var committed = false
        defer { if !committed { _ = execute(db, "ROLLBACK") } }
        guard let read = prepare(db, """
            SELECT result FROM translations WHERE engine=? AND source=? AND target=? AND original=?
            """, bindings) else { return nil }
        let result = sqlite3_step(read) == SQLITE_ROW
            ? sqlite3_column_text(read, 0).map { String(cString: $0) } : nil
        sqlite3_finalize(read)
        guard let result else { return nil }
        guard let touch = prepare(db, """
            UPDATE translations SET last_used=(SELECT COALESCE(MAX(last_used),0)+1 FROM translations)
            WHERE engine=? AND source=? AND target=? AND original=?
            """, bindings) else { return nil }
        let touched = sqlite3_step(touch) == SQLITE_DONE
        sqlite3_finalize(touch)
        guard touched, execute(db, "COMMIT") else { return nil }
        committed = true
        return result
    }

    func remember(engine: String, source: String, target: String, original: String, result: String, revision: UInt64? = nil) {
        guard enabled, revision == nil || revision == generation else { return }
        guard let bindings = key(engine, source, target, original),
              valid(result, limit: 8192), let db = open(),
              execute(db, "BEGIN IMMEDIATE") else { return }
        var committed = false
        defer { if !committed { _ = execute(db, "ROLLBACK") } }
        guard let write = prepare(db, """
            INSERT OR REPLACE INTO translations(engine,source,target,original,result,last_used)
            VALUES(?,?,?,?,?,(SELECT COALESCE(MAX(last_used),0)+1 FROM translations))
            """, bindings + [result]) else { return }
        let written = sqlite3_step(write) == SQLITE_DONE
        sqlite3_finalize(write)
        guard written, let trim = prepare(db, """
            DELETE FROM translations WHERE rowid IN (
                SELECT rowid FROM translations ORDER BY last_used DESC,rowid DESC LIMIT -1 OFFSET ?
            )
            """, []) else { return }
        let trimmed = sqlite3_bind_int(trim, 1, Int32(capacity)) == SQLITE_OK &&
            sqlite3_step(trim) == SQLITE_DONE
        sqlite3_finalize(trim)
        guard trimmed, execute(db, "COMMIT") else { return }
        committed = true
    }
}
