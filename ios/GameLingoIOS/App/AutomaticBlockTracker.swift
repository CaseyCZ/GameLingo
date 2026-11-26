import Foundation
import CoreGraphics

/// A successful read replaces only that zone's observations. A failed or
/// skipped OCR call must never be submitted as an empty read.
struct AutomaticBlockTracker {
    struct Track: Equatable, Identifiable {
        let id: UUID
        let zone: UUID
        var block: AutomaticDialogueLayout.Block
        var confirmations: Int
        var lastSeen: TimeInterval
    }
    private(set) var tracks: [Track] = []
    static let maxTracks = 64
    static let staleAfter: TimeInterval = 5
    static func normalized(_ text: String) -> String {
        text.split(whereSeparator: \.isWhitespace).joined(separator: " ").lowercased()
    }
    static func overlaps(_ a: CGRect, _ b: CGRect) -> Bool {
        let i = a.intersection(b)
        guard !i.isNull, !i.isEmpty else { return false }
        let intersection = i.width * i.height
        let union = a.width * a.height + b.width * b.height - intersection
        return union > 0 && intersection / union >= 0.35
    }
    mutating func reset() { tracks.removeAll() }
    mutating func expire(now: TimeInterval) {
        tracks.removeAll { now - $0.lastSeen > Self.staleAfter }
    }
    mutating func observe(zone: UUID, blocks: [AutomaticDialogueLayout.Block], now: TimeInterval) {
        expire(now: now)
        var previous = tracks.filter { $0.zone == zone }
        tracks.removeAll { $0.zone == zone }
        for block in blocks.prefix(8) where tracks.count < Self.maxTracks {
            if tracks.contains(where: {
                $0.zone == zone && Self.normalized($0.block.text) == Self.normalized(block.text) &&
                Self.overlaps($0.block.rect, block.rect)
            }) { continue }
            if let i = previous.firstIndex(where: {
                Self.normalized($0.block.text) == Self.normalized(block.text) &&
                Self.overlaps($0.block.rect, block.rect)
            }) {
                var old = previous.remove(at: i)
                old.block = block
                old.confirmations = min(2, old.confirmations + 1)
                old.lastSeen = now
                tracks.append(old)
            } else {
                tracks.append(Track(id: UUID(), zone: zone, block: block, confirmations: 1, lastSeen: now))
            }
        }
    }
}
