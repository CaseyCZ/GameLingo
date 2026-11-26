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
                .environment(\.locale, Locale(identifier: model.appLanguage.rawValue))
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
    // Standard remains the default for existing and new installations.
    @Published private(set) var automaticGameDialogue = UserDefaults.standard.bool(forKey: "gamelingo.automaticMode")
    @Published private(set) var automaticZones = AutomaticOCRZone.load()
    func updateAutomaticZones(_ zones: [AutomaticOCRZone]) {
        guard !isRunning else { return }
        automaticZones = AutomaticOCRZone.validated(zones)
        if let data = try? JSONEncoder().encode(automaticZones) {
            UserDefaults.standard.set(data, forKey: "gamelingo.automaticZones")
        }
        invalidateDialogue()
    }
    struct AutomaticOutput: Identifiable {
        let id: UUID
        let text: String
        let layout: AutomaticDialogueLayout
    }
    @Published private(set) var automaticOutputs: [AutomaticOutput] = []
    private struct AutomaticTrack {
        var candidate = ""
        var confirmations = 0
        var revision = UUID()
        var lastSeen: TimeInterval = 0
        var retryAt: TimeInterval = 0
        var layout: AutomaticDialogueLayout? = nil
    }
    private var automaticTracks: [UUID: AutomaticTrack] = [:]
    private var automaticTasks: [UUID: Task<Void, Never>] = [:]
    private var automaticLastTV: AutomaticOutput?
    private var automaticLastTVAt: TimeInterval = 0
    private var automaticFrameAspect = 16.0 / 9.0
    private var automaticSnapshotKey = ""
    private func clearAutomaticOutputs() {
        automaticTasks.values.forEach { $0.cancel() }
        automaticTasks.removeAll()
        automaticTracks.removeAll()
        automaticOutputs.removeAll()
        automaticLastTV = nil
        automaticLastTVAt = 0
        automaticSnapshotKey = ""
    }
    func setAutomaticGameDialogue(_ enabled: Bool) {
        guard !isRunning else { return }
        automaticGameDialogue = enabled
        UserDefaults.standard.set(enabled, forKey: "gamelingo.automaticMode")
        invalidateDialogue()
        invalidateOptions()
    }
    @Published var status = "Připraveno"
    @Published var lastEnglish = ""
    @Published var lastCzech = ""
    @Published var frameCount = 0
    @Published var isRunning = false
    @Published var appLanguage: GLAppLanguage = GLAppLanguage(
        rawValue: UserDefaults.standard.string(forKey: "gamelingo.appLanguage") ?? ""
    ) ?? GLAppLanguage.systemDefault {
        didSet {
            UserDefaults.standard.set(appLanguage.rawValue, forKey: "gamelingo.appLanguage")
        }
    }
    @Published var gameLanguage: GLGameLanguage = GLGameLanguage(
        rawValue: UserDefaults.standard.string(forKey: "gamelingo.gameLanguage") ?? ""
    ) ?? .english {
        didSet {
            UserDefaults.standard.set(gameLanguage.rawValue, forKey: "gamelingo.gameLanguage")
            if oldValue != gameLanguage { invalidateDialogue(); invalidateOptions() }
        }
    }
    @Published var targetLanguage: GLTargetLanguage = GLTargetLanguage(
        rawValue: UserDefaults.standard.string(forKey: "gamelingo.targetLanguage") ?? ""
    ) ?? GLTargetLanguage.systemDefault {
        didSet {
            UserDefaults.standard.set(targetLanguage.rawValue, forKey: "gamelingo.targetLanguage")
            if oldValue != targetLanguage { invalidateDialogue(); invalidateOptions() }
        }
    }
    @Published var ocrMode: OCRMode = OCRMode(
        rawValue: UserDefaults.standard.string(forKey: "ocrMode") ?? ""
    ) ?? .classic {
        didSet {
            UserDefaults.standard.set(ocrMode.rawValue, forKey: "ocrMode")
            if oldValue != ocrMode { invalidateDialogue(); invalidateOptions() }
        }
    }
    @Published var expandOnEmpty = UserDefaults.standard.bool(forKey: "expandOnEmpty") {
        didSet {
            UserDefaults.standard.set(expandOnEmpty, forKey: "expandOnEmpty")
            if oldValue != expandOnEmpty { invalidateDialogue() }
        }
    }
    @Published var showPreviousDialogue = UserDefaults.standard.bool(forKey: "showPreviousDialogue") {
        didSet {
            UserDefaults.standard.set(showPreviousDialogue, forKey: "showPreviousDialogue")
            if oldValue != showPreviousDialogue { invalidateDialogue() }
        }
    }
    @Published var previousDialogueCount = max(1, min(2, UserDefaults.standard.integer(forKey: "previousDialogueCount"))) {
        didSet {
            UserDefaults.standard.set(previousDialogueCount, forKey: "previousDialogueCount")
            if oldValue != previousDialogueCount { invalidateDialogue() }
        }
    }
    private var dialogueWindow = DialogueWindow()
    @Published var hideOnSourceGone = UserDefaults.standard.bool(forKey: "hideOnSourceGone") {
        didSet {
            UserDefaults.standard.set(hideOnSourceGone, forKey: "hideOnSourceGone")
            if oldValue != hideOnSourceGone { invalidateDialogue() }
        }
    }
    @Published var sourceGoneDelay = min(2, max(0, UserDefaults.standard.integer(forKey: "sourceGoneDelay"))) {
        didSet {
            UserDefaults.standard.set(sourceGoneDelay, forKey: "sourceGoneDelay")
            if oldValue != sourceGoneDelay { invalidateDialogue() }
        }
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
        didSet {
            UserDefaults.standard.set(subtitleRegion.rawValue, forKey: "subtitleRegion")
            if oldValue != subtitleRegion { invalidateDialogue() }
        }
    }
    @Published var filterMenuText = (UserDefaults.standard.object(forKey: "filterMenuText") as? Bool) ?? true {
        didSet {
            UserDefaults.standard.set(filterMenuText, forKey: "filterMenuText")
            if oldValue != filterMenuText { invalidateDialogue() }
        }
    }
    // Android-compatible manual OCR rectangle (normalized coordinates with
    // top-left origin). The default matches OblastTitulku.VYCHOZI.
    @Published var manualOCR = UserDefaults.standard.bool(forKey: "manualOCR") {
        didSet {
            UserDefaults.standard.set(manualOCR, forKey: "manualOCR")
            if oldValue != manualOCR { invalidateDialogue() }
        }
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
            if oldValue != manualOCRBox { invalidateDialogue() }
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
    @Published var phoneFontScale: Double =
        (UserDefaults.standard.object(forKey: "gamelingo.phoneFontScale") as? Double) ?? 1.0 {
        didSet { UserDefaults.standard.set(phoneFontScale, forKey: "gamelingo.phoneFontScale") }
    }
    @Published var phoneTextColor: GLPhoneSubtitleColor = GLPhoneSubtitleColor(
        rawValue: UserDefaults.standard.string(forKey: "gamelingo.phoneTextColor") ?? ""
    ) ?? .yellow {
        didSet { UserDefaults.standard.set(phoneTextColor.rawValue, forKey: "gamelingo.phoneTextColor") }
    }
    @Published var phoneBackground: GLPhoneSubtitleBackground = GLPhoneSubtitleBackground(
        rawValue: UserDefaults.standard.string(forKey: "gamelingo.phoneBackground") ?? ""
    ) ?? .dark {
        didSet { UserDefaults.standard.set(phoneBackground.rawValue, forKey: "gamelingo.phoneBackground") }
    }

    // These values are independent of iPhone subtitle colors. The paired TV
    // receiver accepts authenticated STYLE messages and saves them locally.
    @Published var tvTextColor: GLPhoneSubtitleColor = GLPhoneSubtitleColor(
        rawValue: UserDefaults.standard.string(forKey: "gamelingo.tvTextColor") ?? ""
    ) ?? .white {
        didSet {
            UserDefaults.standard.set(tvTextColor.rawValue, forKey: "gamelingo.tvTextColor")
            tv.updateCaptionStyle(color: tvTextColor.rawValue, background: tvBackground.rawValue)
        }
    }
    @Published var tvBackground: GLPhoneSubtitleBackground = GLPhoneSubtitleBackground(
        rawValue: UserDefaults.standard.string(forKey: "gamelingo.tvBackground") ?? ""
    ) ?? .dark {
        didSet {
            UserDefaults.standard.set(tvBackground.rawValue, forKey: "gamelingo.tvBackground")
            tv.updateCaptionStyle(color: tvTextColor.rawValue, background: tvBackground.rawValue)
        }
    }

    @Published var outputLocked = UserDefaults.standard.bool(forKey: "outputLocked") {
        didSet { UserDefaults.standard.set(outputLocked, forKey: "outputLocked") }
    }
    @Published var minBlueHeight = UserDefaults.standard.integer(forKey: "minBlueHeight") {
        didSet {
            UserDefaults.standard.set(minBlueHeight, forKey: "minBlueHeight")
            if oldValue != minBlueHeight { invalidateDialogue() }
        }
    }
    @Published var ignoredBlueLines = UserDefaults.standard.string(forKey: "ignoredBlueLines") ?? "" {
        didSet {
            UserDefaults.standard.set(ignoredBlueLines, forKey: "ignoredBlueLines")
            if oldValue != ignoredBlueLines { invalidateDialogue() }
        }
    }
    @Published var minYellowHeight = UserDefaults.standard.integer(forKey: "minYellowHeight") {
        didSet {
            UserDefaults.standard.set(minYellowHeight, forKey: "minYellowHeight")
            if oldValue != minYellowHeight { invalidateOptions() }
        }
    }
    @Published var yellowOCRMode = UserDefaults.standard.string(forKey: "yellowOCRMode") ?? "follow" {
        didSet {
            UserDefaults.standard.set(yellowOCRMode, forKey: "yellowOCRMode")
            if oldValue != yellowOCRMode { invalidateOptions() }
        }
    }
    private var ocrModeForYellow: OCRMode { OCRMode(rawValue: yellowOCRMode) ?? ocrMode }
    @Published var ignoredYellowLines = UserDefaults.standard.string(forKey: "ignoredYellowLines") ?? "" {
        didSet {
            UserDefaults.standard.set(ignoredYellowLines, forKey: "ignoredYellowLines")
            if oldValue != ignoredYellowLines { invalidateOptions() }
        }
    }
    @Published var filterChoiceControls = UserDefaults.standard.bool(forKey: "filterChoiceControls") {
        didSet {
            UserDefaults.standard.set(filterChoiceControls, forKey: "filterChoiceControls")
            if oldValue != filterChoiceControls { invalidateOptions() }
        }
    }
    // Independent yellow answer-choice region, equivalent to Android's Volby.
    // Disabled by default: dialogue OCR remains unchanged until user enables it.
    @Published var choicesEnabled = UserDefaults.standard.bool(forKey: "choicesEnabled") {
        didSet {
            UserDefaults.standard.set(choicesEnabled, forKey: "choicesEnabled")
            optionsFrames?.setEnabled(choicesEnabled && !automaticGameDialogue)
            if oldValue != choicesEnabled { invalidateOptions() }
        }
    }
    @Published var choicesTextMode = UserDefaults.standard.bool(forKey: "choicesTextMode") {
        didSet {
            UserDefaults.standard.set(choicesTextMode, forKey: "choicesTextMode")
            if oldValue != choicesTextMode { invalidateOptions() }
        }
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
            if oldValue != choicesArea { invalidateOptions() }
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
    @Published private(set) var cacheEnabled = !UserDefaults.standard.bool(forKey: "translationCacheDisabled")
    @Published private(set) var cacheChanging = false
    @Published private(set) var cacheStatus = ""

    func manageTranslationCache(enabled: Bool? = nil) async {
        guard !isRunning, !cacheChanging else { return }
        cacheChanging = true
        defer { cacheChanging = false }
        if let enabled {
            await pipeline.setTranslationCacheEnabled(enabled)
            cacheEnabled = enabled
            cacheStatus = enabled ? "Cache zapnuta." : "Cache vypnuta; uložené překlady lze vymazat zvlášť."
        } else {
            let ok = await pipeline.purgeTranslationCache()
            cacheStatus = ok ? "Místní překladová cache vymazána." : "Cache se nepodařilo úplně vymazat. Zkus to znovu."
        }
    }
    @Published private(set) var translationHistory = TranslationHistory()

    func setHistoryEnabled(_ enabled: Bool) { translationHistory.setEnabled(enabled) }
    func clearHistory() { translationHistory.clear() }
    @Published private(set) var performanceSummary = "Zatím bez měření"
    private var performance: [String: PerformanceWindow] = [:]
    private var performanceTimer: Task<Void, Never>?
    private var translationFailures = 0
    private let sessionMarker = "gamelingo_diagnostics_session_open_v1"
    @Published private(set) var activeGlossaryProfileID: UUID? = UserDefaults.standard.string(forKey: "activeGlossaryProfileID").flatMap(UUID.init(uuidString:))
    private func selectGlossaryProfile(_ id: UUID?) {
        activeGlossaryProfileID = id
        if let id { UserDefaults.standard.set(id.uuidString, forKey: "activeGlossaryProfileID") }
        else { UserDefaults.standard.removeObject(forKey: "activeGlossaryProfileID") }
    }
    func setGameGlossary(profileID: UUID, glossary: GameGlossary) {
        guard !isRunning, let index = gameProfiles.firstIndex(where: { $0.id == profileID }) else { return }
        gameProfiles[index].glossary = glossary
    }
    func disableGameGlossary() {
        guard !isRunning else { return }
        selectGlossaryProfile(nil)
    }
    func deleteGameProfile(_ id: UUID) {
        guard !isRunning else { return }
        if activeGlossaryProfileID == id { selectGlossaryProfile(nil) }
        gameProfiles.removeAll { $0.id == id }
    }
    private func glossaryTranslation(original: String, translated: String, source: String, target: String) -> String {
        guard let id = activeGlossaryProfileID,
              let glossary = gameProfiles.first(where: { $0.id == id })?.glossary else { return translated }
        return glossary.apply(original: original, translated: translated, source: source, target: target)
    }
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

    /// Never send game text to a stale, unpaired or offline receiver,
    /// including after a live television switch.
    private var verifiedTelevisionOutput: Bool {
        guard devices.subtitleOutput == .googleTV,
              let selected = devices.chosenTelevision else { return false }
        return tv.receiverPaired && tv.receiverConnected &&
            tv.ip.trimmingCharacters(in: .whitespacesAndNewlines) == selected.ipAddress
    }

    @Published var phoneSubtitle = ""
    private var psStatusListener: AnyCancellable?
    private let pipeline = CaptionPipeline()
    private var remotePlay: XboxRemotePlay?
    private var xboxReconnectTask: Task<Void, Never>?
    private var xboxReconnectAttempts = 0
    private var processingChoices = false
    private var processingOCR = false
    private var choicesStartedAt = 0.0
    private var ocrStartedAt = 0.0
    private var sessionRevision = 0
    private var dialogueRevision = TranslationRevision()
    private var optionsRevision = TranslationRevision()
    private var dialogueReset: Task<Void, Never>?
    private var optionsReset: Task<Void, Never>?
    private var dialogueTranslation: Task<Void, Never>?
    private var optionsTranslation: Task<Void, Never>?
    private var dialogueExpiry: Task<Void, Never>?
    private var dialogueDisplayRevision = 0
    private var dialogueDisplayDeadline: TimeInterval?
    private typealias TimedFrame = (buffer: CVPixelBuffer, receivedAt: TimeInterval)
    private var dialogueFrames: LatestFrameMailbox<TimedFrame>?
    private var optionsFrames: LatestFrameMailbox<TimedFrame>?
    // Cache only the translation already visible on TV. Moving the orange
    // rectangle must not trigger a fresh OCR/translation or show demo text.
    private var lastTranslatedChoiceItems: [String] = []
    private var lastTranslatedChoiceIsText = false
    private var lastChoicesSentAt: Date?

    init() {
        devices.onOutputChanged = { [weak self] in
            guard let self else { return }
            // Clear the former receiver, not the newly selected television.
            self.tv.clearOverlays(at: self.tv.ip)
            self.invalidateDialogue(clearTV: false)
            self.invalidateOptions(clearTV: false)
        }
        if UserDefaults.standard.bool(forKey: sessionMarker) {
            write("Diagnostika: předchozí překladová relace nebyla řádně uzavřena (příčina neznámá).")
            UserDefaults.standard.removeObject(forKey: sessionMarker)
        }

    }

    func saveGameProfile(named rawName: String) {
        let name = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        let existingIndex = gameProfiles.firstIndex {
            $0.name.localizedCaseInsensitiveCompare(name) == .orderedSame
        }
        var profile = GameSubtitleProfile(
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
            filterChoiceControls: filterChoiceControls,
            ignoredBlueLines: ignoredBlueLines,
            minBlueHeight: minBlueHeight,
            ignoredYellowLines: ignoredYellowLines,
            minYellowHeight: minYellowHeight,
            yellowOCRMode: yellowOCRMode,
            choicesArea: choicesArea,
            choicesLocked: choicesLocked,
            choicesOutputArea: choicesOutputArea,
            choicesOutputLocked: choicesOutputLocked,
            choicesOutputFont: choicesOutputFont,
            subtitleDuration: subtitleDuration,
            showPreviousDialogue: showPreviousDialogue,
            previousDialogueCount: previousDialogueCount,
            hideOnSourceGone: hideOnSourceGone,
            sourceGoneDelay: sourceGoneDelay,
            skipOldTranslations: skipOldTranslations,
            maxTranslationAge: maxTranslationAge,
            gameLanguage: gameLanguage.rawValue,
            targetLanguage: targetLanguage.rawValue,
            phoneFontScale: phoneFontScale,
            phoneTextColor: phoneTextColor.rawValue,
            phoneBackground: phoneBackground.rawValue,
            tvTextColor: tvTextColor.rawValue,
            tvBackground: tvBackground.rawValue,
            automaticMode: automaticGameDialogue,
            automaticZones: automaticZones
        )
        if let existingIndex {
            profile.glossary = gameProfiles[existingIndex].glossary
            gameProfiles[existingIndex] = profile
        } else {
            gameProfiles.append(profile)
        }
        write("Profil uložen: \(name)")
    }

    func applyGameProfile(_ profile: GameSubtitleProfile) {
        guard !isRunning else { return }
        setAutomaticGameDialogue(profile.automaticMode ?? false)
        if let zones = profile.automaticZones { updateAutomaticZones(zones) }
        selectGlossaryProfile(profile.id)
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
        filterChoiceControls = profile.filterChoiceControls ?? false
        ignoredBlueLines = profile.ignoredBlueLines ?? ""
        minBlueHeight = min(5, max(0, profile.minBlueHeight ?? 0))
        ignoredYellowLines = profile.ignoredYellowLines ?? ""
        minYellowHeight = min(5, max(0, profile.minYellowHeight ?? 0))
        yellowOCRMode = OCRMode(rawValue: profile.yellowOCRMode ?? "")?.rawValue ?? "follow"
        choicesLocked = profile.choicesLocked
        choicesEnabled = profile.choicesEnabled
        if let rect = profile.choicesOutputArea { choicesOutputArea = rect }
        if let locked = profile.choicesOutputLocked { choicesOutputLocked = locked }
        choicesOutputFont = profile.choicesOutputFont ?? 1.0
        subtitleDuration = profile.subtitleDuration
        showPreviousDialogue = profile.showPreviousDialogue ?? false
        previousDialogueCount = min(2, max(1, profile.previousDialogueCount ?? 1))
        hideOnSourceGone = profile.hideOnSourceGone ?? false
        sourceGoneDelay = min(2, max(0, profile.sourceGoneDelay ?? 0))
        skipOldTranslations = profile.skipOldTranslations
        maxTranslationAge = profile.maxTranslationAge
        if let savedSource = profile.gameLanguage,
           let language = GLGameLanguage(rawValue: savedSource) {
            gameLanguage = language
        }
        if let savedTarget = profile.targetLanguage,
           let language = GLTargetLanguage(rawValue: savedTarget) {
            targetLanguage = language
        }
        if let value = profile.phoneFontScale, value >= 0.7 && value <= 1.8 {
            phoneFontScale = value
        }
        if let name = profile.phoneTextColor, let value = GLPhoneSubtitleColor(rawValue: name) {
            phoneTextColor = value
        }
        if let name = profile.phoneBackground,
           let value = GLPhoneSubtitleBackground(rawValue: name) {
            phoneBackground = value
        }
        if let name = profile.tvTextColor,
           let value = GLPhoneSubtitleColor(rawValue: name) {
            tvTextColor = value
        }
        if let name = profile.tvBackground,
           let value = GLPhoneSubtitleBackground(rawValue: name) {
            tvBackground = value
        }
        write("Načten profil: \(profile.name)")
    }

    func write(_ message: String) {
        // Only locally defined diagnostic categories enter the retained log.
        // Never keep arbitrary server messages, recognized text or device names.
        let safe: String
        if message.hasPrefix("Diagnostika: Průměr / P95") {
            safe = "Diagnostika: " + performanceSummary.replacingOccurrences(of: "\n", with: " | ")
        } else if message.hasPrefix("OCR:") { safe = "OCR: text rozpoznán" }
        else if message.hasPrefix("Překlad (") { safe = "Překlad dokončen" }
        else if message.hasPrefix("Volby:") { safe = "Herní text: událost zpracování" }
        else if message.hasPrefix("CHYBA:") || message.hasPrefix("Chyba překladu") { safe = "Chyba relace nebo překladu" }
        else if message.hasPrefix("Start Remote Play:") { safe = "Xbox: zahájení relace" }
        else if message.hasPrefix("PlayStation Remote Play:") { safe = "PlayStation: zahájení relace" }
        else if message.hasPrefix("Profil uložen:") || message.hasPrefix("Načten profil:") { safe = "Změna profilu" }
        else { safe = "Technická událost" }
        logLines.append("\(Date().formatted(date: .omitted, time: .standard))  \(safe)")
        if logLines.count > 250 { logLines.removeFirst(logLines.count - 250) }
    }

    private func recordPerformance(_ name: String, milliseconds: Double) {
        performance[name, default: PerformanceWindow()].record(milliseconds: milliseconds)
    }

    private func refreshPerformance() {
        let names = ["Dialog čekání snímku", "Dialog OCR", "Dialog čekání po OCR",
                     "Dialog překlad", "Dialog celkem k výstupu", "Herní text čekání snímku",
                     "Herní text OCR", "Herní text překlad", "Herní text celkem k výstupu"]
        var lines = names.map { "\($0): \(performance[$0]?.summary ?? "–")" }
        if let remotePlay { lines.append(remotePlay.streamSummary()) }
        let now = ProcessInfo.processInfo.systemUptime
        if processingOCR && now - ocrStartedAt >= 5 { lines.append("Dialog OCR: dlouhé čekání ≥ 5 s") }
        if processingChoices && now - choicesStartedAt >= 5 { lines.append("Herní text OCR: dlouhé čekání ≥ 5 s") }
        performanceSummary = (["Průměr / P95 (posledních 128 vzorků)"] + lines +
            ["Chyby překladu: \(translationFailures)", LocalDeviceDiagnostics.sample()]).joined(separator: "\n")
    }

    private func beginPerformanceSession(currentSession: Int) {
        performanceTimer?.cancel()
        performance.removeAll()
        translationFailures = 0
        UserDefaults.standard.set(true, forKey: sessionMarker)
        refreshPerformance()
        performanceTimer = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 30_000_000_000) }
                catch { return }
                guard let self, self.isRunning, self.sessionRevision == currentSession else { return }
                self.refreshPerformance()
                self.write("Diagnostika: " + self.performanceSummary.replacingOccurrences(of: "\n", with: " | "))
            }
        }
    }

    func start(_ console: XboxConsole) {
        guard !isRunning, !cacheChanging else { return }
        xboxReconnectAttempts = 0
        startXbox(console)
    }

    private func startXbox(_ console: XboxConsole) {
        guard !isRunning, !cacheChanging else { return }
        frameCount = 0
        lastEnglish = ""
        lastCzech = ""
        lastChoices = ""
        phoneSubtitle = ""
        isRunning = true
        sessionRevision += 1
        let currentSession = sessionRevision
        beginPerformanceSession(currentSession: currentSession)
        processingOCR = false
        processingChoices = false
        lastTranslatedChoiceItems = []
        lastChoicesSentAt = nil
        let receiveFrame = makeFrameReceiver(currentSession: currentSession)
        UIApplication.shared.isIdleTimerDisabled = true
        status = "Navazuji Xbox Remote Play…"
        write("Start Remote Play: \(console.name)")
        let play = XboxRemotePlay(authorization: xbox,
                                  minimumFrameInterval: automaticGameDialogue ? OCRMode.automatic.minimumFrameInterval : (choicesEnabled ? min(ocrMode.minimumFrameInterval, ocrModeForYellow.minimumFrameInterval) : ocrMode.minimumFrameInterval))
        remotePlay = play
        play.onStatus = { [weak self] status in
            Task { @MainActor in
                guard let self, self.sessionRevision == currentSession else { return }
                self.status = status
                self.write(status)
            }
        }
        play.onFrame = receiveFrame
        play.onInterrupted = { [weak self, weak play] reason, retry in
            Task { @MainActor in
                guard let self, let play, self.remotePlay === play,
                      self.isRunning, self.sessionRevision == currentSession else { return }
                if retry {
                    self.scheduleXboxReconnect(console, currentSession: currentSession, reason: reason)
                } else {
                    self.stop()
                    self.status = reason
                    self.write(reason)
                }
            }
        }
        Task {
            do { try await play.connect(console: console) }
            catch {
                guard self.sessionRevision == currentSession else { return }
                let failure = "Xbox Remote Play: \(error.localizedDescription)"
                stop()
                self.status = failure
                write("CHYBA: " + failure)
            }
        }
    }

    /// Retry the exact authorized console only after an established transport failure.
    /// The ordinary Stop path invalidates pending OCR, releases the old remote
    /// session and cancels any scheduled reconnect. A server stop never retries.
    private func scheduleXboxReconnect(_ console: XboxConsole, currentSession: Int, reason: String) {
        guard isRunning, sessionRevision == currentSession else { return }
        guard xboxReconnectAttempts < 2 else {
            stop()
            status = reason + " · automatické obnovení vyčerpáno"
            write(status)
            return
        }
        xboxReconnectAttempts += 1
        let attempt = xboxReconnectAttempts
        stop()
        let waitingRevision = sessionRevision
        // Keep the normal Stop button available during the reconnect delay.
        isRunning = true
        UIApplication.shared.isIdleTimerDisabled = true
        status = "Obnovuji Xbox · pokus \(attempt)/2"
        write(reason + " · " + status)
        xboxReconnectTask = Task { @MainActor [weak self] in
            do { try await Task.sleep(nanoseconds: UInt64(attempt) * 4_000_000_000) }
            catch { return }
            guard let self, self.isRunning, self.sessionRevision == waitingRevision,
                  !Task.isCancelled else { return }
            self.xboxReconnectTask = nil
            self.isRunning = false
            guard !self.cacheChanging else {
                self.stop()
                self.status = "Obnovení Xbox zrušeno – mění se cache"
                return
            }
            self.startXbox(console)
        }
    }

    /// A decoder callback touches only the thread-safe session mailboxes.
    /// Each lane owns at most one running drain and one pending latest frame.
    private func makeFrameReceiver(currentSession: Int) -> @Sendable (CVPixelBuffer) -> Void {
        dialogueFrames?.close()
        optionsFrames?.close()
        let dialogue = LatestFrameMailbox<TimedFrame>()
        let options = LatestFrameMailbox<TimedFrame>(enabled: choicesEnabled && !automaticGameDialogue)
        dialogueFrames = dialogue
        optionsFrames = options
        let reset = Task {
            await pipeline.resetDialogue()
            await pipeline.resetOptions()
        }

        return { [weak self] buffer in
            let timed = (buffer: buffer, receivedAt: ProcessInfo.processInfo.systemUptime)
            if dialogue.offer(timed) {
                Task { @MainActor [weak self] in
                    await reset.value
                    while let frame = dialogue.takeNext() {
                        guard let self, self.isRunning,
                              self.sessionRevision == currentSession else {
                            dialogue.close()
                            return
                        }
                        self.recordPerformance("Dialog čekání snímku", milliseconds:
                            (ProcessInfo.processInfo.systemUptime - frame.receivedAt) * 1000)
                        await self.processFrame(frame.buffer, currentSession: currentSession, frameReceivedAt: frame.receivedAt)
                        await Task.yield()
                    }
                }
            }
            if options.offer(timed) {
                Task { @MainActor [weak self] in
                    await reset.value
                    while let frame = options.takeNext() {
                        guard let self, self.isRunning,
                              self.sessionRevision == currentSession else {
                            options.close()
                            return
                        }
                        self.recordPerformance("Herní text čekání snímku", milliseconds:
                            (ProcessInfo.processInfo.systemUptime - frame.receivedAt) * 1000)
                        await self.processChoicesFrame(frame.buffer, currentSession: currentSession, frameReceivedAt: frame.receivedAt)
                        await Task.yield()
                    }
                }
            }
        }
    }

    /// Independent yellow-area drain. A slow options translation cannot block
    /// dialogue OCR or enqueue a backlog of decoded images.
    private func invalidateDialogue(clearTV: Bool = true) {
        clearAutomaticOutputs()
        dialogueWindow.reset()
        dialogueRevision.advance()
        dialogueTranslation?.cancel()
        dialogueTranslation = nil
        dialogueExpiry?.cancel()
        dialogueExpiry = nil
        dialogueDisplayDeadline = nil
        dialogueDisplayRevision &+= 1
        lastEnglish = ""
        lastCzech = ""
        phoneSubtitle = ""
        if clearTV, isRunning, verifiedTelevisionOutput {
            tv.sendAutomaticZones([], aspect: automaticFrameAspect)
            tv.sendSubtitle("", duration: 0)
        }
        let previous = dialogueReset
        dialogueReset = Task { [pipeline] in
            await previous?.value
            await pipeline.resetDialogue()
        }
    }

    private func invalidateOptions(clearTV: Bool = true) {
        optionsRevision.advance()
        optionsTranslation?.cancel()
        optionsTranslation = nil
        lastChoices = ""
        lastTranslatedChoiceItems = []
        lastChoicesSentAt = nil
        if clearTV, isRunning, verifiedTelevisionOutput {
            tv.sendChoices([], area: choicesArea, outputArea: choicesOutputArea)
        }
        let previous = optionsReset
        optionsReset = Task { [pipeline] in
            await previous?.value
            await pipeline.resetOptions()
        }
    }

    private func processChoicesFrame(_ buffer: CVPixelBuffer, currentSession: Int, frameReceivedAt: TimeInterval) async {
        guard isRunning, sessionRevision == currentSession, choicesEnabled, !automaticGameDialogue,
              !processingChoices else { return }
        processingChoices = true
        choicesStartedAt = ProcessInfo.processInfo.systemUptime
        let contextRevision = optionsRevision.value
        let area = choicesArea
        let asText = choicesTextMode
        let filterControls = filterChoiceControls
        let ignoredLines = ignoredYellowLines
        let minimumHeight = minYellowHeight
        let activeGameLanguage = gameLanguage
        let activeTargetLanguage = targetLanguage
        defer {
            if self.sessionRevision == currentSession {
                self.processingChoices = false
            }
        }
        await optionsReset?.value
        guard optionsRevision.accepts(contextRevision),
              sessionRevision == currentSession, isRunning else { return }
        let optionsStarted = ProcessInfo.processInfo.systemUptime
        guard let englishChoices = await self.pipeline.recognizeOptions(
            buffer, area: area, asText: asText,
            gameLanguage: activeGameLanguage, mode: self.ocrModeForYellow, filterControls: filterControls, ignoredLines: ignoredLines, minimumHeight: minimumHeight
        ), self.isRunning,
           self.sessionRevision == currentSession,
           self.choicesEnabled,
           self.optionsRevision.accepts(contextRevision) else { return }
        optionsRevision.advance()
        let readingRevision = optionsRevision.value
        optionsTranslation?.cancel()
        optionsTranslation = Task { @MainActor [weak self] in
            guard let self, !Task.isCancelled,
                  self.optionsRevision.accepts(readingRevision) else { return }
            await self.translateOptions(englishChoices, area: area, asText: asText, filterControls: filterControls, ignoredLines: ignoredLines, minimumHeight: minimumHeight,
                activeGameLanguage: activeGameLanguage, activeTargetLanguage: activeTargetLanguage,
                currentSession: currentSession, readingRevision: readingRevision, optionsStarted: optionsStarted, frameReceivedAt: frameReceivedAt)
        }
    }

    private func translateOptions(_ englishChoices: [String], area: CGRect, asText: Bool, filterControls: Bool, ignoredLines: String, minimumHeight: Int,
                                  activeGameLanguage: GLGameLanguage, activeTargetLanguage: GLTargetLanguage,
                                  currentSession: Int, readingRevision: UInt64, optionsStarted: TimeInterval, frameReceivedAt: TimeInterval) async {
        let optionsTranslateStarted = ProcessInfo.processInfo.systemUptime
        recordPerformance("Herní text OCR", milliseconds: (optionsTranslateStarted - optionsStarted) * 1000)
        var translated = [String]()
        for option in englishChoices {
            let outcome = await self.pipeline.translate(
                option, from: activeGameLanguage,
                to: activeTargetLanguage)
            guard self.isRunning,
                  self.sessionRevision == currentSession,
                  self.choicesEnabled,
                  !Task.isCancelled, self.optionsRevision.accepts(readingRevision),
                  self.choicesArea == area, self.choicesTextMode == asText,
                  self.gameLanguage == activeGameLanguage,
                  self.targetLanguage == activeTargetLanguage else { return }
            switch outcome {
            case .success(let item):
                translated.append(self.glossaryTranslation(original: option, translated: item,
                    source: activeGameLanguage.rawValue, target: activeTargetLanguage.rawValue))
            case .failure(let error):
                // A partial list could point to the wrong selectable option.
                // Clear the old list without ever sending the original text.
                self.lastChoices = ""
                self.lastTranslatedChoiceItems = []
                self.lastChoicesSentAt = nil
                if self.devices.subtitleOutput == .googleTV {
                    self.devices.prepareTV(self.tv)
                    if self.verifiedTelevisionOutput {
                        self.tv.sendChoices([], area: area, asText: asText,
                                            outputArea: self.choicesOutputArea,
                                            outputFont: self.choicesOutputFont)
                    }
                }
                self.translationFailures += 1
                self.recordPerformance("Herní text překlad", milliseconds: (ProcessInfo.processInfo.systemUptime - optionsTranslateStarted) * 1000)
                self.write("Volby: chyba překladu (\(error.diagnostic))")
                await self.pipeline.optionsTranslationFailed(englishChoices,
                    area: area, asText: asText, gameLanguage: activeGameLanguage, filterControls: filterControls, ignoredLines: ignoredLines, minimumHeight: minimumHeight)
                return
            }
        }
        guard self.isRunning,
              self.sessionRevision == currentSession,
              self.choicesEnabled,
              !Task.isCancelled, self.optionsRevision.accepts(readingRevision),
              self.choicesArea == area,
              self.choicesTextMode == asText,
              self.gameLanguage == activeGameLanguage,
              self.targetLanguage == activeTargetLanguage else { return }
        self.recordPerformance("Herní text překlad", milliseconds: (ProcessInfo.processInfo.systemUptime - optionsTranslateStarted) * 1000)
        self.lastChoices = translated.joined(separator: " · ")
        self.lastTranslatedChoiceItems = translated
        self.translationHistory.append(kind: asText ? "Herní text" : "Volby",
            source: activeGameLanguage.rawValue, target: activeTargetLanguage.rawValue,
            original: englishChoices.joined(separator: "\n"), translated: translated.joined(separator: "\n"))
        self.lastTranslatedChoiceIsText = asText
        self.lastChoicesSentAt = Date()
        if self.devices.subtitleOutput == .googleTV {
            self.devices.prepareTV(self.tv)
            if self.verifiedTelevisionOutput {
                self.tv.sendChoices(translated, area: area, asText: asText,
                                    outputArea: self.choicesOutputArea,
                                    outputFont: self.choicesOutputFont)
            }
        }
        if !translated.isEmpty && (self.devices.subtitleOutput == .phone || self.verifiedTelevisionOutput) {
            self.recordPerformance("Herní text celkem k výstupu", milliseconds: (ProcessInfo.processInfo.systemUptime - optionsStarted) * 1000)
            self.recordPerformance("Herní text od snímku k předání", milliseconds: (ProcessInfo.processInfo.systemUptime - frameReceivedAt) * 1000)
        }
        self.write("Volby: \(translated.count) rozpoznaných položek")
    }

    /// An explicit clear also cancels the TV sender's repeat task.
    private func clearDialogueOutput() {
        dialogueDisplayRevision &+= 1
        dialogueExpiry?.cancel()
        dialogueExpiry = nil
        dialogueDisplayDeadline = nil
        lastCzech = ""
        phoneSubtitle = ""
        if devices.subtitleOutput == .googleTV {
            devices.prepareTV(tv)
            if verifiedTelevisionOutput { tv.sendSubtitle("", duration: 0) }
        }
    }

    private func hideAfterSourceGone(currentSession: Int) {
        guard hideOnSourceGone, let deadline = dialogueDisplayDeadline else { return }
        let now = ProcessInfo.processInfo.systemUptime
        let target = min(deadline, now + Double(min(2, max(0, sourceGoneDelay))))
        if target <= now { clearDialogueOutput() }
        else { scheduleDialogueExpiry(seconds: target - now, currentSession: currentSession, latestDeadline: target) }
    }

    private func scheduleDialogueExpiry(seconds: Double, currentSession: Int, latestDeadline: TimeInterval? = nil) {
        dialogueExpiry?.cancel()
        dialogueDisplayRevision &+= 1
        let display = dialogueDisplayRevision
        let now = ProcessInfo.processInfo.systemUptime
        let deadline = min(now + seconds, latestDeadline ?? .infinity)
        dialogueDisplayDeadline = deadline
        let delay = max(0, deadline - now)
        dialogueExpiry = Task { @MainActor [weak self] in
            do { try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000)) }
            catch { return }
            guard let self, self.sessionRevision == currentSession,
                  self.dialogueDisplayRevision == display, !Task.isCancelled else { return }
            self.clearDialogueOutput()
        }
    }

    /// Both Xbox WebRTC and PS4/PS5 Chiaki video feed the same latest-frame OCR.
    /// Zone results are independent; only the legacy TV transport is still
    /// restricted to one block. The iPhone retains all confirmed translations.
    private func publishAutomaticOutputs(now: TimeInterval) {
        automaticOutputs.removeAll { output in
            guard let track = automaticTracks[output.id] else { return true }
            return now - track.lastSeen > 3.0
        }
        guard devices.subtitleOutput == .googleTV else { return }
        devices.prepareTV(tv)
        guard verifiedTelevisionOutput else { return }
        let ordered = automaticZones.compactMap { zone in automaticOutputs.first { $0.id == zone.id } }
        if tv.receiverSupportsZones {
            let key = ordered.map { "\($0.id)|\($0.text)|\($0.layout.centerX)|\($0.layout.bottomY)|\($0.layout.width)" }.joined(separator: ";")
            if automaticLastTV != nil {
                tv.sendSubtitle("", duration: 0)
                automaticLastTV = nil
            }
            if key != automaticSnapshotKey || (!ordered.isEmpty && now - automaticLastTVAt >= 0.8) {
                tv.sendAutomaticZones(ordered, aspect: automaticFrameAspect)
                automaticSnapshotKey = key
                automaticLastTVAt = now
            }
            return
        }
        let first = ordered.first
        if let first {
            if automaticLastTV?.id != first.id || automaticLastTV?.text != first.text ||
                automaticLastTV?.layout != first.layout || now - automaticLastTVAt >= 1.0 {
                tv.sendSubtitle(first.text, duration: 1800, automaticLayout: first.layout)
                automaticLastTV = first
                automaticLastTVAt = now
            }
        } else if automaticLastTV != nil {
            tv.sendSubtitle("", duration: 0)
            automaticLastTV = nil
        }
    }

    private func processAutomaticFrame(_ buffer: CVPixelBuffer, currentSession: Int, contextRevision: Int) async {
        let now = ProcessInfo.processInfo.systemUptime
        let height = CVPixelBufferGetHeight(buffer)
        if height > 0 { automaticFrameAspect = Double(CVPixelBufferGetWidth(buffer)) / Double(height) }
        publishAutomaticOutputs(now: now)
        await dialogueReset?.value
        guard isRunning, sessionRevision == currentSession, dialogueRevision.accepts(contextRevision) else { return }
        let source = gameLanguage, target = targetLanguage
        guard let reading = await pipeline.recognizeAutomaticZone(buffer, zones: automaticZones, gameLanguage: source),
              isRunning, sessionRevision == currentSession, dialogueRevision.accepts(contextRevision) else { return }
        let seen = ProcessInfo.processInfo.systemUptime
        let id = reading.zone
        var track = automaticTracks[id] ?? AutomaticTrack()
        if reading.text.isEmpty || reading.layout == nil {
            automaticTasks.removeValue(forKey: id)?.cancel()
            automaticTracks.removeValue(forKey: id)
            automaticOutputs.removeAll { $0.id == id }
            publishAutomaticOutputs(now: seen)
            return
        }
        if track.candidate != reading.text {
            automaticTasks.removeValue(forKey: id)?.cancel()
            automaticOutputs.removeAll { $0.id == id }
            track = AutomaticTrack(candidate: reading.text, confirmations: 1, lastSeen: seen)
        } else {
            track.confirmations = min(2, track.confirmations + 1)
            track.lastSeen = seen
        }
        track.layout = reading.layout
        automaticTracks[id] = track
        guard let layout = reading.layout else { return }
        if let i = automaticOutputs.firstIndex(where: { $0.id == id }) {
            automaticOutputs[i] = AutomaticOutput(id: id, text: automaticOutputs[i].text, layout: layout)
        } else if track.confirmations >= 2, automaticTasks[id] == nil, seen >= track.retryAt {
            let revision = track.revision
            automaticTasks[id] = Task { @MainActor [weak self] in
                guard let self else { return }
                let outcome = await self.pipeline.translate(reading.text, from: source, to: target)
                guard !Task.isCancelled, self.isRunning, self.sessionRevision == currentSession,
                      self.dialogueRevision.accepts(contextRevision),
                      self.automaticTracks[id]?.revision == revision else { return }
                self.automaticTasks.removeValue(forKey: id)
                let finished = ProcessInfo.processInfo.systemUptime
                guard let latest = self.automaticTracks[id], finished - latest.lastSeen <= 3 else { return }
                if case .success(let raw) = outcome {
                    let text = self.glossaryTranslation(original: reading.text, translated: raw,
                        source: source.rawValue, target: target.rawValue)
                    self.automaticOutputs.removeAll { $0.id == id }
                    self.automaticOutputs.append(.init(id: id, text: text, layout: latest.layout ?? layout))
                    self.publishAutomaticOutputs(now: finished)
                } else {
                    self.automaticTracks[id]?.retryAt = finished + 2
                }
            }
        }
        publishAutomaticOutputs(now: seen)
    }

    private func processFrame(_ buffer: CVPixelBuffer, currentSession: Int, frameReceivedAt: TimeInterval) async {
        guard isRunning, sessionRevision == currentSession else { return }
        self.frameCount += 1
        let activeGameLanguage = self.gameLanguage
        let activeTargetLanguage = self.targetLanguage
        let contextRevision = dialogueRevision.value
        let automatic = automaticGameDialogue
        if automatic {
            await processAutomaticFrame(buffer, currentSession: currentSession, contextRevision: contextRevision)
            return
        }

        // Keep recognition independent of network translation: a slow
        // request must not block OCR of the next short street dialogue.
        if !self.processingOCR {
            self.processingOCR = true
            ocrStartedAt = ProcessInfo.processInfo.systemUptime
            await dialogueReset?.value
            guard dialogueRevision.accepts(contextRevision), sessionRevision == currentSession, isRunning else {
                if sessionRevision == currentSession { processingOCR = false }
                return
            }
            let reading = await self.pipeline.recognize(
                buffer, region: self.subtitleRegion,
                manualArea: self.manualOCR ? self.manualOCRBox : nil,
                filterMenus: automatic || self.filterMenuText, ignoredLines: automatic ? "" : self.ignoredBlueLines, minimumHeight: automatic ? 0 : self.minBlueHeight,
                mode: automatic ? .automatic : self.ocrMode,
                expandOnEmpty: self.expandOnEmpty,
                gameLanguage: activeGameLanguage,
                automaticPlacement: automatic
            )
            if self.sessionRevision == currentSession {
                self.processingOCR = false
            }
            if let reading, self.isRunning,
               self.sessionRevision == currentSession,
               self.dialogueRevision.accepts(contextRevision) {
                dialogueRevision.advance()
                let readingRevision = dialogueRevision.value
                dialogueTranslation?.cancel()
                self.recordPerformance("Dialog OCR", milliseconds: reading.ocrMilliseconds)
                self.lastEnglish = reading.english
                if reading.english.isEmpty {
                    // The revision/cancellation above rejects pending stale work.
                    if automatic { self.clearDialogueOutput() }
                    else { self.hideAfterSourceGone(currentSession: currentSession) }
                    // By default, already visible dialogue retains its existing expiry;
                    // stop, source changes and explicit clears remain immediate.
                    return
                }
                if automatic { self.clearDialogueOutput() }
                self.write("OCR: \(reading.english)")
                dialogueTranslation = Task { @MainActor [weak self] in
                    guard let self, !Task.isCancelled,
                          self.dialogueRevision.accepts(readingRevision) else { return }
                    let translateStarted = ProcessInfo.processInfo.systemUptime
                    self.recordPerformance("Dialog čekání po OCR", milliseconds:
                        max(0, (translateStarted - reading.ocrStartedAt) * 1000 - reading.ocrMilliseconds))
                    let outcome = await self.pipeline.translate(
                        reading.english, from: activeGameLanguage,
                        to: activeTargetLanguage)
                    let translateMs = (ProcessInfo.processInfo.systemUptime - translateStarted) * 1000
                    if self.sessionRevision == currentSession {
                        self.recordPerformance("Dialog překlad", milliseconds: translateMs)
                        if case .failure = outcome { self.translationFailures += 1 }
                    }
                    let isLatest = await self.pipeline.isCurrent(revision: reading.revision)
                    guard self.isRunning,
                          self.sessionRevision == currentSession,
                          isLatest, !Task.isCancelled,
                          self.dialogueRevision.accepts(readingRevision),
                          self.gameLanguage == activeGameLanguage,
                          self.targetLanguage == activeTargetLanguage else { return }
                    if self.skipOldTranslations &&
                       ProcessInfo.processInfo.systemUptime - reading.ocrStartedAt > self.maxTranslationAge {
                        self.write("Příliš starý překlad byl přeskočen.")
                        return
                    }
                    guard case .success(let rawTranslation) = outcome else {
                        if case .failure(let error) = outcome {
                            self.write("Chyba překladu (\(error.diagnostic))")
                            // Keep only the previously displayed translation until its
                            // own expiry. Never publish the failed or original text.
                            await self.pipeline.dialogueTranslationFailed(revision: reading.revision)
                        }
                        return
                    }
                    let czech = self.glossaryTranslation(original: reading.english, translated: rawTranslation,
                        source: activeGameLanguage.rawValue, target: activeTargetLanguage.rawValue)
                    // Honor the existing 3–10 s setting; expiry starts when shown.
                    let requested = self.subtitleDuration.isFinite ? self.subtitleDuration : 6
                    let displaySeconds = min(10.0, max(3.0, requested))
                    self.lastCzech = czech
                    let displayText = self.dialogueWindow.render(source: reading.english, translation: czech,
                        enabled: !automatic && self.showPreviousDialogue, now: ProcessInfo.processInfo.systemUptime, previousCount: self.previousDialogueCount)
                    self.translationHistory.append(kind: "Dialog",
                        source: activeGameLanguage.rawValue, target: activeTargetLanguage.rawValue,
                        original: reading.english, translated: czech)
                    self.scheduleDialogueExpiry(seconds: displaySeconds, currentSession: currentSession)
                    if self.devices.subtitleOutput == .phone {
                        self.phoneSubtitle = displayText
                    } else {
                        self.devices.prepareTV(self.tv)
                        if self.verifiedTelevisionOutput {
                            self.tv.sendSubtitle(displayText,
                                                 duration: Int(displaySeconds * 1000),
                                                 automaticLayout: reading.automaticLayout)
                        }
                    }
                    if self.devices.subtitleOutput == .phone || self.verifiedTelevisionOutput {
                        self.recordPerformance("Dialog celkem k výstupu", milliseconds: (ProcessInfo.processInfo.systemUptime - reading.ocrStartedAt) * 1000)
                        self.recordPerformance("Dialog od snímku k předání", milliseconds: (ProcessInfo.processInfo.systemUptime - frameReceivedAt) * 1000)
                    }
                    self.write("Překlad (\(activeTargetLanguage.rawValue)): \(czech)")
                }
            }
        }
    }

    /// Actual native PlayStation Remote Play, not just a console entry.
    func start(_ console: RegisteredConsole) {
        guard !isRunning, !cacheChanging else { return }
        frameCount = 0
        lastEnglish = ""
        lastCzech = ""
        lastChoices = ""
        isRunning = true
        sessionRevision += 1
        let currentSession = sessionRevision
        beginPerformanceSession(currentSession: currentSession)
        processingOCR = false
        processingChoices = false
        lastTranslatedChoiceItems = []
        lastChoicesSentAt = nil
        phoneSubtitle = ""
        let receiveFrame = makeFrameReceiver(currentSession: currentSession)
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
        playStation.start(console: console, onFrame: receiveFrame, onEnded: { [weak self] reason in
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
        guard isRunning, choicesEnabled,
              !lastTranslatedChoiceItems.isEmpty,
              let sentAt = lastChoicesSentAt,
              Date().timeIntervalSince(sentAt) <= (lastTranslatedChoiceIsText ? 300 : 45)
        else { return false }
        devices.prepareTV(tv)
        guard verifiedTelevisionOutput else { return false }
        tv.sendChoices(lastTranslatedChoiceItems, area: choicesArea,
                       asText: lastTranslatedChoiceIsText,
                       outputArea: choicesOutputArea,
                       outputFont: choicesOutputFont)
        return true
    }

    func stop() {
        clearAutomaticOutputs()
        xboxReconnectTask?.cancel()
        xboxReconnectTask = nil
        dialogueRevision.advance()
        optionsRevision.advance()
        dialogueTranslation?.cancel()
        optionsTranslation?.cancel()
        dialogueTranslation = nil
        optionsTranslation = nil
        performanceTimer?.cancel()
        performanceTimer = nil
        if isRunning { refreshPerformance(); write("Diagnostika: " + performanceSummary.replacingOccurrences(of: "\n", with: " | ")) }
        UserDefaults.standard.removeObject(forKey: sessionMarker)
        sessionRevision += 1
        dialogueWindow.reset()
        dialogueExpiry?.cancel()
        dialogueExpiry = nil
        dialogueDisplayDeadline = nil
        dialogueDisplayRevision &+= 1
        dialogueFrames?.close()
        optionsFrames?.close()
        dialogueFrames = nil
        optionsFrames = nil
        processingOCR = false
        processingChoices = false
        remotePlay?.stop()
        remotePlay = nil
        playStation.stop()
        psStatusListener?.cancel()
        psStatusListener = nil
        phoneSubtitle = ""
        isRunning = false
        UIApplication.shared.isIdleTimerDisabled = false
        tv.sendAutomaticZones([], aspect: automaticFrameAspect)
        tv.sendSubtitle("", duration: 0)
        tv.sendChoices([], area: choicesArea, outputArea: choicesOutputArea)
        lastTranslatedChoiceItems = []
        lastChoicesSentAt = nil
        lastChoices = ""
        status = "Zastaveno"
    }

    func shareLogs() -> String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        return (["GameLingo iOS \(version)", isRunning ? "Relace aktivní" : "Relace neaktivní", performanceSummary] + logLines).joined(separator: "\n")
    }
}

// Only shared functional components remain here. The GameLingoHome root,
// GameLingoXboxScreen and GameLingoStyle own the application UI.
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
