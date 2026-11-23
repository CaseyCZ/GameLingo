import SwiftUI
import UIKit
import SafariServices
import Combine
import CoreVideo

@main
struct GameLingoIOSApp: App {
    @StateObject private var model = CaptionAppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            GameLingoHome(devices: model.devices)
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .onAppear { model.xbox.resumeLoginIfNeeded() }
                .onChange(of: scenePhase) { _, phase in
                    model.xbox.setApplicationActive(phase == .active)
                }
        }
    }
}

@MainActor
final class CaptionAppModel: ObservableObject {
    @Published var status = "Připraveno"
    @Published var lastEnglish = ""
    @Published var lastCzech = ""
    @Published var frameCount = 0
    @Published var isRunning = false
    @Published var ocrMode: OCRMode = OCRMode(
        rawValue: UserDefaults.standard.string(forKey: "ocrMode") ?? ""
    ) ?? .classic {
        didSet { UserDefaults.standard.set(ocrMode.rawValue, forKey: "ocrMode") }
    }
    @Published var expandOnEmpty = UserDefaults.standard.bool(forKey: "expandOnEmpty") {
        didSet { UserDefaults.standard.set(expandOnEmpty, forKey: "expandOnEmpty") }
    }
    @Published var subtitleDuration: Double =
        (UserDefaults.standard.object(forKey: "subtitleDuration") as? Double) ?? 6 {
        didSet { UserDefaults.standard.set(subtitleDuration, forKey: "subtitleDuration") }
    }
    @Published var skipOldTranslations = UserDefaults.standard.bool(forKey: "skipOldTranslations") {
        didSet { UserDefaults.standard.set(skipOldTranslations, forKey: "skipOldTranslations") }
    }
    @Published var maxTranslationAge: Double =
        (UserDefaults.standard.object(forKey: "maxTranslationAge") as? Double) ?? 5 {
        didSet { UserDefaults.standard.set(maxTranslationAge, forKey: "maxTranslationAge") }
    }
    @Published var subtitleRegion: SubtitleRegion = SubtitleRegion(
        rawValue: UserDefaults.standard.string(forKey: "subtitleRegion") ?? ""
    ) ?? .center {
        didSet { UserDefaults.standard.set(subtitleRegion.rawValue, forKey: "subtitleRegion") }
    }
    @Published var filterMenuText = (UserDefaults.standard.object(forKey: "filterMenuText") as? Bool) ?? true {
        didSet { UserDefaults.standard.set(filterMenuText, forKey: "filterMenuText") }
    }
    // Android-compatible manual OCR rectangle (normalized coordinates with
    // top-left origin). The default matches OblastTitulku.VYCHOZI.
    @Published var manualOCR = UserDefaults.standard.bool(forKey: "manualOCR") {
        didSet { UserDefaults.standard.set(manualOCR, forKey: "manualOCR") }
    }
    @Published var manualOCRLocked = UserDefaults.standard.bool(forKey: "manualOCRLocked") {
        didSet { UserDefaults.standard.set(manualOCRLocked, forKey: "manualOCRLocked") }
    }
    @Published var manualOCRBox: CGRect = {
        let fallback = CGRect(x: 0.10, y: 0.72, width: 0.80, height: 0.25)
        guard let data = UserDefaults.standard.data(forKey: "manualOCRBox"),
              let rect = try? JSONDecoder().decode(CGRect.self, from: data),
              rect.width >= 0.1, rect.height >= 0.04,
              rect.minX >= 0, rect.minY >= 0, rect.maxX <= 1, rect.maxY <= 1
        else { return fallback }
        return rect
    }() {
        didSet {
            if let data = try? JSONEncoder().encode(manualOCRBox) {
                UserDefaults.standard.set(data, forKey: "manualOCRBox")
            }
        }
    }
    @Published var outputX: Double = (UserDefaults.standard.object(forKey: "outputX") as? Double) ?? 0.5 {
        didSet { UserDefaults.standard.set(outputX, forKey: "outputX") }
    }
    @Published var outputBottom: Double = (UserDefaults.standard.object(forKey: "outputBottom") as? Double) ?? 0.93 {
        didSet { UserDefaults.standard.set(outputBottom, forKey: "outputBottom") }
    }
    @Published var outputWidth: Double = (UserDefaults.standard.object(forKey: "outputWidth") as? Double) ?? 0.62 {
        didSet { UserDefaults.standard.set(outputWidth, forKey: "outputWidth") }
    }
    @Published var outputFont: Double = (UserDefaults.standard.object(forKey: "outputFont") as? Double) ?? 1.0 {
        didSet { UserDefaults.standard.set(outputFont, forKey: "outputFont") }
    }
    @Published var outputLocked = UserDefaults.standard.bool(forKey: "outputLocked") {
        didSet { UserDefaults.standard.set(outputLocked, forKey: "outputLocked") }
    }
    // Independent yellow answer-choice region, equivalent to Android's Volby.
    // Disabled by default: dialogue OCR remains unchanged until user enables it.
    @Published var choicesEnabled = UserDefaults.standard.bool(forKey: "choicesEnabled") {
        didSet {
            UserDefaults.standard.set(choicesEnabled, forKey: "choicesEnabled")
            Task { await pipeline.resetOptions() }
            if !choicesEnabled {
                lastTranslatedChoiceItems = []
                lastChoicesSentAt = nil
                tv.sendChoices([], area: choicesArea, outputArea: choicesOutputArea)
            }
        }
    }
    @Published var choicesTextMode = UserDefaults.standard.bool(forKey: "choicesTextMode") {
        didSet { UserDefaults.standard.set(choicesTextMode, forKey: "choicesTextMode") }
    }
    @Published var choicesLocked = UserDefaults.standard.bool(forKey: "choicesLocked") {
        didSet { UserDefaults.standard.set(choicesLocked, forKey: "choicesLocked") }
    }
    @Published var choicesArea: CGRect = {
        let fallback = CGRect(x: 0.55, y: 0.45, width: 0.40, height: 0.35)
        guard let data = UserDefaults.standard.data(forKey: "choicesArea"),
              let rect = try? JSONDecoder().decode(CGRect.self, from: data),
              rect.width >= 0.1, rect.height >= 0.05,
              rect.minX >= 0, rect.minY >= 0,
              rect.maxX <= 1, rect.maxY <= 1
        else { return fallback }
        return rect
    }() {
        didSet {
            if let data = try? JSONEncoder().encode(choicesArea) {
                UserDefaults.standard.set(data, forKey: "choicesArea")
            }
        }
    }
    /// Output rectangle for text recognized in the yellow region.
    /// This is independent of the green ordinary-dialogue output.
    @Published var choicesOutputArea: CGRect = {
        let fallback = CGRect(x: 0.07, y: 0.43, width: 0.43, height: 0.17)
        guard let data = UserDefaults.standard.data(forKey: "choicesOutputArea"),
              let rect = try? JSONDecoder().decode(CGRect.self, from: data),
              rect.width >= 0.10, rect.height >= 0.05,
              rect.minX >= 0, rect.minY >= 0,
              rect.maxX <= 1, rect.maxY <= 1
        else { return fallback }
        return rect
    }() {
        didSet {
            if let data = try? JSONEncoder().encode(choicesOutputArea) {
                UserDefaults.standard.set(data, forKey: "choicesOutputArea")
            }
        }
    }
    /// Independent font scale for Czech translations of the yellow game-text area.
    /// Normal dialogue subtitles continue using outputFont.
    @Published var choicesOutputFont: Double = {
        let saved = UserDefaults.standard.object(forKey: "choicesOutputFont") as? Double ?? 1.0
        return saved.isFinite && (0.6...2.0).contains(saved) ? saved : 1.0
    }() {
        didSet { UserDefaults.standard.set(choicesOutputFont, forKey: "choicesOutputFont") }
    }
    @Published var choicesOutputLocked = UserDefaults.standard.bool(forKey: "choicesOutputLocked") {
        didSet { UserDefaults.standard.set(choicesOutputLocked, forKey: "choicesOutputLocked") }
    }

