import Foundation
import CoreVideo
import Vision

/// Vision coordinates start at bottom-left. Constrain OCR to subtitle bands
/// rather than all the labels, map controls and HUD in the game image.
enum SubtitleRegion: String, CaseIterable, Identifiable {
    case narrow
    case center
    case wide

    var id: String { rawValue }

    var title: String {
        switch self {
        case .narrow: return "Úzká – pouze titulky"
        case .center: return "Střed – doporučeno"
        case .wide: return "Široká – původní"
        }
    }

    var rect: CGRect {
        switch self {
        case .narrow:
            return CGRect(x: 0.23, y: 0.10, width: 0.54, height: 0.17)
        case .center:
            return CGRect(x: 0.15, y: 0.07, width: 0.70, height: 0.23)
        case .wide:
            return CGRect(x: 0.04, y: 0.03, width: 0.92, height: 0.34)
        }
    }
}

/// OCR speed presets. The verified legacy path remains the default.
enum OCRMode: String, CaseIterable, Identifiable {
    case classic
    case automatic
    case street

    var id: String { rawValue }

    var title: String {
        switch self {
        case .classic: return "Původní · stabilní"
        case .automatic: return "Automatický · vyvážený"
        case .street: return "Krátké dialogy · rychlý"
        }
    }

    var explanation: String {
        switch self {
        case .classic: return "Původní čtení přibližně každých 0,6 sekundy. Výchozí pro zachování funkčního chování."
        case .automatic: return "Častější snímání a šetrnější filtr krátkých herních vět."
        case .street: return "Zachytává i velmi krátké věty. Vyšší náročnost na baterii a výkon iPhonu."
        }
    }

    var cadence: OCRCadence { OCRCadence.preset(rawValue) }
    var minimumOCRInterval: TimeInterval { cadence.dialogue }
    var minimumFrameInterval: TimeInterval { cadence.frame }
}

struct RecognizedCaption {
    let english: String
    let revision: Int
    let capturedAt: Date
    let ocrStartedAt: TimeInterval
    let ocrMilliseconds: Double
}

