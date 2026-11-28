import SwiftUI
import AVFoundation
import Vision
import NaturalLanguage
import CoreImage
import UIKit

// A bounded, evidence-based approximation for smooth backgrounds.
// Busy artwork is not treated as a recoverable gradient.
private struct GLCameraSurface: Sendable {
    let start: UInt32
    let end: UInt32
    let horizontal: Bool
}

// Four corners are ordered clockwise from the upper left in image coordinates.
// The renderer maps a flat text rectangle back to the observed quadrilateral.
private struct GLCameraQuad: Sendable {
    let points: [CGPoint]

    func projection(size: CGSize, rect: CGRect) -> ProjectionTransform? {
        guard points.count == 4, size.width > 0, size.height > 0,
              rect.width > 0, rect.height > 0 else { return nil }
        let p = points.map { CGPoint(x: ($0.x - rect.minX) / rect.width * size.width,
                                     y: ($0.y - rect.minY) / rect.height * size.height) }
        guard p.allSatisfy({ $0.x.isFinite && $0.y.isFinite }) else { return nil }
        for i in 0..<4 {
            let a = p[i], b = p[(i + 1) % 4], c = p[(i + 2) % 4]
            let cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            guard cross > 0.001 else { return nil }
        }
        let dx1 = p[1].x - p[2].x, dx2 = p[3].x - p[2].x
        let dy1 = p[1].y - p[2].y, dy2 = p[3].y - p[2].y
        let dx3 = p[0].x - p[1].x + p[2].x - p[3].x
        let dy3 = p[0].y - p[1].y + p[2].y - p[3].y
        let det = dx1 * dy2 - dx2 * dy1
        guard abs(det) > 0.001 else { return nil }
        let g = (dx3 * dy2 - dx2 * dy3) / det
        let h = (dx1 * dy3 - dx3 * dy1) / det
        guard [1 + g, 1 + h, 1 + g + h].allSatisfy({ $0 > 0.2 && $0 < 5 }) else { return nil }
        var t = CATransform3DIdentity
        t.m11 = (p[1].x - p[0].x + g * p[1].x) / size.width
        t.m12 = (p[1].y - p[0].y + g * p[1].y) / size.width
        t.m14 = g / size.width
        t.m21 = (p[3].x - p[0].x + h * p[3].x) / size.height
        t.m22 = (p[3].y - p[0].y + h * p[3].y) / size.height
        t.m24 = h / size.height
        t.m41 = p[0].x; t.m42 = p[0].y
        return ProjectionTransform(t)
    }
}

private struct GLCameraTexture: Sendable {
    let width: Int
    let height: Int
    let bytes: [UInt8]

    func image() -> CGImage? {
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                       bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
    }
}

private struct GLCameraBlock: Sendable {
    let text: String
    let rect: CGRect // upright image coordinates, origin at top left
    var angle: Double = 0
    var background: UInt32 = 0
    var ink: UInt32? = nil
    var lineCount: Int = 1
    var surface: GLCameraSurface? = nil
    var quad: GLCameraQuad? = nil
    var weight: Int = 0
    var texture: GLCameraTexture? = nil
}

private struct GLCameraOverlay: Identifiable {
    let id: Int
    let rect: CGRect
    let text: String
    let angle: Double
    let background: UInt32
    let ink: UInt32?
    let lineCount: Int
    let surface: GLCameraSurface?
    let quad: GLCameraQuad?
    let weight: Int
    let texture: GLCameraTexture?
}

// Share the camera's bounded palette estimator without exposing its texture renderer.
func glGamePalettes(_ image: CGImage, rects: [CGRect]) -> [(UInt32?, UInt32?)] {
    guard let palette = GLCameraPalette(image) else { return rects.map { _ in (nil, nil) } }
    return rects.map { rect in
        let background = palette.background(for: rect)
        return (background, palette.ink(for: rect, background: background))
    }
}

// Sample the background near the text box edges, not the glyph centers.
// A small RGB image bounds the per-frame sampling cost.
private struct GLCameraPalette {
    let pixels: [UInt8]
    static let size = 256

