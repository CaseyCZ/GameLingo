import Foundation
import CoreGraphics

/// Native first stage of the automatic dialogue mode. Coordinates are in the
/// decoder's normalized frame; the legacy PCT1 receiver supports one block.
struct AutomaticDialogueLayout: Equatable, Sendable {
    let centerX: Double
    let bottomY: Double
    let width: Double

    // Generic discovery band, NOT calibrated GTA VI console coordinates.
    static let searchArea = CGRect(x: 0, y: 0.65, width: 1, height: 0.35)

    struct Line {
        let text: String
        let rect: CGRect // top-left normalized full-frame coordinates
    }

    /// Vision observes the explicitly cropped image in bottom-left coordinates.
    static func fullFrameRect(croppedVision rect: CGRect, crop: CGRect) -> CGRect? {
        guard valid(rect), valid(crop) else { return nil }
        return CGRect(x: crop.minX + rect.minX * crop.width,
                      y: crop.minY + (1 - rect.maxY) * crop.height,
                      width: rect.width * crop.width,
                      height: rect.height * crop.height)
    }

    private static func valid(_ rect: CGRect) -> Bool {
        !rect.isNull && !rect.isInfinite && !rect.isEmpty &&
        rect.minX.isFinite && rect.minY.isFinite &&
        rect.maxX.isFinite && rect.maxY.isFinite &&
        rect.minX >= -0.000001 && rect.minY >= -0.000001 &&
        rect.maxX <= 1.000001 && rect.maxY <= 1.000001
    }

    struct Block: Equatable, Sendable {
        let text: String
        let rect: CGRect
        let layout: AutomaticDialogueLayout
        var background: UInt32? = nil
        var ink: UInt32? = nil
    }

    static func select(_ lines: [Line], centered: Bool = true) -> (text: String, layout: Self)? {
        blocks(lines, centered: centered).first.map { ($0.text, $0.layout) }
    }

    static func blocks(_ lines: [Line], centered: Bool) -> [Block] {
        // Only central dialogue candidates. Do not translate peripheral HUD.
        // Group vertically adjacent, horizontally overlapping text lines.
        let candidates = lines.filter {
            valid($0.rect) && !$0.text.isEmpty &&
            (!centered || abs($0.rect.midX - 0.5) <= 0.20)
        }.sorted { a, b in
            a.rect.minY == b.rect.minY ? a.rect.minX < b.rect.minX : a.rect.minY < b.rect.minY
        }
        var groups: [[Line]] = []
        for line in candidates {
            if let i = groups.indices.reversed().first(where: { idx in
                guard let last = groups[idx].last else { return false }
                let a = last.rect, b = line.rect
                let overlap = min(a.maxX, b.maxX) - max(a.minX, b.minX)
                return overlap >= 0.5 * min(a.width, b.width) &&
                    abs(a.height - b.height) <= 0.6 * max(a.height, b.height) &&
                    b.minY >= a.midY && b.minY - a.maxY <= max(a.height, b.height)
            }) {
                groups[i].append(line)
            } else {
                groups.append([line])
            }
        }
        // The default dialogue band deliberately retains its conservative
        // bottom-centre selection. Explicit custom zones may contain many blocks.
        var ranked: [(lines: [Line], rect: CGRect, score: CGFloat)] = []
        for group in groups {
            var rect = CGRect.null
            for line in group { rect = rect.union(line.rect) }
            let distance = abs(rect.midX - CGFloat(0.5))
            let score = rect.midY - distance
            ranked.append((group, rect, score))
        }
        ranked.sort { $0.score > $1.score }
        return ranked.prefix(centered ? 1 : 8).map { selected in
            let r = selected.rect
            let width = min(1.0, max(centered ? 0.15 : 0.08, Double(r.width) + (centered ? 0.06 : 0.008)))
            let x = min(1 - width / 2, max(width / 2, Double(r.midX)))
            let bottom = min(1.0, max(0.05, Double(r.maxY)))
            let text = selected.lines.map(\.text).joined(separator: " ")
            return Block(text: text, rect: r, layout: Self(centerX: x, bottomY: bottom, width: width))
        }
    }
}
