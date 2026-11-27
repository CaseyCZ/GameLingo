import Foundation

/// Session-local display context. Never persisted or sent to a translation provider.
struct DialogueWindow {
    private struct Entry {
        let source: String
        let translation: String
        let time: TimeInterval
    }
    private var entries: [Entry] = []

    mutating func reset() { entries.removeAll() }

    mutating func render(source: String, translation: String, enabled: Bool, now: TimeInterval, previousCount: Int = 1) -> String {
        guard enabled, !translation.isEmpty else { reset(); return translation }
        let key = source.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        entries.removeAll { now - $0.time > 12 || now < $0.time }
        if let last = entries.last, last.source == key ||
            (min(last.source.count, key.count) >= 8 && (last.source.hasPrefix(key + " ") || key.hasPrefix(last.source + " "))) {
            entries.removeLast()
        }
        entries.append(Entry(source: key, translation: translation, time: now))
        entries = Array(entries.suffix(min(2, max(1, previousCount)) + 1))
        var seen: Set<String> = [translation]
        var previous: [String] = []
        var remaining = 300
        for entry in entries.dropLast().reversed() {
            guard seen.insert(entry.translation).inserted, entry.translation.count <= remaining else { continue }
            previous.append(entry.translation)
            remaining -= entry.translation.count
        }
        return (Array(previous.reversed()) + [translation]).joined(separator: "\n")
    }
}