    init?(_ image: CGImage) {
        let n = Self.size
        var data = [UInt8](repeating: 0, count: n * n * 4)
        let ok = data.withUnsafeMutableBytes { bytes -> Bool in
            guard let ctx = CGContext(data: bytes.baseAddress, width: n, height: n,
                                      bitsPerComponent: 8, bytesPerRow: n * 4,
                                      space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue) else { return false }
            ctx.interpolationQuality = .low
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: CGFloat(n), height: CGFloat(n)))
            return true
        }
        guard ok else { return nil }
        pixels = data
    }

    func background(for rect: CGRect) -> UInt32 {
        let n = Self.size
        var bins: [UInt32: (count: Int, red: Int, green: Int, blue: Int)] = [:]
        for step in 0..<12 {
            let t = (CGFloat(step) + 0.5) / 12
            let x = rect.minX + rect.width * t, y = rect.minY + rect.height * t
            let dx = min(rect.width * 0.08, 0.008), dy = min(rect.height * 0.12, 0.008)
            for point in [CGPoint(x: x, y: rect.minY + dy), CGPoint(x: x, y: rect.maxY - dy),
                          CGPoint(x: rect.minX + dx, y: y), CGPoint(x: rect.maxX - dx, y: y)] {
                let px = min(n - 1, max(0, Int(point.x * CGFloat(n))))
                let py = min(n - 1, max(0, Int(point.y * CGFloat(n))))
                let i = (py * n + px) * 4
                let r = Int(pixels[i]), g = Int(pixels[i+1]), b = Int(pixels[i+2])
                let key = UInt32((r / 32) * 64 + (g / 32) * 8 + b / 32)
                let old = bins[key] ?? (count: 0, red: 0, green: 0, blue: 0)
                bins[key] = (old.count + 1, old.red + r, old.green + g, old.blue + b)
            }
        }
        guard let best = bins.max(by: { $0.value.count < $1.value.count })?.value else { return 0 }
        return UInt32((best.red / best.count) * 65536 + (best.green / best.count) * 256 + best.blue / best.count)
    }

    // Reconstruct a smooth ramp from two coherent opposing edges, checked
    // against the perpendicular edges. Never sample outside the owned box.
    func reconstructedSurface(for rect: CGRect, background: UInt32, ink: UInt32?) -> GLCameraSurface? {
        guard rect.width > 0.015, rect.height > 0.015,
              rect.minX >= 0, rect.minY >= 0, rect.maxX <= 1, rect.maxY <= 1 else { return nil }
        func sample(_ x: CGFloat, _ y: CGFloat) -> UInt32 {
            let n = Self.size
            let px = min(n - 1, max(0, Int(x * CGFloat(n))))
            let py = min(n - 1, max(0, Int(y * CGFloat(n))))
            let i = (py * n + px) * 4
            return UInt32(pixels[i]) * 65536 + UInt32(pixels[i+1]) * 256 + UInt32(pixels[i+2])
        }
        func distance(_ a: UInt32, _ b: UInt32) -> Int {
            Self.channelDistance(a, b)
        }
        func median(_ values: [UInt32]) -> UInt32 {
            var result: UInt32 = 0
            for shift in [16, 8, 0] {
                let channel: [UInt32] = values.map { ($0 >> shift) & 255 }.sorted()
                let mid = channel[channel.count / 2]
                result |= mid << shift
            }
            return result
        }
        func blend(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
            var result: UInt32 = 0
            for shift in [16, 8, 0] {
                let x = Double((a >> shift) & 255)
                let y = Double((b >> shift) & 255)
                let mixed = x + (y - x) * t
                let channel = UInt32(mixed.rounded())
                result |= channel << shift
            }
            return result
        }
        let ts = (0..<12).map { (CGFloat($0) + 0.5) / 12 }
        let left = ts.map { sample(rect.minX + rect.width * 0.04, rect.minY + rect.height * $0) }
        let right = ts.map { sample(rect.maxX - rect.width * 0.04, rect.minY + rect.height * $0) }
        let top = ts.map { sample(rect.minX + rect.width * $0, rect.minY + rect.height * 0.08) }
        let bottom = ts.map { sample(rect.minX + rect.width * $0, rect.maxY - rect.height * 0.08) }
        let foreground = ink ?? (Self.luminance(background) > 0.179 ? UInt32(0) : UInt32(0xffffff))
        func contrast(_ color: UInt32) -> Double {
            let a = Self.luminance(color), b = Self.luminance(foreground)
            return (max(a, b) + 0.05) / (min(a, b) + 0.05)
        }
        func candidate(_ first: [UInt32], _ last: [UInt32], _ crossA: [UInt32], _ crossB: [UInt32], horizontal: Bool) -> GLCameraSurface? {
            let a = median(first), b = median(last), variation = distance(a, b)
            guard (12...100).contains(variation),
                  first.filter({ distance($0, a) <= 22 }).count >= 9,
                  last.filter({ distance($0, b) <= 22 }).count >= 9,
                  contrast(a) >= 4.5, contrast(b) >= 4.5 else { return nil }
            let agreement = ts.indices.filter { i in
                let expected = blend(a, b, Double(ts[i]))
                return distance(crossA[i], expected) <= 35 && distance(crossB[i], expected) <= 35
            }.count
            guard agreement >= 8 else { return nil }
            return GLCameraSurface(start: a, end: b, horizontal: horizontal)
        }
        return candidate(top, bottom, left, right, horizontal: false) ??
               candidate(left, right, top, bottom, horizontal: true)
    }

    // Estimate stroke thickness and remove only pixels close to a reliable ink
    // color. Keep the other sampled texture pixels intact. Reject dense masks,
    // weak evidence and holes without local donors; use the existing fill then.
    func textureAndWeight(for rect: CGRect, ink: UInt32?, lineCount: Int,
                          restore: Bool) -> (GLCameraTexture?, Int) {
        let n = Self.size
        guard let ink, rect.minX >= 0, rect.minY >= 0, rect.maxX <= 1, rect.maxY <= 1 else { return (nil, 0) }
        let x0 = Int(rect.minX * CGFloat(n)), y0 = Int(rect.minY * CGFloat(n))
        let w = min(96, Int(rect.width * CGFloat(n))), h = min(48, Int(rect.height * CGFloat(n)))
        guard w >= 24, h >= 10, h / max(1, lineCount) >= 8 else { return (nil, 0) }
        var source = [UInt32](repeating: 0, count: w * h)
        func distance(_ a: UInt32, _ b: UInt32) -> Int {
            Self.channelDistance(a, b)
        }
        for y in 0..<h { for x in 0..<w {
            let px = min(n - 1, x0 + Int((CGFloat(x) + 0.5) * rect.width * CGFloat(n) / CGFloat(w)))
            let py = min(n - 1, y0 + Int((CGFloat(y) + 0.5) * rect.height * CGFloat(n) / CGFloat(h)))
            let i = (py * n + px) * 4
            source[y*w+x] = UInt32(pixels[i]) * 65536 + UInt32(pixels[i+1]) * 256 + UInt32(pixels[i+2])
        } }
        let inkMask = source.map { distance($0, ink) <= 38 }
        let count = inkMask.filter { $0 }.count
        guard count >= max(8, w*h/25), count <= w*h*45/100 else { return (nil, 0) }
        var thickness: [Int] = []
        for y in 1..<(h-1) { for x in 1..<(w-1) where inkMask[y*w+x] {
            var l = x, r = x, t = y, b = y
            while l > 0 && inkMask[y*w+l-1] { l -= 1 }
            while r+1 < w && inkMask[y*w+r+1] { r += 1 }
            while t > 0 && inkMask[(t-1)*w+x] { t -= 1 }
            while b+1 < h && inkMask[(b+1)*w+x] { b += 1 }
            thickness.append(min(r-l+1, b-t+1))
        } }
        thickness.sort()
        let ratio = Double(thickness.isEmpty ? 1 : thickness[thickness.count/2]) / Double(h) * Double(max(1,lineCount))
        let weight = ratio >= 0.22 ? 2 : (ratio >= 0.14 ? 1 : 0)
        guard restore else { return (nil, weight) }
        var mask = inkMask
        for y in 0..<h { for x in 0..<w where inkMask[y*w+x] {
            for dy in -1...1 { for dx in -1...1 {
                let xx=x+dx, yy=y+dy
                if xx>=0 && xx<w && yy>=0 && yy<h { mask[yy*w+xx]=true }
            } }
        } }
        guard mask.filter({$0}).count <= w*h*65/100 else { return (nil,weight) }
        // Only use texture restoration if donors actually vary.
        let donors = source.indices.filter { !mask[$0] }.map { source[$0] }
        guard let first=donors.first, donors.filter({distance($0,first)>24}).count >= donors.count/5 else { return (nil,weight) }
        let inkLight = Self.luminance(ink)
        guard donors.allSatisfy({ color in
            let light = Self.luminance(color)
            return (max(light,inkLight)+0.05)/(min(light,inkLight)+0.05) >= 4.5
        }) else { return (nil,weight) }
        var output=source
        for y in 0..<h { for x in 0..<w where mask[y*w+x] {
            var found: UInt32?
            for radius in 1...8 {
                let candidates = [(x,y-radius),(x,y+radius),(x-radius,y),(x+radius,y)]
                for (xx,yy) in candidates where xx>=0 && xx<w && yy>=0 && yy<h && !mask[yy*w+xx] {
                    found=source[yy*w+xx]; break
                }
                if found != nil { break }
            }
            guard let found else { return (nil,weight) }
            output[y*w+x]=found
        } }
        var bytes=[UInt8](); bytes.reserveCapacity(w*h*4)
        for rgb in output {
            bytes.append(UInt8((rgb>>16)&255)); bytes.append(UInt8((rgb>>8)&255))
            bytes.append(UInt8(rgb&255)); bytes.append(255)
        }
        return (GLCameraTexture(width:w,height:h,bytes:bytes),weight)
    }

    static func channelDistance(_ a: UInt32, _ b: UInt32) -> Int {
        let redA = Int((a >> 16) & 255)
        let redB = Int((b >> 16) & 255)
        let greenA = Int((a >> 8) & 255)
        let greenB = Int((b >> 8) & 255)
        let blueA = Int(a & 255)
        let blueB = Int(b & 255)
        let red = abs(redA - redB)
        let green = abs(greenA - greenB)
        let blue = abs(blueA - blueB)
        return max(red, max(green, blue))
    }

    static func similar(_ a: UInt32, _ b: UInt32) -> Bool {
        channelDistance(a, b) <= 40
    }

    static func compatibleInk(_ first: UInt32?, _ second: UInt32?) -> Bool {
        guard let first, let second else { return true }
        return similar(first, second)
    }

    static func color(_ rgb: UInt32) -> Color {
        Color(red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255,
              blue: Double(rgb & 255) / 255)
    }

    static func luminance(_ rgb: UInt32) -> Double {
        func linear(_ v: UInt32) -> Double {
            let x = Double(v) / 255
            return x <= 0.04045 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear((rgb >> 16) & 255) + 0.7152 * linear((rgb >> 8) & 255) + 0.0722 * linear(rgb & 255)
    }

    static func foreground(_ rgb: UInt32) -> Color {
        luminance(rgb) > 0.179 ? .black : .white
    }

    // Sample repeated high-contrast ink colors inside the text rectangle.
    // Reject ambiguous or low-contrast estimates instead of copying noise.
    func ink(for rect: CGRect, background: UInt32) -> UInt32? {
        let n = Self.size
        let bg = Self.luminance(background)
        var bins: [UInt32: (count: Int, r: Int, g: Int, b: Int)] = [:]
        for row in 0..<8 {
            for col in 0..<24 {
                let x = rect.minX + rect.width * (CGFloat(col) + 0.5) / 24
                let y = rect.minY + rect.height * (CGFloat(row) + 0.5) / 8
                let px = min(n - 1, max(0, Int(x * CGFloat(n))))
                let py = min(n - 1, max(0, Int(y * CGFloat(n))))
                let i = (py * n + px) * 4
                let r = Int(pixels[i]), g = Int(pixels[i+1]), b = Int(pixels[i+2])
                let rgb = UInt32(r * 65536 + g * 256 + b)
                let light = Self.luminance(rgb)
                guard (max(light, bg) + 0.05) / (min(light, bg) + 0.05) >= 4.5 else { continue }
                let key = UInt32((r / 32) * 64 + (g / 32) * 8 + b / 32)
                let old = bins[key] ?? (count: 0, r: 0, g: 0, b: 0)
                bins[key] = (old.count + 1, old.r + r, old.g + g, old.b + b)
            }
        }
        guard let best = bins.max(by: { $0.value.count < $1.value.count })?.value,
              best.count >= 8 else { return nil }
        let rgb = UInt32((best.r / best.count) * 65536 + (best.g / best.count) * 256 + best.b / best.count)
        let light = Self.luminance(rgb)
        return (max(light, bg) + 0.05) / (min(light, bg) + 0.05) >= 4.5 ? rgb : nil
    }
}

