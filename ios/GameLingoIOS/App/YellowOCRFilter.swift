import Foundation

/// Opt-in filter for explicit controller prompts, not general menu labels.
enum YellowOCRFilter {
    private static let prompt = try! NSRegularExpression(pattern: #"(?i)^(?:press|hold|tap) +(?:button +)?(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU|\[(?:A|B|X|Y|LB|RB|LT|RT|L1|L2|L3|R1|R2|R3|START|SELECT|OPTIONS|MENU)\])(?: +to +(?:continue|confirm|skip|select|go back))?[.!]?$"#)

    static func keeps(_ text: String, enabled: Bool) -> Bool {
        guard enabled else { return true }
        let line = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return prompt.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) == nil
    }
}
