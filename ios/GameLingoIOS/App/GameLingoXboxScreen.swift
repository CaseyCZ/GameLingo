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
                VStack(alignment: .leading, spacing: 17) {
                    HStack(spacing: 14) {
                        Image(systemName: "gamecontroller.fill")
                            .font(.system(size: 30))
                            .foregroundStyle(GLStyle.blue)
                            .frame(width: 57, height: 57)
                            .background(GLStyle.surfaceAlt,
                                        in: RoundedRectangle(cornerRadius: 17))
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Můj Xbox").font(.title2.bold())
                            Text("Účet, konzole a výběr pro překlad")
                                .font(.subheadline)
                                .foregroundStyle(GLStyle.muted)
                        }
                        Spacer()
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
                                        Image(systemName: "gamecontroller.fill")
                                            .font(.title2)
                                            .frame(width: 38)
                                            .foregroundStyle(GLStyle.blue)
                                        VStack(alignment: .leading, spacing: 3) {
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
                                    .background(GLStyle.surfaceAlt,
                                                in: RoundedRectangle(cornerRadius: 14))
                                    .overlay(RoundedRectangle(cornerRadius: 14)
                                        .strokeBorder(selectedXboxID == console.id
                                                      ? GLStyle.blue : GLStyle.border,
                                                      lineWidth: 1))
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
            .background(GLStyle.background.ignoresSafeArea())
            .navigationTitle("Xbox")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Zavřít") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
        .tint(GLStyle.blue)
        .task {
            if auth.signedIn && auth.consoles.isEmpty {
                await auth.refreshConsoles()
            }
        }
    }
}