/// Camera-only source, with a single capture queue and no audio or recording.
private final class GLCameraCapture: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, @unchecked Sendable {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "cz.caseycz.gamelingo.camera.session")
    private let analysisQueue = DispatchQueue(label: "cz.caseycz.gamelingo.camera.ocr", qos: .userInitiated)
    private let context = CIContext()
    private var device: AVCaptureDevice?
    private var running = false
    private var lastRead: TimeInterval = 0
    private var analysisCost: TimeInterval = 0
    private var stableFrames = 0
    private var previousOCR = ""
    private let textRequest = VNRecognizeTextRequest()
    private var language = "auto"
    private var region = CGRect(x: 0, y: 0, width: 1, height: 1)
    private var revision = 0
    private let frame: @Sendable (UIImage, [GLCameraBlock], Int) -> Void
    private let failure: @Sendable () -> Void
    private let ready: @Sendable (AVCaptureSession) -> Void

    init(frame: @escaping @Sendable (UIImage, [GLCameraBlock], Int) -> Void,
         ready: @escaping @Sendable (AVCaptureSession) -> Void, failure: @escaping @Sendable () -> Void) {
        self.frame = frame
        self.failure = failure
        self.ready = ready
    }

    func configure(language: String, region: CGRect, revision: Int) {
        analysisQueue.async {
            self.language = language
            self.region = region
            self.revision = revision
            self.stableFrames = 0; self.previousOCR = ""
        }
    }

    func start() {
        queue.async {
            guard !self.session.isRunning else { return }
            if self.device == nil {
                guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                      let input = try? AVCaptureDeviceInput(device: device) else { self.failure(); return }
                let output = AVCaptureVideoDataOutput()
                output.alwaysDiscardsLateVideoFrames = true
                output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                self.session.beginConfiguration()
                guard self.session.canAddInput(input), self.session.canAddOutput(output) else {
                    self.session.commitConfiguration(); self.failure(); return
                }
                if self.session.canSetSessionPreset(.hd1280x720) { self.session.sessionPreset = .hd1280x720 }
                self.session.addInput(input)
                self.session.addOutput(output)
                output.setSampleBufferDelegate(self, queue: self.analysisQueue)
                if let connection = output.connection(with: .video), connection.isVideoOrientationSupported {
                    connection.videoOrientation = .portrait
                }
                self.session.commitConfiguration()
                self.device = device
            }
            self.analysisQueue.async { self.running = true; self.lastRead = 0 }
            self.session.startRunning()
            if self.session.isRunning {
                self.configureAutomaticFocus()
                self.applyZoom()
                self.ready(self.session)
            }
            else { self.analysisQueue.async { self.running = false }; self.failure() }
        }
    }

    // Called only on the session queue, after the active camera format is set.
    private func configureAutomaticFocus() {
        guard let device = self.device else { return }
        do {
            try device.lockForConfiguration()
            defer { device.unlockForConfiguration() }
            if device.isAutoFocusRangeRestrictionSupported {
                device.autoFocusRangeRestriction = .none
            }
            if device.isFocusPointOfInterestSupported {
                device.focusPointOfInterest = CGPoint(x: 0.5, y: 0.5)
            }
            if device.isFocusModeSupported(.continuousAutoFocus) {
                device.focusMode = .continuousAutoFocus
            } else if device.isFocusModeSupported(.autoFocus) {
                device.focusMode = .autoFocus
            }
            if device.isExposureModeSupported(.continuousAutoExposure) {
                device.exposureMode = .continuousAutoExposure
            }
        } catch { /* Fixed-focus devices or unavailable configuration retain camera defaults. */ }
    }

    func stop() {
        queue.async {
            self.analysisQueue.async { self.running = false }
            if let device = self.device, device.hasTorch, (try? device.lockForConfiguration()) != nil {
                if device.isTorchModeSupported(.off) { device.torchMode = .off }
                device.unlockForConfiguration()
            }
            if self.session.isRunning { self.session.stopRunning() }
        }
    }

    // Session-queue state, retained across pause/resume and initial configuration.
    private var requestedZoom: CGFloat = 1
    private func applyZoom() {
        guard let device = self.device, (try? device.lockForConfiguration()) != nil else { return }
        defer { device.unlockForConfiguration() }
        device.videoZoomFactor = min(max(1, requestedZoom), min(4, device.activeFormat.videoMaxZoomFactor))
    }

    func zoom(_ value: CGFloat) {
        queue.async {
            self.requestedZoom = value.isFinite ? value : 1
            self.applyZoom()
        }
    }

    func torch(_ enabled: Bool, completion: @escaping @Sendable (Bool, Bool) -> Void) {
        queue.async {
            guard self.session.isRunning, let device = self.device, device.hasTorch else {
                completion(false, false); return
            }
            do {
                try device.lockForConfiguration()
                defer { device.unlockForConfiguration() }
                if enabled {
                    guard device.isTorchAvailable, device.isTorchModeSupported(.on) else {
                        completion(device.torchMode == .on, false); return
                    }
                    try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
                } else {
                    guard device.isTorchModeSupported(.off) else {
                        completion(device.torchMode == .on, false); return
                    }
                    device.torchMode = .off
                }
                let on = device.torchMode == .on
                completion(on, on == enabled)
            } catch {
                completion(device.torchMode == .on, false)
            }
        }
    }

    // Only split columns when a clear vertical gutter spans all text.
    private static func columnOrder(_ rows: [VNRecognizedTextObservation]) -> [VNRecognizedTextObservation] {
        guard rows.count >= 4 else { return rows }
        let sorted = rows.sorted { $0.boundingBox.minX < $1.boundingBox.minX }
        var edge = sorted[0].boundingBox.maxX
        var split = 0
        var largestGap: CGFloat = 0.045
        for index in 1..<sorted.count {
            let gap = sorted[index].boundingBox.minX - edge
            if index >= 2, sorted.count - index >= 2, gap > largestGap {
                largestGap = gap; split = index
            }
            edge = max(edge, sorted[index].boundingBox.maxX)
        }
        guard split > 0 else { return rows }
        let boundary = sorted[split].boundingBox.minX
        return columnOrder(rows.filter { $0.boundingBox.minX < boundary }) +
               columnOrder(rows.filter { $0.boundingBox.minX >= boundary })
    }

    // One optional retry on a weak skewed line. OCR runs on the rectified crop,
    // but the original observation remains the sole source of overlay geometry.
    private func rectifiedText(_ observation: VNRecognizedTextObservation,
                               original: VNRecognizedText, image: CIImage,
                               request: VNRecognizeTextRequest, attempted: inout Bool) -> VNRecognizedText? {
        guard original.confidence < 0.75 else { return nil }
        let points = [observation.topLeft, observation.topRight, observation.bottomRight, observation.bottomLeft]
        guard points.allSatisfy({ $0.x >= 0 && $0.x <= 1 && $0.y >= 0 && $0.y <= 1 }) else { return nil }
        let w = image.extent.width, h = image.extent.height
        let p = points.map { CGPoint(x: $0.x * w + image.extent.minX, y: $0.y * h + image.extent.minY) }
        let top = hypot(p[1].x-p[0].x,p[1].y-p[0].y)
        let bottom = hypot(p[2].x-p[3].x,p[2].y-p[3].y)
        let left = hypot(p[3].x-p[0].x,p[3].y-p[0].y)
        let right = hypot(p[2].x-p[1].x,p[2].y-p[1].y)
        guard min(top,bottom) >= 36, min(left,right) >= 12,
              max(top,bottom) <= w, max(left,right) <= h,
              abs(atan2(p[1].y-p[0].y,p[1].x-p[0].x)) > 0.10 ||
              abs(top-bottom)/max(top,bottom) > 0.10 ||
              abs(left-right)/max(left,right) > 0.10 else { return nil }
        guard let filter = CIFilter(name: "CIPerspectiveCorrection") else { return nil }
        attempted = true
        filter.setValue(image, forKey: kCIInputImageKey)
        for (key, point) in zip(["inputTopLeft","inputTopRight","inputBottomRight","inputBottomLeft"], p) {
            filter.setValue(CIVector(cgPoint: point), forKey: key)
        }
        guard let corrected = filter.outputImage, corrected.extent.width.isFinite, corrected.extent.height.isFinite,
              corrected.extent.width >= 24, corrected.extent.height >= 8,
              corrected.extent.width <= 1600, corrected.extent.height <= 512,
              let crop = context.createCGImage(corrected, from: corrected.extent) else { return nil }
        let retry = VNRecognizeTextRequest()
        retry.recognitionLevel = .accurate
        retry.recognitionLanguages = request.recognitionLanguages
        retry.automaticallyDetectsLanguage = request.automaticallyDetectsLanguage
        retry.usesLanguageCorrection = request.usesLanguageCorrection
        guard (try? VNImageRequestHandler(cgImage: crop, orientation: .up).perform([retry])) != nil,
              let results = retry.results, results.count == 1,
              let best = results.first?.topCandidates(1).first, !best.string.isEmpty,
              best.confidence >= max(0.50, original.confidence + 0.08) else { return nil }
        return best
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = ProcessInfo.processInfo.systemUptime
        let wholeImage = region == CGRect(x: 0, y: 0, width: 1, height: 1)
        let base = wholeImage ? 0.35 : 0.45
        let interval = wholeImage ? min(0.7, max(base, max(analysisCost * 1.25, stableFrames >= 3 ? 0.55 : base))) : base
        guard running, now - lastRead >= interval,
              let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastRead = now
        defer { analysisCost = analysisCost * 0.7 + (ProcessInfo.processInfo.systemUptime - now) * 0.3 }
        autoreleasepool {
        let image = CIImage(cvPixelBuffer: buffer)
        guard let cg = context.createCGImage(image, from: image.extent) else { return }
        let request = textRequest
        request.recognitionLevel = .accurate
        // Vision's supported OCR languages vary with the OS and revision.
        // Unsupported explicit languages must not make the OCR request fail.
        let supported = (try? request.supportedRecognitionLanguages()) ?? ["en-US"]
        let wanted = language == "auto" ? ["en", "cs", "sk", "pl", "de", "fr", "es", "it", "pt"] : [language]
        let matches = wanted.compactMap { code in
            supported.first { $0.split(separator: "-").first.map(String.init) == code }
        }
        request.recognitionLanguages = matches.isEmpty ? Array(supported.prefix(1)) : matches
        request.automaticallyDetectsLanguage = language == "auto"
        request.usesLanguageCorrection = language != "auto" && !matches.isEmpty
        request.regionOfInterest = CGRect(x: region.minX, y: 1 - region.maxY, width: region.width, height: region.height)
        do {
            try VNImageRequestHandler(cvPixelBuffer: buffer, orientation: .up).perform([request])
            let rows = (request.results ?? []).sorted {
                if $0.boundingBox.midY == $1.boundingBox.midY {
                    return $0.boundingBox.minX < $1.boundingBox.minX
                }
                return $0.boundingBox.midY > $1.boundingBox.midY
            }
            let observations = wholeImage ? Self.columnOrder(rows) : rows
            var blocks: [GLCameraBlock] = []
            let palette = wholeImage ? GLCameraPalette(cg) : nil
            var remaining = 1200
            var attemptedRectification = false
            for observation in observations.prefix(20) {
                guard var recognized = observation.topCandidates(1).first,
                      !recognized.string.isEmpty, remaining > 0 else { continue }
                if wholeImage && !attemptedRectification && recognized.confidence < 0.75 &&
                    ProcessInfo.processInfo.systemUptime - now < 0.20 {
                    if let refined = rectifiedText(observation, original: recognized, image: image, request: request,
                                                   attempted: &attemptedRectification) {
                        recognized = refined
                    }
                }
                guard !wholeImage || recognized.confidence >= 0.30 else { continue }
                let line = recognized.string
                let box = observation.boundingBox
                let text = wholeImage ? String(line.prefix(remaining)).split(whereSeparator: { $0.isWhitespace }).joined(separator: " ") : String(line.prefix(remaining))
                remaining -= text.count + 1
                let rect = CGRect(x: box.minX, y: 1 - box.maxY, width: box.width, height: box.height)
                let angle = atan2(Double(observation.topLeft.y - observation.topRight.y) * Double(cg.height),
                                  Double(observation.topRight.x - observation.topLeft.x) * Double(cg.width))
                let background = palette?.background(for: rect) ?? 0
                var block = GLCameraBlock(text: text, rect: rect, angle: angle,
                                          background: background, ink: palette?.ink(for: rect, background: background))
                // Merge adjacent, similarly styled horizontal lines into sentences.
                // Keep columns, separate colors and tilted lines independent.
                if wholeImage, let previous = blocks.last,
                   abs(previous.angle) < 0.12, abs(block.angle) < 0.12,
                   GLCameraPalette.similar(previous.background, block.background),
                   GLCameraPalette.compatibleInk(previous.ink, block.ink) {
                    let lineHeight = previous.rect.height / CGFloat(previous.lineCount)
                    let gap = rect.minY - previous.rect.maxY
                    let overlap = min(rect.maxX, previous.rect.maxX) - max(rect.minX, previous.rect.minX)
                    if gap >= -lineHeight * 0.2 && gap <= lineHeight * 0.85 &&
                       rect.height >= lineHeight * 0.55 && rect.height <= lineHeight * 1.7 &&
                       overlap >= min(rect.width, previous.rect.width) * 0.65 &&
                       abs(rect.minX - previous.rect.minX) <= lineHeight * 0.8 {
                        block = GLCameraBlock(text: previous.text + " " + text,
                                              rect: previous.rect.union(rect), background: previous.background,
                                              ink: previous.ink ?? block.ink, lineCount: previous.lineCount + 1)
                        blocks.removeLast()
                    }
                }
                if wholeImage && block.lineCount == 1 {
                    // Vision already returns skewed line corners. Keep the physical
                    // bounding box and project the flat translation to those corners.
                    let points = [observation.topLeft, observation.topRight, observation.bottomRight, observation.bottomLeft]
                        .map { CGPoint(x: $0.x, y: 1 - $0.y) }
                    let quad = GLCameraQuad(points: points)
                    if points.allSatisfy({ $0.x >= 0 && $0.x <= 1 && $0.y >= 0 && $0.y <= 1 }),
                       quad.projection(size: CGSize(width: rect.width * CGFloat(cg.width),
                                                    height: rect.height * CGFloat(cg.height)), rect: rect) != nil {
                        block.quad = quad
                    }
                }
                if wholeImage && block.quad == nil && block.lineCount == 1 && abs(angle) >= 0.12 {
                    let width = hypot(Double(observation.topRight.x - observation.topLeft.x) * Double(cg.width),
                                      Double(observation.topRight.y - observation.topLeft.y) * Double(cg.height)) / Double(cg.width)
                    let height = hypot(Double(observation.bottomLeft.x - observation.topLeft.x) * Double(cg.width),
                                       Double(observation.bottomLeft.y - observation.topLeft.y) * Double(cg.height)) / Double(cg.height)
                    if width > 0, height > 0 {
                        let oriented = CGRect(x: rect.midX - CGFloat(width) / 2, y: rect.midY - CGFloat(height) / 2,
                                              width: CGFloat(width), height: CGFloat(height))
                        block = GLCameraBlock(text: text, rect: oriented, angle: angle, background: block.background, ink: block.ink)
                    }
                }
                blocks.append(block)
            }
            if wholeImage, let palette {
                for index in blocks.indices {
                    let block = blocks[index]
                    let horizontal = abs(block.angle) < 0.06 && (block.quad.map { abs($0.points[1].y - $0.points[0].y) < 0.003 && abs($0.points[3].x - $0.points[0].x) < 0.003 } ?? true)
                    if horizontal {
                        blocks[index].surface = palette.reconstructedSurface(for: block.rect,
                            background: block.background, ink: block.ink)
                    }
                    let appearance = palette.textureAndWeight(for: block.rect, ink: block.ink,
                        lineCount: block.lineCount, restore: horizontal && blocks[index].surface == nil)
                    blocks[index].texture = appearance.0
                    blocks[index].weight = appearance.1
                }
            }
            let signature = blocks.map(\.text).joined(separator: "\n")
            stableFrames = signature == previousOCR && !signature.isEmpty ? min(3, stableFrames + 1) : 0
            previousOCR = signature
            frame(UIImage(cgImage: cg), blocks, revision)
        } catch {
            stableFrames = 0; previousOCR = ""
            frame(UIImage(cgImage: cg), [], revision)
        }
        }
    }
}

