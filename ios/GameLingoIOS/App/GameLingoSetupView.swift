import SwiftUI

/// First-run source/output choice. Does not treat saved text as a paired console.
struct GameLingoSetupView: View {
    @EnvironmentObject private var model: CaptionAppModel
    @ObservedObject var devices: GameLingoDevices
    @Environment(\.dismiss) private var dismiss

    @State private var step = 0
    @State private var chosenFamily: GLConsoleFamily?
    @State private var showXbox = false
    @State private var showTV = false
    @State private var showPlayStation = false

    let onFinished: () -> Void

    private var consoleRegistered: Bool {
        guard let selected = devices.chosenConsole else { return false }
        if selected.family == .xbox {
            return devices.activeXbox(from: model.xbox) != nil
        }
        return model.playStation.findSavedConsole(id: selected.id) != nil
    }

    private var televisionPaired: Bool {
        guard devices.subtitleOutput == .googleTV else { return true }
        guard let chosen = devices.chosenTelevision else { return false }
        return model.tv.ip.trimmingCharacters(in: .whitespacesAndNewlines) ==
            chosen.ipAddress && model.tv.receiverPaired
    }

    private var canFinish: Bool {
        consoleRegistered && televisionPaired
    }

    private func finishSetup() {
        // Completing or skipping first-run setup does not claim a console is
        // connected. The dashboard still checks Xbox/PS and TV readiness.
        devices.completedSetup = true
        onFinished()
        dismiss()
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 21) {
                        header
                        switch step {
                        case 0: welcomeStep
                        case 1: languageStep
                        default:
                            consoleStep
                            outputStep
                        }
                    }
                    .padding(20)
                }
                footer
            }
            .background(GLNeonBackground())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if step > 0 {
                        Button { step -= 1 } label: {
                            Image(systemName: "chevron.left")
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    GLHelpButton(title: "První spuštění", explanation:
                        "Vyber konzoli, jazyk hry a cílový jazyk překladu, potom výstup titulků. Google TV se páruje šestimístným kódem. Vše můžeš později změnit v Nastavení a Zařízeních.")
                }
            }
        }
        .preferredColorScheme(.dark)
        .tint(GLStyle.blue)
        .sheet(isPresented: $showXbox) { xboxSetup }
        .sheet(isPresented: $showTV) { tvSetup }
        .sheet(isPresented: $showPlayStation) {
            GameLingoPlayStationSetup(devices: devices)
        }
        .onAppear {
            devices.refreshXbox(model.xbox.consoles)
            if let family = devices.chosenConsole?.family { chosenFamily = family }
        }
        .onReceive(model.xbox.$consoles) { list in
            devices.refreshXbox(list)
        }
        .onReceive(model.playStation.registrations.$consoles) { list in
            devices.refreshPlayStation(list)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            GLBrandWordmark()
            HStack(spacing: 8) {
                ForEach(GameLingoUIContract.setupSteps.indices, id: \.self) { index in
                    Capsule()
                        .fill(index <= step ? GLNeon.cyan : GLStyle.surfaceAlt)
                        .frame(height: 5)
                }
            }
            .padding(.bottom, 8)
            Text(LocalizedStringKey(GameLingoUIContract.setupSteps[step]))
                .font(.title2.bold())
        }
    }

    private var welcomeStep: some View {
        VStack(spacing: 16) {
            GLNeonPanel(emphasized: true, radius: 27) {
                VStack(spacing: 16) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.system(size: 60, weight: .semibold))
                        .foregroundStyle(GLNeon.accent)
                        .shadow(color: GLNeon.purple.opacity(0.55), radius: 17)
                    GLBrandWordmark()
                    Text("Vítej v GameLingo")
                        .font(.system(size: 24, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                    Text("Překládej dialogy a herní texty přímo během hraní.")
                        .font(.subheadline)
                        .foregroundStyle(GLNeon.mist)
                        .multilineTextAlignment(.center)
                    HStack(spacing: 17) {
                        GLPlatformBadge(family: .xbox, size: 60)
                        GLPlatformBadge(family: .playStation, size: 60)
                        GLPlatformBadge(family: nil, isTelevision: true, size: 60)
                    }
                    .padding(.vertical, 11)
                }
                .frame(maxWidth: .infinity)
                .padding(20)
            }
            GLNeonPanel(emphasized: true) {
                VStack(alignment: .leading, spacing: 10) {
                    Label("Jazyk aplikace", systemImage: "globe")
                        .font(.headline)
                    Picker("Jazyk aplikace", selection: $model.appLanguage) {
                        ForEach(GLAppLanguage.allCases) { language in
                            Text(LocalizedStringKey(language.title)).tag(language)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(GLNeon.cyan)
                    Text("Jazyk prostředí GameLingo. Překlad her se nastavuje samostatně.")
                        .font(.caption)
                        .foregroundStyle(GLNeon.mist)
                }
                .padding(17)
            }
        }
    }

    private var consoleStep: some View {
        VStack(spacing: 13) {
            ForEach(GLConsoleFamily.allCases) { family in
                Button {
                    chosenFamily = family
                    if family == .xbox { showXbox = true }
                    else { showPlayStation = true }
                } label: {
                    HStack(spacing: 14) {
                        GLPlatformBadge(family: family, size: 57)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(family.rawValue)
                                .font(.system(size: 18, weight: .bold,
                                              design: .rounded))
                                .foregroundStyle(.white)
                            Text(family == .xbox ? "Xbox Remote Play" :
                                 "PlayStation 4 a 5")
                                .font(.caption)
                                .foregroundStyle(GLNeon.mist)
                        }
                        Spacer()
                        if chosenFamily == family {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(GLStyle.green)
                        } else {
                            Image(systemName: "chevron.right")
                                .foregroundStyle(GLStyle.muted)
                        }
                    }
                    .padding(14)
                    .background(LinearGradient(
                        colors: [GLNeon.panel, GLNeon.purple.opacity(0.15)],
                        startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 19))
                    .overlay(RoundedRectangle(cornerRadius: 19)
                        .strokeBorder(chosenFamily == family ? GLNeon.cyan :
                                      GLNeon.purple.opacity(0.45), lineWidth: 1.5))
                }
                .buttonStyle(.plain)
            }
            GLCard {
                GLHeader(title: "Moje konzole", help:
                    "Můžeš si uložit více Xboxů i PlayStationů. Při spuštění vybereš konkrétní zařízení. Uložená adresa ještě neznamená úspěšné spárování; vždy se kontroluje skutečné Remote Play spojení.")
                if devices.consoles.isEmpty {
                    Text("Zatím žádná")
                        .foregroundStyle(GLStyle.muted)
                } else {
                    ForEach(devices.consoles) { record in
                        Button { devices.selectConsole(record.id) } label: {
                            HStack {
                                Image(systemName: record.family.icon)
                                    .foregroundStyle(GLStyle.blue)
                                Text(record.name).foregroundStyle(.white)
                                Spacer()
                                if devices.preferredConsoleID == record.id {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(GLStyle.green)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var languageStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            GLCard {
                GLHeader(title: "Jazyk hry")
                Picker("Rozpoznávat jazyk", selection: $model.gameLanguage) {
                    ForEach(GLGameLanguage.allCases) { language in
                        Text(language.title).tag(language)
                    }
                }
                .pickerStyle(.menu)
                .disabled(model.isRunning)
            }
            GLCard {
                GLHeader(title: "Překládat do")
                Picker("Cílový jazyk", selection: $model.targetLanguage) {
                    ForEach(GLTargetLanguage.allCases) { language in
                        Text(language.title).tag(language)
                    }
                }
                .pickerStyle(.menu)
                .disabled(model.isRunning)
            }
            Text("Tyto jazyky se použijí pro běžné dialogy i herní texty. Výběr můžeš později změnit v Nastavení.")
                .font(.caption)
                .foregroundStyle(GLNeon.mist)
        }
    }

    private var outputStep: some View {
        VStack(spacing: 13) {
            ForEach(GLSubtitleOutput.allCases) { output in
                Button {
                    devices.subtitleOutput = output
                    if output == .googleTV { showTV = true }
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: output == .phone ? "smartphone" : "tv.fill")
                            .font(.title2)
                            .foregroundStyle(GLStyle.blue)
                        Text(LocalizedStringKey(output.rawValue))
                            .font(.headline).foregroundStyle(.white)
                        Spacer()
                        if devices.subtitleOutput == output {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(GLStyle.green)
                        }
                    }
                    .padding(20)
                    .background(GLStyle.surface, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(devices.subtitleOutput == output
                                      ? GLStyle.blue : GLStyle.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            if devices.subtitleOutput == .googleTV {
                GLCard {
                    GLHeader(title: "Google TV",
                             help: "Otevři GameLingo TV na televizi a opiš šest číslic. TV a iPhone musejí být ve stejné síti.")
                    Text(devices.chosenTelevision?.name ?? "Vyber nebo přidej TV")
                        .foregroundStyle(GLStyle.muted)
                    GLPrimaryButton(title: "Spárovat TV", systemImage: "link") {
                        showTV = true
                    }
                }
            }
        }
    }

    private var readyStep: some View {
        VStack(spacing: 15) {
            GLCard {
                GLHeader(title: "Konzole")
                Text(devices.chosenConsole?.name ?? "Nevybráno")
                    .foregroundStyle(.white)
            }
            GLCard {
                GLHeader(title: "Titulky")
                Text(devices.subtitleOutput.rawValue)
                    .foregroundStyle(.white)
            }
            GLCard {
                GLHeader(title: "Jazyky")
                Text(LocalizedStringKey(model.gameLanguage.title)) +
                Text(" → ") + Text(LocalizedStringKey(model.targetLanguage.title))
                    .foregroundStyle(.white)
            }
            if devices.chosenConsole == nil {
                Label("Vyber konzoli", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
            }
            Label(consoleRegistered
                  ? "Konzole je spárovaná, spojení se ověří až při spuštění."
                  : "Konzole zatím není spárovaná.",
                  systemImage: consoleRegistered ? "checkmark.circle" : "exclamationmark.triangle")
                .font(.caption)
                .foregroundStyle(consoleRegistered ? GLStyle.green : .orange)
            if devices.subtitleOutput == .googleTV {
                Label(televisionPaired
                      ? "Párovací klíč TV je uložen, živé spojení se ověří při spuštění."
                      : "Televizi je potřeba vybrat a spárovat.",
                      systemImage: televisionPaired ? "checkmark.circle" : "exclamationmark.triangle")
                    .font(.caption)
                    .foregroundStyle(televisionPaired ? GLStyle.green : .orange)
            }
        }
    }

    private var footer: some View {
        VStack(spacing: 9) {
            if step < 2 {
                GLPrimaryButton(title: "Pokračovat",
                                systemImage: "arrow.right",
                                disabled: false) {
                    step += 1
                }
            } else {
                GLPrimaryButton(title: "Dokončit nastavení",
                                systemImage: "checkmark",
                                disabled: !canFinish) {
                    finishSetup()
                }
            }
            Button("Nastavit později") {
                finishSetup()
            }
            .font(.subheadline)
            .foregroundStyle(GLStyle.muted)
            .accessibilityHint("Průvodce se nebude opakovat. Konzole a TV lze připojit v Zařízeních.")
        }
        .padding(17)
        .background(GLNeon.panel.opacity(0.78))
    }

    private var xboxSetup: some View {
        // The first-run wizard and home screen use the same Xbox setup.
        // Console selection must be persisted in GameLingoDevices exactly once.
        GameLingoXboxScreen(devices: devices, auth: model.xbox)
            .environmentObject(model)
    }

    private var tvSetup: some View {
        GameLingoTVSetupScreen(devices: devices, tv: model.tv) {
            showTV = false
        }
    }
}

