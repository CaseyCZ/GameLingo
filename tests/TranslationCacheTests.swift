import Foundation

@main
struct TranslationCacheTests {
    static func check(_ value: Bool, _ label: String) {
        guard value else { fatalError(label) }
    }
    static func main() async throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("test.sqlite")
        let first = GLTranslationCache(url: url, capacity: 3)
        await first.remember(engine: "google", source: "en", target: "cs", original: "Don't go", result: "Nechoď")
        await first.remember(engine: "edge", source: "en", target: "cs", original: "Don't go", result: "Nechoďte")
        await first.remember(engine: "google", source: "en", target: "sk", original: "Don't go", result: "Nechoď preč")
        let hit = await first.lookup(engine: "google", source: "en", target: "cs", original: "Don't go")
        check(hit == "Nechoď", "bound SQL and unicode")
        await first.remember(engine: "google", source: "de", target: "cs", original: "Hallo", result: "Ahoj")
        let evicted = await first.lookup(engine: "edge", source: "en", target: "cs", original: "Don't go")
        check(evicted == nil, "least recently used engine row evicted")
        await first.remember(engine: "google", source: "en", target: "cs", original: "bad", result: " ")
        await first.close()
        let second = GLTranslationCache(url: url, capacity: 3)
        let saved = await second.lookup(engine: "google", source: "en", target: "cs", original: "Don't go")
        let other = await second.lookup(engine: "google", source: "en", target: "sk", original: "Don't go")
        let miss = await second.lookup(engine: "google", source: "de", target: "cs", original: "Don't go")
        let bad = await second.lookup(engine: "google", source: "en", target: "cs", original: "bad")
        check(saved == "Nechoď" && other == "Nechoď preč" && miss == nil && bad == nil, "restart, languages and blank rejection")
        await second.close()
        print("TranslationCache: persistence, engine/language isolation and LRU passed")
    }
}
