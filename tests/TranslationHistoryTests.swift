import Foundation
@main
struct TranslationHistoryTests {
    static func main() {
        var history = TranslationHistory()
        func append(_ text: String, target: String = "cs") {
            history.append(kind: "Dialog", source: "en", target: target, original: text, translated: "překlad")
        }
        append("off")
        precondition(history.entries.isEmpty)
        history.setEnabled(true)
        append("one")
        append("one")
        precondition(history.entries.count == 1)
        append("one", target: "sk")
        precondition(history.entries.count == 2)
        append("")
        precondition(history.entries.count == 2)
        for i in 0..<250 { append("line \(i)") }
        precondition(history.entries.count == 200)
        precondition(history.entries.first?.original == "line 50")
        append(String(repeating: "😀", count: 3000))
        precondition(history.entries.last!.original.utf16.count <= 4096)
        history.clear()
        precondition(history.entries.isEmpty && history.enabled)
        append("new")
        history.setEnabled(false)
        precondition(history.entries.isEmpty && !history.enabled)
        print("TranslationHistory: opt-in, dedup, capacity, bounded text, clear and disable passed")
    }
}