@MainActor
private final class GLCameraState: ObservableObject {
    @Published var image: UIImage?
    @Published var previewSession: AVCaptureSession?
    @Published var recognizedText = ""
    @Published var translating = false
    @Published var translation = ""
    @Published var overlays: [GLCameraOverlay] = []
    @Published var translationFailed = false
    @Published var paused = false
    @Published var unavailable = false
    @Published var torchOn = false
    @Published var torchPending = false
    @Published var torchFailed = false
    private var torchRequest = 0
    private var capture: GLCameraCapture?
    private var task: Task<Void, Never>?
    private var translationRequest = 0
    private var previousVisible = Set<String>()
    private var eligibleVisible = Set<String>()
    private var cacheOrder: [String] = []
    private var revision = 0
    private var lifetime = 0
    private var candidate = ""
    private var emptyFrames = 0
    private var lastTranslated = ""
    private var translatedLines: [String] = []
    private var overlayCache: [String: String] = [:]
    private var overlaySource: String?
    private var currentBlocks: [GLCameraBlock] = []
    private var overlayMode = true
    private var retryAt: TimeInterval = 0
    // Full-image failures must not block unrelated visible text.
    private var blockRetryAt: [String: TimeInterval] = [:]
    private var lastTVSentAt: TimeInterval = 0
    private var running = false
    private var tvOutput = false
    private var source = "auto"
    private var target: GLTargetLanguage = .czech
    private var region = CGRect(x: 0, y: 0, width: 1, height: 1)
    private weak var model: CaptionAppModel?

