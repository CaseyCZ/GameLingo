import SwiftUI

/// User-owned device library, independent of Xbox or PlayStation UI.
struct GameLingoDevicesPanel: View {
    @EnvironmentObject var model: CaptionAppModel
    @ObservedObject var devices: GameLingoDevices

    let addXbox: () -> Void
    let addPlayStation: () -> Void
    let addTV: () -> Void

    @State private var showAdd = false
    @State private var renaming: GLConsoleRecord?
    @State private var newName = ""
    @State private var pendingRemove: GLConsoleRecord?

    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            HStack {
                GLNeonTitle(title: "Konzole a zařízení",
                            subtitle: "Správa konzolí, TV a výstupu titulků",
                            icon: "display.2")
                GLHelpButton(title: "Zařízení", explanation:
                    "Můžeš přidat více Xboxů, PlayStationů i televizí. Uložené zařízení nemusí být právě online; skutečné spojení ověřujeme až při překladu.")
            }
            HStack(spacing: 11) {
                Button(action: addXbox) {
                    platformCard(.xbox)
                }
                .buttonStyle(.plain)
                Button(action: addPlayStation) {
                    platformCard(.playStation)
                }
                .buttonStyle(.plain)
            }
            GLPrimaryButton(title: "Přidat zařízení", systemImage: "plus.circle") {
                showAdd = true
            }
            if model.isRunning {
                Text("Překlad právě běží. Pro změnu nebo odebrání konzole ho nejprve zastav.")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }
            Text("Moje konzole")
                .font(.system(size: 19, weight: .bold, design: .rounded))
                .padding(.top, 4)
            if devices.consoles.isEmpty {
                emptyRow("Žádná konzole", icon: "gamecontroller")
            }
            ForEach(devices.consoles) { console in
                GLNeonPanel(emphasized: devices.preferredConsoleID == console.id) {
                    HStack(spacing: 12) {
                        GLPlatformBadge(family: console.family, size: 54)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(console.name).font(.headline)
                            Text(devices.preferredConsoleID == console.id
                                 ? "Vybraná konzole" : console.family.rawValue)
                                .font(.caption)
                                .foregroundStyle(devices.preferredConsoleID == console.id
                                                 ? GLStyle.green : GLNeon.mist)
                        }
                        Spacer()
                        if devices.preferredConsoleID == console.id {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(GLStyle.green)
                        }
                        Menu {
                            Button("Použít", systemImage: "checkmark.circle") {
                                if !model.isRunning { devices.selectConsole(console.id) }
                            }
                            .disabled(model.isRunning)
                            Button("Přejmenovat", systemImage: "pencil") {
                                newName = console.name
                                renaming = console
                            }
                            Button("Odebrat", systemImage: "trash", role: .destructive) {
                                if !model.isRunning { pendingRemove = console }
                            }
                            .disabled(model.isRunning)
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.title2)
                                .foregroundStyle(GLStyle.blue)
                        }
                        .accessibilityLabel("Spravovat " + console.name)
                    }
                    .contentShape(Rectangle())
                    .padding(13)
                    .onTapGesture {
                        if !model.isRunning { devices.selectConsole(console.id) }
                    }
                }
            }

            Text("Televize")
                .font(.system(size: 19, weight: .bold, design: .rounded))
                .padding(.top, 5)
            if devices.televisions.isEmpty {
                emptyRow("Žádná televize", icon: "tv")
            }
            ForEach(devices.televisions) { television in
                GLNeonPanel(emphasized: devices.preferredTelevisionID == television.id) {
                    HStack(spacing: 12) {
                        GLPlatformBadge(family: nil, isTelevision: true, size: 54)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(television.name).font(.headline)
                            Text(devices.preferredTelevisionID == television.id
                                 ? (model.tv.receiverConnected ? "TV připojena" :
                                    "TV uložena – čekám na spojení")
                                 : "Uložená televize")
                                .font(.caption)
                                .foregroundStyle(devices.preferredTelevisionID == television.id &&
                                                 model.tv.receiverConnected
                                                 ? GLStyle.green : GLNeon.mist)
                        }
                        Spacer()
                        if devices.preferredTelevisionID == television.id {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(GLStyle.green)
                        }
                        Menu {
                            Button("Použít") {
                                guard !model.isRunning else { return }
                                devices.selectTelevision(television.id)
                                devices.prepareTV(model.tv)
                            }
                            .disabled(model.isRunning)
                            Button("Odebrat", role: .destructive) {
                                guard !model.isRunning else { return }
                                if model.tv.ip == television.ipAddress {
                                    model.tv.forgetReceiver()
                                    model.tv.ip = ""
                                }
                                devices.removeTelevision(television.id)
                            }
                            .disabled(model.isRunning)
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.title2)
                                .foregroundStyle(GLStyle.blue)
                        }
                    }
                    .padding(13)
                }
            }
            GLCard {
                GLHeader(title: "Titulky")
                Picker("Zobrazit na", selection: $devices.subtitleOutput) {
                    ForEach(GLSubtitleOutput.allCases) { option in
                        Text(option.rawValue).tag(option)
                    }
                }
                .pickerStyle(.segmented)
                if devices.subtitleOutput == .googleTV {
                    Button {
                        addTV()
                    } label: {
                        GLSettingsLink(icon: "link", title: "Spárovat Google TV")
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .confirmationDialog("Přidat zařízení", isPresented: $showAdd) {
            Button("Xbox") { addXbox() }
            Button("PlayStation 5 / 4") { addPlayStation() }
            Button("Google TV / Android TV") { addTV() }
            Button("Zrušit", role: .cancel) {}
        }
        .alert("Přejmenovat konzoli", isPresented: Binding(
            get: { renaming != nil },
            set: { if !$0 { renaming = nil } }
        )) {
            TextField("Název", text: $newName)
            Button("Uložit") {
                if let record = renaming {
                    devices.renameConsole(record.id, to: newName)
                }
                renaming = nil
            }
            Button("Zrušit", role: .cancel) { renaming = nil }
        }
        .confirmationDialog("Odebrat konzoli?", isPresented: Binding(
            get: { pendingRemove != nil },
            set: { if !$0 { pendingRemove = nil } }
        )) {
            Button("Odebrat", role: .destructive) {
                guard !model.isRunning else { pendingRemove = nil; return }
                if let record = pendingRemove {
                    if record.family == .playStation,
                       let paired = model.playStation.findSavedConsole(id: record.id) {
                        model.playStation.registrations.remove(paired)
                    }
                    devices.removeConsole(record.id)
                }
                pendingRemove = nil
            }
            Button("Zrušit", role: .cancel) { pendingRemove = nil }
        } message: {
            Text("Odebere zařízení z GameLingo. U PlayStationu také odstraní jeho uložené párovací klíče.")
        }
    }

    private func platformCard(_ family: GLConsoleFamily) -> some View {
        GLNeonPanel(radius: 19) {
            VStack(spacing: 9) {
                GLPlatformBadge(family: family, size: 64)
                Text(family.rawValue)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                Text("\(devices.consoles.filter { $0.family == family }.count) uložených")
                    .font(.caption)
                    .foregroundStyle(GLNeon.mist)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
        }
    }

    private func emptyRow(_ title: String, icon: String) -> some View {
        HStack {
            Image(systemName: icon).foregroundStyle(GLStyle.muted)
            Text(title).foregroundStyle(GLStyle.muted)
            Spacer()
        }
        .padding(17)
        .background(GLStyle.surface, in: RoundedRectangle(cornerRadius: 14))
    }
}
