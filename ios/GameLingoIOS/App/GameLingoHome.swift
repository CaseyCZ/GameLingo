import SwiftUI

// GameLingo 0.5 — original navigation, native SwiftUI controls.
// Remote Play, OCR, pairing and translation remain in CaptionAppModel.
private enum GLVisual {
    static let navy = Color(red: 7/255, green: 11/255, blue: 20/255)
    static let card = Color(red: 17/255, green: 24/255, blue: 39/255)
    static let border = Color(red: 43/255, green: 54/255, blue: 83/255)
    static let cyan = Color(red: 17/255, green: 199/255, blue: 249/255)
    static let violet = Color(red: 135/255, green: 80/255, blue: 255/255)
    static let muted = Color(red: 148/255, green: 163/255, blue: 184/255)
    static let green = Color(red: 53/255, green: 229/255, blue: 125/255)
    static let accent = LinearGradient(
        colors: [violet, Color(red: 63/255, green: 109/255, blue: 255/255), cyan],
        startPoint: .leading, endPoint: .trailing)
}

struct GameLingoHome: View {
    @EnvironmentObject private var model: CaptionAppModel
    @ObservedObject var devices: GameLingoDevices

    @State private var tab: Page = .home
    @State private var xboxSheet = false
    @State private var playStationSheet = false
    @State private var tvSheet = false
    @State private var setupSheet = false
    @State private var playStationLoginPIN = ""
    @State private var regionsSheet = false
    @State private var profilesSheet = false
    @State private var settingsSheet = false
    @State private var showPSLoginPIN = false
    @State private var psLoginPIN = ""