    func start(model: CaptionAppModel, source: String, target: GLTargetLanguage, region: CGRect, overlay: Bool) async {
        guard !running, !model.isRunning else { return }
        unavailable = false; paused = false
        self.model = model
        self.source = source; self.target = target; self.region = region; self.overlayMode = overlay
        lifetime += 1
        let current = lifetime
        running = true
        let allowed = await AVCaptureDevice.requestAccess(for: .video)
        guard running, lifetime == current else { return }
        guard allowed else { unavailable = true; running = false; return }
        let capture = GLCameraCapture(frame: { [weak self] image, blocks, frameRevision in
            Task { @MainActor in
                guard let self, self.running, !self.paused, self.lifetime == current,
                      self.revision == frameRevision else { return }
                self.receive(image: image, blocks: blocks)
            }
        }, ready: { [weak self] session in
            Task { @MainActor in
                guard let self, self.running, !self.paused, self.lifetime == current else { return }
                self.previewSession = session
            }
        }, failure: { [weak self] in
            Task { @MainActor in
                guard let self, self.lifetime == current else { return }
                self.unavailable = true; self.stop()
            }
        })
        self.capture = capture
        applyConfiguration()
        capture.start()
    }

    func configure(source: String, target: GLTargetLanguage, region: CGRect, overlay: Bool) {
        guard self.source != source || self.target != target || self.region != region || self.overlayMode != overlay else { return }
        self.source = source; self.target = target; self.region = region; self.overlayMode = overlay
        overlayCache.removeAll(); overlaySource = nil; cacheOrder.removeAll()
        resetTranslation()
        applyConfiguration()
    }

    private func applyConfiguration() {
        capture?.configure(language: source, region: region, revision: revision)
    }

    private func resetTranslation() {
        revision += 1
        translationRequest += 1
        previousVisible.removeAll(); eligibleVisible.removeAll()
        blockRetryAt.removeAll()
        task?.cancel(); task = nil
        candidate = ""; emptyFrames = 0; lastTranslated = ""; translation = ""; recognizedText = ""; translating = false
        translatedLines = []; currentBlocks = []; overlays = []; translationFailed = false
        retryAt = 0; lastTVSentAt = 0
        if tvOutput { model?.sendCameraSubtitle("") }
    }

    private func updateOverlays() {
        guard overlayMode else { overlays = []; return }
        overlays = currentBlocks.enumerated().compactMap { index, block in
            guard let text = overlayCache[block.text], !text.isEmpty else { return nil }
            return GLCameraOverlay(id: index, rect: block.rect, text: text, angle: block.angle,
                                   background: block.background, ink: block.ink, lineCount: block.lineCount, surface: block.surface,
                                   quad: block.quad, weight: block.weight, texture: block.texture)
        }
    }

    private func rememberOverlay(_ original: String, _ translated: String) {
        guard !translated.isEmpty else { return }
        if overlayCache.count >= 256 && overlayCache[original] == nil {
            if let oldest = cacheOrder.first {
                overlayCache.removeValue(forKey: oldest)
                cacheOrder.removeFirst()
            }
        }
        if overlayCache[original] == nil { cacheOrder.append(original) }
        overlayCache[original] = translated
    }

