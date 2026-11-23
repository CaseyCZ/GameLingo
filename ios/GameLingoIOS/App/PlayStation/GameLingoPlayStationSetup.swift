import SwiftUI

struct GameLingoPlayStationSetup: View {
    @EnvironmentObject private var model: CaptionAppModel
    @ObservedObject var devices: GameLingoDevices

    var body: some View {
        GameLingoPSConnectionView(ps: model.playStation, devices: devices)
    }
}

/// GameLingo-owned PlayStation setup. Chiaki provides the protocol, not the UI.
private struct GameLingoPSConnectionView: View {
    @ObservedObject var ps: GameLingoPlayStation
    @ObservedObject var devices: GameLingoDevices
    @ObservedObject var discovered: ConsoleDiscoveryStore
    @ObservedObject var registered: RegisteredConsoleStore
    @ObservedObject var registration: ConsoleRegistration

    @StateObject private var psn = GameLingoPSNLookup()
    @Environment(\.dismiss) private var dismiss
    @State private var host = ""
    @State private var consoleName = "PlayStation 5"
    @State private var isPS5 = true
    @State private var pin = ""
    @State private var accountID: Data?
    @State private var redirectLink = ""
    @State private var advancedExpanded = false
    @State private var selectedStep = 0

    init(ps: GameLingoPlayStation, devices: GameLingoDevices) {
        self.ps = ps
        self.devices = devices
        self.discovered = ps.discovery
        self.registered = ps.registrations
        self.registration = ps.registration
    }

    private var selectedRegistration: RegisteredConsole? {
        registered.consoles.first(where: {
            "ps:" + $0.id == devices.preferredConsoleID
        })
    }

