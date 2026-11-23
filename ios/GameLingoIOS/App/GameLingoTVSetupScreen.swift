import SwiftUI

/// Shared TV setup used by the first-run wizard and the main dashboard.
/// Saving an IP is not the same as successfully pairing or connecting a TV.
struct GameLingoTVSetupScreen: View {
    @ObservedObject var devices: GameLingoDevices
    @ObservedObject var tv: PlayCaptionTV
    @EnvironmentObject private var model: CaptionAppModel
    @Environment(\.dismiss) private var dismiss

    let onFinished: () -> Void

    private var enteredIP: String {
        tv.ip.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var selectedTelevision: GLTelevisionRecord? {
        devices.chosenTelevision
    }

    private func useCurrentTelevision() {
        guard !model.isRunning else { return }
        let address = enteredIP
        guard !address.isEmpty else { return }
        let saved = devices.televisions.first(where: {
            $0.ipAddress.caseInsensitiveCompare(address) == .orderedSame
        })
        let discoveredName = tv.discovered.first(where: {
            $0.ip.caseInsensitiveCompare(address) == .orderedSame
        })?.name
        devices.addTelevision(
            name: saved?.name ?? discoveredName ?? "Google TV",
            ipAddress: address
        )
        devices.selectTelevision("tv:" + address.lowercased())
        devices.subtitleOutput = .googleTV
        devices.prepareTV(tv)
        onFinished()
        dismiss()
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    GLNeonTitle(title: "Google TV",
                                subtitle: "Připojení a párování televize",
                                icon: "tv.fill")
                    if !devices.televisions.isEmpty {
                        GLCard {
                            GLHeader(title: "Moje televize", help:
                                "Vyber uloženou televizi. Pokud se změnila její IP adresa, přidej ji znovu. Samotné uložení TV není potvrzením spojení – připojení se ověřuje párováním a odpovědí přijímače.")
                            ForEach(devices.televisions) { record in
                                Button {
                                    guard !model.isRunning else { return }
                                    tv.ip = record.ipAddress
                                    devices.selectTelevision(record.id)
                                } label: {
                                    HStack(spacing: 12) {
                                        GLPlatformBadge(family: nil,
                                                        isTelevision: true, size: 46)
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(record.name).font(.headline)
                                            Text(record.ipAddress)
                                                .font(.caption.monospaced())
                                                .foregroundStyle(GLStyle.muted)
                                        }
                                        Spacer()
                                        if selectedTelevision?.id == record.id {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundStyle(GLStyle.green)
                                        }
                                    }
                                    .padding(10)
                                    .background(GLNeon.panel,
                                                in: RoundedRectangle(cornerRadius: 16))
                                    .overlay(RoundedRectangle(cornerRadius: 16)
                                        .strokeBorder(selectedTelevision?.id == record.id
                                                      ? GLNeon.cyan : GLNeon.purple.opacity(0.40),
                                                      lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                                .disabled(model.isRunning)
                            }
                        }
                    }

                    GameLingoTVPairingPanel(tv: tv, isRunning: model.isRunning)

                    GLCard {
                        GLHeader(title: "Výstup GameLingo")
                        if enteredIP.isEmpty {
                            Label("Nejdříve vyber televizi nebo zadej její IP.",
                                  systemImage: "exclamationmark.triangle")
                                .foregroundStyle(.orange)
                        } else {
                            Label(tv.receiverPaired
                                  ? (tv.receiverConnected
                                     ? "Přijímač TV je připojen."
                                     : "TV je spárovaná. Čekám na odpověď přijímače.")
                                  : "TV není spárovaná. Použij šest číslic z televize.",
                                  systemImage: tv.receiverConnected
                                      ? "checkmark.circle.fill" : "info.circle")
                                .font(.subheadline)
                                .foregroundStyle(tv.receiverConnected
                                                 ? GLStyle.green : GLStyle.muted)
                        }
                        GLPrimaryButton(title: "Použít tuto televizi",
                                        systemImage: "checkmark",
                                        disabled: enteredIP.isEmpty || model.isRunning) {
                            useCurrentTelevision()
                        }
                        Text("Uložení televizoru neznamená úspěšné spárování. Překlad na TV se spustí až po ověření aktivního přijímače.")
                            .font(.caption)
                            .foregroundStyle(GLStyle.muted)
                    }
                }
                .padding(18)
            }
            .background(GLNeonBackground())
            .navigationTitle("Google TV")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Zavřít") { onFinished(); dismiss() }
                }
            }
        }
        .tint(GLNeon.cyan)
        .preferredColorScheme(.dark)
    }
}

private struct GameLingoTVPairingPanel: View {
    @ObservedObject var tv: PlayCaptionTV
    let isRunning: Bool

    @State private var showManualIP = false
    @State private var showForgetConfirmation = false
    @State private var sample = "GameLingo – zkušební titulek"