    @Published var lastChoices = ""
    @Published var logLines: [String] = []
    @Published var gameProfiles: [GameSubtitleProfile] = {
        guard let data = UserDefaults.standard.data(forKey: "gameProfiles"),
              let saved = try? JSONDecoder().decode([GameSubtitleProfile].self, from: data)
        else { return [] }
        return saved
    }() {
        didSet {
            if let data = try? JSONEncoder().encode(gameProfiles) {
                UserDefaults.standard.set(data, forKey: "gameProfiles")
            }
        }
    }

    let tv = PlayCaptionTV()
    let xbox = XboxAuthorization()
    let playStation = GameLingoPlayStation()
    let devices = GameLingoDevices()

    @Published var phoneSubtitle = ""
    private var psStatusListener: AnyCancellable?
    private let pipeline = CaptionPipeline()
    private var remotePlay: XboxRemotePlay?
    private var processingChoices = false
    private var processingOCR = false
    private var sessionRevision = 0
    // Cache only the translation already visible on TV. Moving the orange
    // rectangle must not trigger a fresh OCR/translation or show demo text.
    private var lastTranslatedChoiceItems: [String] = []
    private var lastTranslatedChoiceIsText = false
    private var lastChoicesSentAt: Date?

    init() {
        tv.onPairingData = { [weak self] fields in
            guard let self else { return }
            if self.xbox.importPairedXbox(fields) {
                self.write("Xbox přihlášení převzato z TV.")
            } else {
                self.write("Televize spárována; Microsoft účet přihlas zvlášť.")
            }
        }
    }