    private func publishTV(force: Bool = false) {
        guard tvOutput else { return }
        let now = ProcessInfo.processInfo.systemUptime
        if force || now - lastTVSentAt >= 4 {
            lastTVSentAt = now
            model?.sendCameraSubtitle(translation)
        }
    }

    private func receive(image: UIImage, blocks: [GLCameraBlock]) {
        self.image = image
        let text = blocks.map(\.text).joined(separator: "\n")
        currentBlocks = blocks
        candidate = text
        recognizedText = text
        if overlayMode {
            receiveFullImage(text: text, blocks: blocks)
            return
        }
        if text.isEmpty {
            updateOverlays()
            emptyFrames += 1
            if emptyFrames >= 2 && (!translation.isEmpty || task != nil) {
                resetTranslation(); applyConfiguration()
            }
            return
        }
        emptyFrames = 0
        if text == lastTranslated {
            updateOverlays()
            publishTV()
            return
        }
        // Preview is independent of OCR. Translate the first readable frame;
        // minor recognition jitter must not prevent a subtitle from appearing.
        updateOverlays()
        if !lastTranslated.isEmpty && !matchesCurrent(lastTranslated) {
            translation = ""; lastTranslated = ""; translatedLines = []
            publishTV(force: true)
        }
        guard task == nil, ProcessInfo.processInfo.systemUptime >= retryAt else { return }
        translating = true
        let current = revision
        let source = resolvedSource(text), target = target
        if overlaySource != source {
            overlayCache.removeAll(); cacheOrder.removeAll(); overlaySource = source
            updateOverlays()
        }
        task = Task { [weak self] in
            guard let self, let model = self.model else { return }
            defer { if self.revision == current { self.task = nil; self.translating = false } }
            let result = await model.translateCameraText(text, source: source, target: target)
            guard !Task.isCancelled, self.revision == current, self.running,
                  !self.paused, self.matchesCurrent(text) else { return }
            guard let result, !result.isEmpty else {
                self.translationFailed = true
                self.retryAt = ProcessInfo.processInfo.systemUptime + 2
                return
            }
            self.lastTranslated = text
            self.translation = result
            model.recordCameraHistory(original: text, translated: result, source: source, target: target)
            self.translationFailed = false
            self.publishTV(force: true)
        }
    }

    // A block is translated once, then its geometry follows the current OCR frame.
    // New blocks in an existing scene must survive one more sample.
    private func receiveFullImage(text: String, blocks: [GLCameraBlock]) {
        let visible = Set(blocks.map(\.text))
        eligibleVisible = previousVisible.isEmpty ? visible : visible.intersection(previousVisible)
        let sceneChanged = !previousVisible.isEmpty && visible.isDisjoint(with: previousVisible)
        previousVisible = visible
        blockRetryAt = blockRetryAt.filter { visible.contains($0.key) && $0.value > ProcessInfo.processInfo.systemUptime }
        if sceneChanged {
            translationRequest += 1
            task?.cancel(); task = nil; translating = false; retryAt = 0
            eligibleVisible = visible
        }
        if text.isEmpty {
            updateOverlays()
            translation = ""; lastTranslated = ""
            publishTV(force: true)
            return
        }
        let source = resolvedSource(text), target = target
        if overlaySource != source {
            translationRequest += 1
            task?.cancel(); task = nil; translating = false; retryAt = 0
            overlayCache.removeAll(); cacheOrder.removeAll(); overlaySource = source
            blockRetryAt.removeAll()
        }
        publishVisibleBlocks()
        guard task == nil, ProcessInfo.processInfo.systemUptime >= retryAt,
              let model = self.model,
              let block = blocks.first(where: { eligibleVisible.contains($0.text) && overlayCache[$0.text] == nil &&
                  (blockRetryAt[$0.text] ?? 0) <= ProcessInfo.processInfo.systemUptime }) else { return }
        translating = true
        let current = revision, request = translationRequest
        task = Task { [weak self] in
            guard let self else { return }
            defer {
                if self.revision == current && self.translationRequest == request {
                    self.task = nil; self.translating = false
                }
            }
            var next: String? = block.text
            var consecutiveFailures = 0
            // Each visible string gets at most one attempt in this drain.
            var attempted = Set<String>()
            while let original = next {
                attempted.insert(original)
                guard !Task.isCancelled, self.revision == current, self.translationRequest == request,
                      self.running, !self.paused else { return }
                let result = await model.translateCameraText(original, source: source, target: target)
                guard !Task.isCancelled, self.revision == current, self.translationRequest == request,
                      self.running, !self.paused else { return }
                if let result, !result.isEmpty {
                    consecutiveFailures = 0
                    self.blockRetryAt.removeValue(forKey: original)
                    if self.previousVisible.contains(original) {
                        self.rememberOverlay(original, result)
                        model.recordCameraHistory(original: original, translated: result, source: source, target: target)
                        self.translationFailed = false
                        self.publishVisibleBlocks()
                    }
                } else {
                    consecutiveFailures += 1
                    self.translationFailed = true
                    if self.previousVisible.contains(original) {
                        self.blockRetryAt[original] = ProcessInfo.processInfo.systemUptime + 5
                    }
                    // A general provider outage still uses bounded backoff.
                    if consecutiveFailures >= 2 {
                        self.retryAt = ProcessInfo.processInfo.systemUptime + 2
                        return
                    }
                }
                next = self.currentBlocks.first(where: {
                    self.eligibleVisible.contains($0.text) && self.overlayCache[$0.text] == nil &&
                    !attempted.contains($0.text) &&
                    (self.blockRetryAt[$0.text] ?? 0) <= ProcessInfo.processInfo.systemUptime
                })?.text
            }
        }
    }

    private func publishVisibleBlocks() {
        updateOverlays()
        let result = currentBlocks.compactMap { overlayCache[$0.text] }.joined(separator: "\n")
        let changed = translation != result
        translation = result
        publishTV(force: changed)
    }

    // Restrict local language identification to the camera's supported
    // translation languages. Short/ambiguous strings use the game preference.
    private func resolvedSource(_ text: String) -> String {
        if source != "auto" { return source }
        let recognizer = NLLanguageRecognizer()
        recognizer.languageConstraints = GLTargetLanguage.allCases.map { NLLanguage(rawValue: $0.rawValue) }
        recognizer.processString(text)
        if let result = recognizer.languageHypotheses(withMaximum: 1).first,
           result.value >= 0.45, GLTargetLanguage(rawValue: result.key.rawValue) != nil {
            return result.key.rawValue
        }
        return overlaySource ?? model?.gameLanguage.rawValue ?? "en"
    }

    private func matchesCurrent(_ text: String) -> Bool {
        // Whitespace jitter is harmless; changed words, numbers or punctuation are not.
        text.split(whereSeparator: { $0.isWhitespace }) == candidate.split(whereSeparator: { $0.isWhitespace })
    }

    func setTV(_ enabled: Bool) {
        if tvOutput && !enabled { model?.sendCameraSubtitle("") }
        tvOutput = enabled
        if enabled && !translation.isEmpty { publishTV(force: true) }
    }

    func togglePause() {
        guard capture != nil else { return }
        paused.toggle()
        if paused {
            revision += 1; task?.cancel(); task = nil; translating = false
            capture?.stop(); resetTorch()
            if tvOutput { model?.sendCameraSubtitle("") }
        } else {
            resetTranslation(); applyConfiguration()
            previewSession = nil
            capture?.start()
        }
    }

    func zoom(_ value: Double) { capture?.zoom(CGFloat(value)) }
    private func resetTorch() {
        torchRequest += 1
        torchOn = false; torchPending = false; torchFailed = false
    }

