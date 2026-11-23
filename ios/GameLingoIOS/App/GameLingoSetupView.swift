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

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 21) {
                        header
                        switch step {
                        case 0: consoleStep
                        case 1: outputStep
                        default: readyStep
                        }
                    }
                    .padding(20)
                }
                footer
            }
            .background(GLStyle.background.ignoresSafeArea())
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
                        "Nejprve vyber zdroj hry, potom kam chceš titulky zobrazovat. K zařízení se přihlašuješ jeho vlastní oficiální metodou. Párování na Google TV používá šestimístný kód. Vše můžeš později změnit v Zařízeních.")
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
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "gamecontroller.fill")
                    .font(.system(size: 27))
                    .foregroundStyle(GLStyle.gradient)
                Text("GameLingo")
                    .font(.system(size: 29, weight: .bold, design: .rounded))
            }
            HStack(spacing: 8) {
                ForEach(0..<3) { index in
                    Capsule()
                        .fill(index <= step ? GLStyle.blue : GLStyle.surfaceAlt)
                        .frame(height: 5)
                }
            }
            .padding(.bottom, 8)
            Text(["Vyber konzoli", "Kam zobrazit titulky?", "Připraveno"][step])
                .font(.title2.bold())
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
                        Image(systemName: family.icon)
                            .font(.title2)
                            .foregroundStyle(GLStyle.blue)
                            .frame(width: 44, height: 44)
                            .background(GLStyle.surfaceAlt,
                                        in: RoundedRectangle(cornerRadius: 13))
                        Text(family.rawValue)
                            .font(.headline).foregroundStyle(.white)
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
                    .background(GLStyle.surface, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(chosenFamily == family ? GLStyle.blue : GLStyle.border,
                                      lineWidth: 1))
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
                        Text(output.rawValue)
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
            if devices.chosenConsole == nil {
                Label("Vyber konzoli", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
            }
            if devices.chosenConsole?.family == .playStation {
                Text("Dokončení závisí na ověřeném připojení PS Remote Play.")
                    .font(.caption)
                    .foregroundStyle(GLStyle.muted)
            }
        }
    }

    private var footer: some View {
        VStack(spacing: 9) {
            if step < 2 {
                GLPrimaryButton(title: "Pokračovat",
                                systemImage: "arrow.right",
                                disabled: step == 0 && devices.chosenConsole == nil) {
                    step += 1
                }
            } else {
                GLPrimaryButton(title: "Dokončit nastavení",
                                systemImage: "checkmark",
                                disabled: devices.chosenConsole == nil ||
                                    !consoleRegistered) {
                    devices.completedSetup = true
                    onFinished()
                    dismiss()
                }
            }
        }
        .padding(17)
        .background(GLStyle.surface.opacity(0.7))
    }

    private var xboxSetup: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    XboxAccountView(auth: model.xbox)
                    XboxRemotePlayView(auth: model.xbox)
                    if !model.xbox.consoles.isEmpty {
                        GLCard {
                            GLHeader(title: "Použít Xbox")
                            ForEach(model.xbox.consoles) { console in
                                Button {
                                    devices.addXbox(console)
                                    showXbox = false
                                } label: {
                                    HStack {
                                        Text(console.name)
                                        Spacer()
                                        Image(systemName: "checkmark.circle")
                                    }
                                    .padding(9)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(17)
            }
            .background(GLStyle.background.ignoresSafeArea())
            .navigationTitle("Xbox")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hotovo") { showXbox = false }
                }
            }
        }
    }

    private var tvSetup: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 15) {
                    TVSelectionView(tv: model.tv)
                    if !model.tv.ip.isEmpty {
                        GLPrimaryButton(title: "Použít tuto TV", systemImage: "checkmark") {
                            devices.addTelevision(name: "Google TV", ipAddress: model.tv.ip)
                            devices.selectTelevision("tv:" + model.tv.ip.lowercased())
                            showTV = false
                        }
                    }
                }
                .padding(17)
            }
            .background(GLStyle.background.ignoresSafeArea())
            .navigationTitle("Google TV")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hotovo") { showTV = false }
                }
            }
        }
    }
}

