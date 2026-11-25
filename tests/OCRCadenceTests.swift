import Foundation

@main
struct OCRCadenceTests {
    static func main() {
        let stable = OCRCadence.preset("classic")
        let balanced = OCRCadence.preset("automatic")
        let fast = OCRCadence.preset("street")
        precondition(stable.dialogue > balanced.dialogue && balanced.dialogue > fast.dialogue)
        precondition(stable.frame > balanced.frame && balanced.frame > fast.frame)
        precondition(stable.gameTextInterval(asText: false, recentCost: 0) == 0.65)
        precondition(fast.gameTextInterval(asText: false, recentCost: 0) == 0.30)
        precondition(fast.gameTextInterval(asText: true, recentCost: 0) == 0.375)
        precondition(fast.gameTextInterval(asText: false, recentCost: 0.8) > 1.1)
        precondition(fast.gameTextInterval(asText: true, recentCost: 20) == 1.5)
        precondition(fast.gameTextInterval(asText: false, recentCost: .nan) == 0.30)
        precondition(OCRCadence.preset("unknown").dialogue == stable.dialogue)
        for prompt in ["Press A", "Hold R2", "TAP [X] to continue.", "Press button LB to go back"] {
            precondition(YellowOCRFilter.keeps(prompt, enabled: false))
            precondition(!YellowOCRFilter.keeps(prompt, enabled: true))
        }
        for option in ["YES", "NO", "LEAVE", "Hold on", "Press X to open the door", "A", "Inventory"] {
            precondition(YellowOCRFilter.keeps(option, enabled: true))
        }
        for shortChoice in ["NO", "OK", "NE", "はい", "YES"] {
            precondition(YellowOCRFilter.hasReadableText(shortChoice))
        }
        for noise in ["", "X", "1", "A!", "..."] {
            precondition(!YellowOCRFilter.hasReadableText(noise))
        }
        let ignored = OCRIgnoredLines.entries("  SAVE  GAME\nLoading\n")
        precondition(!OCRIgnoredLines.keeps("save game", entries: ignored))
        precondition(OCRIgnoredLines.keeps("Please save game now", entries: ignored))
        precondition(OCRIgnoredLines.keeps("Loading", entries: []))
        var window = DialogueWindow()
        precondition(window.render(source: "First", translation: "První", enabled: true, now: 1) == "První")
        precondition(window.render(source: "Second", translation: "Druhý", enabled: true, now: 2) == "První\nDruhý")
        precondition(window.render(source: "Second", translation: "Druhá", enabled: true, now: 3) == "První\nDruhá")
        precondition(window.render(source: "Third", translation: "Třetí", enabled: true, now: 20) == "Třetí")
        precondition(window.render(source: "Fourth", translation: "Čtvrtý", enabled: false, now: 21) == "Čtvrtý")
        window.reset()
        _ = window.render(source: "1", translation: "A", enabled: true, now: 1, previousCount: 2)
        _ = window.render(source: "2", translation: "B", enabled: true, now: 2, previousCount: 2)
        precondition(window.render(source: "3", translation: "C", enabled: true, now: 3, previousCount: 2) == "A\nB\nC")
        precondition(window.render(source: "4", translation: "D", enabled: true, now: 4, previousCount: 1) == "C\nD")
        print("OCRCadence: mode order, independent adaptive budget, fallback and yellow filter passed")
    }
}