    private var selectedIP: String {
        tv.ip.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            GLNeonPanel(emphasized: true) {
                VStack(alignment: .leading, spacing: 13) {
                    GLNeonTitle(title: "Vyhledat televizi",
                                subtitle: "GameLingo TV ve stejné domácí síti",
                                icon: "magnifyingglass")
                    HStack(spacing: 10) {
                        GLPrimaryButton(title: "Vyhledat", systemImage: "arrow.clockwise",
                                        disabled: isRunning) {
                            tv.discover()
                        }
                        Button {
                            showManualIP.toggle()
                        } label: {
                            Image(systemName: "network")
                                .font(.system(size: 20))
                                .foregroundStyle(GLNeon.cyan)
                                .frame(width: 48, height: 48)
                                .background(GLNeon.panel,
                                            in: RoundedRectangle(cornerRadius: 15))
                                .overlay(RoundedRectangle(cornerRadius: 15)
                                    .strokeBorder(GLNeon.edge, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                        .disabled(isRunning)
                        .accessibilityLabel("Zadat IP adresu")
                    }
                    if tv.discovered.isEmpty {
                        Text("Zatím nebyla nalezena žádná televize.")
                            .font(.caption)
                            .foregroundStyle(GLNeon.mist)
                    } else {
                        ForEach(tv.discovered) { device in
                            Button {
                                guard !isRunning else { return }
                                tv.ip = device.ip
                            } label: {
                                HStack(spacing: 12) {
                                    GLPlatformBadge(family: nil,
                                                    isTelevision: true, size: 47)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(device.name)
                                            .font(.subheadline.bold())
                                            .foregroundStyle(.white)
                                        Text(device.ip)
                                            .font(.caption.monospaced())
                                            .foregroundStyle(GLNeon.mist)
                                    }
                                    Spacer()
                                    Image(systemName: selectedIP == device.ip
                                          ? "checkmark.circle.fill" : "chevron.right")
                                        .foregroundStyle(selectedIP == device.ip
                                                         ? GLStyle.green : GLNeon.mist)
                                }
                                .padding(12)
                                .background(GLNeon.panel,
                                            in: RoundedRectangle(cornerRadius: 14))
                                .overlay(RoundedRectangle(cornerRadius: 14)
                                    .strokeBorder(selectedIP == device.ip
                                                  ? GLNeon.cyan : GLNeon.purple.opacity(0.35),
                                                  lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                            .disabled(isRunning)
                        }
                    }
                    if showManualIP {
                        TextField("IP adresa televize", text: $tv.ip)
                            .keyboardType(.numbersAndPunctuation)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textFieldStyle(.roundedBorder)
                            .disabled(isRunning)
                    }
                }
                .padding(15)
            }

            GLCard {
                GLHeader(title: "Párování televize",
                         help: "Na televizi spusť GameLingo TV a opiš šest číslic. Uložení IP adresy neznamená úspěšné párování. Pro živý překlad je nutné potvrzené spojení.")
                HStack(spacing: 10) {
                    Image(systemName: tv.receiverConnected
                          ? "checkmark.circle.fill" : "link.circle")
                        .font(.system(size: 26))
                        .foregroundStyle(tv.receiverConnected
                                         ? GLStyle.green : GLNeon.cyan)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(tv.receiverConnected ? "Přijímač připojen" :
                             tv.receiverPaired ? "Spárováno, čekám na TV" :
                             "Zatím nespárováno")
                            .font(.subheadline.bold())
                        if !tv.receiverStatus.isEmpty {
                            Text(tv.receiverStatus)
                                .font(.caption)
                                .foregroundStyle(GLNeon.mist)
                        }
                    }
                    Spacer(minLength: 0)
                }
                TextField("Šestimístný kód z TV", text: $tv.receiverCode)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: tv.receiverCode) { _, newCode in
                        tv.receiverCode = String(newCode.filter(\.isNumber).prefix(6))
                    }
                    .disabled(isRunning)
                GLPrimaryButton(title: tv.receiverPaired
                                ? "Spárovat znovu" : "Spárovat",
                                systemImage: "link",
                                disabled: selectedIP.isEmpty ||
                                    tv.receiverCode.count != 6 || isRunning) {
                    tv.pairReceiver()
                }
                if tv.receiverPaired {
                    Button("Zrušit párování", role: .destructive) {
                        showForgetConfirmation = true
                    }
                    .font(.subheadline)
                    .disabled(isRunning)
                    .confirmationDialog("Zrušit párování TV?",
                                        isPresented: $showForgetConfirmation) {
                        Button("Zrušit párování", role: .destructive) {
                            tv.forgetReceiver()
                        }
                    } message: {
                        Text("Televizi pak bude nutné znovu spárovat.")
                    }
                }
            }

            GLCard {
                GLHeader(title: "Zkušební titulek",
                         help: "Zobrazí vlastní testovací text na vybraném, skutečně připojeném GameLingo přijímači.")
                GLPrimaryButton(title: "Zobrazit na TV",
                                systemImage: "play.fill",
                                disabled: !tv.receiverPaired || !tv.receiverConnected) {
                    tv.sendSubtitle(sample, duration: 8_000, preview: true)
                }
                DisclosureGroup("Vlastní text") {
                    TextField("Zkušební text", text: $sample)
                        .textFieldStyle(.roundedBorder)
                }
                .tint(GLNeon.cyan)
                if !tv.message.isEmpty {
                    Text(tv.message).font(.caption)
                        .foregroundStyle(GLNeon.mist)
                }
            }
        }
        .onAppear {
            if !isRunning { tv.discover() }
        }
    }
}
