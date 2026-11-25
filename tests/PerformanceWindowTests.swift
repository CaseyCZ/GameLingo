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
        print("PerformanceWindow: bounded rolling samples, P95 and decoder frame metrics passed")
    }
}