    func toggleTorch() {
        guard running, !paused, previewSession != nil, !torchPending, let capture else { return }
        torchRequest += 1
        let request = torchRequest
        torchPending = true; torchFailed = false
        capture.torch(!torchOn) { [weak self] enabled, success in
            Task { @MainActor in
                guard let self, self.running, !self.paused, self.torchRequest == request else { return }
                self.torchOn = enabled
                self.torchPending = false
                self.torchFailed = !success
            }
        }
    }

    func stop() {
        running = false
        lifetime += 1
        resetTranslation()
        capture?.stop(); capture = nil
        previewSession = nil; image = nil; resetTorch()
        overlayCache.removeAll(); overlaySource = nil; cacheOrder.removeAll()
    }
}

/// Camera screen independent of console pairing and existing game OCR settings.
struct GameLingoCameraScreen: View {
    @ObservedObject var model: CaptionAppModel
    @StateObject private var state = GLCameraState()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @AppStorage("gamelingo.camera.source") private var source = "auto"
    @AppStorage("gamelingo.camera.target") private var target: GLTargetLanguage = .czech
    @AppStorage("gamelingo.camera.languagesInitialized") private var languagesInitialized = false
    @AppStorage("gamelingo.camera.fullImage") private var fullImage = true
    @AppStorage("gamelingo.camera.overlay") private var overlayEnabled = true
    @AppStorage("gamelingo.camera.regionX") private var regionX = 0.05
    @AppStorage("gamelingo.camera.regionY") private var regionY = 0.66
    @AppStorage("gamelingo.camera.regionWidth") private var regionWidth = 0.9
    @AppStorage("gamelingo.camera.regionHeight") private var regionHeight = 0.24
    @State private var draftRegion: CGRect?
    @State private var dragOrigin: CGRect?
    private static let defaultRegion = CGRect(x: 0.05, y: 0.66, width: 0.9, height: 0.24)
    @State private var zoom = 1.0
    @ObservedObject var devices: GameLingoDevices
    @ObservedObject var receiver: PlayCaptionTV

    private var selectedRegion: CGRect {
        let h = min(1, max(0.08, regionHeight))
        let w = min(1, max(0.15, regionWidth))
        return CGRect(x: min(1 - w, max(0, regionX)),
                      y: min(1 - h, max(0, regionY)), width: w, height: h)
    }

    private var region: CGRect {
        fullImage ? CGRect(x: 0, y: 0, width: 1, height: 1) : selectedRegion
    }

    private func commitRegion(_ rect: CGRect) {
        regionX = Double(rect.minX); regionY = Double(rect.minY)
        regionWidth = Double(rect.width); regionHeight = Double(rect.height)
        draftRegion = nil; dragOrigin = nil
        state.configure(source: source, target: target, region: fullImage ? region : rect,
                        overlay: fullImage && overlayEnabled)
    }

