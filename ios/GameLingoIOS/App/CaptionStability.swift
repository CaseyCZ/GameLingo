import Foundation

/// Dialogue OCR stabilization, driven by monotonic timestamps from real reads.
/// A skipped fingerprint is not an empty observation. Independent of translation.
struct CaptionStability {
    enum Decision: Equatable { case hold, accept(String), clear }
    private(set) var accepted = ""
    private var candidate: String?
    private var candidateSince: TimeInterval?
    private var missingSince: TimeInterval?
    var needsConfirmation: Bool { candidate != nil || missingSince != nil }

    mutating func reset() { self = CaptionStability() }

    private static func normalized(_ text: String) -> String {
        text.lowercased().split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    private static func related(_ a: String, _ b: String) -> Bool {
        let x = normalized(a), y = normalized(b)
        guard !x.isEmpty, !y.isEmpty else { return false }
        return x.hasPrefix(y) || y.hasPrefix(x)
    }

    mutating func observe(_ raw: String, now: TimeInterval, retry: Bool = false) -> Decision {
        let text = raw.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        if text.isEmpty {
            candidate = nil
            candidateSince = nil
            guard !accepted.isEmpty else { missingSince = nil; return .hold }
            let since = missingSince ?? now
            missingSince = since
            guard now - since >= 0.8 else { return .hold }
            reset()
            return .clear
        }
        missingSince = nil
        if Self.normalized(text) == Self.normalized(accepted) {
            candidate = nil
            candidateSince = nil
            return retry ? .accept(accepted) : .hold
        }
        let complete = text.last.map { ".!?…\"'”’".contains($0) } ?? false
        let shrink = !accepted.isEmpty && Self.normalized(accepted).hasPrefix(Self.normalized(text))
        let growing = Self.related(accepted, text) || candidate.map { Self.related($0, text) } == true
        // Very short replies must not wait for a second read just because they
        // have no punctuation. Longer unfinished sentences and shrinkage do.
        let suspicious = shrink || (!complete && (growing || text.split(separator: " ").count > 3))
        if suspicious {
            if candidate != text {
                if candidateSince == nil || !(candidate.map { Self.related($0, text) } ?? false) {
                    candidateSince = now
                }
                candidate = text
                if now - (candidateSince ?? now) < 0.8 { return .hold }
            }
        }
        accepted = text
        candidate = nil
        candidateSince = nil
        return .accept(text)
    }
}

/// Independent empty-frame debounce for lists and long game text. Never
/// expire a readable document just because it remains on screen.
struct GameTextStability {
    private var missingSince: TimeInterval?
    var needsConfirmation: Bool { missingSince != nil }
    mutating func reset() { missingSince = nil }
    mutating func permits(_ items: [String], previous: [String], now: TimeInterval) -> Bool {
        if !items.isEmpty || previous.isEmpty {
            missingSince = nil
            return true
        }
        let since = missingSince ?? now
        missingSince = since
        guard now - since >= 0.8 else { return false }
        missingSince = nil
        return true
    }
}
