import Foundation

/// Shared preset meaning: stable, balanced, fast. Platform-specific floors
/// account for different recognition engines and device capabilities.
struct OCRCadence {
    let dialogue: TimeInterval
    let frame: TimeInterval
    let gameText: TimeInterval

    static func preset(_ key: String) -> OCRCadence {
        switch key {
        case "automatic": return OCRCadence(dialogue: 0.42, frame: 0.37, gameText: 0.45)
        case "street": return OCRCadence(dialogue: 0.28, frame: 0.26, gameText: 0.30)
        default: return OCRCadence(dialogue: 0.60, frame: 0.55, gameText: 0.65)
        }
    }

    /// A separate budget for the yellow area. A slow read stretches only
    /// its own interval. The busy/actor gate still prevents overlapping OCR.
    func gameTextInterval(asText: Bool, recentCost: TimeInterval) -> TimeInterval {
        let floor = gameText * (asText ? 1.25 : 1)
        let cost = recentCost.isFinite ? max(0, recentCost) : 0
        return max(floor, min(1.5, cost * 1.5))
    }
}
