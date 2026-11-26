import Foundation
import CoreGraphics

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
        let initial = AutomaticOCRZone.initial
        precondition(initial.count == 1 && initial[0].enabled && initial[0].valid)
        precondition(AutomaticOCRZone.validated(initial + initial).count == 1)
        let invalid = AutomaticOCRZone(name: "Invalid", rect: CGRect(x: 0.9, y: 0, width: 0.5, height: 1))
        precondition(AutomaticOCRZone.validated([invalid]).isEmpty)
        let many = (0..<11).map { AutomaticOCRZone(name: String($0), rect: crop) }
        precondition(AutomaticOCRZone.validated(many).count == 10)
        precondition(AutomaticDialogueLayout.select([hud], centered: false)?.text == "HUD")
        let blocks = AutomaticDialogueLayout.blocks([hud, dialogue, upper], centered: false)
        precondition(blocks.count == 3)
        var tracker = AutomaticBlockTracker()
        let zone = UUID(), other = UUID()
        tracker.observe(zone: zone, blocks: blocks, now: 1)
        let ids = Set(tracker.tracks.map(\.id))
        tracker.observe(zone: zone, blocks: blocks, now: 2)
        precondition(tracker.tracks.allSatisfy { $0.confirmations == 2 } && Set(tracker.tracks.map(\.id)) == ids)
        tracker.observe(zone: other, blocks: [blocks[0]], now: 2)
        tracker.observe(zone: zone, blocks: [], now: 3)
        precondition(tracker.tracks.count == 1 && tracker.tracks[0].zone == other)
        tracker.expire(now: 8)
        precondition(tracker.tracks.isEmpty)
        print("AUTOMATIC_DIALOGUE_LAYOUT_TESTS passed")
    }
}
