import Foundation

/// Main-actor-owned generation counters. Equality of settings alone cannot
/// distinguish an A → B → A change while a translation was in flight.
struct TranslationRevision {
    private(set) var value: UInt64 = 0
    mutating func advance() { value &+= 1 }
    func accepts(_ snapshot: UInt64) -> Bool { snapshot == value }
}
