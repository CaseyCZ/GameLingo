import Foundation
import CoreVideo
import CoreGraphics

@main
struct OCRFrameFingerprintTests {
    static func check(_ condition: @autoclosure () -> Bool, _ label: String) {
        guard condition() else { fatalError(label) }
    }

    static func frame(_ format: OSType, brightness: UInt8,
                      changedX: Int? = nil, changedY: Int = 0) -> CVPixelBuffer {
        var result: CVPixelBuffer?
        let status = CVPixelBufferCreate(kCFAllocatorDefault, 64, 64, format,
                                         nil, &result)
        check(status == kCVReturnSuccess, "create synthetic frame")
        let buffer = result!
        let planar = CVPixelBufferIsPlanar(buffer)
        check(CVPixelBufferLockBaseAddress(buffer, []) == kCVReturnSuccess, "lock frame")
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        let base = (planar ? CVPixelBufferGetBaseAddressOfPlane(buffer, 0) :
                    CVPixelBufferGetBaseAddress(buffer))!.assumingMemoryBound(to: UInt8.self)
        let stride = planar ? CVPixelBufferGetBytesPerRowOfPlane(buffer, 0) : CVPixelBufferGetBytesPerRow(buffer)
        for y in 0..<64 {
            for x in 0..<64 {
                let v: UInt8 = (x == changedX && y == changedY) ? 220 : brightness
                if planar {
                    base[y * stride + x] = v
                } else {
                    let p = base + y * stride + x * 4
                    p[0] = v; p[1] = v; p[2] = v; p[3] = 255
                }
            }
        }
        return buffer
    }

    static func main() {
        let roi = CGRect(x: 0, y: 0, width: 1, height: 0.5)
        for format in [kCVPixelFormatType_32BGRA,
                       kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
                       kCVPixelFormatType_420YpCbCr8BiPlanarFullRange] {
            func fingerprint(_ level: UInt8, _ x: Int? = nil, _ y: Int = 0) -> OCRFrameFingerprint {
                OCRFrameFingerprint.read(frame(format, brightness: level, changedX: x, changedY: y),
                                         visionROI: roi)!
            }
            let original = fingerprint(80)
            check(original.approximatelyMatches(fingerprint(81)), "small noise")
            check(!original.approximatelyMatches(fingerprint(80, 10, 48)), "local text change")
            check(original.approximatelyMatches(fingerprint(80, 10, 10)), "outside bottom ROI")
            var dialogue = OCRFrameGate(maximumSkip: 1.5)
            let options = OCRFrameGate(maximumSkip: 2)
            check(dialogue.shouldRead(original, now: 10), "initial OCR")
            // No successful OCR yet: an error must never poison the gate.
            check(dialogue.shouldRead(original, now: 10.2), "retry unsuccessful OCR")
            dialogue.recordSuccessfulRead(original, now: 10)
            check(!dialogue.shouldRead(fingerprint(81), now: 10.4), "skip unchanged ROI")
            check(dialogue.shouldRead(fingerprint(83), now: 10.6), "cumulative change vs last OCR")
            check(dialogue.shouldRead(original, now: 11.5), "periodic forced OCR")
            check(dialogue.shouldRead(nil, now: 10.5), "unsupported input fail open")
            check(dialogue.shouldRead(original, now: 9), "clock reversal")
            check(options.shouldRead(original, now: 10.4), "independent OCR lane")
            let otherROI = OCRFrameFingerprint.read(frame(format, brightness: 80),
                visionROI: CGRect(x: 0, y: 0.5, width: 1, height: 0.5))!
            check(dialogue.shouldRead(otherROI, now: 10.5), "ROI change")
            dialogue.reset()
            check(dialogue.shouldRead(original, now: 10.5), "new session/settings")
        }
        print("OCRFrameFingerprint: all synthetic image and gate checks passed")
    }
}
