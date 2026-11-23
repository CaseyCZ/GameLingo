import Foundation
import SwiftUI

/// A console is not a television: inputs and subtitle outputs are independent.
/// Keep only non-secret device metadata in UserDefaults. Authentication tokens
/// remain in the existing Xbox/TV keychains; PlayStation credentials will use
/// their own Keychain adapter when the Chiaki core is integrated.
enum GLConsoleFamily: String, Codable, CaseIterable, Identifiable {
    case xbox = "Xbox"
    case playStation = "PlayStation"

    var id: String { rawValue }
    var icon: String { self == .xbox ? "gamecontroller.fill" : "gamepad.fill" }
}

enum GLSubtitleOutput: String, Codable, CaseIterable, Identifiable {
    case phone = "Telefon"
    case googleTV = "Google TV"

    var id: String { rawValue }
}

struct GLConsoleRecord: Identifiable, Codable, Hashable {
    let id: String
    var family: GLConsoleFamily
    var name: String
    var address: String?
    var lastSeen: Date?

    init(id: String, family: GLConsoleFamily, name: String,
         address: String? = nil, lastSeen: Date? = nil) {
        self.id = id
        self.family = family
        self.name = name
        self.address = address
        self.lastSeen = lastSeen
    }
}

struct GLTelevisionRecord: Identifiable, Codable, Hashable {
    /// Stable ID (the TV discovery service currently supplies IP-based IDs).
    let id: String
    var name: String
    var ipAddress: String
}

@MainActor
final class GameLingoDevices: ObservableObject {
    @Published private(set) var consoles: [GLConsoleRecord] = []
    @Published private(set) var televisions: [GLTelevisionRecord] = []

    @Published var preferredConsoleID: String? {
        didSet { persist(); syncLegacyXboxID() }
    }
    @Published var preferredTelevisionID: String? {
        didSet { persist() }
    }
    @Published var subtitleOutput: GLSubtitleOutput = .googleTV {
        didSet { persist() }
    }
    @Published var completedSetup = false {
        didSet { persist() }
    }

    private let defaults = UserDefaults.standard
    private let storageKey = "gamelingo.devices.v1"
    private var isRestoring = false

    private struct Stored: Codable {
        var consoles: [GLConsoleRecord]
        var televisions: [GLTelevisionRecord]
        var preferredConsoleID: String?
        var preferredTelevisionID: String?
        var subtitleOutput: GLSubtitleOutput
        var completedSetup: Bool
    }

    var chosenConsole: GLConsoleRecord? {
        consoles.first(where: { $0.id == preferredConsoleID })
    }

    var chosenTelevision: GLTelevisionRecord? {
        televisions.first(where: { $0.id == preferredTelevisionID })
    }

    init() {
        if let data = defaults.data(forKey: storageKey),
           let stored = try? JSONDecoder().decode(Stored.self, from: data) {
            isRestoring = true
            consoles = stored.consoles
            televisions = stored.televisions
            preferredConsoleID = stored.preferredConsoleID
            preferredTelevisionID = stored.preferredTelevisionID
            subtitleOutput = stored.subtitleOutput
            completedSetup = stored.completedSetup
            isRestoring = false
        }
        // Existing iOS GameLingo/PlayCaption users do not lose their Xbox
        // selection or TV address when upgrading to this device library.
        let previousXboxID = defaults.string(forKey: "selectedXboxServerID") ?? ""
        if !previousXboxID.isEmpty &&
            !consoles.contains(where: { $0.id == "xbox:" + previousXboxID }) {
            consoles.append(GLConsoleRecord(id: "xbox:" + previousXboxID,
                                           family: .xbox,
                                           name: "Xbox"))
        }
        if preferredConsoleID == nil && !previousXboxID.isEmpty {
            preferredConsoleID = "xbox:" + previousXboxID
        }
        if let previousIP = defaults.string(forKey: "tvIP"),
           !previousIP.isEmpty {
            addTelevision(name: "Google TV", ipAddress: previousIP)
        }
        persist()
    }

