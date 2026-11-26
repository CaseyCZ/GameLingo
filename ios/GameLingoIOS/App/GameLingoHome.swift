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

    @StateObject private var updates = GameLingoUpdateModel()
    @State private var showUpdateOffer = false
    @State private var offeredVersion: String?
    @State private var tab: Page = .home
    @State private var xboxSheet = false
    @State private var playStationSheet = false
    @State private var tvSheet = false
    @State private var setupSheet = false
    @State private var regionsSheet = false
    @State private var profilesSheet = false
    @State private var settingsSheet = false
    @State private var modeSheet = false
    @State private var appearanceSheet = false
    @State private var licenseSheet = false
    @State private var showPSLoginPIN = false
    @State private var psLoginPIN = ""

    private enum Page: Int, CaseIterable, Identifiable {
        case home, regions, devices, settings, about

        var id: Int { rawValue }
        var title: String { GameLingoUIContract.tabs[rawValue].title }
        var icon: String { GameLingoUIContract.tabs[rawValue].symbol }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .center, spacing: 11) {
                        if tab == .home { brandHeader }
                        switch tab {
                        case .home: homePage
                        case .regions: regionsPage
                        case .devices: devicesPage
                        case .settings:
                            GameLingoSettingsOverview(model: model) {
                                tab = .devices
                            }
                        case .about: aboutPage
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 16)
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
        .task { if !model.isRunning && devices.completedSetup { updates.checkAtStartup() } }
        .onChange(of: updates.available) { _, _ in offerUpdateIfReady() }
        .onChange(of: setupSheet) { _, open in
            if !open && !model.isRunning { updates.checkAtStartup(); offerUpdateIfReady() }
        }
        .onChange(of: model.isRunning) { _, running in
            if !running && devices.completedSetup { updates.checkAtStartup(); offerUpdateIfReady() }
        }
        .alert("Nová verze GameLingo", isPresented: $showUpdateOffer) {
            Button("Stáhnout IPA") {
                tab = .about
                updates.download()
            }
            Button("Později", role: .cancel) {}
        } message: {
            Text(verbatim: GLInterfaceText.localized("Nová verze:") + " " + (updates.available ?? "") +
                "\n" + GLInterfaceText.localized("Nepodepsaná IPA: instalaci a podpis provádíš vlastním nástrojem."))
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
        .sheet(isPresented: $regionsSheet) {
            if model.automaticGameDialogue {
                NavigationStack { GLAutomaticZonesView(model: model) }
            } else { SubtitleSettingsView(model: model) }
        }
        .sheet(isPresented: $profilesSheet) {
            NavigationStack {
                GameLingoProfilesScreen(model: model)
            }
            .preferredColorScheme(.dark)
        }
        .sheet(isPresented: $modeSheet) {
            NavigationStack {
                GLScreen(title: "Režim překladu", help:
                    "Standardní režim používá ověřené oblasti. Automatický režim rozpoznává dialogy v aktivních zónách. Režim měň před spuštěním překladu.") {
                    GLTranslationModePicker(model: model, showTitle: false)
                    NavigationLink {
                        GameLingoOCRSettingsView(model: model)
                    } label: {
                        GLDetailRow(icon: "slider.horizontal.3", title: "Pokročilé nastavení OCR", detail: "Citlivost, režim a filtrování textu")
                    }.buttonStyle(.plain)
                }
            }
            .preferredColorScheme(.dark)
        }
        .sheet(isPresented: $appearanceSheet) {
            NavigationStack {
                GameLingoAppearanceSettingsView(model: model)
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

    private func offerUpdateIfReady() {
        guard !model.isRunning, devices.completedSetup, !setupSheet,
              let version = updates.available, offeredVersion != version else { return }
        offeredVersion = version
        showUpdateOffer = true
    }

    private var brandHeader: some View {
        HStack(spacing: 10) {
            VStack(alignment: .center, spacing: 3) {
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
                    .frame(width: 44, height: 44)
                    .background(GLNeon.panel,
                                in: RoundedRectangle(cornerRadius: 13))
                    .overlay(RoundedRectangle(cornerRadius: 13)
                        .strokeBorder(GLNeon.edge, lineWidth: 1))
            }
            .accessibilityLabel("Nastavení")
        }
        .padding(.bottom, 5)
    }

    private var heroBanner: some View {
        GLNeonPanel(emphasized: true, radius: 23) {
            VStack(alignment: .center, spacing: 10) {
                HStack(alignment: .center) {
                    Color.clear.frame(width: 34, height: 34)
                    VStack(alignment: .center, spacing: 4) {
                        Text("HRAJ. ROZUMĚJ.")
                            .font(.system(size: 11, weight: .heavy, design: .rounded))
                            .tracking(2)
                            .foregroundStyle(GLNeon.cyan)
                        Text("Překlad přímo při hraní")
                            .font(.system(size: 17, weight: .heavy, design: .rounded))
                            .foregroundStyle(.white)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                    Image(systemName: "waveform")
                        .font(.title2)
                        .foregroundStyle(GLNeon.accent)
                        .shadow(color: GLNeon.purple.opacity(0.55), radius: 10)
                        .frame(width: 34, height: 34)
                }
                HStack(spacing: 8) {
                    VStack(alignment: .center, spacing: 6) {
                        GLPlatformBadge(family: .xbox, size: 42)
                        Text("Xbox")
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                    Rectangle()
                        .fill(GLNeon.edge)
                        .frame(width: 1, height: 58)
                    VStack(alignment: .center, spacing: 6) {
                        GLPlatformBadge(family: .playStation, size: 42)
                        Text("PlayStation")
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .padding(12)
        }
    }

    private var activeConsoleName: String {
        devices.chosenConsole?.name ?? GLInterfaceText.localized("Vybrat konzoli")
    }

    private var activeConsoleStatus: String {
        guard model.isRunning else {
            return consoleActuallyReady ? GLInterfaceText.localized("Nastaveno") : GLInterfaceText.localized("Připojit")
        }
        // Remote Play creation is asynchronous. No decoded video frame means
        // we are still connecting, not actively translating the game.
        return model.frameCount > 0 ? GLInterfaceText.localized("Snímání aktivní") : GLInterfaceText.localized("Připojuji…")
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
        VStack(spacing: 10) {
            heroBanner
            HStack {
                Text("Moje zařízení")
                    .font(.system(size: 18, weight: .bold, design: .rounded))
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
                                GridItem(.flexible())], spacing: 9) {
                tile("Oblasti", icon: "rectangle.dashed") { regionsSheet = true }
                tile("Profily her", icon: "gamecontroller") { profilesSheet = true }
                tile("Režim překladu", icon: "slider.horizontal.3") { modeSheet = true }
                tile("Vzhled titulků", icon: "paintpalette") { appearanceSheet = true }
            }
            readiness
            if devices.subtitleOutput == .phone && model.automaticGameDialogue {
                ForEach(model.automaticOutputs) { output in
                    GLCard {
                        Text(model.automaticZones.first { $0.id == output.zone }?.name ?? GLInterfaceText.localized("Překlad"))
                            .font(.caption)
                        Text(output.text).font(.headline)
                    }
                }
            }
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
                    GLHeader(title: "Přeložené herní texty")
                    Text(model.lastChoices)
                        .font(.system(size: 21 * model.phoneFontScale,
                                      weight: .semibold))
                        .foregroundStyle(model.phoneTextColor.color)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .padding(.horizontal, 9)
                        .background(model.phoneBackground.fill,
                                    in: RoundedRectangle(cornerRadius: 12))
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
                    .frame(width: 34, height: 34)
                    .background(.white.opacity(0.18), in: Circle())
                Text(LocalizedStringKey(model.isRunning ? "Zastavit překlad" : "Spustit překlad"))
                    .font(.system(size: 18, weight: .heavy, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.84)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                Image(systemName: "arrow.right")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 34, height: 34)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 15)
            .frame(height: 54)
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
                    .frame(width: 44, height: 44)
                VStack(alignment: .center, spacing: 4) {
                    Text(verbatim: model.isRunning ? GLInterfaceText.localizedStatus(model.status) :
                         GLInterfaceText.localized(consoleActuallyReady && outputActuallyReady
                          ? "Připraveno ke spuštění" :
                             (!consoleActuallyReady
                              ? "Nejprve připoj konzoli" : "Spáruj televizi")))
                        .font(.subheadline.bold())
                        .foregroundStyle(consoleActuallyReady && outputActuallyReady
                                         ? GLStyle.green : .white)
                    Text(verbatim: GLInterfaceText.localized(model.isRunning
                         ? "Sleduj živý stav překladu"
                         : "Překlad se spustí až po ověření konzole a výstupu."))
                        .font(.caption)
                        .foregroundStyle(GLNeon.mist)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
                GLHelpButton(title: "Stav překladu",
                    explanation: "Uložená konzole není automaticky připojená. Snímání aktivní se objeví až po přijetí video snímků; Google TV odpovídá na kontrolu spojení.")
                    .frame(width: 44, height: 44)
            }
            .padding(15)
        }
    }

    private var regionsPage: some View {
        VStack(alignment: .center, spacing: 14) {
            GLNeonTitle(title: "Oblasti obrazovky",
                        subtitle: model.automaticGameDialogue ? "Automatické oblasti překladu" : "Čtyři nezávislé oblasti pro rozpoznávání a překlad",
                        icon: "viewfinder")
            Button { regionsSheet = true } label: {
                GLDetailRow(icon: "rectangle.dashed",
                            title: "Otevřít editor oblastí",
                            detail: "")
            }
            .buttonStyle(.plain)
            if model.automaticGameDialogue {
                ForEach(model.automaticZones) { zone in
                    areaLine(zone.name, zone.enabled ? "Aktivní" : "Vypnutá", zone.enabled ? .green : .gray)
                }
            } else {
                areaLine("Modrá", "Čtení dialogů", .blue)
                areaLine("Zelená", "Výstup přeložených titulků", .green)
                areaLine("Žlutá", "Čtení herních textů a voleb", .yellow)
                areaLine("Oranžová", "Překlad herních textů", .orange)
            }
        }
    }

    private func areaLine(_ name: String, _ detail: String, _ color: Color) -> some View {
        GLNeonPanel(radius: 15) {
            HStack(spacing: 11) {
                RoundedRectangle(cornerRadius: 5).fill(color).frame(width: 9, height: 34)
                VStack(alignment: .center, spacing: 3) {
                    Text(LocalizedStringKey(name)).font(.subheadline.bold())
                    Text(LocalizedStringKey(detail)).font(.caption).foregroundStyle(GLVisual.muted)
                }
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
                Color.clear.frame(width: 9, height: 34)
            }
            .padding(14)
        }
    }

    private var profilesPage: some View {
        VStack(alignment: .center, spacing: 14) {
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
        VStack(alignment: .center, spacing: 15) {
            GLNeonTitle(title: "O aplikaci",
                        subtitle: "GameLingo – hraj a rozuměj svým hrám",
                        icon: "info.circle")
            GLNeonPanel(emphasized: true) {
                VStack(spacing: 11) {
                    GLBrandWordmark()
                    Text("Překlad herních dialogů a textů")
                        .font(.subheadline)
                        .foregroundStyle(GLNeon.mist)
                    Text(GLInterfaceText.localized("Verze:") + " " + GameLingoUpdateModel.currentVersion)
                        .font(.caption.bold())
                        .foregroundStyle(GLNeon.cyan)
                }
                .frame(maxWidth: .infinity)
                .padding(23)
            }
            GameLingoUpdatePanel(updates: updates, running: model.isRunning)
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
        VStack(alignment: .center, spacing: 5) {
            Text(LocalizedStringKey(title)).font(.headline)
            Text(LocalizedStringKey(text)).font(.subheadline).foregroundStyle(GLVisual.muted)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .multilineTextAlignment(.center)
        .padding(15)
        .background(GLVisual.card, in: RoundedRectangle(cornerRadius: 15))
    }

    private func sectionTitle(_ title: String, detail: String) -> some View {
        VStack(alignment: .center, spacing: 6) {
            Text(LocalizedStringKey(title)).font(.title2.bold())
            Text(LocalizedStringKey(detail)).font(.subheadline).foregroundStyle(GLVisual.muted)
        }
        .padding(.vertical, 6)
    }

    private func tile(_ title: String, icon: String,
                      action: @escaping () -> Void) -> some View {
        Button(action: action) {
            GLNeonPanel(radius: 19) {
                VStack(spacing: 6) {
                    Image(systemName: icon)
                        .font(.system(size: 24, weight: .medium))
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
                .frame(height: 74)
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
                        Text(LocalizedStringKey(option.title))
                            .font(.system(size: 10, weight: .medium))
                    }
                    .foregroundStyle(tab == option ? .white : GLNeon.mist)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
                    .padding(.vertical, 5)
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
        .padding(.top, 5)
        .padding(.bottom, 3)
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
            HStack(spacing: 10) {
                if family != nil || isTelevision {
                    GLPlatformBadge(family: family, isTelevision: isTelevision,
                                    size: 44)
                } else {
                    Image(systemName: icon)
                        .font(.system(size: 23))
                        .foregroundStyle(GLNeon.accent)
                        .frame(width: 44, height: 44)
                        .background(GLNeon.panel,
                                    in: RoundedRectangle(cornerRadius: 15))
                }
                VStack(alignment: .center, spacing: 5) {
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
                .frame(maxWidth: .infinity, alignment: .center)
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(GLNeon.mist)
                    .frame(width: 44, height: 44, alignment: .trailing)
            }
            .padding(11)
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint("Otevřít a spravovat zařízení")
    }
}
