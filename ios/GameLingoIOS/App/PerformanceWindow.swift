import Foundation

/// Bounded, local rolling statistics. No subtitle text or network identifiers.
struct PerformanceWindow {
    private var samples: [Double] = []
    private var cursor = 0
    private(set) var count = 0
    let capacity: Int

    init(capacity: Int = 128) { self.capacity = max(1, min(1024, capacity)) }

    mutating func record(milliseconds value: Double) {
        guard value.isFinite, value >= 0 else { return }
        count += 1
        if samples.count < capacity { samples.append(value) }
        else { samples[cursor] = value }
        cursor = (cursor + 1) % capacity
    }

    var summary: String {
        guard !samples.isEmpty else { return "–" }
        let sorted = samples.sorted()
        let rank = max(0, Int(ceil(Double(sorted.count) * 0.95)) - 1)
        let average = sorted.reduce(0, +) / Double(sorted.count)
        return "\(Int(average.rounded())) / \(Int(sorted[rank].rounded())) ms (n=\(count))"
    }
}

 
/// Decoder-delivered frames; excludes the later OCR throttle.
struct XboxDecodedVideoWindow {
    private var start: TimeInterval?
    private var last: TimeInterval?
    private var frames = 0
    private var rate: Double?
    private(set) var width = 0
    private(set) var height = 0
    mutating func record(width: Int, height: Int, now: TimeInterval) {
        guard width > 0, height > 0, now.isFinite else { return }
        self.width = width; self.height = height
        defer { last = now }
        guard let start, let last, now >= last, now - last <= 2 else {
            self.start = now; frames = 0; rate = nil; return
        }
        frames += 1
        let elapsed = now - start
        if elapsed >= 1 {
            rate = Double(frames) / elapsed
            self.start = now; frames = 0
        }
    }
    func fps(now: TimeInterval) -> Double? {
        guard let last, now >= last, now - last <= 2 else { return nil }
        return rate
    }
    func summary(now: TimeInterval) -> String {
        let size = width > 0 && height > 0 ? "\(width)×\(height)" : "–"
        let frameRate = fps(now: now).map { String(format: "%.1f", $0) } ?? "–"
        return "Xbox dekódovaný obraz: \(size) · \(frameRate) FPS"
    }
}


/// Numeric video statistics from native WebRTC. Never stores a raw report.
struct XboxNetworkWindow {
    struct Sample {
        let id: String
        let now: TimeInterval
        let bytes: Double?
        let received: Double?
        let lost: Double?
        let jitter: Double?
        let rtt: Double?
    }
    private var previous: Sample?
    private var kbps: Double?
    private var loss: Double?
    private var lostInWindow: Double?
    private func delta(_ new: Double?, _ old: Double?) -> Double? {
        guard let new, let old, new.isFinite, old.isFinite, new >= old else { return nil }
        return new - old
    }
    mutating func record(_ sample: Sample) {
        let old = previous
        previous = sample
        kbps = nil; loss = nil; lostInWindow = nil
        guard let old, old.id == sample.id, sample.now > old.now else { return }
        kbps = delta(sample.bytes, old.bytes).map { $0 * 8 / (sample.now - old.now) / 1000 }
        if let r = delta(sample.received, old.received), let l = delta(sample.lost, old.lost) {
            lostInWindow = l
            if r + l > 0 { loss = 100 * l / (r + l) }
        }
    }
    func summary(now: TimeInterval) -> String {
        guard let s = previous, now >= s.now, now - s.now <= 15 else { return "Xbox síť: nedostupná" }
        func fmt(_ value: Double?, _ unit: String) -> String {
            guard let value, value.isFinite, value >= 0 else { return "–" }
            return String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value) + " " + unit
        }
        return "Xbox síť (video): \(fmt(kbps,"kb/s")) · ztráta \(fmt(loss,"%")) (\(fmt(lostInWindow,"paketů"))) · jitter \(fmt(s.jitter.map { $0 * 1000 },"ms")) · RTT \(fmt(s.rtt.map { $0 * 1000 },"ms"))"
    }
}
