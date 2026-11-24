import Foundation

/// Monotonic, per-provider breaker. Tokens from an older generation cannot
/// close a circuit opened by newer completions.
struct TranslationCircuitBreaker {
    struct Ticket {
        fileprivate let generation: UInt64
        fileprivate let probe: Bool
    }
    private var generation: UInt64 = 0
    private var failures = 0
    private var openUntil: TimeInterval?
    private var probing = false

    mutating func acquire(now: TimeInterval) -> Ticket? {
        if let until = openUntil {
            guard now >= until, !probing else { return nil }
            probing = true
            return Ticket(generation: generation, probe: true)
        }
        return Ticket(generation: generation, probe: false)
    }

    /// nil is cancellation/local invalid input, not a provider error.
    mutating func complete(_ ticket: Ticket, success: Bool?, now: TimeInterval) {
        guard ticket.generation == generation else { return }
        if ticket.probe {
            guard probing else { return }
            probing = false
            generation &+= 1
            if success == true {
                failures = 0
                openUntil = nil
            } else {
                openUntil = now + 30
            }
            return
        }
        guard openUntil == nil, let success else { return }
        if success { failures = 0; return }
        failures += 1
        if failures >= 2 {
            generation &+= 1
            openUntil = now + 30
        }
    }
}