    func saveGameProfile(named rawName: String) {
        let name = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        let existingIndex = gameProfiles.firstIndex {
            $0.name.localizedCaseInsensitiveCompare(name) == .orderedSame
        }
        let profile = GameSubtitleProfile(
            id: existingIndex.map { gameProfiles[$0].id } ?? UUID(),
            name: name,
            region: subtitleRegion.rawValue,
            manualOCR: manualOCR,
            manualOCRLocked: manualOCRLocked,
            manualOCRBox: manualOCRBox,
            filterMenuText: filterMenuText,
            ocrMode: ocrMode.rawValue,
            expandOnEmpty: expandOnEmpty,
            outputX: outputX,
            outputBottom: outputBottom,
            outputWidth: outputWidth,
            outputFont: outputFont,
            outputLocked: outputLocked,
            choicesEnabled: choicesEnabled,
            choicesTextMode: choicesTextMode,
            choicesArea: choicesArea,
            choicesLocked: choicesLocked,
            choicesOutputArea: choicesOutputArea,
            choicesOutputLocked: choicesOutputLocked,
            choicesOutputFont: choicesOutputFont,
            subtitleDuration: subtitleDuration,
            skipOldTranslations: skipOldTranslations,
            maxTranslationAge: maxTranslationAge
        )
        if let existingIndex {
            gameProfiles[existingIndex] = profile
        } else {
            gameProfiles.append(profile)
        }
        write("Profil uložen: \(name)")
    }

    func applyGameProfile(_ profile: GameSubtitleProfile) {
        guard !isRunning else { return }
        subtitleRegion = SubtitleRegion(rawValue: profile.region) ?? .center
        manualOCR = profile.manualOCR
        manualOCRLocked = profile.manualOCRLocked
        manualOCRBox = profile.manualOCRBox
        filterMenuText = profile.filterMenuText
        ocrMode = OCRMode(rawValue: profile.ocrMode) ?? .classic
        expandOnEmpty = profile.expandOnEmpty
        outputX = profile.outputX
        outputBottom = profile.outputBottom
        outputWidth = profile.outputWidth
        outputFont = profile.outputFont
        outputLocked = profile.outputLocked
        choicesArea = profile.choicesArea
        choicesTextMode = profile.choicesTextMode
        choicesLocked = profile.choicesLocked
        choicesEnabled = profile.choicesEnabled
        if let rect = profile.choicesOutputArea { choicesOutputArea = rect }
        if let locked = profile.choicesOutputLocked { choicesOutputLocked = locked }
        choicesOutputFont = profile.choicesOutputFont ?? 1.0
        subtitleDuration = profile.subtitleDuration
        skipOldTranslations = profile.skipOldTranslations
        maxTranslationAge = profile.maxTranslationAge
        write("Načten profil: \(profile.name)")
    }

    func write(_ message: String) {
        logLines.append("\(Date().formatted(date: .omitted, time: .standard))  \(message)")
        if logLines.count > 250 { logLines.removeFirst(logLines.count - 250) }
    }

    func start(_ console: XboxConsole) {
        guard !isRunning else { return }
        isRunning = true
        sessionRevision += 1
        let currentSession = sessionRevision
        processingOCR = false
        lastTranslatedChoiceItems = []
        lastChoicesSentAt = nil
        Task { await pipeline.resetDialogue() }
        UIApplication.shared.isIdleTimerDisabled = true
        status = "Navazuji Xbox Remote Play…"
        write("Start Remote Play: \(console.name)")
        let play = XboxRemotePlay(authorization: xbox,
                                  minimumFrameInterval: ocrMode.minimumFrameInterval)
        remotePlay = play
        play.onStatus = { [weak self] status in
            Task { @MainActor in
                guard let self, self.sessionRevision == currentSession else { return }
                self.status = status
                self.write(status)
            }
        }
        play.onFrame = { [weak self] buffer in
            Task { @MainActor in
                await self?.processFrame(buffer, currentSession: currentSession)
            }
        }
        Task {
            do { try await play.connect(console: console) }
            catch {
                self.status = "Remote Play: \(error.localizedDescription)"
                write("CHYBA Remote Play: \(error.localizedDescription)")
                stop()
            }
        }
    }

