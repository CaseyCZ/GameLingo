import SwiftUI

// GameLingo 0.5 — original navigation, native SwiftUI controls.
// Remote Play, OCR, pairing and translation remain in CaptionAppModel.
private enum GLVisual {
    // The dashboard reuses the same tokens as every other GameLingo screen.
    // Do not allow an independent look to drift from settings or onboarding.
    static let navy = GLStyle.background
    static let card = GLStyle.surface
    static let border = GLStyle.border
    static let cyan = GLStyle.blue
    static let violet = GLStyle.violet
    static let muted = GLStyle.muted
    static let green = GLStyle.green
    static let accent = GLStyle.gradient
}

struct GameLingoHome: View {
    @EnvironmentObject private var model: CaptionAppModel
    @ObservedObject var devices: GameLingoDevices

    @State private var tab: Page = .home
    @State private var xboxSheet = false
    @State private var playStationSheet = false
    @State private var tvSheet = false
    @State private var setupSheet = false
    @State private var regionsSheet = false
    @State private var profilesSheet = false
    @State private var settingsSheet = false
    @State private var licenseSheet = false
    @State private var showPSLoginPIN = false
    @State private var psLoginPIN = ""

    private enum Page: String, CaseIterable, Identifiable {
        case home = "Domů"
        case regions = "Obrazovka"
        case devices = "Konzole"
        case settings = "Nastavení"
        case about = "O aplikaci"

