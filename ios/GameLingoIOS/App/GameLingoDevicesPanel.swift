import SwiftUI

/// User-owned device library, independent of Xbox or PlayStation UI.
struct GameLingoDevicesPanel: View {
    @EnvironmentObject var model: CaptionAppModel
    @ObservedObject var devices: GameLingoDevices
    @AppStorage("gamelingo.cameraSourceSelected") private var cameraSource = false

    let addXbox: () -> Void
    let addPlayStation: () -> Void

    let onAddDevice: () -> Void
    @State private var renaming: GLConsoleRecord?
    @State private var newName = ""
    @State private var pendingRemove: GLConsoleRecord?

    var body: some View {
        VStack(alignment: .center, spacing: 15) {
            GLNeonTitle(title: "Konzole a zařízení",
                        subtitle: "Správa konzolí, TV a výstupu titulků",
                        icon: "display.2")
                .overlay(alignment: .trailing) {
                    GLHelpButton(title: "Zařízení", explanation:
                        "Můžeš přidat více Xboxů, PlayStationů i televizí. Uložené zařízení nemusí být právě online; skutečné spojení ověřujeme až při překladu.")
                        .frame(width: 44, height: 44)
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
                onAddDevice()
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
                GLNeonPanel(emphasized: (!cameraSource && devices.preferredConsoleID == console.id)) {
                    HStack(spacing: 12) {
                        GLPlatformBadge(family: console.family, size: 54)
                        VStack(alignment: .center, spacing: 4) {
                            Text(console.name).font(.headline)
                            Text((!cameraSource && devices.preferredConsoleID == console.id)
                                 ? GLInterfaceText.localized("Vybraná konzole") : console.family.rawValue)
                                .font(.caption)
                                .foregroundStyle((!cameraSource && devices.preferredConsoleID == console.id)
                                                 ? GLStyle.green : GLNeon.mist)
                        }
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                        HStack(spacing: 3) {
                            if (!cameraSource && devices.preferredConsoleID == console.id) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(GLStyle.green)
                            }
                            Menu {
                                Button("Použít", systemImage: "checkmark.circle") {
                                    if !model.isRunning { devices.selectConsole(console.id); cameraSource = false }
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
                            .accessibilityLabel(GLInterfaceText.localized("Spravovat:") + " " + console.name)
                        }
                        .frame(width: 54)
                    }
                    .contentShape(Rectangle())
                    .padding(13)
                    .onTapGesture {
                        if !model.isRunning { devices.selectConsole(console.id); cameraSource = false }
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
                        VStack(alignment: .center, spacing: 4) {
                            Text(television.name).font(.headline)
                            Text(LocalizedStringKey(devices.preferredTelevisionID == television.id
                                 ? (model.tv.receiverConnected ? "TV připojena" :
                                    "TV uložena – čekám na spojení")
                                 : "Uložená televize"))
                                .font(.caption)
                                .foregroundStyle(devices.preferredTelevisionID == television.id &&
                                                 model.tv.receiverConnected
                                                 ? GLStyle.green : GLNeon.mist)
                        }
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                        HStack(spacing: 3) {
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
                        .frame(width: 54)
                    }
                    .padding(13)
                }
            }
            GLCard {
                GLHeader(title: "Titulky")
                HStack(spacing: 6) {
                    ForEach(GLSubtitleOutput.allCases) { option in
                        Button {
                            devices.subtitleOutput = option
                        } label: {
                            Text(LocalizedStringKey(option.rawValue))
                                .font(.subheadline.bold())
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(devices.subtitleOutput == option
                                    ? AnyShapeStyle(GLStyle.gradient) : AnyShapeStyle(GLStyle.surface),
                                    in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(devices.subtitleOutput == option ? .isSelected : [])
                    }
                }
            }
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
                Text(verbatim: GLInterfaceText.localized("Uloženo:") + " " + String(devices.consoles.filter { $0.family == family }.count))
                    .font(.caption)
                    .foregroundStyle(GLNeon.mist)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
        }
    }

    private func emptyRow(_ title: String, icon: String) -> some View {
        GLNeonPanel(radius: 14) {
            HStack(spacing: 10) {
                Image(systemName: icon).foregroundStyle(GLStyle.muted).frame(width: 24)
                Text(LocalizedStringKey(title)).foregroundStyle(GLStyle.muted)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                Color.clear.frame(width: 24, height: 1)
            }
            .padding(17)
        }
    }
}

/// Shared branded add-device overlay, used from the home tab and standalone settings.
struct GLAddDevicePicker: View {
    let onCancel: () -> Void
    let onXbox: () -> Void
    let onPlayStation: () -> Void
    let onTV: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.65).ignoresSafeArea()
                .onTapGesture(perform: onCancel)
                .accessibilityHidden(true)
            GLNeonPanel(emphasized: true, radius: 24) {
                VStack(spacing: 10) {
                    HStack {
                        GLBackButton { onCancel() }
                            .labelStyle(.iconOnly)
                            .frame(width: 44, height: 44)
                            .foregroundStyle(GLNeon.cyan)
                        Text("Přidat zařízení")
                            .font(.headline).foregroundStyle(.white)
                            .accessibilityAddTraits(.isHeader)
                            .frame(maxWidth: .infinity)
                        GLHelpButton(title: "Přidat zařízení", explanation: "Vyber zdroj obrazu podle zařízení.")
                            .frame(width: 44, height: 44)
                    }
                    selectionRow("Xbox", icon: "gamecontroller.fill", family: .xbox, selected: false, action: onXbox)
                    selectionRow("PlayStation 5 / 4", icon: "gamecontroller.fill", family: .playStation, selected: false, action: onPlayStation)
                    selectionRow("Google TV / Android TV", icon: "tv.fill", selected: false, action: onTV)
                    Button(action: onCancel) {
                        Text("Zrušit").font(.subheadline.weight(.semibold))
                            .foregroundStyle(GLNeon.cyan)
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.plain)
                }.padding(16)
            }
            .padding(.horizontal, 16).padding(.bottom, 12)
            .accessibilityAddTraits(.isModal)
        }
    }
}

func selectionRow(_ title: String, icon: String, family: GLConsoleFamily? = nil,
                          selected: Bool, action: @escaping () -> Void) -> some View {
    Button(action: action) {
        HStack(spacing: 12) {
            if let family {
                GLPlatformBadge(family: family, size: 42)
            } else if icon == "tv.fill" {
                GLPlatformBadge(family: nil, isTelevision: true, size: 42)
            } else {
                Image(systemName: icon).font(.title2).foregroundStyle(GLNeon.cyan)
                    .frame(width: 42, height: 42)
            }
            Text(LocalizedStringKey(title)).font(.headline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white).frame(maxWidth: .infinity)
            Image(systemName: selected ? "checkmark.circle.fill" : "chevron.right")
                .foregroundStyle(selected ? GLStyle.green : GLNeon.mist)
                .frame(width: 24)
        }
        .padding(9)
        .background(GLNeon.panel, in: RoundedRectangle(cornerRadius: 15))
        .overlay(RoundedRectangle(cornerRadius: 15)
            .strokeBorder(selected ? AnyShapeStyle(GLNeon.cyan) : AnyShapeStyle(GLNeon.edge), lineWidth: 1))
        .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityAddTraits(selected ? .isSelected : [])
}

