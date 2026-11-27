import SwiftUI
import AVFoundation
import Vision
import CoreImage
import UIKit

private struct GLCameraBlock: Sendable {
    let text: String
    let rect: CGRect // upright image coordinates, origin at top left
}

private struct GLCameraOverlay: Identifiable {
    let id: Int
    let rect: CGRect
    let text: String
}

/// Camera-only source, with a single capture queue and no audio or recording.
private final class GLCameraCapture: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, @unchecked Sendable {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "cz.caseycz.gamelingo.camera")
    private let context = CIContext()
    private var device: AVCaptureDevice?
    private var running = false
    private var lastRead: TimeInterval = 0
    private var language = "en-US"
    private var region = CGRect(x: 0, y: 0, width: 1, height: 1)
    private var revision = 0
    private let frame: @Sendable (UIImage, [GLCameraBlock], Int) -> Void
    private let failure: @Sendable () -> Void

    init(frame: @escaping @Sendable (UIImage, [GLCameraBlock], Int) -> Void, failure: @escaping @Sendable () -> Void) {
        self.frame = frame
        self.failure = failure
    }

    func configure(language: String, region: CGRect, revision: Int) {
        queue.async {
            self.language = language
            self.region = region
            self.revision = revision
        }
    }

    func start() {
        queue.async {
            guard !self.running else { return }
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
                output.setSampleBufferDelegate(self, queue: self.queue)
                if let connection = output.connection(with: .video), connection.isVideoOrientationSupported {
                    connection.videoOrientation = .portrait
                }
                self.session.commitConfiguration()
                self.device = device
                do {
                    try device.lockForConfiguration()
                    if device.isFocusModeSupported(.continuousAutoFocus) { device.focusMode = .continuousAutoFocus }
                    if device.isExposureModeSupported(.continuousAutoExposure) { device.exposureMode = .continuousAutoExposure }
                    device.unlockForConfiguration()
                } catch { /* retain camera defaults */ }
            }
            self.running = true
            self.lastRead = 0
            self.session.startRunning()
            if !self.session.isRunning { self.running = false; self.failure() }
        }
    }

    func stop() {
        queue.async {
            self.running = false
            if let device = self.device, device.hasTorch, (try? device.lockForConfiguration()) != nil {
                if device.isTorchModeSupported(.off) { device.torchMode = .off }
                device.unlockForConfiguration()
            }
            if self.session.isRunning { self.session.stopRunning() }
        }
    }

    func zoom(_ value: CGFloat) {
        queue.async {
            guard let device = self.device, (try? device.lockForConfiguration()) != nil else { return }
            device.videoZoomFactor = min(max(1, value), min(4, device.activeFormat.videoMaxZoomFactor))
            device.unlockForConfiguration()
        }
    }

    func torch(_ enabled: Bool, completion: @escaping @Sendable (Bool) -> Void) {
        queue.async {
            guard let device = self.device, device.hasTorch, device.isTorchAvailable,
                  (try? device.lockForConfiguration()) != nil else { completion(false); return }
            defer { device.unlockForConfiguration() }
            let mode: AVCaptureDevice.TorchMode = enabled ? .on : .off
            if device.isTorchModeSupported(mode) { device.torchMode = mode }
            completion(device.torchMode == .on)
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = ProcessInfo.processInfo.systemUptime
        guard running, now - lastRead >= 0.45,
              let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastRead = now
        let image = CIImage(cvPixelBuffer: buffer)
        guard let cg = context.createCGImage(image, from: image.extent) else { return }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = [language]
        request.usesLanguageCorrection = true
        request.regionOfInterest = CGRect(x: region.minX, y: 1 - region.maxY, width: region.width, height: region.height)
        do {
            try VNImageRequestHandler(cgImage: cg, orientation: .up).perform([request])
            let observations = (request.results ?? []).sorted {
                $0.boundingBox.midY > $1.boundingBox.midY
            }
            var blocks: [GLCameraBlock] = []
            var remaining = 1200
            for observation in observations.prefix(20) {
                guard let line = observation.topCandidates(1).first?.string, !line.isEmpty,
                      remaining > 0 else { continue }
                let box = observation.boundingBox
                let text = String(line.prefix(remaining))
                remaining -= text.count + 1
                blocks.append(GLCameraBlock(text: text,
                    rect: CGRect(x: box.minX, y: 1 - box.maxY, width: box.width, height: box.height)))
            }
            frame(UIImage(cgImage: cg), blocks, revision)
        } catch {
            frame(UIImage(cgImage: cg), [], revision)
        }
    }
}