        var id: String { rawValue }
        var icon: String {
            switch self {
            case .home: return "house.fill"
            case .regions: return "rectangle.dashed"
            case .devices: return "gamecontroller"
            case .settings: return "slider.horizontal.3"
            case .about: return "info.circle"
            }
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 15) {
                        brandHeader
                        switch tab {
                        case .home: homePage
                        case .regions: regionsPage
                        case .devices: devicesPage
                        case .settings: GameLingoSettingsOverview(model: model)
                        case .about: aboutPage
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 24)
                }
                bottomTabs
            }
            .background(GLNeonBackground())
            .toolbar(.hidden, for: .navigationBar)
        }
        .tint(GLVisual.cyan)
        .preferredColorScheme(.dark)
        .sheet(isPresented: $xboxSheet) { xboxScreen }
        .sheet(isPresented: $playStationSheet) {
            GameLingoPlayStationSetup(devices: devices)
                .environmentObject(model)
        }
        .sheet(isPresented: $tvSheet) { tvScreen }
        .sheet(isPresented: $setupSheet) {
            GameLingoSetupView(devices: devices, onFinished: {})
                .environmentObject(model)
        }
        .onAppear {
            devices.refreshXbox(model.xbox.consoles)
            devices.refreshPlayStation(model.playStation.registered)
            devices.prepareTV(model.tv)
            if !devices.completedSetup { setupSheet = true }
        }
        .onChange(of: devices.preferredTelevisionID) { previous, selected in
            // prepareTV updates the IP; PlayCaptionTV clears both overlays on
            // the former receiver before replacing its authenticated session.
            if previous != selected { devices.prepareTV(model.tv) }
        }
        .onChange(of: devices.subtitleOutput) { previous, output in
            if previous == .googleTV && output == .phone {
                // The saved TV may differ from an IP manually typed in setup.
                // Always clear the selected receiver rather than a new target.
                let previousIP = devices.chosenTelevision?.ipAddress ?? model.tv.ip
                model.tv.clearOverlays(at: previousIP)
            } else if output == .googleTV {
                devices.prepareTV(model.tv)
            }
        }
        .onReceive(model.xbox.$consoles) { consoles in
            devices.refreshXbox(consoles)
        }
        .onReceive(model.playStation.registrations.$consoles) { consoles in
            devices.refreshPlayStation(consoles)
        }
        .onReceive(model.playStation.$needsLoginPIN) { required in
            guard required else { return }
            psLoginPIN = ""
            showPSLoginPIN = true
        }
        .alert("Přihlašovací PIN PlayStation", isPresented: $showPSLoginPIN) {
            SecureField("PIN", text: $psLoginPIN)
                .keyboardType(.numberPad)
            Button("Potvrdit") {
                model.playStation.submitLoginPIN(psLoginPIN)
                psLoginPIN = ""
            }
            Button("Zrušit", role: .cancel) {
                model.stop()
                psLoginPIN = ""
            }
        } message: {
            Text("Zadej PIN uživatele konzole, ne osmimístný párovací kód.")
        }
        .sheet(isPresented: $regionsSheet) { SubtitleSettingsView(model: model) }
        .sheet(isPresented: $profilesSheet) {
            NavigationStack {
                GameLingoProfilesScreen(model: model)
            }
            .preferredColorScheme(.dark)
        }
        .sheet(isPresented: $settingsSheet) {
            GameLingoSettingsView(model: model)
        }
        .sheet(isPresented: $licenseSheet) {
            NavigationStack {
                GameLingoLicenseView()
            }
            .preferredColorScheme(.dark)
        }
    }

    private var brandHeader: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                GLBrandWordmark(compact: true)
                Text("Titulky a překlady her v reálném čase")
                    .font(.caption)
                    .foregroundStyle(GLNeon.mist)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
            GLHelpButton(title: "GameLingo", explanation:
                "Vyber Xbox nebo PlayStation, nastav výstup titulků na telefon či Google TV, uprav čtyři oblasti OCR a spusť překlad.")
            Button { tab = .settings } label: {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 43, height: 43)
                    .background(GLNeon.panel,
                                in: RoundedRectangle(cornerRadius: 13))
                    .overlay(RoundedRectangle(cornerRadius: 13)
                        .strokeBorder(GLNeon.edge, lineWidth: 1))
            }
            .accessibilityLabel("Nastavení")
        }
        .padding(.bottom, 10)
    }

    private var heroBanner: some View {
        GLNeonPanel(emphasized: true, radius: 23) {
            VStack(alignment: .leading, spacing: 15) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("HRAJ. ROZUMĚJ.")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .tracking(2)
                            .foregroundStyle(GLNeon.cyan)
                        Text("Překlad přímo při hraní")
                            .font(.system(size: 19, weight: .heavy, design: .rounded))
                            .foregroundStyle(.white)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "waveform")
                        .font(.title2)
                        .foregroundStyle(GLNeon.accent)
                        .shadow(color: GLNeon.purple.opacity(0.55), radius: 10)
                }
                HStack(spacing: 12) {
                    GLPlatformBadge(family: .xbox, size: 52)
                    Text("Xbox")
                        .font(.subheadline.bold())
                        .foregroundStyle(.white)
                    Spacer(minLength: 8)
                    Rectangle()
                        .fill(GLNeon.edge)
                        .frame(width: 1, height: 42)
                    Spacer(minLength: 8)
                    GLPlatformBadge(family: .playStation, size: 52)
                    Text("PlayStation")
                        .font(.subheadline.bold())
                        .foregroundStyle(.white)
                }
            }
            .padding(17)
        }
    }

    private var activeConsoleName: String {
        devices.chosenConsole?.name ?? "Vybrat konzoli"
    }

    private var activeConsoleStatus: String {
        guard model.isRunning else {
            return consoleActuallyReady ? "Nastaveno" : "Připojit"
        }
        // Remote Play creation is asynchronous. No decoded video frame means
        // we are still connecting, not actively translating the game.
        return model.frameCount > 0 ? "Snímání aktivní" : "Připojuji…"
    }

    private var consoleActuallyReady: Bool {
        guard let choice = devices.chosenConsole else { return false }
        if choice.family == .xbox {
            return model.xbox.signedIn && devices.activeXbox(from: model.xbox) != nil
        }
        return model.playStation.findSavedConsole(id: choice.id) != nil
    }

    private var outputActuallyReady: Bool {
        if devices.subtitleOutput == .phone { return true }
        guard let selectedTV = devices.chosenTelevision else { return false }
        return model.tv.receiverConnected &&
            model.tv.ip.trimmingCharacters(in: .whitespacesAndNewlines) ==
            selectedTV.ipAddress
    }

    private func openSelectedConsole() {
        switch devices.chosenConsole?.family {
        case .xbox: xboxSheet = true
        case .playStation: playStationSheet = true
        case nil: setupSheet = true
        }
    }

    private func startCurrentSource() {
        guard !model.isRunning else {
            model.stop()
            return
        }
        guard let record = devices.chosenConsole else {
            setupSheet = true
            return
        }
        // Align the receiver before checking readiness, not after the
        // stream has started. This matters when switching between TVs.
        devices.prepareTV(model.tv)
        guard outputActuallyReady else {
            if devices.subtitleOutput == .googleTV { tvSheet = true }
            return
        }
        if record.family == .xbox {
            guard let xbox = devices.activeXbox(from: model.xbox) else {
                xboxSheet = true
                return
            }
            model.start(xbox)
        } else {
            guard let ps = model.playStation.findSavedConsole(id: record.id) else {
                playStationSheet = true
                return
            }
            model.start(ps)
        }
    }

    private var homePage: some View {
        VStack(spacing: 14) {
            heroBanner
            HStack {
                Text("Moje zařízení")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                Spacer()
                Button { tab = .devices } label: {
                    Label("Přidat", systemImage: "plus")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(GLNeon.cyan)
                }
                .accessibilityLabel("Přidat zařízení")
            }
            Button { openSelectedConsole() } label: {
                GameLingoDeviceRow(
                    icon: devices.chosenConsole?.family.icon ?? "gamecontroller",
                    name: activeConsoleName,
                    subtitle: devices.chosenConsole?.family.rawValue ?? "Konzole",
                    connected: model.isRunning && model.frameCount > 0,
                    status: activeConsoleStatus,
                    family: devices.chosenConsole?.family
                )
            }
            .buttonStyle(.plain)
            Button {
                if devices.subtitleOutput == .phone { tab = .devices }
                else { tvSheet = true }
            } label: {
                GameLingoDeviceRow(
                    icon: devices.subtitleOutput == .phone ? "smartphone" : "tv.fill",
                    name: devices.subtitleOutput.rawValue,
                    subtitle: "",
                    connected: outputActuallyReady,
                    status: devices.subtitleOutput == .phone ? "Na telefonu" :
                        (outputActuallyReady ? "TV připojena" : "Spárovat"),
                    isTelevision: devices.subtitleOutput == .googleTV
                )
            }
            .buttonStyle(.plain)
            actionButton
            LazyVGrid(columns: [GridItem(.flexible()),
                                GridItem(.flexible())], spacing: 11) {
                tile("Oblasti", icon: "rectangle.dashed") { regionsSheet = true }
                tile("Profily her", icon: "gamecontroller") { profilesSheet = true }
                tile("Nastavení překladu", icon: "slider.horizontal.3") { tab = .settings }
                tile("Zařízení", icon: "display.2") { tab = .devices }
            }
            readiness
            if devices.subtitleOutput == .phone && !model.phoneSubtitle.isEmpty {
                GLCard {
                    GLHeader(title: "Dialog")
                    Text(model.phoneSubtitle)
                        .font(.system(size: 23 * model.phoneFontScale,
                                      weight: .bold))
                        .foregroundStyle(model.phoneTextColor.color)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .padding(.horizontal, 9)
                        .background(model.phoneBackground.fill,
                                    in: RoundedRectangle(cornerRadius: 12))
                }
            }
            if devices.subtitleOutput == .phone && !model.lastChoices.isEmpty {
                GLCard {
                    GLHeader(title: "Herní texty")
                    Text(model.lastChoices)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.yellow)
                }
            }
            if devices.subtitleOutput == .googleTV && !model.lastCzech.isEmpty {
                GLCard {
                    Text(model.lastCzech)
                        .font(.headline)
                        .foregroundStyle(.white)
                }
            }
        }
    }

    private var actionButton: some View {
        Button(action: startCurrentSource) {
            HStack(spacing: 13) {
                Image(systemName: model.isRunning ? "stop.fill" : "play.fill")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(.white.opacity(0.18), in: Circle())
                Text(model.isRunning ? "Zastavit překlad" : "Spustit překlad")
                    .font(.system(size: 20, weight: .heavy, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.84)
                Spacer(minLength: 0)
                Image(systemName: "arrow.right")
                    .font(.system(size: 17, weight: .semibold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .frame(height: 66)
            .background(GLNeon.accent,
                        in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20)
                .strokeBorder(.white.opacity(0.35), lineWidth: 1))
            .shadow(color: GLNeon.purple.opacity(0.35), radius: 16, y: 7)
        }
        .buttonStyle(.plain)
        .accessibilityHint(model.isRunning
                           ? "Ukončit aktuální Remote Play a překlad"
                           : "Spustit překlad z vybrané konzole")
    }

    private var readiness: some View {
        GLNeonPanel(emphasized: consoleActuallyReady && outputActuallyReady) {
            HStack(spacing: 13) {
                Image(systemName: model.isRunning ? "waveform" :
                      (consoleActuallyReady && outputActuallyReady
                       ? "checkmark.circle.fill" : "exclamationmark.circle"))
                    .font(.system(size: 29, weight: .medium))
                    .foregroundStyle(consoleActuallyReady && outputActuallyReady
                                     ? GLStyle.green : GLNeon.mist)
                    .shadow(color: GLStyle.green.opacity(0.20), radius: 9)
                VStack(alignment: .leading, spacing: 4) {
                    Text(model.isRunning ? model.status :
                         (consoleActuallyReady && outputActuallyReady
                          ? "Připraveno ke spuštění" :
                             (!consoleActuallyReady
                              ? "Nejprve připoj konzoli" : "Spáruj televizi")))
                        .font(.subheadline.bold())
                        .foregroundStyle(consoleActuallyReady && outputActuallyReady
                                         ? GLStyle.green : .white)
                    Text(model.isRunning
                         ? "Sleduj živý stav překladu"
                         : "Překlad se spustí až po ověření konzole a výstupu.")
                        .font(.caption)
                        .foregroundStyle(GLNeon.mist)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                GLHelpButton(title: "Stav překladu",
                    explanation: "Uložená konzole není automaticky připojená. Snímání aktivní se objeví až po přijetí video snímků; Google TV odpovídá na kontrolu spojení.")
            }
            .padding(15)
        }
    }

    private var regionsPage: some View {
        VStack(alignment: .leading, spacing: 14) {
            GLNeonTitle(title: "Oblasti obrazovky",
                        subtitle: "Čtyři nezávislé oblasti pro rozpoznávání a překlad",
                        icon: "viewfinder")
            Button { regionsSheet = true } label: {
                HStack {
                    Image(systemName: "rectangle.dashed")
                        .foregroundStyle(GLVisual.cyan)
                    Text("Otevřít editor oblastí")
                    Spacer()
                    Image(systemName: "arrow.up.right")
                }
                .padding(18)
                .background(GLNeon.panel, in: RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            areaLine("Modrá", "Čtení dialogů", .blue)
            areaLine("Zelená", "Výstup přeložených titulků", .green)
            areaLine("Žlutá", "Čtení herních textů a voleb", .yellow)
            areaLine("Oranžová", "Překlad herních textů", .orange)
        }
    }

    private func areaLine(_ name: String, _ detail: String, _ color: Color) -> some View {
        HStack(spacing: 11) {
            RoundedRectangle(cornerRadius: 5).fill(color).frame(width: 9, height: 34)
            VStack(alignment: .leading, spacing: 3) {
                Text(LocalizedStringKey(name)).font(.subheadline.bold())
                Text(LocalizedStringKey(detail)).font(.caption).foregroundStyle(GLVisual.muted)
            }
            Spacer()
        }
        .padding(14)
        .background(GLNeon.panel, in: RoundedRectangle(cornerRadius: 15))
        .overlay(RoundedRectangle(cornerRadius: 15)
            .strokeBorder(GLNeon.edge.opacity(0.65), lineWidth: 1))
    }

    private var profilesPage: some View {
        VStack(alignment: .leading, spacing: 14) {
            GLNeonTitle(title: "Profily her",
                        subtitle: "Pro každou hru vlastní oblasti, rychlost OCR a titulky",
                        icon: "gamecontroller")
            Button { profilesSheet = true } label: {
                Label("Spravovat profily her", systemImage: "gamecontroller.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(17)
                    .background(GLVisual.accent, in: RoundedRectangle(cornerRadius: 15))
            }
            .buttonStyle(.plain)
            ForEach(model.gameProfiles) { profile in
                HStack {
                    Image(systemName: "gamecontroller")
                        .foregroundStyle(GLVisual.cyan)
                    Text(profile.name)
                    Spacer()
                    Button("Použít") { model.applyGameProfile(profile) }
                        .disabled(model.isRunning)
                        .font(.subheadline.bold())
                }
                .padding(15)
                .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 14))
            }
            if model.gameProfiles.isEmpty {
                Text("Zatím nemáš žádný uložený profil.")
                    .font(.subheadline)
                    .foregroundStyle(GLVisual.muted)
            }
        }
    }

    private var devicesPage: some View {
        GameLingoDevicesPanel(
            devices: devices,
            addXbox: { xboxSheet = true },
            addPlayStation: { playStationSheet = true },
            addTV: { tvSheet = true }
        )
    }

    private var aboutPage: some View {
        VStack(alignment: .leading, spacing: 15) {
            GLNeonTitle(title: "O aplikaci",
                        subtitle: "GameLingo – hraj a rozuměj svým hrám",
                        icon: "info.circle")
            GLNeonPanel(emphasized: true) {
                VStack(spacing: 11) {
                    GLBrandWordmark()
                    Text("Překlad herních dialogů a textů")
                        .font(.subheadline)
                        .foregroundStyle(GLNeon.mist)
                    Text("Verze 0.5 Beta")
                        .font(.caption.bold())
                        .foregroundStyle(GLNeon.cyan)
                }
                .frame(maxWidth: .infinity)
                .padding(23)
            }
            Button {
                licenseSheet = true
            } label: {
                GLDetailRow(icon: "doc.text",
                            title: "Licence (AGPLv3)",
                            detail: "Otevřený kód a původní autoři")
            }
            .buttonStyle(.plain)
            Link(destination: URL(string: "https://github.com/CaseyCZ/GameLingo")!) {
                GLDetailRow(icon: "chevron.left.forwardslash.chevron.right",
                            title: "Zdrojový kód (GitHub)",
                            detail: "GameLingo – veřejný repozitář")
            }
            .buttonStyle(.plain)
            GLNeonPanel {
                Text("GameLingo je nezávislý projekt a není oficiální aplikací Sony ani Microsoftu.")
                    .font(.caption)
                    .foregroundStyle(GLNeon.mist)
                    .padding(16)
            }
        }
    }

    private func infoRow(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(LocalizedStringKey(title)).font(.headline)
            Text(LocalizedStringKey(text)).font(.subheadline).foregroundStyle(GLVisual.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(15)
        .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 15))
    }

    private func sectionTitle(_ title: String, detail: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(LocalizedStringKey(title)).font(.title2.bold())
            Text(LocalizedStringKey(detail)).font(.subheadline).foregroundStyle(GLVisual.muted)
        }
        .padding(.vertical, 6)
    }

    private func tile(_ title: String, icon: String,
                      action: @escaping () -> Void) -> some View {
        Button(action: action) {
            GLNeonPanel(radius: 19) {
                VStack(spacing: 10) {
                    Image(systemName: icon)
                        .font(.system(size: 27, weight: .medium))
                        .foregroundStyle(GLNeon.accent)
                    Text(LocalizedStringKey(title))
                        .font(.system(size: 14, weight: .bold,
                                      design: .rounded))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .minimumScaleFactor(0.85)
                        .frame(maxWidth: .infinity)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 96)
                .padding(.horizontal, 5)
            }
        }
        .buttonStyle(.plain)
    }

    private var bottomTabs: some View {
        HStack(spacing: 0) {
            ForEach(Page.allCases) { option in
                Button {
                    withAnimation(.easeInOut(duration: 0.16)) { tab = option }
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: option.icon).font(.system(size: 20))
                        Text(LocalizedStringKey(option.rawValue))
                            .font(.system(size: 10, weight: .medium))
                    }
                    .foregroundStyle(tab == option ? .white : GLNeon.mist)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(
                        tab == option
                        ? GLNeon.purple.opacity(0.32) : Color.clear,
                        in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(tab == option ? GLNeon.purple.opacity(0.60) :
                                      .clear, lineWidth: 1))
                    .shadow(color: tab == option ? GLNeon.purple.opacity(0.24) :
                             .clear, radius: 10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 9)
        .padding(.top, 8)
        .padding(.bottom, 5)
        .background(GLNeon.ink.opacity(0.98))
        .overlay(alignment: .top) {
            Rectangle().fill(GLNeon.edge).frame(height: 1)
        }
    }

    private var xboxScreen: some View {
        GameLingoXboxScreen(devices: devices, auth: model.xbox)
            .environmentObject(model)
    }

    private var tvScreen: some View {
        GameLingoTVSetupScreen(devices: devices, tv: model.tv) {
            tvSheet = false
        }
    }
}

private struct GameLingoDeviceRow: View {
    let icon: String
    let name: String
    let subtitle: String
    let connected: Bool
    let status: String
    var family: GLConsoleFamily? = nil
    var isTelevision: Bool = false

    var body: some View {
        GLNeonPanel(emphasized: connected) {
            HStack(spacing: 13) {
                if family != nil || isTelevision {
                    GLPlatformBadge(family: family, isTelevision: isTelevision,
                                    size: 54)
                } else {
                    Image(systemName: icon)
                        .font(.system(size: 23))
                        .foregroundStyle(GLNeon.accent)
                        .frame(width: 54, height: 54)
                        .background(GLNeon.panel,
                                    in: RoundedRectangle(cornerRadius: 15))
                }
                VStack(alignment: .leading, spacing: 5) {
                    Text(name)
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    HStack(spacing: 7) {
                        Circle()
                            .fill(connected ? GLStyle.green : GLNeon.mist)
                            .frame(width: 7, height: 7)
                        Text(LocalizedStringKey(status))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(connected ? GLStyle.green :
                                                GLNeon.mist)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(GLNeon.mist)
            }
            .padding(14)
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint("Otevřít a spravovat zařízení")
    }
}