/// OCR of a configurable bottom-center band. Only changed subtitle text is sent.
actor CaptionPipeline {
    private var lastText = ""
    private var dialogueStability = CaptionStability()
    private var gameTextStability = GameTextStability()
    private var lastRead = Date.distantPast
    private var lastSeen = Date.distantPast
    private let translationCache = GLTranslationCache()
    func purgeTranslationCache() async -> Bool { await translationCache.purge() }
    func setTranslationCacheEnabled(_ enabled: Bool) async { await translationCache.setEnabled(enabled) }
    private var providerBreakers: [String: TranslationCircuitBreaker] = [:]
    private var lastSettings = ""
    private var emptyReads = 0
    private var revision = 0
    private var dialogueRetryAt: TimeInterval?
    private var optionsRetryAt: TimeInterval?
    private var dialogueImageGate = OCRFrameGate(maximumSkip: 1.5)

    func resetDialogue() {
        lastText = ""
        dialogueStability.reset()
        lastRead = .distantPast
        lastSeen = .distantPast
        lastSettings = ""
        dialogueImageGate.reset()
        dialogueRetryAt = nil
        emptyReads = 0
        revision += 1
    }

    func isCurrent(revision candidate: Int) -> Bool {
        revision == candidate
    }

    /// Permit a new attempt only if this exact failed reading is still current.
    /// Do not continuously retry the same visible sentence on every OCR pass.
    func dialogueTranslationFailed(revision candidate: Int) {
        guard revision == candidate, !lastText.isEmpty else { return }
        dialogueRetryAt = ProcessInfo.processInfo.systemUptime + 2
    }

    func optionsTranslationFailed(_ options: [String], area: CGRect,
                                  asText: Bool, gameLanguage: GLGameLanguage) {
        let signature = "\(area)|\(asText)|\(gameLanguage.rawValue)"
        guard lastOptionsSettings == signature, lastOptions == options,
              !options.isEmpty else { return }
        optionsRetryAt = ProcessInfo.processInfo.systemUptime + 2
    }

    func recognize(_ buffer: CVPixelBuffer,
                   region: SubtitleRegion,
                   manualArea: CGRect?,
                   filterMenus: Bool,
                   mode: OCRMode = .classic,
                   expandOnEmpty: Bool = false,
                   gameLanguage: GLGameLanguage = .english) -> RecognizedCaption? {
        let baseROI = manualArea.map { CGRect(
            x: $0.minX, y: 1 - $0.maxY, width: $0.width, height: $0.height
        ) } ?? region.rect
        let settings = "\(baseROI)|\(filterMenus)|\(mode.rawValue)|\(expandOnEmpty)|\(gameLanguage.rawValue)"
        if settings != lastSettings {
            lastSettings = settings
            lastText = ""
            dialogueStability.reset()
            lastRead = .distantPast
            lastSeen = .distantPast
            dialogueImageGate.reset()
            dialogueRetryAt = nil
            emptyReads = 0
            revision += 1
        }

        let now = Date()
        guard now.timeIntervalSince(lastRead) > mode.minimumOCRInterval else { return nil }
        lastRead = now

        // Optional low-frequency fallback. It NEVER overrides manually selected OCR.
        let searchWide = expandOnEmpty && manualArea == nil &&
            mode != .classic && emptyReads >= 3 && emptyReads % 3 == 0
        let roi = searchWide ? SubtitleRegion.wide.rect : baseROI
        let imageTime = ProcessInfo.processInfo.systemUptime
        let fingerprint = OCRFrameFingerprint.read(buffer, visionROI: roi)
        guard dialogueStability.needsConfirmation || dialogueImageGate.shouldRead(fingerprint, now: imageTime) else { return nil }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = [gameLanguage.visionIdentifier]
        request.usesLanguageCorrection = true
        request.regionOfInterest = roi

        do {
            try VNImageRequestHandler(cvPixelBuffer: buffer, options: [:]).perform([request])
            dialogueImageGate.recordSuccessfulRead(fingerprint, now: imageTime)
            let minLetters = mode == .street ? 2 : 3
            let lines = (request.results ?? []).compactMap { item -> String? in
                guard let candidate = item.topCandidates(1).first else { return nil }
                let line = candidate.string.trimmingCharacters(in: .whitespacesAndNewlines)
                guard line.filter({ $0.isLetter }).count >= minLetters else { return nil }
                if filterMenus && looksLikeMenu(line, conservative: mode != .classic) {
                    return nil
                }
                return line
            }
            let text = lines.joined(separator: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let readable = text.count >= (mode == .street ? 2 : 4) &&
                text.filter({ $0.isLetter }).count >= minLetters
            if readable { lastSeen = now; emptyReads = 0 } else { emptyReads += 1 }
            let retryDue = dialogueRetryAt.map { imageTime >= $0 } ?? false
            switch dialogueStability.observe(readable ? text : "", now: imageTime, retry: retryDue) {
            case .hold:
                return nil
            case .clear:
                lastText = ""
                dialogueRetryAt = nil
                revision += 1
                return RecognizedCaption(english: "", revision: revision, capturedAt: now,
                    ocrStartedAt: imageTime, ocrMilliseconds: (ProcessInfo.processInfo.systemUptime - imageTime) * 1000)
            case .accept(let stable):
                dialogueRetryAt = nil
                lastText = stable
                revision += 1
                return RecognizedCaption(english: stable, revision: revision, capturedAt: now,
                    ocrStartedAt: imageTime, ocrMilliseconds: (ProcessInfo.processInfo.systemUptime - imageTime) * 1000)
            }
        } catch {
            return nil
        }
    }

    // Dedicated OCR lane for the optional Android-style yellow response area.
    // It never changes the standard dialogue OCR region.
    private var lastOptions = [String]()
    private var lastOptionsRead: TimeInterval?
    private var optionsRecentCost: TimeInterval = 0
    private var optionsMode: OCRMode = .classic
    private var lastOptionsSettings = ""
    private var optionsImageGate = OCRFrameGate(maximumSkip: 2.0)

    func resetOptions() {
        lastOptions = []
        gameTextStability.reset()
        lastOptionsRead = nil
        optionsRecentCost = 0
        lastOptionsSettings = ""
        optionsImageGate.reset()
        optionsRetryAt = nil
    }

    /// The area uses top-left normalized screen coordinates, unlike Vision.
    /// Returns nil when unchanged; [] when choices disappeared and TV should clear.
    func recognizeOptions(_ buffer: CVPixelBuffer,
                          area: CGRect,
                          asText: Bool,
                          gameLanguage: GLGameLanguage = .english,
                          mode: OCRMode = .classic) -> [String]? {
        if optionsMode != mode {
            optionsMode = mode
            lastOptionsRead = nil
            optionsRecentCost = 0
        }
        let signature = "\(area)|\(asText)|\(gameLanguage.rawValue)"
        if signature != lastOptionsSettings {
            lastOptionsSettings = signature
            lastOptions = []
            gameTextStability.reset()
            lastOptionsRead = nil
            optionsRecentCost = 0
            optionsImageGate.reset()
            optionsRetryAt = nil
        }
        let imageTime = ProcessInfo.processInfo.systemUptime
        let interval = mode.cadence.gameTextInterval(asText: asText, recentCost: optionsRecentCost)
        if let lastOptionsRead, imageTime - lastOptionsRead < interval { return nil }
        lastOptionsRead = imageTime

        let roi = CGRect(x: area.minX, y: 1 - area.maxY,
                         width: area.width, height: area.height)
        let fingerprint = OCRFrameFingerprint.read(buffer, visionROI: roi)
        guard gameTextStability.needsConfirmation || optionsImageGate.shouldRead(fingerprint, now: imageTime) else { return nil }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = [gameLanguage.visionIdentifier]
        request.usesLanguageCorrection = true
        request.regionOfInterest = roi
        let workStarted = ProcessInfo.processInfo.systemUptime
        defer {
            let cost = ProcessInfo.processInfo.systemUptime - workStarted
            optionsRecentCost = optionsRecentCost == 0 ? cost : 0.75 * optionsRecentCost + 0.25 * cost
        }
        do {
            try VNImageRequestHandler(cvPixelBuffer: buffer, options: [:]).perform([request])
            optionsImageGate.recordSuccessfulRead(fingerprint, now: imageTime)
            let lines = (request.results ?? [])
                .sorted { $0.boundingBox.midY > $1.boundingBox.midY }
                .compactMap { $0.topCandidates(1).first?.string
                    .trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { $0.filter { $0.isLetter }.count >= 3 }

            let output = asText
                ? (lines.isEmpty ? [] : [lines.joined(separator: " ")])
                : Array(lines.prefix(6))
            guard gameTextStability.permits(output, previous: lastOptions, now: imageTime) else { return nil }
            let retryDue = optionsRetryAt.map { imageTime >= $0 } ?? false
            guard output != lastOptions || retryDue else { return nil }
            optionsRetryAt = nil
            lastOptions = output
            return output
        } catch {
            return nil
        }
    }

    /// Conservative, optional filter for many uppercase short menu labels.
    /// Not perfect: all-uppercase in-game dialogue can be excluded; users can
    /// switch the filter off without affecting the Remote Play session.
    private func looksLikeMenu(_ text: String, conservative: Bool) -> Bool {
        let words = text.split(whereSeparator: { $0.isWhitespace })
        guard !words.isEmpty else { return true }
        let upperWords = words.filter { word in
            let letters = word.filter { $0.isLetter }
            return letters.count >= 3 && letters.allSatisfy { $0.isUppercase }
        }.count
        let punctuation = text.contains { ".!?;:".contains($0) }
        if punctuation { return false }

        if conservative {
            // A short all-caps greeting ("HEY", "HELP") is not automatically a menu.
            let normalized = text.uppercased()
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let menus: Set<String> = [
                "PAUSE", "OPTIONS", "SETTINGS", "NEW GAME", "LOAD GAME",
                "SAVE GAME", "INVENTORY", "QUEST LOG", "MAP", "EXIT",
                "CONTINUE", "RESTART", "GAME OVER", "OBJECTIVES", "SKIP"
            ]
            return menus.contains(normalized) || (words.count >= 4 && upperWords >= 4)
        }
        if words.count <= 3 && upperWords >= 1 { return true }
        return words.count >= 4 && upperWords >= 3
    }

    func translate(_ original: String,
                   from source: GLGameLanguage = .english,
                   to target: GLTargetLanguage = .czech) async -> Result<String, GLTranslationFailure> {
        guard !original.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .failure(.emptyInput)
        }
        if Task.isCancelled { return .failure(.cancelled) }
        if source.rawValue == target.rawValue { return .success(original) }
        // Cache hits do not consume a provider probe. Prefer Google results.
        let cacheRevision = await translationCache.revision()
        let engines = ["google-gtx-v1", "mymemory-v1"]
        for engine in engines {
            if let cached = await translationCache.lookup(engine: engine, source: source.rawValue,
                target: target.rawValue, original: original) { return .success(cached) }
        }
        var lastFailure: GLTranslationFailure = .providerUnavailable
        for engine in engines {
            if Task.isCancelled { return .failure(.cancelled) }
            // MyMemory has a small per-query size limit; do not truncate text.
            if engine == "mymemory-v1" && original.utf8.count > 500 { continue }
            let now = ProcessInfo.processInfo.systemUptime
            guard let ticket = providerBreakers[engine, default: TranslationCircuitBreaker()]
                .acquire(now: now) else { continue }
            let outcome = await requestTranslation(engine: engine, original: original,
                source: source.rawValue, target: target.rawValue)
            let success: Bool?
            switch outcome {
            case .success: success = true
            case .failure(.cancelled), .failure(.invalidRequest), .failure(.emptyInput): success = nil
            case .failure: success = false
            }
            providerBreakers[engine, default: TranslationCircuitBreaker()]
                .complete(ticket, success: success, now: ProcessInfo.processInfo.systemUptime)
            switch outcome {
            case .success(let translated):
                await translationCache.remember(engine: engine, source: source.rawValue,
                    target: target.rawValue, original: original, result: translated, revision: cacheRevision)
                return outcome
            case .failure(.cancelled): return outcome
            case .failure(let failure): lastFailure = failure
            }
        }
        return .failure(lastFailure)
    }

    private func requestTranslation(engine: String, original: String,
                                    source: String, target: String) async -> Result<String, GLTranslationFailure> {
        let google = engine == "google-gtx-v1"
        var components = URLComponents(string: google
            ? "https://translate.googleapis.com/translate_a/single"
            : "https://api.mymemory.translated.net/get")
        components?.queryItems = google ? [
            URLQueryItem(name: "client", value: "gtx"),
            URLQueryItem(name: "sl", value: source),
            URLQueryItem(name: "tl", value: target),
            URLQueryItem(name: "dt", value: "t"),
            URLQueryItem(name: "q", value: original)
        ] : [
            URLQueryItem(name: "langpair", value: source + "|" + target),
            URLQueryItem(name: "q", value: original)
        ]
        guard let url = components?.url else { return .failure(.invalidRequest) }
        do {
            var req = URLRequest(url: url)
            req.timeoutInterval = google ? 5 : 3
            let (data, response) = try await URLSession.shared.data(for: req)
            if Task.isCancelled { return .failure(.cancelled) }
            let status = (response as? HTTPURLResponse)?.statusCode
            return google ? GLTranslationOutcome.googleResponse(data, statusCode: status)
                : GLTranslationOutcome.myMemoryResponse(data, statusCode: status)
        } catch {
            return .failure(GLTranslationFailure.classify(error))
        }
    }
}
