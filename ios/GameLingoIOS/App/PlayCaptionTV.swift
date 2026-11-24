import Foundation
import Network
import Combine

import CryptoKit
import Security

private enum ReceiverSecretStore {
    static func account(_ ip: String) -> String { "iphone-tv-" + ip }

    static func load(_ ip: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "cz.caseycz.playcaption.tvpairing",
            kSecAttrAccount as String: account(ip),
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var value: CFTypeRef?
        let code = SecItemCopyMatching(query as CFDictionary, &value)
        guard code == errSecSuccess, let data = value as? Data,
              let token = String(data: data, encoding: .utf8) else { return nil }
        return token
    }

    static func save(_ token: String, ip: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "cz.caseycz.playcaption.tvpairing",
            kSecAttrAccount as String: account(ip)
        ]
        SecItemDelete(query as CFDictionary)
        var item = query
        item[kSecValueData as String] = Data(token.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(item as CFDictionary, nil) == errSecSuccess
    }

    static func remove(_ ip: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "cz.caseycz.playcaption.tvpairing",
            kSecAttrAccount as String: account(ip)
        ]
        SecItemDelete(query as CFDictionary)
    }
}


struct PlayCaptionDevice: Identifiable, Hashable {
    var id: String
    var name: String
    var ip: String
}

@MainActor
final class PlayCaptionTV: ObservableObject {
    @Published var ip: String = UserDefaults.standard.string(forKey: "tvIP") ?? "" {
        didSet {
            UserDefaults.standard.set(ip, forKey: "tvIP")
            if oldValue != ip {
                // A new IP must never leave old dialogue or game-text overlays
                // behind. Send an explicit clear to the former receiver first.
                clearOverlays(at: oldValue)
                startReceiverHeartbeat()
            }
        }
    }
    @Published var receiverCode = ""
    @Published private(set) var receiverStatus = "Televize zatím není spárovaná s iPhonem."
    @Published private(set) var receiverConnected = false
    @Published private(set) var lastCaptionAcknowledgement = ""

    private let receiverID: String = {
        let defaults = UserDefaults.standard
        if let value = defaults.string(forKey: "receiverPhoneID") { return value }
        let value = UUID().uuidString
        defaults.set(value, forKey: "receiverPhoneID")
        return value
    }()
    private var pairingRequest: NWConnection?
    private var pairingTimeoutTask: Task<Void, Never>?
    private var pairingRetryTask: Task<Void, Never>?
    private var liveReceiver: NWConnection?
    private var receiverHeartbeatTask: Task<Void, Never>?
    private var liveReceiverReady = false
    private var liveReceiverTarget = ""
    private var lastPong: Date?
    private var pendingPingNonce: String?
    private var lastWireSequence: Int64 = 0
    private var portCheckAt: Date?

    var receiverPaired: Bool {
        ReceiverSecretStore.load(ip.trimmingCharacters(in: .whitespacesAndNewlines)) != nil
    }
    @Published var discovered: [PlayCaptionDevice] = []
    @Published var message = "Na Google TV otevři GameLingo TV a zvol „Překlad z telefonu“."
    private let queue = DispatchQueue(label: "cz.caseycz.playcaption.udp")

    // GameLingo TV may pick UDP 41990–41993 when the original PlayCaption
    // receiver already owns 41989. Probe candidates rather than assuming the
    // legacy port is free. GLPORT1 exchanges contain no pairing secrets.
    private final class PortLookup {
        var connections: [NWConnection] = []
        var completed = false
    }

    private func cachedReceiverPort(_ target: String) -> Int {
        let value = UserDefaults.standard.integer(
            forKey: "gamelingo.tvReceiverPort." + target)
        return (41989...41993).contains(value) ? value : 41989
    }

