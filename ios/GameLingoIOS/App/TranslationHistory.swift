import Foundation

/// Separate, opt-in history. No network, disk, screenshots or identifiers.
/// Retains the most recent 200 accepted entries during this app process.
struct TranslationHistory {
    struct Entry: Identifiable, Equatable {
        let id: UUID
        let date: Date
        let kind: String
        let source: String
        let target: String
        let original: String
        let translated: String
    }
    private(set) var enabled = false
    private(set) var entries: [Entry] = []
    static let limit = 200

    mutating func setEnabled(_ value: Bool) {
        enabled = value
        if !value { clear() }
    }
    mutating func clear() { entries.removeAll() }

    mutating func append(kind: String, source: String, target: String,
                         original: String, translated: String, now: Date = Date()) {
        guard enabled, !original.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !translated.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        // Limit in UTF-16 code units; preserve grapheme boundaries at truncation.
        func bounded(_ text: String) -> String {
            guard text.utf16.count > 4096 else { return text }
            var prefix = String()
            var units = 0
            for char in text {
                let size = String(char).utf16.count
                if units + size > 4095 { break }
                prefix.append(char)
                units += size
            }
            return prefix + "…"
        }
        let input = bounded(original), output = bounded(translated)
        if let last = entries.last, last.kind == kind, last.source == source,
           last.target == target, last.original == input, last.translated == output { return }
        entries.append(Entry(id: UUID(), date: now, kind: kind, source: source,
                             target: target, original: input, translated: output))
        if entries.count > Self.limit { entries.removeFirst(entries.count - Self.limit) }
    }
}
