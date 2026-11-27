import SwiftUI
import AVFoundation
import Vision
import NaturalLanguage
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
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "cz.caseycz.gamelingo.camera.session")
    private let analysisQueue = DispatchQueue(label: "cz.caseycz.gamelingo.camera.ocr", qos: .userInitiated)
    private let context = CIContext()
    private var device: AVCaptureDevice?
    private var running = false
    private var lastRead: TimeInterval = 0
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
                do {
                    try device.lockForConfiguration()
                    if device.isFocusModeSupported(.continuousAutoFocus) { device.focusMode = .continuousAutoFocus }
                    if device.isExposureModeSupported(.continuousAutoExposure) { device.exposureMode = .continuousAutoExposure }
                    device.unlockForConfiguration()
                } catch { /* retain camera defaults */ }
            }
            self.analysisQueue.async { self.running = true; self.lastRead = 0 }
            self.session.startRunning()
            if self.session.isRunning { self.ready(self.session) }
            else { self.analysisQueue.async { self.running = false }; self.failure() }
        }
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

    func zoom(_ value: CGFloat) {
        queue.async {
            guard let device = self.device, (try? device.lockForConfiguration()) != nil else { return }
            device.videoZoomFactor = min(max(1, value), min(4, device.activeFormat.videoMaxZoomFactor))
            device.unlockForConfiguration()
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

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = ProcessInfo.processInfo.systemUptime
        guard running, now - lastRead >= 0.45,
              let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastRead = now
        let image = CIImage(cvPixelBuffer: buffer)
        guard let cg = context.createCGImage(image, from: image.extent) else { return }
        let request = VNRecognizeTextRequest()
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
        overlayCache.removeAll(); overlaySource = nil
        resetTranslation()
        applyConfiguration()
    }

    private func applyConfiguration() {
        capture?.configure(language: source, region: region, revision: revision)
    }

    private func resetTranslation() {
        revision += 1
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
            return GLCameraOverlay(id: index, rect: block.rect, text: text)
        }
    }

    private func rememberOverlay(_ original: String, _ translated: String) {
        guard !translated.isEmpty else { return }
        if overlayCache.count >= 256 && overlayCache[original] == nil {
            overlayCache.removeAll(keepingCapacity: true)
        }
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
            overlayCache.removeAll(); overlaySource = source
            updateOverlays()
        }
        let wantsOverlay = overlayMode
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
            self.translationFailed = false
            self.translatedLines = []
            self.updateOverlays()
            self.publishTV(force: true)
            var lines = result.components(separatedBy: "\n")
            if wantsOverlay && lines.count != blocks.count {
                // Do not align a provider's merged or split lines to the wrong text.
                // Reuse the same cached translator for individual OCR blocks.
                lines = []
                for block in blocks {
                    guard !Task.isCancelled, self.revision == current, self.matchesCurrent(text) else { return }
                    if let cached = self.overlayCache[block.text] {
                        lines.append(cached)
                        continue
                    }
                    guard let line = await model.translateCameraText(block.text, source: source, target: target),
                          !line.isEmpty else { lines = []; break }
                    guard !Task.isCancelled, self.revision == current, self.running, !self.paused else { return }
                    lines.append(line)
                    self.rememberOverlay(block.text, line)
                    self.updateOverlays()
                }
            }
            guard !Task.isCancelled, self.revision == current, self.running,
                  !self.paused, self.matchesCurrent(text) else { return }
            self.translatedLines = lines
            if wantsOverlay && lines.count == blocks.count {
                for (block, line) in zip(blocks, lines) { self.rememberOverlay(block.text, line) }
            }
            self.updateOverlays()
            self.publishTV(force: true)
        }
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
        return model?.gameLanguage.rawValue ?? "en"
    }

    private func matchesCurrent(_ text: String) -> Bool {
        if candidate == text { return true }
        let previous = Set(text.lowercased().split(whereSeparator: { !$0.isLetter && !$0.isNumber }))
        let current = Set(candidate.lowercased().split(whereSeparator: { !$0.isLetter && !$0.isNumber }))
        return !previous.isEmpty && previous.intersection(current).count * 2 >= max(previous.count, current.count)
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
        overlayCache.removeAll(); overlaySource = nil
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
                    HStack {
                        Button { state.toggleTorch() } label: {
                            Label("Svítilna", systemImage: state.torchFailed ? "exclamationmark.triangle.fill" : (state.torchOn ? "flashlight.on.fill" : "flashlight.off.fill"))
                                .foregroundStyle(state.torchFailed ? Color.orange : (state.torchOn ? Color.yellow : Color.white))
                        }.disabled(state.paused || state.unavailable || state.previewSession == nil || state.torchPending)
                        Spacer()
                        Button { state.togglePause() } label: {
                            Label(state.paused ? "Pokračovat" : "Pozastavit", systemImage: state.paused ? "play.fill" : "pause.fill")
                        }.disabled(state.unavailable)
                    }
                    GLPrimaryButton(title: "Zastavit překlad", systemImage: "stop.fill") { state.stop(); dismiss() }
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(GLNeon.panel)
            }
            .background(GLNeonBackground())
            .navigationTitle("Překlad kamerou")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { GLBackButton { state.stop(); dismiss() } } }
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
                        .font(.system(size: 20 * model.phoneFontScale, weight: .semibold))
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
