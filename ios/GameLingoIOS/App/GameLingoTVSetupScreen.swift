import SwiftUI
import VisionKit
import AVFoundation

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
                VStack(alignment: .center, spacing: 11) {
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
                                            .frame(width: 72, alignment: .leading)
                                        VStack(alignment: .center, spacing: 3) {
                                            Text(record.name).font(.headline)
                                            Text(record.ipAddress)
                                                .font(.caption.monospaced())
                                                .foregroundStyle(GLStyle.muted)
                                        }
                                        .frame(maxWidth: .infinity)
                                        .multilineTextAlignment(.center)
                                        Text(LocalizedStringKey(tv.receiverConnected ? "Připojeno" : "Vybraná"))
                                            .font(.caption.weight(.semibold))
                                            .foregroundStyle(tv.receiverConnected ? GLStyle.green : GLNeon.cyan)
                                            .multilineTextAlignment(.trailing)
                                            .opacity(selectedTelevision?.id == record.id ? 1 : 0)
                                            .frame(width: 72, alignment: .trailing)
                                            .accessibilityHidden(selectedTelevision?.id != record.id)
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
                .padding(14)
            }
            .background(GLNeonBackground())
            .navigationTitle("Google TV")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    GLBackButton { onFinished(); dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    GLHelpButton(title: "Google TV", explanation: "Google TV se páruje pomocí QR nebo kódu.")
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
    @State private var showQRScanner = false
    @State private var qrError = false
    @State private var showForgetConfirmation = false
    @State private var sample = GLInterfaceText.localized("GameLingo – zkušební titulek")

    private var selectedIP: String {
        tv.ip.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        VStack(alignment: .center, spacing: 15) {
            GLNeonPanel(emphasized: true) {
                VStack(alignment: .center, spacing: 13) {
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
                                    VStack(alignment: .center, spacing: 3) {
                                        Text(device.name)
                                            .font(.subheadline.bold())
                                            .foregroundStyle(.white)
                                        Text(device.ip)
                                            .font(.caption.monospaced())
                                            .foregroundStyle(GLNeon.mist)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .multilineTextAlignment(.center)
                                    Image(systemName: selectedIP == device.ip
                                          ? "checkmark.circle.fill" : "chevron.right")
                                        .foregroundStyle(selectedIP == device.ip
                                                         ? GLStyle.green : GLNeon.mist)
                                        .frame(width: 47, alignment: .trailing)
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
                            .keyboardType(.asciiCapable)
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
                         help: "Na TV vyber Překlad z telefonu, nikoli Přímo na TV. Načti QR nebo opiš celý kód. Uložená IP neznamená spárování.")
                Text("1. Na TV otevři Překlad z telefonu. 2. Načti QR nebo opiš 32 znaků kódu. 3. Zvol Spárovat.")
                    .font(.caption)
                    .foregroundStyle(GLNeon.mist)
                Text("Párovací klíč se nepřenáší po síti. Titulky jsou podepsané, ne šifrované.")
                    .font(.caption).foregroundStyle(GLStyle.muted)
                HStack(spacing: 10) {
                    Image(systemName: tv.receiverConnected
                          ? "checkmark.circle.fill" : "link.circle")
                        .font(.system(size: 26))
                        .foregroundStyle(tv.receiverConnected
                                         ? GLStyle.green : GLNeon.cyan)
                        .frame(width: 32, alignment: .leading)
                    VStack(alignment: .center, spacing: 3) {
                        Text(LocalizedStringKey(tv.receiverConnected ? "Přijímač připojen" :
                             tv.receiverPaired ? "Spárováno, čekám na TV" :
                             "Zatím nespárováno"))
                            .font(.subheadline.bold())
                        if !tv.receiverStatus.isEmpty {
                            Text(verbatim: GLInterfaceText.localizedStatus(tv.receiverStatus))
                                .font(.caption)
                                .foregroundStyle(GLNeon.mist)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                    Color.clear.frame(width: 32, height: 1).accessibilityHidden(true)
                }
                GLPrimaryButton(title: "Načíst QR kód z TV", systemImage: "qrcode.viewfinder",
                                disabled: isRunning) {
                    guard DataScannerViewController.isSupported else { qrError = true; return }
                    Task { @MainActor in
                        let allowed = await AVCaptureDevice.requestAccess(for: .video)
                        guard allowed, DataScannerViewController.isAvailable, !isRunning else {
                            qrError = true
                            return
                        }
                        showQRScanner = true
                    }
                }
                TextField("Párovací kód z TV", text: $tv.receiverCode)
                    .keyboardType(.asciiCapable)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.system(.caption, design: .monospaced))
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: tv.receiverCode) { _, newCode in
                        tv.receiverCode = String(newCode.prefix(48))
                    }
                    .disabled(isRunning)
                GLPrimaryButton(title: tv.receiverPaired
                                ? "Spárovat znovu" : "Spárovat",
                                systemImage: "link",
                                disabled: selectedIP.isEmpty ||
                                    tv.receiverCode.filter { !$0.isWhitespace && $0 != "-" }.count != 32 || isRunning) {
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
                    Text(verbatim: GLInterfaceText.localizedStatus(tv.message)).font(.caption)
                        .foregroundStyle(GLNeon.mist)
                }
            }
        }
        .sheet(isPresented: $showQRScanner) {
            NavigationStack {
                GameLingoQRScanner(onCode: { code in
                    guard !isRunning else { showQRScanner = false; return }
                    tv.ip = code.ip
                    tv.receiverCode = code.pin
                    showQRScanner = false
                }, onFailure: {
                    showQRScanner = false
                    qrError = true
                })
                .navigationTitle("Načíst QR z TV")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        GLBackButton { showQRScanner = false }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        GLHelpButton(title: "Párování telefonu", explanation: "Telefon a TV připoj ke stejné síti. V mobilu otevři Zařízení, vyber TV a naskenuj QR kód nebo zadej celý párovací kód. Po vypršení platnosti vytvoř nový kód.")
                    }
                }
            }
        }
        .alert("QR skener není dostupný", isPresented: $qrError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text("Povol fotoaparát v nastavení iOS, nebo zadej IP a PIN ručně.")
        }
        .onAppear {
            if !isRunning { tv.discover() }
        }
    }
}
