import Foundation
import CoreGraphics

/// Stable IDs and normalized top-left screen geometry. Global automatic setup
/// works without a game profile; old standard preferences are not modified.
struct AutomaticOCRZone: Identifiable, Codable, Equatable, Sendable {
    var id = UUID()
    var name: String
    var enabled = true
    var rect: CGRect
    var centered = false
    // Optional for compatibility with existing saved zones and profiles.
    var dialogueDefault: Bool? = nil

    mutating func restoreArea() {
        let dialogue = dialogueDefault ?? (centered || name == "Dialogy")
        dialogueDefault = dialogue
        rect = dialogue ? AutomaticDialogueLayout.searchArea : CGRect(x: 0.2, y: 0.2, width: 0.6, height: 0.25)
        centered = dialogue
    }

    static var initial: [Self] {
        [.init(name: "Dialogy", rect: AutomaticDialogueLayout.searchArea, centered: true, dialogueDefault: true)]
    }

    var valid: Bool {
        rect.minX.isFinite && rect.minY.isFinite && rect.maxX.isFinite && rect.maxY.isFinite &&
        rect.width >= 0.08 - 0.000001 && rect.height >= 0.04 - 0.000001 &&
        rect.minX >= 0 && rect.minY >= 0 && rect.maxX <= 1.000001 && rect.maxY <= 1.000001
    }

    static func validated(_ zones: [Self]) -> [Self] {
        var ids = Set<UUID>()
        return Array(zones.filter { $0.valid && ids.insert($0.id).inserted }.prefix(10))
    }

    static func load() -> [Self] {
        guard let data = UserDefaults.standard.data(forKey: "gamelingo.automaticZones"),
              let saved = try? JSONDecoder().decode([Self].self, from: data) else { return initial }
        return validated(saved)
    }
}