    private var tvReady: Bool {
        guard devices.subtitleOutput == .googleTV,
              let tv = devices.chosenTelevision else { return false }
        return receiver.receiverPaired && receiver.receiverConnected &&
            receiver.ip.trimmingCharacters(in: .whitespacesAndNewlines) == tv.ipAddress
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    HStack {
                        Picker("Jazyk textu", selection: $source) {
                            Text("Automaticky").tag("auto")
                            ForEach(GLTargetLanguage.allCases) { Text(LocalizedStringKey($0.title)).tag($0.rawValue) }
                        }
                        Image(systemName: "arrow.right")
                        Picker("Cílový jazyk", selection: $target) {
                            ForEach(GLTargetLanguage.allCases) { Text(LocalizedStringKey($0.title)).tag($0) }
                        }
                    }
                    Picker("Oblast", selection: $fullImage) {
                        Text("Celý obraz").tag(true)
                        Text("Vybraná oblast").tag(false)
                    }.pickerStyle(.segmented)
                    HStack {
                        Image(systemName: devices.subtitleOutput == .phone ? "smartphone" : "tv.fill")
                        Text(LocalizedStringKey(devices.subtitleOutput.rawValue))
                        if devices.subtitleOutput == .googleTV {
                            Text(LocalizedStringKey(tvReady ? "TV připojena" : "Spárovat")).foregroundStyle(tvReady ? GLStyle.green : .orange)
                        }
                    }.font(.caption)
                    GLNeonPanel(emphasized: true) {
                        VStack(alignment: .leading, spacing: 9) {
                            HStack {
                                Label("Náhled obrazovky", systemImage: "camera.fill").font(.subheadline.bold())
                                Spacer()
                                if !fullImage { Text("Vybraná oblast").font(.caption).foregroundStyle(.yellow) }
                            }
                            preview.aspectRatio(fullImage ? 9.0 / 16.0 : 0.9, contentMode: .fit)
                            if !fullImage {
                                Text("Vyber oblast, potom uprav její polohu a velikost v náhledu.")
                                    .font(.caption).foregroundStyle(GLNeon.mist)
                                Button("Obnovit oblast") { commitRegion(Self.defaultRegion) }
                                    .font(.subheadline).foregroundStyle(GLNeon.cyan)
                            }
                        }.padding(12)
                    }
                    if fullImage {
                        Toggle("Překlad přes původní text", isOn: $overlayEnabled)
                    }
                    translationPanel
                    Text("Online překlad může odesílat rozpoznaný text externím poskytovatelům.")
                        .font(.caption).foregroundStyle(GLStyle.muted)
                }.padding(16)
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 8) {
                    HStack {
                        Image(systemName: "magnifyingglass")
                        Slider(value: $zoom, in: 1...4)
                        Text(String(format: "%.1f×", zoom)).monospacedDigit()
                    }.accessibilityLabel("Přiblížení")
                    Button { state.togglePause() } label: {
                        Label(state.paused ? "Pokračovat" : "Pozastavit", systemImage: state.paused ? "play.fill" : "pause.fill")
                            .frame(maxWidth: .infinity)
                    }.disabled(state.unavailable)
                    GLPrimaryButton(title: "Zastavit překlad", systemImage: "stop.fill") { state.stop(); dismiss() }
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(GLNeon.panel)
            }
            .background(GLNeonBackground())
            .navigationTitle("Překlad kamerou")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { GLBackButton { state.stop(); dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 4) {
                    Button { state.toggleTorch() } label: {
                        Image(systemName: state.torchFailed ? "exclamationmark.triangle.fill" : (state.torchOn ? "flashlight.on.fill" : "flashlight.off.fill"))
                            .foregroundStyle(state.torchFailed ? Color.orange : (state.torchOn ? Color.yellow : Color.white))
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("Svítilna")
                    .accessibilityAddTraits(state.torchOn ? .isSelected : [])
                    .disabled(state.paused || state.unavailable || state.previewSession == nil || state.torchPending)
                    GLHelpButton(title: "Překlad kamerou", explanation: "Zvol celý obraz nebo nastav žlutý rámeček na původní titulky.")
                    }
                }
            }
        }
        .task {
            if !languagesInitialized {
                source = "auto"; target = model.targetLanguage; languagesInitialized = true
            }
            state.setTV(tvReady)
            await state.start(model: model, source: source, target: target, region: region, overlay: fullImage && overlayEnabled)
        }
        .onChange(of: source) { _, _ in update() }
        .onChange(of: target) { _, _ in update() }
        .onChange(of: fullImage) { _, _ in update() }
        .onChange(of: overlayEnabled) { _, _ in update() }
        .onChange(of: zoom) { _, value in state.zoom(value) }
        .onChange(of: tvReady) { _, value in state.setTV(value) }
        .onChange(of: phase) { _, value in if value == .background { state.stop(); dismiss() } }
        .onDisappear { state.stop() }
        .preferredColorScheme(.dark)
    }

    // Keep all dynamic OCR, provider status and subtitles inside a fixed
    // viewport. Long text scrolls locally; the camera controls never reflow.
    private var translationPanel: some View {
        ScrollView {
            VStack(spacing: 5) {
                if state.unavailable {
                    Text("Fotoaparát není dostupný.").foregroundStyle(.orange)
                } else if state.translationFailed {
                    Text("Překlad selhal.").foregroundStyle(.orange)
                } else if state.translating {
                    HStack(spacing: 6) {
                        ProgressView().tint(GLNeon.cyan)
                        Text("Překládám").font(.caption).foregroundStyle(GLStyle.muted)
                    }
                }
                if !state.translation.isEmpty {
                    Text(state.translation)
                        .font(.system(size: max(1, 20 * model.phoneFontScale), weight: .semibold))
                        .foregroundStyle(model.phoneTextColor.color)
                        .textSelection(.enabled)
                } else if !state.recognizedText.isEmpty {
                    Text(state.recognizedText).font(.caption).foregroundStyle(GLStyle.muted)
                        .textSelection(.enabled)
                }
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, minHeight: 88)
            .padding(8)
        }
        .frame(height: 108)
        .background(model.phoneBackground.fill, in: RoundedRectangle(cornerRadius: 12))
        .clipped()
    }

    private func update() {
        state.configure(source: source, target: target, region: region, overlay: fullImage && overlayEnabled)
    }

    // Drag the filled region to move it; drag any corner to resize it.
    // Keep the opposite corner fixed and clamp to the actual image.
    private func regionDrag(size: CGSize, corner: Int?) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("cameraImage"))
            .onChanged { value in
                let original = dragOrigin ?? selectedRegion
                if dragOrigin == nil { dragOrigin = original }
                let dx = value.translation.width / max(1, size.width)
                let dy = value.translation.height / max(1, size.height)
                if let corner {
                    let left = corner == 0 || corner == 2
                    let top = corner == 0 || corner == 1
                    let x1 = left ? min(original.maxX - 0.15, max(0, original.minX + dx)) : original.minX
                    let x2 = left ? original.maxX : max(original.minX + 0.15, min(1, original.maxX + dx))
                    let y1 = top ? min(original.maxY - 0.08, max(0, original.minY + dy)) : original.minY
                    let y2 = top ? original.maxY : max(original.minY + 0.08, min(1, original.maxY + dy))
                    draftRegion = CGRect(x: x1, y: y1, width: x2 - x1, height: y2 - y1)
                } else {
                    draftRegion = CGRect(x: min(1 - original.width, max(0, original.minX + dx)),
                                         y: min(1 - original.height, max(0, original.minY + dy)),
                                         width: original.width, height: original.height)
                }
            }
            .onEnded { _ in commitRegion(draftRegion ?? selectedRegion) }
    }

    private func regionEditor(size: CGSize) -> some View {
        let rect = draftRegion ?? selectedRegion
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color.yellow.opacity(0.12))
                .overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(Color.yellow, lineWidth: 2))
                .contentShape(Rectangle())
                .frame(width: size.width * rect.width, height: size.height * rect.height)
                .position(x: size.width * rect.midX, y: size.height * rect.midY)
                .gesture(regionDrag(size: size, corner: nil))
                .accessibilityLabel("Vybraná oblast")
            ForEach(0..<4, id: \.self) { corner in
                let left = corner == 0 || corner == 2
                let top = corner == 0 || corner == 1
                Circle().fill(Color.yellow)
                    .frame(width: 13, height: 13)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                    .position(x: size.width * (left ? rect.minX : rect.maxX),
                              y: size.height * (top ? rect.minY : rect.maxY))
                    .gesture(regionDrag(size: size, corner: corner))
                    .accessibilityHidden(true)
            }
        }.frame(width: size.width, height: size.height)
    }

    private var preview: some View {
        let aspect = state.image.map { $0.size.width / max(1, $0.size.height) } ?? 0.5625
        return GeometryReader { container in
            let width = min(container.size.width, container.size.height * aspect)
            let height = width / aspect
            GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                Color.black
                if state.paused, let image = state.image {
                    Image(uiImage: image).resizable().scaledToFit()
                } else if let session = state.previewSession {
                    GLLiveCameraPreview(session: session, fallback: state.image)
                } else if let image = state.image {
                    Image(uiImage: image).resizable().scaledToFit()
                } else {
                    Text("Čekám na první snímek").font(.caption)
                        .foregroundStyle(GLNeon.mist)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                if fullImage && overlayEnabled {
                    ForEach(state.overlays) { overlay in
                        let boxSize = CGSize(width: max(1, proxy.size.width * overlay.rect.width),
                                             height: max(1, proxy.size.height * overlay.rect.height))
                        let projection = overlay.quad?.projection(size: boxSize, rect: overlay.rect)
                        Text(overlay.text)
                            .font(.system(size: max(1, boxSize.height / CGFloat(overlay.lineCount) * 0.83 * model.phoneFontScale),
                                          weight: overlay.weight == 2 ? .bold : (overlay.weight == 1 ? .semibold : .regular)))
                            .foregroundStyle(overlay.ink.map { GLCameraPalette.color($0) } ?? GLCameraPalette.foreground(overlay.background))
                            .minimumScaleFactor(0.18)
                            .lineLimit(max(1, overlay.lineCount + 1))
                            .multilineTextAlignment(.leading)
                            .frame(width: max(1, proxy.size.width * overlay.rect.width),
                                   height: max(1, proxy.size.height * overlay.rect.height), alignment: .leading)
                            .background {
                                if let texture = overlay.texture, let image = texture.image() {
                                    Image(decorative: image, scale: 1).resizable().interpolation(.medium)
                                } else if let surface = overlay.surface {
                                    LinearGradient(colors: [GLCameraPalette.color(surface.start), GLCameraPalette.color(surface.end)],
                                                   startPoint: surface.horizontal ? .leading : .top,
                                                   endPoint: surface.horizontal ? .trailing : .bottom)
                                } else {
                                    GLCameraPalette.color(overlay.background)
                                }
                            }
                            .clipped()
                            .rotationEffect(.radians(projection == nil ? overlay.angle : 0))
                            .projectionEffect(projection ?? ProjectionTransform(CGAffineTransform.identity))
                            .position(x: proxy.size.width * overlay.rect.midX,
                                      y: proxy.size.height * overlay.rect.midY)
                    }
                }
                if !fullImage {
                    regionEditor(size: proxy.size)
                }
            }.coordinateSpace(name: "cameraImage")
            }.frame(width: width, height: height)
                .position(x: container.size.width / 2, y: container.size.height / 2)
        }.background(.black).clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct GLLiveCameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    let fallback: UIImage?

    final class PreviewView: UIView {
        private let backup = UIImageView()
        private let previewLayer = AVCaptureVideoPreviewLayer()

        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .black
            backup.contentMode = .scaleAspectFit
            addSubview(backup)
            previewLayer.videoGravity = .resizeAspect
            layer.addSublayer(previewLayer)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        func update(session: AVCaptureSession, fallback: UIImage?) {
            backup.image = fallback
            if previewLayer.session !== session { previewLayer.session = session }
            orient()
            // The sampled frame remains visible if the native layer has not
            // started presenting video. No dependence on OCR for native FPS.
            previewLayer.opacity = previewLayer.isPreviewing || fallback == nil ? 1 : 0
            setNeedsLayout()
        }

        private func orient() {
            if let connection = previewLayer.connection, connection.isVideoOrientationSupported,
               connection.videoOrientation != .portrait {
                connection.videoOrientation = .portrait
            }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            backup.frame = bounds
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            previewLayer.frame = bounds
            CATransaction.commit()
            orient()
        }
    }
    func makeUIView(context: Context) -> PreviewView { PreviewView(frame: .zero) }
    func updateUIView(_ view: PreviewView, context: Context) {
        view.update(session: session, fallback: fallback)
    }
}