@MainActor
private final class GLCameraState: ObservableObject {
    @Published var image: UIImage?
    @Published var translation = ""
    @Published var overlays: [GLCameraOverlay] = []
    @Published var translationFailed = false
    @Published var paused = false
    @Published var unavailable = false
    @Published var torchOn = false
    private var capture: GLCameraCapture?
    private var task: Task<Void, Never>?
    private var revision = 0
    private var lifetime = 0
    private var candidate = ""
    private var confirmations = 0
    private var lastTranslated = ""
    private var translatedLines: [String] = []
    private var currentBlocks: [GLCameraBlock] = []
    private var overlayMode = true
    private var retryAt: TimeInterval = 0
    private var lastTVSentAt: TimeInterval = 0
    private var running = false
    private var tvOutput = false
    private var source: GLGameLanguage = .english
    private var target: GLTargetLanguage = .czech
    private var region = CGRect(x: 0, y: 0, width: 1, height: 1)
    private weak var model: CaptionAppModel?

    func start(model: CaptionAppModel, source: GLGameLanguage, target: GLTargetLanguage, region: CGRect, overlay: Bool) async {
        guard !running, !model.isRunning else { return }
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

    func configure(source: GLGameLanguage, target: GLTargetLanguage, region: CGRect, overlay: Bool) {
        self.source = source; self.target = target; self.region = region; self.overlayMode = overlay
        resetTranslation()
        applyConfiguration()
    }

    private func applyConfiguration() {
        capture?.configure(language: source.visionIdentifier, region: region, revision: revision)
    }

    private func resetTranslation() {
        revision += 1
        task?.cancel(); task = nil
        candidate = ""; confirmations = 0; lastTranslated = ""; translation = ""
        translatedLines = []; currentBlocks = []; overlays = []; translationFailed = false
        retryAt = 0; lastTVSentAt = 0
        if tvOutput { model?.sendCameraSubtitle("") }
    }

    private func updateOverlays() {
        guard overlayMode, currentBlocks.count == translatedLines.count else { overlays = []; return }
        overlays = zip(currentBlocks, translatedLines).enumerated().map { index, pair in
            GLCameraOverlay(id: index, rect: pair.0.rect, text: pair.1)
        }
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
        if text == candidate { confirmations = min(2, confirmations + 1) }
        else { candidate = text; confirmations = 1 }
        guard confirmations >= 2 else { return }
        if text.isEmpty {
            if !translation.isEmpty || task != nil { resetTranslation(); applyConfiguration() }
            return
        }
        if text == lastTranslated {
            updateOverlays()
            publishTV()
            return
        }
        // Never display a translation over a different, confirmed source.
        if !translation.isEmpty {
            translation = ""; overlays = []; translatedLines = []; lastTranslated = ""
            publishTV(force: true)
        }
        guard task == nil, ProcessInfo.processInfo.systemUptime >= retryAt else { return }
        let current = revision
        let source = source, target = target
        let wantsOverlay = overlayMode
        task = Task { [weak self] in
            guard let self, let model = self.model else { return }
            defer { if self.revision == current { self.task = nil } }
            let result = await model.translateCameraText(text, source: source, target: target)
            guard !Task.isCancelled, self.revision == current, self.running,
                  !self.paused, self.candidate == text else { return }
            guard let result, !result.isEmpty else {
                self.translationFailed = true
                self.retryAt = ProcessInfo.processInfo.systemUptime + 2
                return
            }
            var lines = result.components(separatedBy: "\n")
            if wantsOverlay && lines.count != blocks.count {
                // Do not align a provider's merged or split lines to the wrong text.
                // Reuse the same cached translator for individual OCR blocks.
                lines = []
                for block in blocks {
                    guard !Task.isCancelled, self.revision == current, self.candidate == text else { return }
                    guard let line = await model.translateCameraText(block.text, source: source, target: target),
                          !line.isEmpty else { lines = []; break }
                    lines.append(line)
                }
            }
            guard !Task.isCancelled, self.revision == current, self.running,
                  !self.paused, self.candidate == text else { return }
            self.lastTranslated = text
            self.translation = result
            self.translationFailed = false
            self.translatedLines = lines
            self.updateOverlays()
            self.publishTV(force: true)
        }
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
            revision += 1; task?.cancel(); task = nil
            capture?.stop(); torchOn = false
            if tvOutput { model?.sendCameraSubtitle("") }
        } else {
            resetTranslation(); applyConfiguration()
            capture?.start()
        }
    }

