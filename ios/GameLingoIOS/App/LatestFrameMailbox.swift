import Foundation

/// Single-slot handoff from a decoder callback to a single async consumer.
/// The lock covers slot replacement and the idle-to-running transition.
/// Producers never enqueue an individual task for a frame while a drain owns
/// the slot. A session owns its mailboxes; close permanently rejects late frames.
final class LatestFrameMailbox<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var pending: Value?
    private var draining = false
    private var enabled: Bool
    private var closed = false

    init(enabled: Bool = true) {
        self.enabled = enabled
    }

    /// True only for the producer responsible for scheduling a new drain.
    /// An in-flight drain observes the newest replacement on its next take.
    func offer(_ value: Value) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard !closed, enabled else { return false }
        pending = value
        guard !draining else { return false }
        draining = true
        return true
    }

    /// Empty and idle are one atomic transition: a concurrent offer either
    /// replaces the pending value or becomes responsible for a fresh drain.
    func takeNext() -> Value? {
        lock.lock()
        defer { lock.unlock() }
        guard !closed, enabled, let value = pending else {
            pending = nil
            draining = false
            return nil
        }
        pending = nil
        return value
    }

    func setEnabled(_ value: Bool) {
        lock.lock()
        defer { lock.unlock() }
        guard !closed else { return }
        enabled = value
        if !value { pending = nil }
        // A drain that has already been scheduled remains responsible until
        // takeNext returns nil. Re-enabling must not start a second consumer.
    }

    func close() {
        lock.lock()
        defer { lock.unlock() }
        closed = true
        enabled = false
        pending = nil
    }
}
