import Foundation

@main struct AutomaticDialogueLayoutTests {
    static func main() {
        let crop = AutomaticDialogueLayout.searchArea
        let box = AutomaticDialogueLayout.fullFrameRect(croppedVision: CGRect(x: 0.3, y: 0.1, width: 0.4, height: 0.1), crop: crop)!
        precondition(abs(box.minY - 0.93) < 0.00001)
        precondition(abs(box.width - 0.4) < 0.00001)
        let dialogue = AutomaticDialogueLayout.Line(text: "Hello", rect: box)
        let hud = AutomaticDialogueLayout.Line(text: "HUD", rect: CGRect(x: 0.02, y: 0.9, width: 0.08, height: 0.03))
        let upper = AutomaticDialogueLayout.Line(text: "Mission", rect: CGRect(x: 0.35, y: 0.70, width: 0.3, height: 0.03))
        let result = AutomaticDialogueLayout.select([hud, upper, dialogue])!
        precondition(result.text == "Hello")
        precondition(abs(result.layout.centerX - 0.5) < 0.00001)
        precondition(abs(result.layout.bottomY - 0.965) < 0.00001)
        precondition(abs(result.layout.width - 0.46) < 0.00001)
        let above = AutomaticDialogueLayout.Line(text: "First", rect: CGRect(x: 0.3, y: 0.895, width: 0.4, height: 0.03))
        precondition(AutomaticDialogueLayout.select([dialogue, above])?.text == "First Hello")
        precondition(AutomaticDialogueLayout.select([hud]) == nil)
        precondition(AutomaticDialogueLayout.fullFrameRect(croppedVision: .null, crop: crop) == nil)
        precondition(AutomaticDialogueLayout.fullFrameRect(croppedVision: CGRect(x: -0.1, y: 0, width: 0.5, height: 0.2), crop: crop) == nil)
        print("AUTOMATIC_DIALOGUE_LAYOUT_TESTS passed")
    }
}
