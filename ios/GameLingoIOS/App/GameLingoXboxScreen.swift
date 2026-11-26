import SwiftUI

/// One Xbox entry point for onboarding, the dashboard and the device library.
/// OAuth is owned by XboxAuthorization; selection is owned by GameLingoDevices.
/// Do not persist a second independent selected Xbox ID in a view.
struct GameLingoXboxScreen: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: CaptionAppModel
    @ObservedObject var devices: GameLingoDevices
    @ObservedObject var auth: XboxAuthorization

    private var selectedXboxID: String? {
        guard let id = devices.preferredConsoleID, id.hasPrefix("xbox:") else {
            return nil
        }
        return String(id.dropFirst("xbox:".count))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .center, spacing: 17) {
                    GLNeonTitle(title: "Můj Xbox",
                                subtitle: "Účet, konzole a výběr pro překlad",
                                icon: "gamecontroller.fill")
                    GLNeonPanel(emphasized: true) {
                        HStack(spacing: 13) {
                            GLPlatformBadge(family: .xbox, size: 62)
                            VStack(alignment: .center, spacing: 4) {
                                Text("Xbox Remote Play")
                                    .font(.headline)
                                Text("Microsoft účet a vlastní konzole")
                                    .font(.caption)
                                    .foregroundStyle(GLNeon.mist)
                            }
                            Spacer()
                        }
                        .padding(15)
                    }

                    // Preserve the working Microsoft device-code OAuth flow.
                    XboxAccountView(auth: auth)

                    GLCard {
                        GLHeader(title: "Moje konzole", help:
                            "Načti všechny Xboxy přiřazené k účtu. Zvol konkrétní konzoli pro překlad, zejména pokud jich máš více. Výběr se uloží do GameLingo a zobrazí na úvodní obrazovce. Samotné zobrazení v seznamu neznamená aktivní Remote Play.")
                        HStack(spacing: 9) {
                            Text("\(auth.consoles.count) nalezených")
                                .foregroundStyle(GLStyle.muted)
                            Spacer()
                            Button {
                                Task { await auth.refreshConsoles() }
                            } label: {
                                Label("Obnovit", systemImage: "arrow.clockwise")
                            }
                            .disabled(!auth.signedIn || model.isRunning)
                        }
                        .font(.subheadline)

                        if auth.consoles.isEmpty {
                            Text(auth.signedIn
                                 ? "Žádný Xbox zatím nebyl nalezen. Zkontroluj vzdálené funkce konzole a zkus Obnovit."
                                 : "Nejdříve se přihlas ke svému účtu Microsoft.")
                                .font(.subheadline)
                                .foregroundStyle(GLStyle.muted)
                        } else {
                            ForEach(auth.consoles) { console in
                                Button {
                                    guard !model.isRunning else { return }
                                    devices.addXbox(console)
                                } label: {
                                    HStack(spacing: 13) {
                                        GLPlatformBadge(family: .xbox, size: 44)
                                        VStack(alignment: .center, spacing: 3) {
                                            Text(console.name)
                                                .font(.headline)
                                                .foregroundStyle(.white)
                                            Text(console.powerState.isEmpty
                                                 ? "Stav konzole není známý"
                                                 : "Stav: " + console.powerState)
                                                .font(.caption)
                                                .foregroundStyle(GLStyle.muted)
                                        }
                                        Spacer()
                                        if selectedXboxID == console.id {
                                            Image(systemName: "checkmark.circle.fill")
                                                .font(.title2)
                                                .foregroundStyle(GLStyle.green)
                                        } else {
                                            Image(systemName: "circle")
                                                .font(.title2)
                                                .foregroundStyle(GLStyle.muted)
                                        }
                                    }
                                    .padding(13)
                                    .background(GLNeon.panel,
                                                in: RoundedRectangle(cornerRadius: 17))
                                    .overlay(RoundedRectangle(cornerRadius: 17)
                                        .strokeBorder(selectedXboxID == console.id
                                                      ? GLNeon.cyan : GLNeon.purple.opacity(0.45),
                                                      lineWidth: selectedXboxID == console.id ? 1.6 : 0.8))
                                }
                                .buttonStyle(.plain)
                                .disabled(model.isRunning)
                                .accessibilityLabel("Vybrat konzoli " + console.name)
                            }
                        }
                    }

                    if let selectedXboxID,
                       auth.consoles.contains(where: { $0.id == selectedXboxID }) {
                        GLCard {
                            Label("Vybraný Xbox je uložený", systemImage: "checkmark.circle.fill")
                                .foregroundStyle(GLStyle.green)
                            Text("Vrať se na úvodní obrazovku a spusť překlad. Zde nemusíš spouštět další samostatnou relaci.")
                                .font(.subheadline)
                                .foregroundStyle(GLStyle.muted)
                            GLPrimaryButton(title: "Hotovo", systemImage: "checkmark") {
                                dismiss()
                            }
                        }
                    }
                    if model.isRunning {
                        Text("Během aktivního překladu nelze měnit konzoli. Nejprve překlad zastav.")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }
                .padding(18)
            }
            .background(GLNeonBackground())
            .navigationTitle("Xbox")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Zavřít") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
        .tint(GLNeon.cyan)
        .task {
            if auth.signedIn && auth.consoles.isEmpty {
                await auth.refreshConsoles()
            }
        }
    }
}