    func zoom(_ value: Double) { capture?.zoom(CGFloat(value)) }
    func toggleTorch() {
        capture?.torch(!torchOn) { [weak self] enabled in
            Task { @MainActor in self?.torchOn = enabled }
        }
    }

    func stop() {
        running = false
        lifetime += 1
        resetTranslation()
        capture?.stop(); capture = nil
        image = nil; torchOn = false
    }
}

/// Camera screen independent of console pairing and existing game OCR settings.
struct GameLingoCameraScreen: View {
    @ObservedObject var model: CaptionAppModel
    @StateObject private var state = GLCameraState()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @State private var source: GLGameLanguage = .english
    @State private var target: GLTargetLanguage = .czech
    @State private var fullImage = true
    @State private var overlayEnabled = true
    @State private var centerX = 0.5
    @State private var centerY = 0.78
    @State private var regionWidth = 0.9
    @State private var regionHeight = 0.24
    @State private var zoom = 1.0
    @State private var tvOutput = false

    private var region: CGRect {
        if fullImage { return CGRect(x: 0, y: 0, width: 1, height: 1) }
        let height = min(0.8, max(0.08, regionHeight))
        let width = min(0.95, max(0.15, regionWidth))
        return CGRect(x: min(1 - width, max(0, centerX - width / 2)),
                      y: min(1 - height, max(0, centerY - height / 2)), width: width, height: height)
    }