    private enum Page: String, CaseIterable, Identifiable {
        case home = "Domů"
        case regions = "Oblasti"
        case profiles = "Profily"
        case devices = "Zařízení"
        case about = "O aplikaci"
        var id: String { rawValue }
        var icon: String {
            switch self {
            case .home: return "house.fill"
            case .regions: return "rectangle.dashed"
            case .profiles: return "gamecontroller.fill"
            case .devices: return "tv"
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
                        case .profiles: profilesPage
                        case .devices: devicesPage
                        case .about: aboutPage
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 24)
                }
                bottomTabs
            }
            .background {
                ZStack {
                    GLVisual.navy
                    RadialGradient(
                        colors: [GLVisual.violet.opacity(0.18), .clear],
                        center: .topTrailing, startRadius: 10, endRadius: 490)
                    RadialGradient(
                        colors: [GLVisual.cyan.opacity(0.09), .clear],
                        center: .topLeading, startRadius: 10, endRadius: 360)
                }
                .ignoresSafeArea()
            }
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
            if previous != selected && model.isRunning &&
                devices.subtitleOutput == .googleTV {
                // Remove the previous receiver's overlay before changing its IP.
                model.tv.sendSubtitle("", duration: 0)
                model.tv.sendChoices([], area: model.choicesArea,
                                     outputArea: model.choicesOutputArea)
            }
            devices.prepareTV(model.tv)
        }
        .onChange(of: devices.subtitleOutput) { previous, output in
            if previous == .googleTV && output == .phone {
                // Switching to phone output should clear stale TV captions.
                model.tv.sendSubtitle("", duration: 0)
                model.tv.sendChoices([], area: model.choicesArea,
                                     outputArea: model.choicesOutputArea)
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
            PlayCaptionSettingsView(model: model)
        }
    }

    private var brandHeader: some View {
        HStack(alignment: .center, spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(GLVisual.card)
                    .frame(width: 53, height: 53)
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(GLVisual.accent, lineWidth: 1.4)
                    .frame(width: 53, height: 53)
                Image(systemName: "gamecontroller.fill")
                    .font(.system(size: 27, weight: .semibold))
                    .foregroundStyle(GLVisual.accent)
            }
            VStack(alignment: .leading, spacing: 1) {
                (Text("Game").foregroundColor(.white)
                 + Text("Lingo").foregroundColor(GLVisual.violet))
                    .font(.system(size: 27, weight: .bold, design: .rounded))
                Text("Hraj. Rozuměj.")
                    .font(.caption)
                    .foregroundStyle(GLVisual.muted)
            }
            Spacer()
            GLHelpButton(title: "GameLingo", explanation: "Zvol Xbox a Google TV. Pak spusť překlad. Oblasti určí, odkud číst dialogy a kde zobrazovat překlad. Profily uloží nastavení pro jednotlivé hry.")
            Button { settingsSheet = true } label: {
                Image(systemName: "gearshape.fill")
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 45, height: 45)
                    .background(GLVisual.card, in: Circle())
                    .overlay(Circle().strokeBorder(GLVisual.border, lineWidth: 1))
            }
            .accessibilityLabel("Nastavení")
        }
        .padding(.bottom, 6)
    }

    private var activeConsoleName: String {
        devices.chosenConsole?.name ?? "Vybrat konzoli"
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
        VStack(spacing: 12) {
            HStack {
                Text("Moje hra").font(.headline)
                Spacer()
                Button {
                    tab = .devices
                } label: {
                    Label("Přidat", systemImage: "plus.circle.fill")
                        .font(.subheadline.weight(.semibold))
                }
            }
            Button { openSelectedConsole() } label: {
                GameLingoDeviceRow(
                    icon: devices.chosenConsole?.family.icon ?? "gamecontroller",
                    name: activeConsoleName,
                    subtitle: devices.chosenConsole?.family.rawValue ?? "Konzole",
                    connected: consoleActuallyReady,
                    status: consoleActuallyReady ? "Připraveno" : "Připojit"
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
                    status: outputActuallyReady ? "Připraveno" : "Spárovat"
                )
            }
            .buttonStyle(.plain)
            actionButton
            LazyVGrid(columns: [GridItem(.flexible()),
                                GridItem(.flexible())], spacing: 11) {
                tile("Oblasti", icon: "rectangle.dashed") { regionsSheet = true }
                tile("Profily her", icon: "gamecontroller") { profilesSheet = true }
                tile("Překlad", icon: "slider.horizontal.3") { settingsSheet = true }
                tile("Zařízení", icon: "display.2") { tab = .devices }
            }
            readiness
            if model.isRunning &&
                devices.chosenConsole?.family == .playStation &&
                model.playStation.status.localizedCaseInsensitiveContains("PIN") {
                GLCard {
                    GLHeader(title: "PIN PlayStation",
                        help: "Zadej přihlašovací kód požadovaný PlayStation Remote Play při navazování streamu. To není osmimístný PIN pro prvotní spárování.")
                    SecureField("Přihlašovací PIN", text: $playStationLoginPIN)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: playStationLoginPIN) { _, value in
                            playStationLoginPIN = String(value.filter(\.isNumber).prefix(8))
                        }
                    GLPrimaryButton(title: "Potvrdit PIN",
                                    systemImage: "lock.open",
                                    disabled: playStationLoginPIN.isEmpty) {
                        model.playStation.submitLoginPIN(playStationLoginPIN)
                        playStationLoginPIN = ""
                    }
                }
            }
            if devices.subtitleOutput == .phone && !model.phoneSubtitle.isEmpty {
                GLCard {
                    GLHeader(title: "Dialog")
                    Text(model.phoneSubtitle)
                        .font(.system(size: 23, weight: .bold))
                        .foregroundStyle(.white)
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
            HStack(spacing: 12) {
                Image(systemName: model.isRunning ? "stop.fill" : "play.circle.fill")
                    .font(.title3)
                Text(model.isRunning ? "Zastavit překlad" : "Spustit překlad")
                    .font(.headline)
                Spacer()
                Image(systemName: "arrow.right")
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 21)
            .frame(height: 56)
            .background(GLVisual.accent, in: RoundedRectangle(cornerRadius: 15))
        }
        .buttonStyle(.plain)
    }

    private var readiness: some View {
        HStack(spacing: 12) {
            Image(systemName: model.isRunning ? "waveform" :
                  (consoleActuallyReady && outputActuallyReady
                   ? "checkmark.circle.fill" : "circle.dashed"))
                .foregroundStyle(consoleActuallyReady && outputActuallyReady
                                 ? GLVisual.green : GLVisual.muted)
            Text(model.isRunning
                 ? model.status
                 : (!consoleActuallyReady
                    ? "Připoj konzoli" :
                       (!outputActuallyReady ? "Spáruj TV" : "Připraveno")))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
            Spacer()
            GLHelpButton(title: "Stav překladu",
                explanation: "Stav Připraveno znamená, že je vybraná skutečně registrovaná konzole a připravený výstup titulků. Uložení IP adresy samo o sobě nestačí k párování.")
        }
        .padding(14)
        .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 16))
    }

    private var regionsPage: some View {
        VStack(alignment: .leading, spacing: 14) {
            sectionTitle("Oblasti obrazovky", detail: "Čtyři nezávislé oblasti pro rozpoznávání a překlad")
            Button { regionsSheet = true } label: {
                HStack {
                    Image(systemName: "rectangle.dashed")
                        .foregroundStyle(GLVisual.cyan)
                    Text("Otevřít editor oblastí")
                    Spacer()
                    Image(systemName: "arrow.up.right")
                }
                .padding(18)
                .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            areaLine("Modrá", "Čtení dialogů", .blue)
            areaLine("Zelená", "Výstup českých titulků", .green)
            areaLine("Žlutá", "Čtení herních textů a voleb", .yellow)
            areaLine("Oranžová", "Překlad herních textů", .orange)
        }
    }

    private func areaLine(_ name: String, _ detail: String, _ color: Color) -> some View {
        HStack(spacing: 11) {
            RoundedRectangle(cornerRadius: 5).fill(color).frame(width: 9, height: 34)
            VStack(alignment: .leading, spacing: 3) {
                Text(name).font(.subheadline.bold())
                Text(detail).font(.caption).foregroundStyle(GLVisual.muted)
            }
            Spacer()
        }
        .padding(14)
        .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 15))
    }

    private var profilesPage: some View {
        VStack(alignment: .leading, spacing: 14) {
            sectionTitle("Profily her", detail: "Pro každou hru vlastní oblasti, rychlost OCR a titulky")
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
            sectionTitle("O aplikaci", detail: "GameLingo – hraj a rozuměj svým hrám")
            infoRow("GameLingo", "Překlad herních dialogů a textů na telefonu i TV")
            infoRow("iPhone", "Xbox / PlayStation 5 / PlayStation 4")
            infoRow("Google TV", "Přenos českých titulků přes domácí síť")
            infoRow("Autor", "CaseyCZ")
            Text("GameLingo je nezávislý open-source projekt navazující na PlayCaption. Xbox, PlayStation a Google TV jsou ochranné známky svých vlastníků.")
                .font(.caption)
                .foregroundStyle(GLVisual.muted)
                .padding(15)
        }
    }

    private func infoRow(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.headline)
            Text(text).font(.subheadline).foregroundStyle(GLVisual.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(15)
        .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 15))
    }

    private func sectionTitle(_ title: String, detail: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.title2.bold())
            Text(detail).font(.subheadline).foregroundStyle(GLVisual.muted)
        }
        .padding(.vertical, 6)
    }

    private func tile(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 27))
                    .foregroundStyle(.white)
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 92)
            .padding(5)
            .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 17))
            .overlay(RoundedRectangle(cornerRadius: 17)
                .strokeBorder(GLVisual.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var bottomTabs: some View {
        HStack(spacing: 0) {
            ForEach(Page.allCases) { option in
                Button {
                    tab = option
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: option.icon).font(.system(size: 20))
                        Text(option.rawValue).font(.system(size: 10, weight: .medium))
                    }
                    .foregroundStyle(tab == option ? GLVisual.violet : GLVisual.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        tab == option ? GLVisual.violet.opacity(0.14) : Color.clear,
                        in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 9)
        .padding(.top, 8)
        .padding(.bottom, 5)
        .background(.regularMaterial)
        .overlay(alignment: .top) {
            Rectangle().fill(GLVisual.border).frame(height: 1)
        }
    }

    private var xboxScreen: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    XboxAccountView(auth: model.xbox)
                    XboxRemotePlayView(auth: model.xbox)
                }
                .padding(18)
            }
            .background(GLVisual.navy.ignoresSafeArea())
            .navigationTitle("Xbox")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hotovo") { xboxSheet = false }
                }
            }
        }
        .tint(GLVisual.cyan)
    }

    private var tvScreen: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 13) {
                    TVSelectionView(tv: model.tv)
                }
                .padding(18)
            }
            .background(GLVisual.navy.ignoresSafeArea())
            .navigationTitle("Google TV")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hotovo") { tvSheet = false }
                }
            }
        }
        .tint(GLVisual.cyan)
    }
}

private struct GameLingoDeviceRow: View {
    let icon: String
    let name: String
    let subtitle: String
    let connected: Bool
    let status: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(GLVisual.cyan)
                .frame(width: 50, height: 50)
                .background(GLVisual.navy, in: RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 4) {
                Text(name).font(.headline).foregroundStyle(.white)
                HStack(spacing: 5) {
                    Circle()
                        .fill(connected ? GLVisual.green : GLVisual.muted)
                        .frame(width: 7, height: 7)
                    Text(status)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(connected ? GLVisual.green : GLVisual.muted)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .foregroundStyle(GLVisual.muted)
        }
        .padding(14)
        .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18)
            .strokeBorder(GLVisual.border, lineWidth: 1))
    }
}
