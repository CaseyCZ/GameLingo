import Foundation
import CoreVideo
import CoreGraphics

/// Compact, conservative image signature. The sampling geometry belongs to the
/// selected ROI, not the whole frame. No video copies or image allocations.
struct OCRFrameFingerprint {
    private let width: Int
    private let height: Int
    private let format: OSType
    private let roi: CGRect
    private let samples: [UInt8]

    static func read(_ buffer: CVPixelBuffer, visionROI: CGRect) -> OCRFrameFingerprint? {
        guard visionROI.minX.isFinite, visionROI.minY.isFinite,
              visionROI.width.isFinite, visionROI.height.isFinite else { return nil }
        let roi = visionROI.intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        guard !roi.isNull, roi.width > 0, roi.height > 0 else { return nil }
        let format = CVPixelBufferGetPixelFormatType(buffer)
        let luma = format == kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange ||
            format == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
        let bgra = format == kCVPixelFormatType_32BGRA
        // Unknown layouts still get full Vision OCR.
        guard luma || bgra else { return nil }
        guard CVPixelBufferLockBaseAddress(buffer, .readOnly) == kCVReturnSuccess else { return nil }
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }

        let w = CVPixelBufferGetWidth(buffer)
        let h = CVPixelBufferGetHeight(buffer)
        guard w > 0, h > 0 else { return nil }
        if luma {
            guard CVPixelBufferGetPlaneCount(buffer) >= 1,
                  CVPixelBufferGetWidthOfPlane(buffer, 0) == w,
                  CVPixelBufferGetHeightOfPlane(buffer, 0) == h else { return nil }
        }
        let address = luma ? CVPixelBufferGetBaseAddressOfPlane(buffer, 0) :
            CVPixelBufferGetBaseAddress(buffer)
        let stride = luma ? CVPixelBufferGetBytesPerRowOfPlane(buffer, 0) :
            CVPixelBufferGetBytesPerRow(buffer)
        let bytesPerPixel = luma ? 1 : 4
        guard let address, stride >= w * bytesPerPixel else { return nil }
        let data = address.assumingMemoryBound(to: UInt8.self)
        // Vision is bottom-left, decoder rows are top-left.
        let left = max(0, min(w - 1, Int(floor(roi.minX * CGFloat(w)))))
        let right = max(left + 1, min(w, Int(ceil(roi.maxX * CGFloat(w)))))
        let top = max(0, min(h - 1, Int(floor((1 - roi.maxY) * CGFloat(h)))))
        let bottom = max(top + 1, min(h, Int(ceil((1 - roi.minY) * CGFloat(h)))))
        // Sample up to 192 × 64 points, preserving spatial detail rather than
        // averaging an entire caption into one brightness number.
        let columns = min(192, right - left)
        let rows = min(64, bottom - top)
        var values = [UInt8]()
        values.reserveCapacity(columns * rows)
        for row in 0..<rows {
            let y = top + (2 * row + 1) * (bottom - top) / (2 * rows)
            for col in 0..<columns {
                let x = left + (2 * col + 1) * (right - left) / (2 * columns)
                let p = data + y * stride + x * bytesPerPixel
                if luma {
                    values.append(p[0])
                } else {
                    // Integer approximation to luminance. Ignore alpha.
                    values.append(UInt8((29 * Int(p[0]) + 150 * Int(p[1]) + 77 * Int(p[2])) >> 8))
                }
            }
        }
        return OCRFrameFingerprint(width: w, height: h, format: format,
                                   roi: roi, samples: values)
    }

    func approximatelyMatches(_ other: OCRFrameFingerprint) -> Bool {
        guard width == other.width, height == other.height, format == other.format,
              roi == other.roi, samples.count == other.samples.count,
              !samples.isEmpty else { return false }
        var sum = 0
        var changed = 0
        for i in samples.indices {
            let difference = abs(Int(samples[i]) - Int(other.samples[i]))
            // A single pronounced local change is enough to force a read.
            if difference > 24 { return false }
            if difference > 8 { changed += 1 }
            sum += difference
        }
        return sum <= samples.count * 2 && changed <= max(1, samples.count / 500)
    }
}

/// Compare against the last successfully read frame, never the previous
/// skipped frame (otherwise slow cumulative changes can go unnoticed).
struct OCRFrameGate {
    private var lastRead: OCRFrameFingerprint?
    private var readAt: TimeInterval?
    let maximumSkip: TimeInterval

    init(maximumSkip: TimeInterval) {
        self.maximumSkip = maximumSkip
    }

    mutating func reset() {
        lastRead = nil
        readAt = nil
    }

    func shouldRead(_ fingerprint: OCRFrameFingerprint?, now: TimeInterval) -> Bool {
        guard let fingerprint, let lastRead, let readAt,
              now >= readAt, now - readAt < maximumSkip else { return true }
        return !fingerprint.approximatelyMatches(lastRead)
    }

    mutating func recordSuccessfulRead(_ fingerprint: OCRFrameFingerprint?, now: TimeInterval) {
        lastRead = fingerprint
        readAt = fingerprint == nil ? nil : now
    }
}
