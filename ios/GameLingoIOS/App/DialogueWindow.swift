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

    mutating func render(source: String, translation: String, enabled: Bool, now: TimeInterval) -> String {
        guard enabled, !translation.isEmpty else { reset(); return translation }
        let key = source.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ").lowercased()
        entries.removeAll { now - $0.time > 12 || now < $0.time }
        if let last = entries.last, last.source == key ||
            (min(last.source.count, key.count) >= 8 && (last.source.hasPrefix(key + " ") || key.hasPrefix(last.source + " "))) {
            entries.removeLast()
        }
        entries.append(Entry(source: key, translation: translation, time: now))
        entries = Array(entries.suffix(2))
        if entries.count == 2, entries[0].translation != translation {
            return entries[0].translation + "\n" + translation
        }
        return translation
    }
}