    /// Both Xbox WebRTC and PS4/PS5 Chiaki video feed these same OCR and
    /// translation lanes. Source-specific networking is kept outside.
    private func processFrame(_ buffer: CVPixelBuffer, currentSession: Int) async {
        guard isRunning, sessionRevision == currentSession else { return }
        self.frameCount += 1

                // Choices use an independent region and may be translated while
                // standard captions continue; don't block dialogue reading.
                if self.choicesEnabled && !self.processingChoices {
                    self.processingChoices = true
                    let area = self.choicesArea
                    let asText = self.choicesTextMode
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        defer { self.processingChoices = false }
                        guard let englishChoices = await self.pipeline.recognizeOptions(
                            buffer, area: area, asText: asText
                        ) else { return }
                        var translated = [String]()
                        for option in englishChoices {
                            let item = await self.pipeline.translate(option)
                            guard self.isRunning, self.choicesEnabled else { return }
                            translated.append(item)
                        }
                        guard self.isRunning, self.choicesEnabled,
                              self.choicesArea == area,
                              self.choicesTextMode == asText else { return }
                        self.lastChoices = translated.joined(separator: " · ")
                        self.lastTranslatedChoiceItems = translated
                        self.lastTranslatedChoiceIsText = asText
                        self.lastChoicesSentAt = Date()
                        if self.devices.subtitleOutput == .googleTV {
                            self.devices.prepareTV(self.tv)
                            self.tv.sendChoices(translated, area: area, asText: asText,
                                                outputArea: self.choicesOutputArea,
                                                outputFont: self.choicesOutputFont)
                        }
                        self.write("Volby: \(translated.count) rozpoznaných položek")
                    }
                }

                // Keep recognition independent of network translation: a slow
                // request must not block OCR of the next short street dialogue.
                if !self.processingOCR {
                    self.processingOCR = true
                    let reading = await self.pipeline.recognize(
                        buffer, region: self.subtitleRegion,
                        manualArea: self.manualOCR ? self.manualOCRBox : nil,
                        filterMenus: self.filterMenuText,
                        mode: self.ocrMode,
                        expandOnEmpty: self.expandOnEmpty
                    )
                    self.processingOCR = false
                    if let reading, self.isRunning,
                       self.sessionRevision == currentSession {
                        self.lastEnglish = reading.english
                        self.write("OCR: \(reading.english)")
                        Task { @MainActor [weak self] in
                            guard let self else { return }
                            let czech = await self.pipeline.translate(reading.english)
                            let isLatest = await self.pipeline.isCurrent(revision: reading.revision)
                            guard self.isRunning,
                                  self.sessionRevision == currentSession,
                                  isLatest else { return }
                            if self.skipOldTranslations &&
                               Date().timeIntervalSince(reading.capturedAt) > self.maxTranslationAge {
                                self.write("Příliš starý překlad byl přeskočen.")
                                return
                            }
                            self.lastCzech = czech
                            if self.devices.subtitleOutput == .phone {
                                self.phoneSubtitle = czech
                            } else {
                                self.devices.prepareTV(self.tv)
                                self.tv.sendSubtitle(czech,
                                                     duration: Int(self.subtitleDuration * 1000))
                            }
                            self.write("CZ: \(czech)")
                        }
                    }
                }
    }

    /// Actual native PlayStation Remote Play, not just a console entry.
    func start(_ console: RegisteredConsole) {
        guard !isRunning else { return }
        isRunning = true
        sessionRevision += 1
        let currentSession = sessionRevision
        processingOCR = false
        processingChoices = false
        lastTranslatedChoiceItems = []
        lastChoicesSentAt = nil
        phoneSubtitle = ""
        Task { await pipeline.resetDialogue() }
        UIApplication.shared.isIdleTimerDisabled = true
        status = "Připojuji PlayStation…"
        write("PlayStation Remote Play: " + console.nickname)

        psStatusListener?.cancel()
        psStatusListener = playStation.$status.sink { [weak self] value in
            Task { @MainActor in
                guard let self, self.sessionRevision == currentSession,
                      self.isRunning else { return }
                self.status = value
            }
        }
        playStation.start(console: console, onFrame: { [weak self] frame in
            Task { @MainActor in
                await self?.processFrame(frame, currentSession: currentSession)
            }
        }, onEnded: { [weak self] reason in
            guard let self, self.isRunning,
                  self.sessionRevision == currentSession else { return }
            self.stop()
            self.status = "PlayStation: " + reason
            self.write(self.status)
        })
    }

    /// Move the already-translated game text without altering the current OCR
    /// input, starting another translation or placing a sample over the game.
    @discardableResult
    func relocateActiveChoices() -> Bool {
        // Moving the TV overlay must never transmit text while phone output
        // is selected, even if a previous TV session is still paired.
        guard devices.subtitleOutput == .googleTV,
              devices.chosenTelevision != nil,
              isRunning, choicesEnabled,
              !lastTranslatedChoiceItems.isEmpty,
              let sentAt = lastChoicesSentAt,
              Date().timeIntervalSince(sentAt) <= (lastTranslatedChoiceIsText ? 300 : 45)
        else { return false }
        devices.prepareTV(tv)
        tv.sendChoices(lastTranslatedChoiceItems, area: choicesArea,
                       asText: lastTranslatedChoiceIsText,
                       outputArea: choicesOutputArea,
                       outputFont: choicesOutputFont)
        return true
    }

    func stop() {
        sessionRevision += 1
        processingOCR = false
        remotePlay?.stop()
        remotePlay = nil
        playStation.stop()
        psStatusListener?.cancel()
        psStatusListener = nil
        phoneSubtitle = ""
        isRunning = false
        UIApplication.shared.isIdleTimerDisabled = false
        tv.sendSubtitle("", duration: 0)
        tv.sendChoices([], area: choicesArea, outputArea: choicesOutputArea)
        lastTranslatedChoiceItems = []
        lastChoicesSentAt = nil
        lastChoices = ""
        status = "Zastaveno"
    }

    func shareLogs() -> String {
        (["GameLingo iOS 0.5.0", "Stav: \(status)"] + logLines).joined(separator: "\n")
    }
}

