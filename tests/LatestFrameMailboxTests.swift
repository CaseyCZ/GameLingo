import Foundation

@main
struct LatestFrameMailboxTests {
    static func check(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else { fatalError(label) }
    }

    final class Frame {}

    static func main() {
        let mailbox = LatestFrameMailbox<Int>()
        check(mailbox.offer(1), "first producer schedules")
        for value in 2...10_000 {
            check(!mailbox.offer(value), "no per-frame task while busy")
        }
        check(mailbox.takeNext() == 10_000, "only latest frame survives")
        check(!mailbox.offer(10_001), "active reader keeps responsibility")
        check(mailbox.takeNext() == 10_001, "consume latest after work")
        check(mailbox.takeNext() == nil, "empty drain releases responsibility")
        check(mailbox.offer(10_002), "next producer schedules")
        mailbox.close()
        check(mailbox.takeNext() == nil, "stop drops pending frame")
        check(!mailbox.offer(10_003), "old session cannot restart")
        mailbox.setEnabled(true)
        check(!mailbox.offer(10_004), "close is permanent")

        let choices = LatestFrameMailbox<Int>(enabled: false)
        check(!choices.offer(1), "disabled choices do not schedule")
        choices.setEnabled(true)
        check(choices.offer(2), "enabled choices schedule independently")
        choices.setEnabled(false)
        choices.setEnabled(true)
        check(!choices.offer(3), "no duplicate drain on rapid toggle")
        check(choices.takeNext() == 3, "newest frame after toggle")
        check(choices.takeNext() == nil, "choice drain idle")
        check(choices.offer(4), "can schedule again")

        let retained = LatestFrameMailbox<Frame>()
        var first: Frame? = Frame()
        weak var old = first
        check(retained.offer(first!), "retain pending frame")
        first = nil
        check(old != nil, "pending frame alive")
        check(!retained.offer(Frame()), "replace frame")
        check(old == nil, "replaced frame released")

        // Concurrent producers share one drain scheduling decision.
        let concurrent = LatestFrameMailbox<Int>()
        let scheduling = LatestFrameMailbox<Int>()
        DispatchQueue.concurrentPerform(iterations: 2048) { index in
            if concurrent.offer(index) {
                check(scheduling.offer(index), "exactly one concurrent producer schedules")
            }
        }
        check(scheduling.takeNext() != nil, "one scheduling event")
        check(concurrent.takeNext() != nil, "one latest pending value")
        check(concurrent.takeNext() == nil, "no hidden backlog")

        print("LatestFrameMailbox: all bounded-slot, lifecycle and concurrency checks passed")
    }
}