    func refreshXbox(_ discovered: [XboxConsole]) {
        // Preserve manually registered PlayStations and existing Xbox devices;
        // update all Xbox entries instead of picking the first console found.
        for xbox in discovered {
            let id = "xbox:" + xbox.id
            if let index = consoles.firstIndex(where: { $0.id == id }) {
                consoles[index].name = xbox.name
                consoles[index].lastSeen = Date()
            } else {
                consoles.append(GLConsoleRecord(
                    id: id, family: .xbox, name: xbox.name, lastSeen: Date()))
            }
        }
        persist()
    }

    func addPlayStation(name: String, address: String) {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !name.isEmpty else { return }
        // Discovery and registration are separate: saving an address must NOT
        // mark the console paired or pretend that Remote Play works.
        let id = "ps:" + trimmed.lowercased()
        let device = GLConsoleRecord(id: id, family: .playStation,
                                     name: name, address: trimmed)
        if let index = consoles.firstIndex(where: { $0.id == id }) {
            consoles[index] = device
        } else {
            consoles.append(device)
        }
        persist()
    }

    func addTelevision(name: String, ipAddress: String) {
        let trimmed = ipAddress.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let id = "tv:" + trimmed.lowercased()
        let tv = GLTelevisionRecord(id: id, name: name, ipAddress: trimmed)
        if let index = televisions.firstIndex(where: { $0.id == id }) {
            televisions[index] = tv
        } else {
            televisions.append(tv)
        }
        // Do not auto-change the default TV when adding a second one.
        if preferredTelevisionID == nil { preferredTelevisionID = id }
        persist()
    }

    func selectConsole(_ id: String) {
        guard consoles.contains(where: { $0.id == id }) else { return }
        preferredConsoleID = id
    }

    func selectTelevision(_ id: String) {
        guard televisions.contains(where: { $0.id == id }) else { return }
        preferredTelevisionID = id
    }

    func renameConsole(_ id: String, to name: String) {
        let newName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !newName.isEmpty,
              let index = consoles.firstIndex(where: { $0.id == id }) else { return }
        consoles[index].name = String(newName.prefix(64))
        persist()
    }

    func removeConsole(_ id: String) {
        consoles.removeAll(where: { $0.id == id })
        if preferredConsoleID == id { preferredConsoleID = nil }
        persist()
    }

    func removeTelevision(_ id: String) {
        televisions.removeAll(where: { $0.id == id })
        if preferredTelevisionID == id { preferredTelevisionID = nil }
        persist()
    }

    func activeXbox(from auth: XboxAuthorization) -> XboxConsole? {
        guard let id = chosenConsole?.id,
              id.hasPrefix("xbox:") else { return nil }
        return auth.consoles.first(where: { "xbox:" + $0.id == id })
    }

    func prepareTV(_ receiver: PlayCaptionTV) {
        guard subtitleOutput == .googleTV,
              let tv = chosenTelevision else { return }
        if receiver.ip != tv.ipAddress { receiver.ip = tv.ipAddress }
    }

    private func syncLegacyXboxID() {
        guard !isRestoring else { return }
        if let id = preferredConsoleID, id.hasPrefix("xbox:") {
            defaults.set(String(id.dropFirst("xbox:".count)),
                         forKey: "selectedXboxServerID")
        } else {
            defaults.removeObject(forKey: "selectedXboxServerID")
        }
    }

    private func persist() {
        guard !isRestoring else { return }
        let data = Stored(consoles: consoles,
                          televisions: televisions,
                          preferredConsoleID: preferredConsoleID,
                          preferredTelevisionID: preferredTelevisionID,
                          subtitleOutput: subtitleOutput,
                          completedSetup: completedSetup)
        if let encoded = try? JSONEncoder().encode(data) {
            defaults.set(encoded, forKey: storageKey)
        }
    }
}
