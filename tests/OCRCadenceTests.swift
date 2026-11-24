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
        print("OCRCadence: mode order, independent adaptive budget and fallback passed")
    }
}
