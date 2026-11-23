import SwiftUI

/// Shared TV setup used by the first-run wizard and the main dashboard.
/// Saving an IP is not the same as successfully pairing or connecting a TV.
struct GameLingoTVSetupScreen: View {
    @ObservedObject var devices: GameLingoDevices
    @ObservedObject var tv: PlayCaptionTV
    @Environment(\.dismiss) private var dismiss

    let onFinished: () -> Void

    private var enteredIP: String {
        tv.ip.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var selectedTelevision: GLTelevisionRecord? {
        devices.chosenTelevision
    }

    private func useCurrentTelevision() {
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
                            }
                        }
                    }

                    TVSelectionView(tv: tv)

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
                                        disabled: enteredIP.isEmpty) {
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
