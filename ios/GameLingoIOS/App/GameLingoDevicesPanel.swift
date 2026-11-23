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
                Text("Moje zařízení").font(.title2.bold())
                Spacer()
                GLHelpButton(title: "Zařízení", explanation:
                    "Můžeš přidat více Xboxů, PlayStationů i televizí. U konzolí vyber výchozí, kterou bude GameLingo překládat. Xbox se přihlašuje přes Microsoft, PS5/PS4 přes Sony a osmimístný PIN. Pro Google TV je samostatný šestimístný párovací kód. Uložené zařízení nemusí být právě online.")
            }
            GLPrimaryButton(title: "Přidat zařízení", systemImage: "plus.circle") {
                showAdd = true
            }
            if model.isRunning {
                Text("Překlad právě běží. Pro změnu nebo odebrání konzole ho nejprve zastav.")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }
            Text("Konzole").font(.headline).padding(.top, 4)
            if devices.consoles.isEmpty {
                emptyRow("Žádná konzole", icon: "gamecontroller")
            }
            ForEach(devices.consoles) { console in
                GLCard {
                    HStack(spacing: 12) {
                        Image(systemName: console.family.icon)
                            .font(.title2)
                            .foregroundStyle(GLStyle.blue)
                            .frame(width: 36)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(console.name).font(.headline)
                            Text(console.family.rawValue)
                                .font(.caption).foregroundStyle(GLStyle.muted)
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
                    .onTapGesture {
                        if !model.isRunning { devices.selectConsole(console.id) }
                    }
                }
            }

            Text("Televize").font(.headline).padding(.top, 5)
            if devices.televisions.isEmpty {
                emptyRow("Žádná televize", icon: "tv")
            }
            ForEach(devices.televisions) { television in
                GLCard {
                    HStack(spacing: 12) {
                        Image(systemName: "tv")
                            .foregroundStyle(GLStyle.blue)
                            .frame(width: 36)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(television.name).font(.headline)
                            Text(television.ipAddress)
                                .font(.caption.monospaced())
                                .foregroundStyle(GLStyle.muted)
                        }
                        Spacer()
                        if devices.preferredTelevisionID == television.id {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(GLStyle.green)
                        }
                        Menu {
                            Button("Použít") {
                                devices.selectTelevision(television.id)
                                devices.prepareTV(model.tv)
                            }
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
