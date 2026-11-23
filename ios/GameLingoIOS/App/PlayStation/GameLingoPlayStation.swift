import Combine
import CoreVideo
import Foundation

/// Native PS4/PS5 Remote Play through the pinned Chiaki/TP Play C core.
/// This class does not know about OCR, translation, subtitles, or GameLingo UI.
/// The caller receives decoded CVPixelBuffer frames (no video duplication).
@MainActor
final class GameLingoPlayStation: ObservableObject {
    @Published private(set) var status = "PlayStation nepřipojen"
    @Published private(set) var isRunning = false
    @Published private(set) var streamingConsoleID: String?
    @Published private(set) var needsLoginPIN = false

    let registrations = RegisteredConsoleStore()
    let registration = ConsoleRegistration()
    let discovery = ConsoleDiscoveryStore()

    private var session: RemotePlaySession?
    private var subscriptions = Set<AnyCancellable>()
    private var sessionRevision = 0

    var registered: [RegisteredConsole] { registrations.consoles }

    init() {
        discovery.updateRegisteredConsoles(registrations.consoles)
    }

    func discover() {
        status = "Hledám PlayStation…"
        discovery.discoverAll()
    }

    func stopDiscovery() {
        discovery.stopAll()
    }

    func findSavedConsole(id: String) -> RegisteredConsole? {
        registrations.consoles.first(where: { "ps:" + $0.id == id })
    }

    func register(address: String, name: String,
                  isPS5: Bool, accountID: Data, pin: UInt32) {
        guard !address.isEmpty else {
            status = "Chybí adresa PlayStation"
            return
        }
        let discovered = discovery.consoles.first {
            $0.address == address
        } ?? DiscoveredConsole(
            id: address,
            name: name.isEmpty ? (isPS5 ? "PlayStation 5" : "PlayStation 4") : name,
            address: address,
            systemVersion: "",
            runningAppName: nil,
            isPS5: isPS5,
            target: isPS5 ? 1_000_100 : 1_000,
            state: .unknown
        )
        status = "Páruji PlayStation…"
        registration.start(console: discovered,
                           accountID: accountID,
                           pin: pin,
                           store: registrations)
    }

    func start(console: RegisteredConsole,
               onFrame: @escaping @Sendable (CVPixelBuffer) -> Void) {
        stop()
        sessionRevision &+= 1
        let revision = sessionRevision
        let name = console.nickname.isEmpty ? "PlayStation" : console.nickname
        status = "Připojuji \(name)…"
        isRunning = true
        needsLoginPIN = false
        streamingConsoleID = "ps:" + console.id

        // 720p/30 is sufficient for OCR and reduces work on the iPhone.
        // PS5 can use HEVC via VideoToolbox, PS4 defaults to H.264.
        let config = StreamConfiguration(width: 1280, height: 720,
                                         fps: 30, bitrate: 8_000)
        let remote = RemotePlaySession(
            console: console,
            configuration: config,
            onConnected: { [weak self] in
                Task { @MainActor in
                    guard let self, revision == self.sessionRevision else { return }
                    self.status = "PlayStation připojen"
                }
            },
            onDecodedFrame: onFrame
        )
        session = remote

        remote.$state
            .sink { [weak self] state in
                guard let self, revision == self.sessionRevision else { return }
                switch state {
                case .connecting:
                    self.status = "Připojuji PlayStation…"
                case .connected:
                    self.status = "PlayStation připojen"
                case .loginPINRequired:
                    self.needsLoginPIN = true
                    self.status = "Zadej přihlašovací PIN PlayStation"
                case .ended(let message):
                    self.status = "PlayStation: " + message
                    self.isRunning = false
                    self.needsLoginPIN = false
                }
            }
            .store(in: &subscriptions)
    }

    func submitLoginPIN(_ pin: String) {
        guard pin.allSatisfy(\.isNumber), !pin.isEmpty else { return }
        session?.submitLoginPIN(pin)
        needsLoginPIN = false
    }

    func stop() {
        sessionRevision &+= 1
        subscriptions.removeAll()
        let old = session
        session = nil
        isRunning = false
        needsLoginPIN = false
        streamingConsoleID = nil
        old?.stop()
        status = "PlayStation odpojen"
    }
}