    private var tvReady: Bool {
        guard model.devices.subtitleOutput == .googleTV,
              let tv = model.devices.chosenTelevision else { return false }
        return model.tv.receiverPaired && model.tv.receiverConnected &&
            model.tv.ip.trimmingCharacters(in: .whitespacesAndNewlines) == tv.ipAddress
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    HStack {
                        Picker("Jazyk textu", selection: $source) {
                            ForEach(GLGameLanguage.allCases) { Text(LocalizedStringKey($0.title)).tag($0) }
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
                    preview
                    if fullImage {
                        Toggle("Překlad přes původní text", isOn: $overlayEnabled)
                    } else {
                        Group {
                            regionSlider("Vodorovná pozice oblasti", value: $centerX, range: 0...1)
                            regionSlider("Pozice oblasti", value: $centerY, range: 0...1)
                            regionSlider("Šířka oblasti", value: $regionWidth, range: 0.15...0.95)
                            regionSlider("Velikost oblasti", value: $regionHeight, range: 0.08...0.8)
                        }
                    }
                    if state.translationFailed { Text("Překlad selhal.").foregroundStyle(.orange) }
                    if !fullImage || !overlayEnabled || state.overlays.isEmpty {
                    Text(state.translation).font(.system(size: 20 * model.phoneFontScale, weight: .semibold))
                        .foregroundStyle(model.phoneTextColor.color)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .padding(8)
                        .background(model.phoneBackground.fill, in: RoundedRectangle(cornerRadius: 12))
                        .textSelection(.enabled)
                    }
                    HStack {
                        Image(systemName: "magnifyingglass")
                        Slider(value: $zoom, in: 1...4)
                        Text(String(format: "%.1f×", zoom)).monospacedDigit()
                    }.accessibilityLabel("Přiblížení")
                    HStack {
                        Button { state.toggleTorch() } label: {
                            Label("Svítilna", systemImage: state.torchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                        }.disabled(state.paused || state.unavailable)
                        Spacer()
                        Button { state.togglePause() } label: {
                            Label(state.paused ? "Pokračovat" : "Pozastavit", systemImage: state.paused ? "play.fill" : "pause.fill")
                        }.disabled(state.unavailable)
                    }
                    if tvReady { Toggle("Překlady na TV", isOn: $tvOutput) }
                    if state.unavailable { Text("Fotoaparát není dostupný.").foregroundStyle(.orange) }
                    Text("Online překlad může odesílat rozpoznaný text externím poskytovatelům.")
                        .font(.caption).foregroundStyle(GLStyle.muted)
                    GLPrimaryButton(title: "Zastavit překlad", systemImage: "stop.fill") { state.stop(); dismiss() }
                }.padding(16)
            }
            .background(GLNeonBackground())
            .navigationTitle("Překlad kamerou")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { GLBackButton { state.stop(); dismiss() } } }
        }
        .task {
            source = model.gameLanguage; target = model.targetLanguage
            await state.start(model: model, source: source, target: target, region: region, overlay: fullImage && overlayEnabled)
        }
        .onChange(of: source) { _, _ in update() }
        .onChange(of: target) { _, _ in update() }
        .onChange(of: fullImage) { _, _ in update() }
        .onChange(of: centerX) { _, _ in update() }
        .onChange(of: centerY) { _, _ in update() }
        .onChange(of: regionWidth) { _, _ in update() }
        .onChange(of: overlayEnabled) { _, _ in update() }
        .onChange(of: regionHeight) { _, _ in update() }
        .onChange(of: zoom) { _, value in state.zoom(value) }
        .onChange(of: tvOutput) { _, value in state.setTV(value && tvReady) }
        .onChange(of: tvReady) { _, value in if !value { tvOutput = false; state.setTV(false) } }
        .onChange(of: phase) { _, value in if value == .background { state.stop(); dismiss() } }
        .onDisappear { state.stop() }
        .preferredColorScheme(.dark)
    }

    private func update() {
        state.configure(source: source, target: target, region: region, overlay: fullImage && overlayEnabled)
    }

    private func regionSlider(_ label: LocalizedStringKey, value: Binding<Double>,
                              range: ClosedRange<Double>) -> some View {
        HStack {
            Text(label).font(.caption).frame(width: 120, alignment: .leading)
            Slider(value: value, in: range).accessibilityLabel(Text(label))
        }
    }

    private var preview: some View {
        let aspect = state.image.map { $0.size.width / max(1, $0.size.height) } ?? 0.5625
        return GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                Color.black
                if let image = state.image { Image(uiImage: image).resizable().scaledToFit() }
                if fullImage && overlayEnabled {
                    ForEach(state.overlays) { overlay in
                        Text(overlay.text)
                            .font(.system(size: max(9, min(22, proxy.size.height * overlay.rect.height * model.phoneFontScale)), weight: .semibold))
                            .foregroundStyle(model.phoneTextColor.color)
                            .minimumScaleFactor(0.35)
                            .lineLimit(2)
                            .frame(width: max(30, proxy.size.width * overlay.rect.width),
                                   height: max(18, proxy.size.height * overlay.rect.height))
                            .background(model.phoneBackground.fill)
                            .position(x: proxy.size.width * overlay.rect.midX,
                                      y: proxy.size.height * overlay.rect.midY)
                    }
                }
                if !fullImage {
                    Rectangle().strokeBorder(Color.yellow, lineWidth: 2)
                        .frame(width: proxy.size.width * region.width, height: proxy.size.height * region.height)
                        .offset(x: proxy.size.width * region.minX, y: proxy.size.height * region.minY)
                }
            }
        }.aspectRatio(aspect, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
