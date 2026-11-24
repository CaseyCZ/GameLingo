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
