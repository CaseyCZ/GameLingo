import Foundation

/// Local, deterministic postprocessing. Never changes the provider cache.
struct GameGlossary: Codable, Equatable {
    struct Rule: Codable, Equatable {
        let source: String
        let wrong: String
        let preferred: String
    }
    let sourceLanguage: String
    let targetLanguage: String
    let rules: [Rule]
    static let maxRules = 50
    static func parse(_ text: String, source: String, target: String) -> GameGlossary? {
        guard text.utf16.count <= 25_000 else { return nil }
        var rules = [Rule]()
        var seen = Set<String>()
        for line in text.components(separatedBy: .newlines) {
            if line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { continue }
            let fields = line.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }
            guard fields.count == 3, !fields[0].isEmpty, !fields[2].isEmpty,
                  fields.allSatisfy({ $0.utf16.count <= 160 && !$0.contains("\0") }),
                  seen.insert(fields[0]).inserted, rules.count < maxRules else { return nil }
            rules.append(Rule(source: fields[0], wrong: fields[1], preferred: fields[2]))
        }
        return GameGlossary(sourceLanguage: source, targetLanguage: target, rules: rules)
    }
    var editorText: String { rules.map { "\($0.source) | \($0.wrong) | \($0.preferred)" }.joined(separator: "\n") }

    private static func pattern(_ text: String) -> String {
        "(?<![\\p{L}\\p{N}_])" + NSRegularExpression.escapedPattern(for: text) + "(?![\\p{L}\\p{N}_])"
    }
    func apply(original: String, translated: String, source: String, target: String) -> String {
        guard source == sourceLanguage, target == targetLanguage, !translated.isEmpty,
              original.utf16.count <= 8192, translated.utf16.count <= 8192 else { return translated }
        if let exact = rules.first(where: { $0.source == original.trimmingCharacters(in: .whitespacesAndNewlines) }) {
            return exact.preferred
        }
        let eligible = rules.filter {
            !$0.wrong.isEmpty && original.range(of: Self.pattern($0.source), options: .regularExpression) != nil
        }.sorted { $0.wrong.utf16.count > $1.wrong.utf16.count }
        // Find all replacements on the original provider result, not on
        // earlier replacements; no chaining, regex injection or reordering.
        var hits = [(NSRange, String)]()
        let full = NSRange(translated.startIndex..., in: translated)
        for rule in eligible {
            guard let regex = try? NSRegularExpression(pattern: Self.pattern(rule.wrong)) else { continue }
            for match in regex.matches(in: translated, range: full) {
                if !hits.contains(where: { NSIntersectionRange($0.0, match.range).length > 0 }) {
                    hits.append((match.range, rule.preferred))
                }
                if hits.count >= 256 { break }
            }
            if hits.count >= 256 { break }
        }
        let result = NSMutableString(string: translated)
        for (range, value) in hits.sorted(by: { $0.0.location > $1.0.location }) {
            result.replaceCharacters(in: range, with: value)
        }
        return result.length <= 8192 ? result as String : translated
    }
}
