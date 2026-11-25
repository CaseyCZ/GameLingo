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
        var video = XboxDecodedVideoWindow()
        precondition(video.fps(now: 0) == nil)
        for n in 0...30 { video.record(width: 1280, height: 720, now: Double(n) / 30) }
        precondition(video.fps(now: 1) == 30)
        precondition(video.width == 1280 && video.height == 720)
        precondition(video.fps(now: 3.1) == nil)
        var network = XboxNetworkWindow()
        network.record(.init(id: "a", now: 0, bytes: 1000, received: 100, lost: 10, jitter: 0.003, rtt: 0.04))
        precondition(!network.summary(now: 0).contains("kb/s"))
        network.record(.init(id: "a", now: 1, bytes: 2000, received: 190, lost: 20, jitter: 0.003, rtt: 0.04))
        let summary = network.summary(now: 1)
        precondition(summary.contains("8.0 kb/s") && summary.contains("10.0 %"))
        precondition(summary.contains("3.0 ms") && summary.contains("40.0 ms"))
        network.record(.init(id: "b", now: 2, bytes: 500, received: 10, lost: 0, jitter: nil, rtt: nil))
        precondition(!network.summary(now: 2).contains("kb/s"))
        precondition(network.summary(now: 18).contains("nedostupná"))
        print("PerformanceWindow: phase, video and network metrics passed")
    }
}