// CaseyCZ visual language: midnight navy, white and sky-blue.
// Network, OCR and console logic remain in the tested underlying components.
enum GameLingoTheme {
    static let background = Color(red: 7/255, green: 11/255, blue: 20/255)
    static let card = Color(red: 17/255, green: 24/255, blue: 39/255)
    static let cardAlt = Color(red: 23/255, green: 32/255, blue: 51/255)
    static let border = Color(red: 42/255, green: 56/255, blue: 80/255)
    static let accent = Color(red: 56/255, green: 189/255, blue: 248/255)
    static let action = Color(red: 2/255, green: 132/255, blue: 199/255)
    static let secondary = Color(red: 148/255, green: 163/255, blue: 184/255)
    static let success = Color(red: 134/255, green: 239/255, blue: 172/255)
}

struct ContentView: View {
    @EnvironmentObject var model: CaptionAppModel
    @State private var showSettings = false
    @State private var showRegions = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    VStack(alignment: .leading, spacing: 13) {
                        HStack {
                            Label("Překlad hry", systemImage: "waveform.badge.mic")
                                .font(.headline)
                            Spacer()
                            Circle()
                                .fill(model.isRunning ? GameLingoTheme.success : GameLingoTheme.secondary)
                                .frame(width: 9, height: 9)
                            Text(model.isRunning ? "Probíhá" : "Připraveno")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(model.isRunning ? GameLingoTheme.success : GameLingoTheme.secondary)
                        }
                        Text(model.isRunning ? model.status : "Připoj konzoli a začni překládat dialogy i herní texty.")
                            .font(.subheadline)
                            .foregroundStyle(GameLingoTheme.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                        NavigationLink {
                            xboxDetails
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "play.fill")
                                Text(model.isRunning ? "Ovládat překlad" : "Pokračovat k Xboxu")
                                Spacer(minLength: 0)
                                Image(systemName: "arrow.right")
                            }
                            .font(.headline)
                            .foregroundStyle(.white)
                            .padding(15)
                            .background(GameLingoTheme.action,
                                        in: RoundedRectangle(cornerRadius: 13))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(18)
                    .background(GameLingoTheme.card,
                                in: RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(GameLingoTheme.border, lineWidth: 1))

                    Text("Moje zařízení")
                        .font(.headline)
                        .padding(.top, 2)
                    HStack(spacing: 12) {
                        NavigationLink { xboxDetails } label: {
                            deviceTile(symbol: "gamecontroller.fill", title: "Xbox",
                                       status: model.xbox.signedIn ? "Přihlášeno" : "Připojit",
                                       connected: model.xbox.signedIn)
                        }
                        NavigationLink { tvDetails } label: {
                            deviceTile(symbol: "tv.fill", title: "Google TV",
                                       status: model.tv.receiverConnected ? "Připojeno" : "Spárovat",
                                       connected: model.tv.receiverConnected)
                        }
                    }
                    .buttonStyle(.plain)

                    Text("Další platformy")
                        .font(.headline)
                        .padding(.top, 3)
                    HStack(spacing: 12) {
                        pendingTile(symbol: "gamecontroller", title: "PlayStation",
                                    detail: "iOS – připravujeme")
                        pendingTile(symbol: "desktopcomputer", title: "PC",
                                    detail: "Ve vývoji")
                    }

                    Text("Rychlé nastavení")
                        .font(.headline)
                        .padding(.top, 2)
                    HStack(spacing: 12) {
                        Button { showRegions = true } label: {
                            quickTile(symbol: "rectangle.dashed", label: "Oblasti")
                        }
                        Button { showSettings = true } label: {
                            quickTile(symbol: "slider.horizontal.3", label: "Nastavení")
                        }
                    }
                    .buttonStyle(.plain)
                    if !model.lastCzech.isEmpty {
                        VStack(alignment: .leading, spacing: 7) {
                            Text("Poslední překlad")
                                .font(.caption)
                                .foregroundStyle(GameLingoTheme.secondary)
                            Text(model.lastCzech)
                                .font(.body.weight(.medium))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(GameLingoTheme.card,
                                    in: RoundedRectangle(cornerRadius: 16))
                    }
                    Text("GameLingo · CaseyCZ · testovací verze 0.3.0")
                        .font(.caption2)
                        .foregroundStyle(GameLingoTheme.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 18)
            }
            .background(GameLingoTheme.background.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showSettings = true } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Nastavení")
                }
            }
            .sheet(isPresented: $showSettings) {
                PlayCaptionSettingsView(model: model)
            }
            .sheet(isPresented: $showRegions) {
                SubtitleSettingsView(model: model)
            }
        }
        .tint(GameLingoTheme.accent)
    }

    private var header: some View {
        HStack(spacing: 13) {
            Image(systemName: "gamecontroller.fill")
                .font(.system(size: 25, weight: .semibold))
                .foregroundStyle(GameLingoTheme.accent)
                .frame(width: 54, height: 54)
                .background(GameLingoTheme.cardAlt,
                            in: RoundedRectangle(cornerRadius: 17))
            VStack(alignment: .leading, spacing: 3) {
                Text("GameLingo")
                    .font(.system(size: 29, weight: .bold, design: .rounded))
                Text("Hraj. Rozuměj.")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(GameLingoTheme.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.bottom, 5)
    }

    private var xboxDetails: some View {
        ScrollView {
            VStack(spacing: 16) {
                PlayCaptionSection(title: "Účet Microsoft", symbol: "person.crop.circle",
                                   subtitle: "Přihlas se ke svému Xbox účtu.") {
                    XboxAccountView(auth: model.xbox)
                }
                PlayCaptionSection(title: "Vybrat konzoli", symbol: "gamecontroller.fill",
                                   subtitle: "Pokud máš více Xboxů, vyber ten správný.") {
                    XboxRemotePlayView(auth: model.xbox)
                }
            }
            .padding(16)
        }
        .background(GameLingoTheme.background.ignoresSafeArea())
        .navigationTitle("Xbox")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var tvDetails: some View {
        ScrollView {
            PlayCaptionSection(title: "Google TV / Android TV",
                               symbol: "tv.fill",
                               subtitle: "Párování a zkušební český titulek.") {
                TVSelectionView(tv: model.tv)
            }
            .padding(16)
        }
        .background(GameLingoTheme.background.ignoresSafeArea())
        .navigationTitle("Google TV")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func deviceTile(symbol: String, title: String,
                            status: String, connected: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(GameLingoTheme.accent)
            Text(title)
                .font(.headline)
                .foregroundStyle(.white)
            HStack(spacing: 6) {
                Circle()
                    .fill(connected ? GameLingoTheme.success : GameLingoTheme.secondary)
                    .frame(width: 7, height: 7)
                Text(status)
                    .font(.caption)
                    .foregroundStyle(connected ? GameLingoTheme.success : GameLingoTheme.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(GameLingoTheme.card,
                    in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .strokeBorder(GameLingoTheme.border, lineWidth: 1))
    }

    private func pendingTile(symbol: String, title: String, detail: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(GameLingoTheme.secondary)
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
            Text(detail)
                .font(.caption2)
                .foregroundStyle(GameLingoTheme.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(GameLingoTheme.card,
                    in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .strokeBorder(GameLingoTheme.border, lineWidth: 1))
    }

    private func quickTile(symbol: String, label: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: symbol).foregroundStyle(GameLingoTheme.accent)
            Text(label).foregroundStyle(.white)
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").foregroundStyle(GameLingoTheme.secondary)
        }
        .font(.subheadline.weight(.medium))
        .padding(14)
        .background(GameLingoTheme.cardAlt,
                    in: RoundedRectangle(cornerRadius: 14))
    }
}

private struct PlayCaptionSection<Content: View>: View {
    let title: String
    let symbol: String
    let subtitle: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: symbol)
                    .font(.system(size: 21, weight: .semibold))
                    .foregroundStyle(GameLingoTheme.accent)
                    .frame(width: 43, height: 43)
                    .background(Color.blue.opacity(0.15),
                                in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.title3.bold())
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(GameLingoTheme.card,
                    in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20)
            .strokeBorder(GameLingoTheme.border, lineWidth: 1))
    }
}

/// Observe XboxAuthorization directly: the parent model does not forward its
/// objectWillChange notifications, so console discovery otherwise updates
/// the account card but leaves this section stuck at "Žádná konzole".
// Real Xbox console choice: no automatic selection when multiple devices exist.
struct XboxRemotePlayView: View {
    @EnvironmentObject var model: CaptionAppModel
    @ObservedObject var auth: XboxAuthorization
    @AppStorage("selectedXboxServerID") private var selectedXboxServerID = ""
    @State private var showDetails = false

    private var selectedConsole: XboxConsole? {
        auth.consoles.first(where: { $0.id == selectedXboxServerID })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            GLCard {
                GLHeader(title: "Konzole", help:
                    "Po přihlášení načti své Xboxy a zvol ten, ze kterého chceš číst obraz hry. Pokud máš více konzolí, GameLingo žádnou automaticky nevybere. Na Xboxu musí být povolené vzdálené funkce.")
                HStack {
                    Text("\(auth.consoles.count) dostupných")
                        .foregroundStyle(GLStyle.muted)
                    Spacer()
                    Button {
                        Task { await auth.refreshConsoles() }
                    } label: { Label("Obnovit", systemImage: "arrow.clockwise") }
                        .disabled(!auth.signedIn || model.isRunning)
                }
                .font(.subheadline)
                Menu {
                    if auth.consoles.isEmpty { Text("Žádný Xbox") }
                    ForEach(auth.consoles) { console in
                        Button {
                            selectedXboxServerID = console.id
                        } label: {
                            if selectedConsole?.id == console.id {
                                Label(console.name, systemImage: "checkmark.circle.fill")
                            } else {
                                Text(console.name)
                            }
                        }
                    }
                } label: {
                    HStack {
                        Image(systemName: "gamecontroller.fill")
                            .foregroundStyle(GLStyle.blue)
                        Text(selectedConsole?.name ?? "Vybrat Xbox")
                            .font(.headline)
                        Spacer()
                        Image(systemName: "chevron.up.chevron.down")
                            .foregroundStyle(GLStyle.muted)
                    }
                    .foregroundStyle(.white)
                    .padding(14)
                    .background(GLStyle.surfaceAlt, in: RoundedRectangle(cornerRadius: 14))
                }
                .disabled(auth.consoles.isEmpty || model.isRunning)
                if auth.consoles.isEmpty && auth.signedIn {
                    Text("Žádná konzole. Zkus Obnovit.")
                        .font(.caption).foregroundStyle(.orange)
                }
            }
            GLCard {
                GLHeader(title: "Překlad", help:
                    "Během překladu nech iPhone odemčený a aplikaci otevřenou. iOS může spojení při přechodu na pozadí pozastavit.")
                GLPrimaryButton(title: "Spustit překlad", systemImage: "play.fill",
                    disabled: selectedConsole == nil || model.isRunning) {
                    if let selectedConsole { model.start(selectedConsole) }
                }
                if model.isRunning {
                    Button(role: .destructive) { model.stop() } label: {
                        Label("Zastavit překlad", systemImage: "stop.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                Text(model.status)
                    .font(.caption)
                    .foregroundStyle(GLStyle.muted)
                DisclosureGroup("Podrobnosti", isExpanded: $showDetails) {
                    Text("Snímky: \(model.frameCount)")
                    Text("EN: \(model.lastEnglish)")
                    Text("CZ: \(model.lastCzech)")
                }
                .font(.caption)
                .tint(GLStyle.blue)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .task {
            if auth.signedIn && auth.consoles.isEmpty {
                await auth.refreshConsoles()
            }
        }
    }
}

struct TVSelectionView: View {
    @ObservedObject var tv: PlayCaptionTV
    @State private var test = "Ahoj! GameLingo z iPhonu funguje."
    @State private var showDevices = false
    @State private var editIP = false
    @State private var editTest = false

    private var chosenIP: String {
        tv.ip.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    private var chosenDevice: PlayCaptionDevice? {
        tv.discovered.first(where: { $0.ip == chosenIP })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            GLCard {
                GLHeader(title: "Televize", help:
                    "Na Google TV spusť GameLingo TV. iPhone a TV musí být ve stejné domácí síti. Pokud se TV nenajde, můžeš zadat její IP adresu ručně.")
                HStack {
                    Image(systemName: "tv.fill")
                        .foregroundStyle(GLStyle.blue)
                    Text(chosenDevice?.name ?? (chosenIP.isEmpty ? "Nepřipojeno" : "Google TV"))
                        .font(.headline)
                    Spacer()
                    if tv.receiverConnected {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(GLStyle.green)
                    }
                }
                HStack(spacing: 9) {
                    Button {
                        showDevices.toggle()
                        tv.discover()
                    } label: { Label("Vyhledat", systemImage: "magnifyingglass") }
                        .buttonStyle(.borderedProminent)
                    Button("Zadat IP") {
                        showDevices = true
                        editIP.toggle()
                        tv.discover()
                    }
                    .buttonStyle(.bordered)
                }
                .tint(GLStyle.blue)
                if showDevices {
                    if tv.discovered.isEmpty {
                        Text("Zatím žádná TV")
                            .font(.caption).foregroundStyle(GLStyle.muted)
                    }
                    ForEach(tv.discovered) { device in
                        Button {
                            tv.ip = device.ip
                            showDevices = false
                            editIP = false
                        } label: {
                            HStack {
                                Image(systemName: "tv")
                                Text(device.name)
                                Spacer()
                                if chosenIP == device.ip {
                                    Image(systemName: "checkmark")
                                }
                            }
                            .padding(12)
                            .background(GLStyle.surfaceAlt,
                                        in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                    }
                    if editIP {
                        TextField("IP adresa TV", text: $tv.ip)
                            .keyboardType(.numbersAndPunctuation)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textFieldStyle(.roundedBorder)
                    }
                }
            }

            GLCard {
                GLHeader(title: "Párování", help:
                    "Na TV otevři obrazovku pro párování a opiš šest číslic. Po reinstalaci TV aplikace lze stejný iPhone spárovat znovu. Párovací kód není kód účtu Microsoft.")
                HStack(spacing: 8) {
                    Image(systemName: tv.receiverConnected
                          ? "checkmark.circle.fill" : "link")
                        .foregroundStyle(tv.receiverConnected ? GLStyle.green : GLStyle.blue)
                    Text(tv.receiverConnected ? "Připojeno" :
                         (tv.receiverPaired ? "Spárováno" : "Nepřipojeno"))
                        .font(.subheadline.bold())
                    Spacer()
                    if tv.receiverPaired {
                        Button("Zrušit") { tv.forgetReceiver() }
                            .font(.caption)
                    }
                }
                TextField("6místný kód z TV", text: $tv.receiverCode)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: tv.receiverCode) { _, newValue in
                        tv.receiverCode = String(newValue.filter { $0.isNumber }.prefix(6))
                    }
                GLPrimaryButton(title: tv.receiverPaired ? "Spárovat znovu" : "Spárovat",
                                systemImage: "link",
                                disabled: chosenIP.isEmpty || tv.receiverCode.count != 6) {
                    tv.pairReceiver()
                }
                if !tv.receiverStatus.isEmpty {
                    Text(tv.receiverStatus).font(.caption)
                        .foregroundStyle(GLStyle.muted)
                }
            }

            GLCard {
                GLHeader(title: "Zkušební titulek", help:
                    "Zkušební titulek se zobrazí přímo na Google TV, pokud je přijímač spuštěný a telefon má s TV platné spojení.")
                GLPrimaryButton(title: "Zobrazit na TV", systemImage: "play.fill",
                    disabled: chosenIP.isEmpty) {
                    tv.sendSubtitle(test, duration: 8_000, preview: true)
                }
                DisclosureGroup("Vlastní text", isExpanded: $editTest) {
                    TextField("Titulek", text: $test)
                        .textFieldStyle(.roundedBorder)
                }
                .font(.caption)
                .tint(GLStyle.blue)
                if !tv.message.isEmpty {
                    Text(tv.message)
                        .font(.caption)
                        .foregroundStyle(GLStyle.muted)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct MicrosoftSignInSafari: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

struct XboxAccountView: View {
    @ObservedObject var auth: XboxAuthorization
    @State private var showMicrosoftBrowser = false

    var body: some View {
        GLCard {
            GLHeader(title: "Účet Microsoft", help:
                "GameLingo se přihlašuje oficiální stránkou Microsoftu pomocí jednorázového kódu. Heslo do GameLingo nezadáváš. Po schválení se načtou tvoje dostupné Xboxy.")
            if auth.signedIn {
                Label("Přihlášeno", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(GLStyle.green)
                HStack(spacing: 12) {
                    Button("Načíst konzole") {
                        Task { await auth.refreshConsoles() }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Odhlásit") { auth.signOut() }
                        .buttonStyle(.bordered)
                }
                .tint(GLStyle.blue)
            } else if let code = auth.currentCode {
                Text("Kód pro Microsoft")
                    .font(.caption).foregroundStyle(GLStyle.muted)
                Text(code.userCode)
                    .font(.system(size: 29, weight: .bold, design: .monospaced))
                    .textSelection(.enabled)
                HStack(spacing: 9) {
                    Button("Kopírovat") { UIPasteboard.general.string = code.userCode }
                        .buttonStyle(.bordered)
                    Button("Otevřít Microsoft") { showMicrosoftBrowser = true }
                        .buttonStyle(.borderedProminent)
                }
                .tint(GLStyle.blue)
                Button("Ověřit přihlášení") { auth.resumeLoginIfNeeded() }
                    .font(.subheadline)
            } else {
                GLPrimaryButton(title: "Přihlásit se", systemImage: "person.crop.circle") {
                    Task { await auth.beginLogin() }
                }
            }
            if !auth.status.isEmpty {
                Text(auth.status)
                    .font(.caption)
                    .foregroundStyle(GLStyle.muted)
            }
        }
        .sheet(isPresented: $showMicrosoftBrowser, onDismiss: {
            auth.resumeLoginIfNeeded()
        }) {
            if let code = auth.currentCode,
               let url = URL(string: code.verificationURL),
               url.scheme?.lowercased() == "https" {
                MicrosoftSignInSafari(url: url)
            }
        }
    }
}
