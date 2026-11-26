import Foundation
import CoreVideo
import CoreImage
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
    var automaticLayout: AutomaticDialogueLayout? = nil
}

/// OCR of a configurable bottom-center band. Only changed subtitle text is sent.
private final class GLTranslationNoRedirect: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        // OCR text is sensitive even when it is sent as a GET query.
        completionHandler(nil)
    }
}

actor CaptionPipeline {
    private let providerSession: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        return URLSession(configuration: configuration, delegate: GLTranslationNoRedirect(), delegateQueue: nil)
    }()
    private var lastText = ""
    private var dialogueStability = CaptionStability()
    private var gameTextStability = GameTextStability()
    private var lastRead = Date.distantPast
    private var lastSeen = Date.distantPast
    private let translationCache = GLTranslationCache()
    func purgeTranslationCache() async -> Bool { await translationCache.purge() }
    func setTranslationCacheEnabled(_ enabled: Bool) async { await translationCache.setEnabled(enabled) }
    private var providerBreakers: [String: TranslationCircuitBreaker] = [:]
    private var edgeToken: String?
    private var edgeTokenExpires: TimeInterval = 0
    private var lastSettings = ""
    private var emptyReads = 0
    private var revision = 0
    private var dialogueRetryAt: TimeInterval?
    private var optionsRetryAt: TimeInterval?
    private var dialogueImageGate = OCRFrameGate(maximumSkip: 1.5)

    func resetDialogue() {
        automaticLanes.removeAll()
        nextAutomaticZone = 0
        lastAutomaticAttempt = -.infinity
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
                                  asText: Bool, gameLanguage: GLGameLanguage, filterControls: Bool = false, ignoredLines: String = "", minimumHeight: Int = 0) {
        let signature = "\(area)|\(asText)|\(gameLanguage.rawValue)|\(filterControls)|\(ignoredLines)|\(minimumHeight)"
        guard lastOptionsSettings == signature, lastOptions == options,
              !options.isEmpty else { return }
        optionsRetryAt = ProcessInfo.processInfo.systemUptime + 2
    }

    struct AutomaticZoneReading {
        let zone: UUID
        let text: String
        let layout: AutomaticDialogueLayout?
    }
    private struct AutomaticLane {
        var gate = OCRFrameGate(maximumSkip: 1.0)
        var lastAttempt: TimeInterval = -.infinity
    }
    private var automaticLanes: [UUID: AutomaticLane] = [:]
    private var nextAutomaticZone = 0
    private var lastAutomaticAttempt: TimeInterval = -.infinity

    /// One actual crop per invocation, round-robin across enabled regions.
    /// No OCR of pixels outside the selected zone and no unbounded task queue.
    func recognizeAutomaticZone(_ buffer: CVPixelBuffer, zones: [AutomaticOCRZone],
                                gameLanguage: GLGameLanguage) -> AutomaticZoneReading? {
        let enabled = zones.filter { $0.enabled && $0.valid }
        guard !enabled.isEmpty else { return nil }
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastAutomaticAttempt >= 0.10 else { return nil }
        let image = CIImage(cvPixelBuffer: buffer)
        let e = image.extent
        for offset in 0..<enabled.count {
            let index = (nextAutomaticZone + offset) % enabled.count
            let zone = enabled[index]
            let roi = CGRect(x: zone.rect.minX, y: 1-zone.rect.maxY,
                             width: zone.rect.width, height: zone.rect.height)
            var lane = automaticLanes[zone.id] ?? AutomaticLane()
            guard now - lane.lastAttempt >= 0.10 else { continue }
            let fingerprint = OCRFrameFingerprint.read(buffer, visionROI: roi)
            guard lane.gate.shouldRead(fingerprint, now: now) else { continue }
            lane.lastAttempt = now
            lastAutomaticAttempt = now
            nextAutomaticZone = (index + 1) % enabled.count
            automaticLanes[zone.id] = lane
            let crop = CGRect(x: e.minX + e.width * roi.minX, y: e.minY + e.height * roi.minY,
                              width: e.width * roi.width, height: e.height * roi.height)
            let local = image.cropped(to: crop).transformed(by: CGAffineTransform(translationX: -crop.minX, y: -crop.minY))
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.recognitionLanguages = [gameLanguage.visionIdentifier]
            request.usesLanguageCorrection = true
            do {
                try VNImageRequestHandler(ciImage: local, options: [:]).perform([request])
                lane.gate.recordSuccessfulRead(fingerprint, now: now)
                automaticLanes[zone.id] = lane
                let lines: [AutomaticDialogueLayout.Line] = (request.results ?? []).compactMap { item in
                    guard let text = item.topCandidates(1).first?.string.trimmingCharacters(in: .whitespacesAndNewlines),
                          text.filter({ $0.isLetter }).count >= 2,
                          !looksLikeMenu(text, conservative: true),
                          let rect = AutomaticDialogueLayout.fullFrameRect(croppedVision: item.boundingBox, crop: zone.rect)
                    else { return nil }
                    return .init(text: text, rect: rect)
                }
                let selected = AutomaticDialogueLayout.select(lines, centered: zone.centered)
                return .init(zone: zone.id, text: selected?.text ?? "", layout: selected?.layout)
            } catch { return nil } // failed read is not an empty observation
        }
        return nil
    }

    func recognize(_ buffer: CVPixelBuffer,
                   region: SubtitleRegion,
                   manualArea: CGRect?,
                   filterMenus: Bool, ignoredLines: String = "", minimumHeight: Int = 0,
                   mode: OCRMode = .classic,
                   expandOnEmpty: Bool = false,
                   gameLanguage: GLGameLanguage = .english,
                   automaticPlacement: Bool = false) -> RecognizedCaption? {
        let baseROI = automaticPlacement ? CGRect(x: 0, y: 0, width: 1, height: 0.35) : (manualArea.map { CGRect(
            x: $0.minX, y: 1 - $0.maxY, width: $0.width, height: $0.height
        ) } ?? region.rect)
        let settings = "\(baseROI)|\(filterMenus)|\(mode.rawValue)|\(expandOnEmpty)|\(gameLanguage.rawValue)|\(ignoredLines)|\(minimumHeight)|\(automaticPlacement)"
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
        let searchWide = !automaticPlacement && expandOnEmpty && manualArea == nil &&
            mode != .classic && emptyReads >= 3 && emptyReads % 3 == 0
        let roi = searchWide ? SubtitleRegion.wide.rect : baseROI
        let imageTime = ProcessInfo.processInfo.systemUptime
        let fingerprint = OCRFrameFingerprint.read(buffer, visionROI: roi)
        guard dialogueStability.needsConfirmation || dialogueImageGate.shouldRead(fingerprint, now: imageTime) else { return nil }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = [gameLanguage.visionIdentifier]
        request.usesLanguageCorrection = true
        request.regionOfInterest = automaticPlacement ? CGRect(x: 0, y: 0, width: 1, height: 1) : roi
        if minimumHeight > 0 { request.minimumTextHeight = Float(min(5, minimumHeight)) / 100 }

        do {
            if automaticPlacement {
                // Explicit crop makes Vision's normalized result coordinate space unambiguous.
                let image = CIImage(cvPixelBuffer: buffer)
                let e = image.extent
                let crop = CGRect(x: e.minX, y: e.minY, width: e.width, height: e.height * 0.35)
                let local = image.cropped(to: crop).transformed(by: CGAffineTransform(translationX: -crop.minX, y: -crop.minY))
                try VNImageRequestHandler(ciImage: local, options: [:]).perform([request])
            } else {
                try VNImageRequestHandler(cvPixelBuffer: buffer, options: [:]).perform([request])
            }
            dialogueImageGate.recordSuccessfulRead(fingerprint, now: imageTime)
            let ignored = OCRIgnoredLines.entries(ignoredLines)
            let minLetters = mode == .street ? 2 : 3
            var positioned = [AutomaticDialogueLayout.Line]()
            let lines = (request.results ?? []).compactMap { item -> String? in
                guard let candidate = item.topCandidates(1).first else { return nil }
                let line = candidate.string.trimmingCharacters(in: .whitespacesAndNewlines)
                guard line.filter({ $0.isLetter }).count >= minLetters, OCRIgnoredLines.keeps(line, entries: ignored) else { return nil }
                if filterMenus && looksLikeMenu(line, conservative: mode != .classic) {
                    return nil
                }
                if automaticPlacement, let rect = AutomaticDialogueLayout.fullFrameRect(croppedVision: item.boundingBox, crop: AutomaticDialogueLayout.searchArea) {
                    positioned.append(.init(text: line, rect: rect))
                }
                return line
            }
            let automatic = automaticPlacement ? AutomaticDialogueLayout.select(positioned) : nil
            let text = (automaticPlacement ? (automatic?.text ?? "") : lines.joined(separator: " "))
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
                    ocrStartedAt: imageTime, ocrMilliseconds: (ProcessInfo.processInfo.systemUptime - imageTime) * 1000,
                    automaticLayout: automatic?.layout)
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
                          mode: OCRMode = .classic, filterControls: Bool = false, ignoredLines: String = "", minimumHeight: Int = 0) -> [String]? {
        if optionsMode != mode {
            optionsMode = mode
            lastOptionsRead = nil
            optionsRecentCost = 0
        }
        let signature = "\(area)|\(asText)|\(gameLanguage.rawValue)|\(filterControls)|\(ignoredLines)|\(minimumHeight)"
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
        if minimumHeight > 0 { request.minimumTextHeight = Float(min(5, minimumHeight)) / 100 }
        let workStarted = ProcessInfo.processInfo.systemUptime
        defer {
            let cost = ProcessInfo.processInfo.systemUptime - workStarted
            optionsRecentCost = optionsRecentCost == 0 ? cost : 0.75 * optionsRecentCost + 0.25 * cost
        }
        do {
            try VNImageRequestHandler(cvPixelBuffer: buffer, options: [:]).perform([request])
            optionsImageGate.recordSuccessfulRead(fingerprint, now: imageTime)
            let ignored = OCRIgnoredLines.entries(ignoredLines)
            let lines = (request.results ?? [])
                .sorted { $0.boundingBox.midY > $1.boundingBox.midY }
                .compactMap { $0.topCandidates(1).first?.string
                    .trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { YellowOCRFilter.hasReadableText($0) }
                .filter { YellowOCRFilter.keeps($0, enabled: filterControls) && OCRIgnoredLines.keeps($0, entries: ignored) }

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
        let engines = ["google-gtx-v1", "microsoft-edge-v1", "mymemory-v1"]
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
        if engine == "microsoft-edge-v1" {
            return await requestEdge(original: original, source: source, target: target)
        }
        guard engine == "google-gtx-v1" || engine == "mymemory-v1" else { return .failure(.invalidRequest) }
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
        guard let url = components?.url, url.scheme == "https",
              url.host == (google ? "translate.googleapis.com" : "api.mymemory.translated.net"),
              url.user == nil, url.password == nil, url.port == nil else {
            return .failure(.invalidRequest)
        }
        do {
            var req = URLRequest(url: url)
            req.timeoutInterval = google ? 5 : 3
            req.setValue("no-store", forHTTPHeaderField: "Cache-Control")
            req.httpShouldHandleCookies = false
            let (data, response) = try await providerSession.data(for: req)
            if Task.isCancelled { return .failure(.cancelled) }
            let status = (response as? HTTPURLResponse)?.statusCode
            return google ? GLTranslationOutcome.googleResponse(data, statusCode: status)
                : GLTranslationOutcome.myMemoryResponse(data, statusCode: status)
        } catch {
            return .failure(GLTranslationFailure.classify(error))
        }
    }

    // Same secondary provider as Android. No redirects, cookies or disk cache.
    private func requestEdge(original: String, source: String, target: String) async -> Result<String, GLTranslationFailure> {
        do {
            if Task.isCancelled { return .failure(.cancelled) }
            let now = ProcessInfo.processInfo.systemUptime
            let token: String
            if let saved = edgeToken, now < edgeTokenExpires {
                token = saved
            } else {
                var auth = URLRequest(url: URL(string: "https://edge.microsoft.com/translate/auth")!)
                auth.timeoutInterval = 4
                auth.setValue("no-store", forHTTPHeaderField: "Cache-Control")
                auth.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
                auth.httpShouldHandleCookies = false
                let (data, response) = try await providerSession.data(for: auth)
                if Task.isCancelled { return .failure(.cancelled) }
                guard let status = (response as? HTTPURLResponse)?.statusCode else { return .failure(.invalidResponse) }
                guard status == 200 else { return .failure(.httpStatus(status)) }
                guard let value = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
                      !value.isEmpty, value.utf8.count <= 16384,
                      value.utf8.allSatisfy({ (33...126).contains($0) }) else { return .failure(.invalidResponse) }
                token = value
                edgeToken = value
                edgeTokenExpires = ProcessInfo.processInfo.systemUptime + 480
            }
            var endpoint = URLComponents(string: "https://api-edge.cognitive.microsofttranslator.com/translate")!
            endpoint.queryItems = [URLQueryItem(name: "from", value: source),
                                   URLQueryItem(name: "to", value: target),
                                   URLQueryItem(name: "api-version", value: "3.0")]
            guard let url = endpoint.url else { return .failure(.invalidRequest) }
            var request = URLRequest(url: url)
            request.timeoutInterval = 5
            request.httpMethod = "POST"
            request.httpShouldHandleCookies = false
            request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
            request.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
            request.setValue("no-store", forHTTPHeaderField: "Cache-Control")
            request.httpBody = try JSONSerialization.data(withJSONObject: [["Text": original]])
            let (data, response) = try await providerSession.data(for: request)
            if Task.isCancelled { return .failure(.cancelled) }
            let status = (response as? HTTPURLResponse)?.statusCode
            if status == 401 || status == 403 {
                if edgeToken == token { edgeToken = nil; edgeTokenExpires = 0 }
            }
            return GLTranslationOutcome.edgeResponse(data, statusCode: status, target: target)
        } catch {
            return .failure(GLTranslationFailure.classify(error))
        }
    }

}
