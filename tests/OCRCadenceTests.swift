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
        let ignored = OCRIgnoredLines.entries("  SAVE  GAME\nLoading\n")
        precondition(!OCRIgnoredLines.keeps("save game", entries: ignored))
        precondition(OCRIgnoredLines.keeps("Please save game now", entries: ignored))
        precondition(OCRIgnoredLines.keeps("Loading", entries: []))
        print("OCRCadence: mode order, independent adaptive budget, fallback and yellow filter passed")
    }
}