    private var validPIN: UInt32? {
        let value = pin.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value.count == 8,
              value.allSatisfy(\.isNumber) else { return nil }
        return UInt32(value)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 15) {
                    GLNeonTitle(title: "PlayStation",
                                subtitle: "PlayStation 4 / 5 Remote Play",
                                icon: "gamecontroller.fill")
                    GLNeonPanel(emphasized: true) {
                        HStack(spacing: 14) {
                            GLPlatformBadge(family: .playStation, size: 65)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Moje konzole").font(.headline)
                                Text("Vyber konzoli nebo přidej novou")
                                    .font(.caption)
                                    .foregroundStyle(GLNeon.mist)
                            }
                            Spacer()
                        }
                        .padding(15)
                    }
                    GLCard {
                        GLHeader(title: "PlayStation",
                                 help: "PS5: Nastavení → Systém → Remote Play → Povolit Remote Play a Propojit zařízení. PS4: Nastavení → Nastavení připojení Remote Play. Uvidíš osmimístný párovací kód. Přihlaš se svým účtem PlayStation přes oficiální stránku Sony.")
                        HStack {
                            Text("Moje konzole")
                                .font(.subheadline.bold())
                            Spacer()
                            Button {
                                ps.discover()
                            } label: {
                                Label("Hledat", systemImage: "arrow.clockwise")
                            }
                            .font(.subheadline)
                        }
                        if !registered.consoles.isEmpty {
                            ForEach(registered.consoles) { console in
                                Button {
                                    devices.refreshPlayStation(registered.consoles)
                                    devices.selectConsole("ps:" + console.id)
                                    dismiss()
                                } label: {
                                    HStack {
                                        GLPlatformBadge(family: .playStation, size: 46)
                                        Text(console.nickname.isEmpty ? "PlayStation" : console.nickname)
                                            .foregroundStyle(.white)
                                        Spacer()
                                        if selectedRegistration?.id == console.id {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundStyle(GLStyle.green)
                                        }
                                    }
                                    .padding(13)
                                    .background(GLNeon.panel,
                                        in: RoundedRectangle(cornerRadius: 15))
                                    .overlay(RoundedRectangle(cornerRadius: 15)
                                        .strokeBorder(GLNeon.edge.opacity(0.65), lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        if !discovered.consoles.isEmpty {
                            Text("Nalezené")
                                .font(.subheadline)
                                .foregroundStyle(GLStyle.muted)
                            ForEach(discovered.consoles) { console in
                                Button {
                                    host = console.address
                                    consoleName = console.name
                                    isPS5 = console.isPS5
                                    selectedStep = 1
                                } label: {
                                    HStack {
                                        GLPlatformBadge(family: .playStation, size: 44)
                                        Text(console.name)
                                        Spacer()
                                        Text(console.isPS5 ? "PS5" : "PS4")
                                            .font(.caption)
                                    }
                                    .foregroundStyle(.white)
                                    .padding(12)
                                    .background(GLNeon.panel,
                                        in: RoundedRectangle(cornerRadius: 15))
                                    .overlay(RoundedRectangle(cornerRadius: 15)
                                        .strokeBorder(GLNeon.edge.opacity(0.65), lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Text(ps.status)
                            .font(.caption)
                            .foregroundStyle(GLStyle.muted)
                    }

                    GLCard {
                        GLHeader(title: "Přidat PlayStation",
                            help: "Konzole se automaticky hledá v lokální síti. Pokud ji GameLingo nenajde, lze zadat její IP adresu ručně. Pro PS5 i PS4 je potřeba jednorázové párování.")
                        Picker("Konzole", selection: $isPS5) {
                            Text("PS5").tag(true)
                            Text("PS4").tag(false)
                        }
                        .pickerStyle(.segmented)
                        TextField("IP adresa konzole", text: $host)
                            .keyboardType(.numbersAndPunctuation)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textFieldStyle(.roundedBorder)
                        TextField("Název konzole", text: $consoleName)
                            .textFieldStyle(.roundedBorder)
                        if selectedStep == 0 {
                            GLPrimaryButton(title: "Pokračovat", systemImage: "arrow.right",
                                disabled: host.trimmingCharacters(in: .whitespaces).isEmpty) {
                                selectedStep = 1
                            }
                        }
                    }

                    if selectedStep > 0 {
                        GLCard {
                            GLHeader(title: "Účet PlayStation",
                                help: "Otevři přihlášení Sony, dokonči jej v Safari a zkopíruj závěrečný odkaz ze stránky zpět do GameLingo. Heslo se nezadává do GameLingo a přihlašovací údaje se neukládají.")
                            Button {
                                UIApplication.shared.open(GameLingoPSNLookup.loginURL)
                            } label: {
                                GLSettingsLink(icon: "person.crop.circle",
                                    title: "Přihlásit Sony")
                            }
                            .buttonStyle(.plain)
                            TextField("Vložit odkaz po přihlášení", text: $redirectLink,
                                      axis: .vertical)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .lineLimit(2...3)
                                .textFieldStyle(.roundedBorder)
                            GLPrimaryButton(title: accountID == nil
                                            ? "Ověřit účet" : "Účet ověřen",
                                systemImage: accountID == nil
                                            ? "checkmark.shield" : "checkmark.circle.fill",
                                disabled: redirectLink.isEmpty || psn.isLoading) {
                                Task {
                                    accountID = await psn.resolve(redirectLink: redirectLink)
                                    // The one-time redirect code does not persist.
                                    if accountID != nil { redirectLink = "" }
                                }
                            }
                            if let error = psn.error {
                                Text(error).font(.caption).foregroundStyle(.orange)
                            }
                            DisclosureGroup("Pokročilé", isExpanded: $advancedExpanded) {
                                Text("ID účtu (Base64)")
                                    .font(.caption).foregroundStyle(GLStyle.muted)
                                TextField("Base64 ID účtu", text: Binding(
                                    get: { accountID?.base64EncodedString() ?? "" },
                                    set: { accountID = Data(base64Encoded: $0) }
                                ))
                                .textInputAutocapitalization(.never)
                                .textFieldStyle(.roundedBorder)
                            }
                            .font(.caption)
                        }
                        GLCard {
                            GLHeader(title: "Spárovat konzoli",
                                help: "Na PS5 otevři Nastavení → Systém → Remote Play → Propojit zařízení. Zadaný osmimístný PIN slouží pouze k propojení. Připojení funguje jen na vlastních nebo autorizovaných konzolích.")
                            SecureField("8místný PIN z PlayStation", text: $pin)
                                .keyboardType(.numberPad)
                                .textFieldStyle(.roundedBorder)
                                .onChange(of: pin) { _, new in
                                    pin = String(new.filter(\.isNumber).prefix(8))
                                }
                            if registration.state == .registering {
                                ProgressView("Páruji…")
                                Button("Zrušit") { registration.cancel() }
                            } else {
                                GLPrimaryButton(title: "Spárovat",
                                    systemImage: "link",
                                    disabled: validPIN == nil ||
                                        accountID?.count != 8 ||
                                        host.trimmingCharacters(in: .whitespaces).isEmpty) {
                                    guard let accountID, let pin = validPIN else { return }
                                    ps.register(address: host, name: consoleName,
                                                isPS5: isPS5, accountID: accountID,
                                                pin: pin)
                                }
                            }
                            switch registration.state {
                            case .failed(let reason):
                                Text(reason).font(.caption).foregroundStyle(.orange)
                            case .succeeded:
                                Label("PlayStation spárován",
                                      systemImage: "checkmark.circle.fill")
                                    .foregroundStyle(GLStyle.green)
                            default: EmptyView()
                            }
                        }
                    }
                }
                .padding(18)
            }
            .background(GLNeonBackground())
            .navigationTitle("PlayStation")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hotovo") { dismiss() }
                }
            }
        }
        .tint(GLNeon.cyan)
        .preferredColorScheme(.dark)
        .onAppear {
            ps.discover()
            devices.refreshPlayStation(ps.registered)
        }
        .onDisappear { ps.stopDiscovery() }
        .onChange(of: registration.state) { _, newState in
            if newState == .succeeded {
                devices.refreshPlayStation(ps.registered)
                if let console = ps.registered.first(where: { $0.address == host }) {
                    devices.addRegisteredPlayStation(console)
                }
                // Do not keep a one-time PlayStation PIN in SwiftUI state.
                pin = ""
                accountID = nil
                selectedStep = 0
            }
        }
    }
}
