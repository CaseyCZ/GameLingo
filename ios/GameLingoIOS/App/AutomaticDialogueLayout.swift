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

    static func select(_ lines: [Line], centered: Bool = true) -> (text: String, layout: Self)? {
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
        // Prefer the bottom-centred group. The one-block receiver cannot
        // display two independently located groups, so do not concatenate them.
        let ranked = groups.map { group in
            (group, group.reduce(CGRect.null) { $0.union($1.rect) })
        }
        guard let selected = ranked.max(by: { a, b in
            (a.1.midY - abs(a.1.midX - 0.5)) < (b.1.midY - abs(b.1.midX - 0.5))
        }) else { return nil }
        let r = selected.1
        let width = min(1.0, max(0.15, Double(r.width) + 0.06))
        let x = min(1 - width / 2, max(width / 2, Double(r.midX)))
        let bottom = min(0.99, max(0.05, Double(r.maxY)))
        let text = selected.0.map(\.text).joined(separator: " ")
        return (text, Self(centerX: x, bottomY: bottom, width: width))
    }
}
