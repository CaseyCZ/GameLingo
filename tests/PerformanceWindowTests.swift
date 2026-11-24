import Foundation

@main
struct PerformanceWindowTests {
    static func main() {
        var window = PerformanceWindow(capacity: 3)
        precondition(window.summary == "–")
        window.record(milliseconds: -1)
        window.record(milliseconds: .nan)
        precondition(window.count == 0)
        for n in [10.0, 20, 30, 40] { window.record(milliseconds: n) }
        precondition(window.count == 4)
        precondition(window.summary == "30 / 40 ms (n=4)")
        for _ in 0..<1000 { window.record(milliseconds: 7) }
        precondition(window.summary == "7 / 7 ms (n=1004)")
        print("PerformanceWindow: bounded rolling samples and P95 passed")
    }
}
