import Foundation

/// Opt-in filter for explicit controller prompts, not general menu labels.
enum OCRIgnoredLines {
    /// Exact whole-line matching, never a substring of a real dialogue.
    static func normalize(_ text: String) -> String {
        text.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ").lowercased()
    }
    static func entries(_ raw: String) -> Set<String> {
        Set(raw.prefix(8192).split(whereSeparator: { $0.isNewline }).prefix(100).map { normalize(String($0)) }.filter { !$0.isEmpty })
    }
    static func keeps(_ text: String, entries: Set<String>) -> Bool {
        !entries.contains(normalize(text))
    }
}

enum YellowOCRFilter {
    private static let prompt = try! NSRegularExpression(pattern: #"(?i)^(?:press|hold|tap) +(?:button +)?(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU|\[(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU)\])(?: +to +(?:continue|confirm|skip|select|go back))?[.!]?$"#)

    /// Match the Android game-text lane: retain short choices such as NO.
    static func hasReadableText(_ text: String) -> Bool {
        text.filter { $0.isLetter }.count >= 2
    }

    static func keeps(_ text: String, enabled: Bool) -> Bool {
        guard enabled else { return true }
        let line = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return prompt.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) == nil
    }
}