    private func discoverReceiverPort(_ target: String,
                                      completion: @escaping (Int) -> Void) {
        let lookup = PortLookup()
        let complete: (Int) -> Void = { [weak self, lookup] port in
            guard !lookup.completed else { return }
            lookup.completed = true
            lookup.connections.forEach { $0.cancel() }
            guard (41989...41993).contains(port) else { return }
            UserDefaults.standard.set(port, forKey: "gamelingo.tvReceiverPort." + target)
            self?.receiverStatus = "Přijímač TV: UDP \(port)"
            completion(port)
        }

        for candidate in 41989...41993 {
            let connection = NWConnection(
                host: NWEndpoint.Host(target),
                port: NWEndpoint.Port(rawValue: UInt16(candidate))!,
                using: .udp)
            lookup.connections.append(connection)
            connection.stateUpdateHandler = { [weak connection] state in
                guard let connection else { return }
                if case .ready = state {
                    connection.receiveMessage { data, _, _, _ in
                        Task { @MainActor in
                            guard let data, let reply = String(data: data, encoding: .utf8)
                            else { return }
                            let fields = reply.components(separatedBy: "\t")
                            guard fields.count == 3, fields[0] == "GLPORT1",
                                  fields[1] == "PORT", Int(fields[2]) == candidate
                            else { return }
                            complete(candidate)
                        }
                    }
                    connection.send(content: Data("GLPORT1\tQUERY".utf8),
                                    completion: .contentProcessed { _ in })
                }
            }
            connection.start(queue: queue)
        }
        // Old GameLingo/PlayCaption receivers do not implement GLPORT1.
        // Preserve their default port, or the last successfully discovered one.
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(1500))
            if !Task.isCancelled { complete(cachedReceiverPort(target)) }
        }
    }

    init() {
        startReceiverHeartbeat()
    }
    private var listener: NWListener?
    private var repeatTask: Task<Void, Never>?
    private var sequence = UInt64(Date().timeIntervalSince1970 * 1000)

    func discover() {
        listener?.cancel()
        discovered = []
        let parameters = NWParameters.udp
        parameters.allowLocalEndpointReuse = true
        do {
            let listening = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: 41988)!)
            listening.newConnectionHandler = { [weak self] connection in
                guard let self else { return }
                connection.start(queue: self.queue)
                self.receive(on: connection)
            }
            listening.stateUpdateHandler = { [weak self] state in
                if case .failed(let error) = state {
                    Task { @MainActor in self?.message = "Hledání selhalo: \(error.localizedDescription). IP lze zadat ručně." }
                }
            }
            listener = listening
            listening.start(queue: queue)
            message = "Hledám GameLingo TV… Pokud ji nenajdu, zadej IP adresu z televize."
        } catch {
            message = "Hledání není dostupné: \(error.localizedDescription). Zadej IP ručně."
        }
    }

    nonisolated private func receive(on connection: NWConnection) {
        connection.receiveMessage { [weak self] data, _, _, error in
            guard let self else { return }
            if let data, let message = String(data: data, encoding: .utf8) {
                let parts = message.components(separatedBy: "\t")
                if parts.count >= 3 && parts[0] == "PLAYCAPTION_TV",
                   case .hostPort(let host, _) = connection.endpoint {
                    let device = PlayCaptionDevice(id: parts[1], name: parts[2], ip: host.debugDescription)
                    Task { @MainActor in
                        if !self.discovered.contains(where: { $0.id == device.id }) {
                            self.discovered.append(device)
                        }
                    }
                }
            }
            if error == nil { self.receive(on: connection) }
        }
    }


    // The PIN is displayed only on our independent TV receiver app.
    func pairReceiver() {
        let target = ip.trimmingCharacters(in: .whitespacesAndNewlines)
        let code = receiverCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty, code.count == 6, code.allSatisfy(\.isNumber) else {
            receiverStatus = "Zadej šest číslic z obrazovky TV."
            return
        }
        pairingRequest?.cancel()
        pairingTimeoutTask?.cancel()
        pairingRetryTask?.cancel()
        receiverStatus = "Hledám dostupný přijímací port televize…"
        discoverReceiverPort(target) { [weak self] port in
            guard let self, self.ip.trimmingCharacters(in: .whitespacesAndNewlines) == target
            else { return }
            self.beginReceiverPairing(target: target, code: code, port: port)
        }
    }

    private func beginReceiverPairing(target: String, code: String, port: Int) {
        pairingRequest?.cancel()
        pairingTimeoutTask?.cancel()
        pairingRetryTask?.cancel()
        receiverStatus = "Ověřuji kód na TV (UDP \(port))…"
        let pairingClientID = receiverID
        let conn = NWConnection(host: .init(target),
                                port: NWEndpoint.Port(rawValue: UInt16(port))!,
                                using: .udp)
        pairingRequest = conn
        // UDP does not report a missing reply as a connection failure.
        // Always end a stalled request with actionable feedback.
        pairingTimeoutTask = Task { [weak self, weak conn] in
            try? await Task.sleep(for: .seconds(10))
            guard !Task.isCancelled, let self, let conn,
                  self.pairingRequest === conn else { return }
            self.receiverStatus = "TV neodpověděla ani po opakovaném odeslání. Na TV zvol Překlad z telefonu a zkontroluj, že je vidět platný šestimístný kód."
            self.pairingRetryTask?.cancel()
            self.pairingRetryTask = nil
            self.pairingRequest = nil
            conn.cancel()
        }
        conn.stateUpdateHandler = { [weak self, weak conn] state in
            guard let conn else { return }
            switch state {
            case .ready:
                conn.receiveMessage { [weak self] data, _, _, error in
                    Task { @MainActor in
                        guard let self, self.pairingRequest === conn else { return }
                        self.pairingTimeoutTask?.cancel()
                        self.pairingRetryTask?.cancel()
                        self.pairingRetryTask = nil
                        self.pairingRequest = nil
                        guard let data, let text = String(data: data, encoding: .utf8) else {
                            self.receiverStatus = "TV neodpověděla. Ověř režim S mobilem, IP a síť."
                            conn.cancel()
                            return
                        }
                        let fields = text.components(separatedBy: "\t")
                        if fields.count == 4, fields[0] == "PCPAIR1",
                           fields[1] == "OK", fields[2] == pairingClientID,
                           fields[3].count == 64,
                           fields[3].allSatisfy({ $0.isHexDigit }),
                           ReceiverSecretStore.save(fields[3], ip: target) {
                            self.receiverCode = ""
                            self.receiverStatus = "iPhone úspěšně spárován. Ověřuji spojení…"
                            self.startReceiverHeartbeat()
                        } else if fields.first == "PCPAIR1", fields.dropFirst().first == "ERROR" {
                            self.receiverStatus = "TV odmítla kód. Vytvoř na TV nový šestimístný kód a zadej ho znovu."
                        } else {
                            self.receiverStatus = "TV vrátila neplatnou odpověď."
                        }
                        conn.cancel()
                    }
                }
                let request = Data("PCPAIR1\tPAIR\t\(code)\t\(pairingClientID)\tiPhone".utf8)
                conn.send(content: request,
                          completion: .contentProcessed { [weak self] error in
                    if let error {
                        Task { @MainActor in
                            self?.receiverStatus = "Párování nebylo odesláno: \(error.localizedDescription)"
                        }
                    }
                })
                // UDP does not guarantee delivery. Retry on the SAME socket
                // (and therefore the same reply port) while awaiting TV OK.
                // The receiver now resends an identical secret for a repeated
                // successful PIN, instead of considering the PIN expired.
                Task { @MainActor [weak self, weak conn] in
                    guard let self, let conn, self.pairingRequest === conn else { return }
                    self.pairingRetryTask?.cancel()
                    self.pairingRetryTask = Task { @MainActor [weak self, weak conn] in
                        for _ in 0..<3 {
                            try? await Task.sleep(for: .seconds(2))
                            guard !Task.isCancelled,
                                  let self, let conn,
                                  self.pairingRequest === conn else { return }
                            conn.send(content: request,
                                      completion: .contentProcessed { _ in })
                        }
                    }
                }
            case .failed(let error):
                Task { @MainActor in
                    guard let self, self.pairingRequest === conn else { return }
                    self.pairingTimeoutTask?.cancel()
                    self.pairingRetryTask?.cancel()
                    self.pairingRetryTask = nil
                    self.pairingRequest = nil
                    self.receiverStatus = "Spojení s TV selhalo: \(error.localizedDescription)"
                }
                conn.cancel()
            default: break
            }
        }
        conn.start(queue: queue)
    }

    func forgetReceiver() {
        pairingTimeoutTask?.cancel()
        pairingRetryTask?.cancel()
        pairingRetryTask = nil
        pairingRequest?.cancel()
        pairingRequest = nil
        ReceiverSecretStore.remove(ip.trimmingCharacters(in: .whitespacesAndNewlines))
        receiverCode = ""
        startReceiverHeartbeat()
        receiverStatus = "TV zapomenuta na iPhonu. Přístup telefonu odvolej také v nastavení TV."
    }

    private func signature(_ payload: String, tokenHex: String) -> String? {
        guard tokenHex.count == 64 else { return nil }
        var keyBytes = [UInt8]()
        for offset in stride(from: 0, to: tokenHex.count, by: 2) {
            let a = tokenHex.index(tokenHex.startIndex, offsetBy: offset)
            let b = tokenHex.index(a, offsetBy: 2)
            guard let byte = UInt8(tokenHex[a..<b], radix: 16) else { return nil }
            keyBytes.append(byte)
        }
        let mac = HMAC<SHA256>.authenticationCode(
            for: Data(payload.utf8), using: SymmetricKey(data: Data(keyBytes)))
        return mac.map { String(format: "%02x", $0) }.joined()
    }

    /// A new envelope for every send, including a deliberate UDP retransmit.
    private func freshBody(_ body: String) -> String {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        lastWireSequence = max(lastWireSequence + 1, now * 1000)
        return "\(body)\t\(now)\t\(lastWireSequence)"
    }

    private func startReceiverHeartbeat() {
        receiverHeartbeatTask?.cancel()
        liveReceiver?.cancel()
        liveReceiver = nil
        liveReceiverReady = false
        receiverConnected = false
        lastPong = nil
        pendingPingNonce = nil
        lastCaptionAcknowledgement = ""
        let target = ip.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty, ReceiverSecretStore.load(target) != nil else {
            receiverStatus = target.isEmpty ? "Nejdříve vyber televizi." :
                "TV je vybraná. Pro ověřené spojení ji spáruj šestímístným kódem."
            return
        }
        receiverStatus = "Vyhledávám přijímač na TV…"
        discoverReceiverPort(target) { [weak self] port in
            guard let self, self.ip.trimmingCharacters(in: .whitespacesAndNewlines) == target,
                  ReceiverSecretStore.load(target) != nil
            else { return }
            self.connectLiveReceiver(target: target, port: port)
        }
    }

    private func connectLiveReceiver(target: String, port: Int) {
        liveReceiver?.cancel()
        receiverHeartbeatTask?.cancel()
        receiverConnected = false
        lastPong = nil
        pendingPingNonce = nil
        portCheckAt = Date()
        receiverStatus = "Připojuji se k TV na UDP \(port)…"
        let conn = NWConnection(host: .init(target),
                                port: NWEndpoint.Port(rawValue: UInt16(port))!,
                                using: .udp)
        liveReceiver = conn
        liveReceiverTarget = target
        conn.stateUpdateHandler = { [weak self, weak conn] state in
            guard let conn else { return }
            switch state {
            case .ready:
                Task { @MainActor in
                    guard let self, self.liveReceiver === conn, self.ip == target else { return }
                    self.liveReceiverReady = true
                    self.readReceiverReply(on: conn, target: target)
                    // Reapply this phone's TV style after reconnect or pairing;
                    // style is authenticated by the saved per-phone HMAC token.
                    self.updateCaptionStyle(
                        color: UserDefaults.standard.string(forKey: "gamelingo.tvTextColor") ?? "white",
                        background: UserDefaults.standard.string(forKey: "gamelingo.tvBackground") ?? "dark")
                    self.receiverHeartbeatTask = Task { [weak self] in
                        while !Task.isCancelled {
                            guard let self, let token = ReceiverSecretStore.load(target),
                                  self.liveReceiver === conn else { break }
                            let nonce = UUID().uuidString
                            self.pendingPingNonce = nonce
                            let body = self.freshBody("PING\t\(self.receiverID)\t\(nonce)")
                            if let mac = self.signature(body, tokenHex: token) {
                                conn.send(content: Data("PCPAIR1\t\(body)\t\(mac)".utf8),
                                          completion: .contentProcessed { _ in })
                            }
                            try? await Task.sleep(for: .seconds(3))
                            guard !Task.isCancelled else { break }
                            if let last = self.lastPong, Date().timeIntervalSince(last) < 9 {
                                self.receiverConnected = true
                                self.receiverStatus = "TV spárovaná · spojení aktivní"
                            } else {
                                self.receiverConnected = false
                                self.receiverStatus = "TV spárovaná · čekám na odpověď"
                                // On TV restart the previous UDP port may become
                                // unavailable. Discover a new port without
                                // invalidating the existing authenticated token.
                                if let checked = self.portCheckAt,
                                   Date().timeIntervalSince(checked) > 12 {
                                    self.portCheckAt = Date()
                                    self.discoverReceiverPort(target) { [weak self] found in
                                        guard let self, self.liveReceiver === conn else { return }
                                        if found != port { self.startReceiverHeartbeat() }
                                    }
                                }
                            }
                        }
                    }
                }
            case .failed(let error):
                Task { @MainActor in
                    guard let self, self.liveReceiver === conn else { return }
                    self.liveReceiverReady = false
                    self.receiverConnected = false
                    self.receiverStatus = "TV neodpovídá: \(error.localizedDescription)"
                }
            case .cancelled:
                Task { @MainActor in
                    if let self, self.liveReceiver === conn { self.liveReceiverReady = false }
                }
            default: break
            }
        }
        conn.start(queue: queue)
    }

    private func readReceiverReply(on conn: NWConnection, target: String) {
        conn.receiveMessage { [weak self, weak conn] data, _, _, error in
            guard let conn else { return }
            Task { @MainActor in
                guard let self, self.liveReceiver === conn, self.ip == target else { return }
                if let data, let raw = String(data: data, encoding: .utf8) {
                    let fields = raw.components(separatedBy: "\t")
                    if fields.count == 4 && fields[0] == "PCPAIR1" && fields[1] == "PONG",
                       fields[2] == self.pendingPingNonce,
                       let token = ReceiverSecretStore.load(target),
                       let expected = self.signature("PONG\t\(self.receiverID)\t\(fields[2])", tokenHex: token),
                       fields[3] == expected {
                        self.pendingPingNonce = nil
                        self.lastPong = Date()
                        self.receiverConnected = true
                        self.receiverStatus = "TV spárovaná · spojení aktivní"
                    }
                    // Legacy unsigned PCA1 and STYLE_OK cannot authenticate
                    // reception or rendering. Only a signed PONG proves liveness.
                }
                if error == nil { self.readReceiverReply(on: conn, target: target) }
            }
        }
    }

    /// Authenticated subtitle sent through the verified receiver session.
    /// New clients never fall back to unsigned caption traffic.
    private func sendSignedSubtitle(_ canonicalPayload: Data, to target: String,
                                    reportStatus: Bool,
                                    forceDatagram: Bool = false) -> Bool {
        guard let token = ReceiverSecretStore.load(target) else { return false }
        let encoded = canonicalPayload.base64EncodedString()
        guard encoded.utf8.count < 11000 else { return false }
        let body = freshBody("TEXT\t\(receiverID)\t\(encoded)")
        guard let mac = signature(body, tokenHex: token) else { return false }
        let packet = Data("PCPAIR1\t\(body)\t\(mac)".utf8)
        if !forceDatagram, liveReceiverReady,
           liveReceiverTarget == target, let conn = liveReceiver {
            conn.send(content: packet, completion: .contentProcessed { [weak self] error in
                if reportStatus {
                    Task { @MainActor in
                        self?.message = error == nil
                            ? "Titulek předán síti. Zobrazení ověř na TV."
                            : "Chyba odeslání: \(error!.localizedDescription)"
                    }
                }
            })
        } else {
            sendDatagram(packet, to: target, reportStatus: reportStatus)
        }
        return true
    }

    /// Updates the GameLingo TV receiver's own subtitle color/background.
    /// This is NOT a change to the local iPhone overlay style. Uses the same
    /// authenticated UDP connection and token as PING/PONG and live captions.
    /// Old TV receivers safely ignore the unknown STYLE packet.
    func updateCaptionStyle(color: String, background: String) {
        let allowedColors = ["white", "yellow", "cyan", "green", "purple"]
        let allowedBackgrounds = ["dark", "translucent", "none"]
        guard allowedColors.contains(color), allowedBackgrounds.contains(background) else { return }
        let target = ip.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty, let token = ReceiverSecretStore.load(target) else { return }
        let body = "STYLE\t\(receiverID)\t\(color)\t\(background)"
        let fresh = freshBody(body)
        guard let mac = signature(fresh, tokenHex: token) else { return }
        let packet = Data("PCPAIR1\t\(fresh)\t\(mac)".utf8)
        if liveReceiverReady, liveReceiverTarget == target,
           let conn = liveReceiver {
            conn.send(content: packet, completion: .contentProcessed { _ in })
        } else {
            sendDatagram(packet, to: target, reportStatus: false)
        }
    }

    /// Clear captions on a previously selected receiver without changing the
    /// active TV IP, switching the heartbeat, or publishing a false connection.
    /// Uses a dedicated UDP datagram: the former live connection is cancelled
    /// immediately after the IP changes.
    func clearOverlays(at address: String) {
        // Stop periodic retransmits for the former TV, otherwise a previous
        // subtitle can reappear one second after the explicit clear.
        repeatTask?.cancel()
        repeatTask = nil
        let target = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty else { return }

        sequence += 1
        let dialogue = Data([
            "PCT1", String(sequence), "0", "", "",
            "0.5", "0.93", "0.62", "1.0", "0"
        ].joined(separator: "\t").utf8)
        sequence += 1
        let choices = Data("PCV1\t\(sequence)\t0\t0\t0\t1\t1\t-1\t".utf8)

        for payload in [dialogue, choices] {
            if !sendSignedSubtitle(payload, to: target,
                                   reportStatus: false, forceDatagram: true) {
                // No authenticated receiver: do not send unsigned game text.
                continue
            }
        }
    }

    /// Sends one packet and repeats it once a second while the caption is valid,
    /// matching Android's heartbeat. Preview uses the Android layout/edit flag so
    /// Google TV shows it even when HDMI is not the selected input.
    func sendSubtitle(_ subtitle: String, duration: Int, preview: Bool = false) {
        repeatTask?.cancel()
        let target = ip.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty else {
            message = "Nejprve vyber televizi nebo zadej její IP."
            return
        }
        UserDefaults.standard.set(target, forKey: "tvIP")
        sequence += 1
        let clean = subtitle.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\t", with: " ")
            .replacingOccurrences(of: "\n", with: "\\n")
        // Same 5 layout fields as Android: center X, bottom Y, width,
        // font size, and 1 while previewing the position.
        let prefs = UserDefaults.standard
        let x = (prefs.object(forKey: "outputX") as? Double) ?? 0.5
        let y = (prefs.object(forKey: "outputBottom") as? Double) ?? 0.93
        let width = (prefs.object(forKey: "outputWidth") as? Double) ?? 0.62
        let font = (prefs.object(forKey: "outputFont") as? Double) ?? 1.0
        // Original PlayCaption 0.22 protocol: field 4 is an empty hint.
        // Without it the TV misreads X/Y/width and draws a huge black strip.
        let fields = [
            "PCT1", String(sequence), String(max(0, duration)),
            clean, "", String(x), String(y), String(width), String(font),
            preview ? "1" : "0"
        ]
        let payload = Data(fields.joined(separator: "\t").utf8)
        if !sendSignedSubtitle(payload, to: target, reportStatus: true) {
            message = "Nejdříve bezpečně spáruj GameLingo TV."
            return
        }
        guard duration > 0 else { return }
        repeatTask = Task { [weak self] in
            let expiry = Date().addingTimeInterval(Double(duration) / 1000.0)
            while !Task.isCancelled && Date() < expiry {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled, Date() < expiry else { break }
                guard let self else { break }
                if !self.sendSignedSubtitle(payload, to: target, reportStatus: false) {
                    break
                }
            }
        }
    }

    /// Blue dashed OCR region preview, supported by official Android TV receiver.
    /// Coordinates are left, top, right, bottom in a top-left normalized frame.
    func showOCRRegion(_ area: CGRect, visible: Bool) {
        let target = ip.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty else {
            message = "Nejprve vyber televizi."
            return
        }
        let packet = "PCO1\t\(visible ? 1 : 0)\t\(area.minX)\t\(area.minY)\t\(area.maxX)\t\(area.maxY)"
        _ = sendSignedSubtitle(Data(packet.utf8), to: target, reportStatus: false)
    }

    private var choicesSequence: UInt64 {
        // Choices are delivered through their own sequence counter in Android.
        sequence += 1
        return sequence
    }

    /// PCV1 is accepted by the original PlayCaption TV receiver. No guessed
    /// selected option: -1 is neutral until highlight detection is implemented.
    /// A selected value of -2 signals the Android "Text" (letter/SMS) mode.
    func sendChoices(_ items: [String], area: CGRect,
                     asText: Bool = false,
                     showFrame: Bool = false,
                     outputArea: CGRect? = nil,
                     outputFont: Double = 1.0) {
        let target = ip.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty else { return }
        let escaped = items.joined(separator: "\n")
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\t", with: " ")
            .replacingOccurrences(of: "\n", with: "\\n")
        let frameFlag = showFrame ? 1 : 0
        let chosen = asText ? -2 : -1
        // PCV1 extension: output rectangle and independent game-text font scale.
        // Older PlayCaption receivers ignore additional fields safely.
        let outputSuffix: String
        if let rect = outputArea {
            let scale = outputFont.isFinite ? min(2.0, max(0.6, outputFont)) : 1.0
            outputSuffix = "\t\(rect.minX)\t\(rect.minY)\t\(rect.maxX)\t\(rect.maxY)\t\(scale)"
        } else {
            outputSuffix = ""
        }
        let packet = "PCV1\t\(choicesSequence)\t\(frameFlag)\t\(area.minX)\t\(area.minY)\t\(area.maxX)\t\(area.maxY)\t\(chosen)\t\(escaped)\(outputSuffix)"
        let bytes = Data(packet.utf8)
        if !sendSignedSubtitle(bytes, to: target, reportStatus: false) {
            message = "Nejdříve bezpečně spáruj GameLingo TV."
        }
    }

    private func sendDatagram(_ payload: Data, to target: String, reportStatus: Bool) {
        let connection = NWConnection(host: NWEndpoint.Host(target),
                                      port: NWEndpoint.Port(rawValue: UInt16(cachedReceiverPort(target)))!,
                                      using: .udp)
        connection.stateUpdateHandler = { [weak self, weak connection] state in
            guard let connection else { return }
            switch state {
            case .ready:
                connection.send(content: payload, completion: .contentProcessed { error in
                    if reportStatus {
                        Task { @MainActor in
                            self?.message = error == nil
                                ? "Odesláno do \(target). Zobrazení potvrdí až televize."
                                : "Chyba odeslání: \(error!.localizedDescription)"
                        }
                    }
                    connection.cancel()
                })
            case .failed(let error):
                if reportStatus {
                    Task { @MainActor in self?.message = "Nelze odeslat: \(error.localizedDescription)" }
                }
                connection.cancel()
            default: break
            }
        }
        connection.start(queue: queue)
    }
}
