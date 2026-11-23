import SwiftUI

// Compact GameLingo pages: titles and actions first, details in (?) Help.
struct GameLingoSettingsView: View {
    @ObservedObject var model: CaptionAppModel
    @Environment(\.dismiss) private var dismiss
    @State private var showRegions = false

    var body: some View {
        NavigationStack {
            GLScreen(title: "Nastavení", help:
                "Uprav tu čtení textu, překlad, vzhled titulků nebo profily. Jednotlivé volby mají vlastní nápovědu.") {
                GLCard {
                    NavigationLink { GameLingoOCRSettingsView(model: model) } label: {
                        GLSettingsLink(icon: "text.viewfinder", title: "Rozpoznávání")
                    }
                    Divider().overlay(GLStyle.border)
                    NavigationLink { GameLingoTranslationSettingsView(model: model) } label: {
                        GLSettingsLink(icon: "character.bubble", title: "Překlad")
                    }
                    Divider().overlay(GLStyle.border)
                    Button { showRegions = true } label: {
                        GLSettingsLink(icon: "rectangle.dashed", title: "Oblasti obrazovky")
                    }
                    .buttonStyle(.plain)
                }
                GLCard {
                    NavigationLink { GameLingoAppearanceSettingsView(model: model) } label: {
                        GLSettingsLink(icon: "captions.bubble", title: "Titulky na TV")
                    }
                    Divider().overlay(GLStyle.border)
                    NavigationLink { GameLingoProfilesScreen(model: model) } label: {
                        GLSettingsLink(icon: "gamecontroller", title: "Profily her")
                    }
                    Divider().overlay(GLStyle.border)
                    NavigationLink { GameLingoDiagnosticsView(model: model) } label: {
                        GLSettingsLink(icon: "waveform.path.ecg", title: "Diagnostika")
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Hotovo") { dismiss() }
                }
            }
            .sheet(isPresented: $showRegions) { SubtitleSettingsView(model: model) }
        }
        .tint(GLStyle.blue)
        .preferredColorScheme(.dark)
    }
}

private struct GameLingoOCRSettingsView: View {
    @ObservedObject var model: CaptionAppModel

    var body: some View {
        GLScreen(title: "Rozpoznávání", help:
            "Rychlost OCR ovlivňuje čtení herních titulků. Klasický režim vychází z ověřené verze. Rozšířené hledání může zachytit také mapu, nápisy a HUD. Při ruční oblasti se rozšíření nepoužívá. Filtr menu může odstranit i některé skutečné dialogy psané velkými písmeny.") {
            GLCard {
                GLHeader(title: "OCR", help: "Během běžícího překladu nelze změnit rychlost čtení.")
                Picker("Režim", selection: $model.ocrMode) {
                    ForEach(OCRMode.allCases) { mode in Text(mode.title).tag(mode) }
                }
                .pickerStyle(.menu)
                .disabled(model.isRunning)
            }
            GLCard {
                Toggle("Filtrovat menu hry", isOn: $model.filterMenuText)
                Toggle("Rozšířit hledání", isOn: $model.expandOnEmpty)
                    .disabled(model.ocrMode == .classic || model.manualOCR)
                HStack {
                    Text("Oblast")
                    Spacer()
                    Text(model.manualOCR ? "Ruční" : model.subtitleRegion.title)
                        .foregroundStyle(GLStyle.muted)
                }
                .font(.subheadline)
            }
            GLCard {
                Button("Výchozí nastavení OCR") {
                    model.ocrMode = .classic
                    model.expandOnEmpty = false
                    model.filterMenuText = true
                }
                .disabled(model.isRunning)
            }
        }
    }
}

private struct GameLingoTranslationSettingsView: View {
    @ObservedObject var model: CaptionAppModel

    var body: some View {
        GLScreen(title: "Překlad", help:
            "Doba zobrazení řídí, jak dlouho český titulek zůstane na televizi. Starší překlady může GameLingo zahodit, pokud se mezitím objeví nový dialog. Překlad EN → CZ používá stejnou službu jako ověřená předchozí verze.") {
            GLCard {
                GLValueSlider(title: "Doba titulku",
                    value: "\(Int(model.subtitleDuration)) s",
                    selection: $model.subtitleDuration, range: 3...10, step: 1)
            }
            GLCard {
                Toggle("Přeskočit starý překlad", isOn: $model.skipOldTranslations)
                if model.skipOldTranslations {
                    GLValueSlider(title: "Maximální stáří",
                        value: "\(Int(model.maxTranslationAge)) s",
                        selection: $model.maxTranslationAge, range: 2...8, step: 1)
                }
            }
        }
    }
}

private struct GameLingoAppearanceSettingsView: View {
    @ObservedObject var model: CaptionAppModel

    var body: some View {
        GLScreen(title: "Titulky na TV", help:
            "Poloha klasických českých titulků se upravuje v zelené oblasti. Barvu a pozadí vykresluje TV přijímač. Velikost písma a šířka bloku se přenesou při dalším titulku.") {
            GLCard {
                Toggle("Zamknout polohu", isOn: $model.outputLocked)
                GLValueSlider(title: "Velikost písma",
                    value: "\(Int(model.outputFont * 100)) %",
                    selection: $model.outputFont, range: 0.70...1.80,
                    disabled: model.outputLocked)
                GLValueSlider(title: "Šířka titulků",
                    value: "\(Int(model.outputWidth * 100)) %",
                    selection: $model.outputWidth, range: 0.30...0.95,
                    disabled: model.outputLocked)
            }
            GLCard {
                GLPrimaryButton(title: "Náhled na TV", systemImage: "tv",
                    disabled: model.tv.ip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    model.tv.sendSubtitle("Ukázka českých titulků", duration: 6_000, preview: true)
                }
            }
        }
    }
}

struct GameLingoProfilesScreen: View {
    @ObservedObject var model: CaptionAppModel
    @State private var profileName = ""

    var body: some View {
        GLScreen(title: "Profily her", help:
            "Ulož si pro každou hru vlastní OCR režim, všechny čtyři oblasti, filtry a nastavení titulků. Stejný název profil aktualizuje. Neobsahuje hesla ani přihlašovací údaje. Profil měň před spuštěním překladu.") {
            GLCard {
                TextField("Název hry", text: $profileName)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .padding(10)
                    .background(GLStyle.surfaceAlt, in: RoundedRectangle(cornerRadius: 11))
                GLPrimaryButton(title: "Uložit profil", systemImage: "square.and.arrow.down",
                    disabled: profileName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || model.isRunning) {
                    model.saveGameProfile(named: profileName)
                    profileName = ""
                }
            }
            if model.gameProfiles.isEmpty {
                GLCard {
                    Text("Zatím žádné profily").foregroundStyle(GLStyle.muted)
                }
            } else {
                ForEach(model.gameProfiles) { profile in
                    GLCard {
                        HStack(spacing: 10) {
                            Image(systemName: "gamecontroller")
                                .foregroundStyle(GLStyle.blue)
                            Text(profile.name).font(.headline)
                            Spacer()
                        }
                        HStack(spacing: 10) {
                            Button("Použít") { model.applyGameProfile(profile) }
                                .buttonStyle(.borderedProminent)
                                .tint(GLStyle.blue)
                                .disabled(model.isRunning)
                            Spacer()
                            Button(role: .destructive) {
                                model.gameProfiles.removeAll { $0.id == profile.id }
                            } label: {
                                Label("Smazat", systemImage: "trash")
                                    .font(.subheadline)
                            }
                            .disabled(model.isRunning)
                        }
                    }
                }
            }
        }
    }
}

private struct GameLingoDiagnosticsView: View {
    @ObservedObject var model: CaptionAppModel
    @State private var showLog = false

    var body: some View {
        GLScreen(title: "Diagnostika", help:
            "Tady najdeš stav překladu, počet zpracovaných snímků a poslední rozpoznané titulky. Zkušební titulek ověří propojení s TV. Log může obsahovat texty ze hry, IP adresu TV a názvy konzolí — před sdílením jej zkontroluj.") {
            GLCard {
                GLHeader(title: "Stav")
                status("Xbox", model.isRunning ? "Překládá" : "Neaktivní")
                status("Snímky", "\(model.frameCount)")
                status("Google TV", model.tv.ip.isEmpty ? "Nepřipojeno" : model.tv.ip)
                Text(model.status).font(.caption).foregroundStyle(GLStyle.muted)
            }
            GLCard {
                GLHeader(title: "Poslední překlad")
                Text(model.lastEnglish.isEmpty ? "–" : model.lastEnglish)
                    .foregroundStyle(GLStyle.muted).textSelection(.enabled)
                Text(model.lastCzech.isEmpty ? "–" : model.lastCzech)
                    .font(.headline).textSelection(.enabled)
            }
            GLCard {
                GLPrimaryButton(title: "Zkusit titulek na TV",
                    systemImage: "tv",
                    disabled: model.tv.ip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    model.tv.sendSubtitle("Zkušební titulek GameLingo", duration: 8_000, preview: true)
                }
            }
            GLCard {
                DisclosureGroup("Technický log", isExpanded: $showLog) {
                    ScrollView {
                        Text(model.logLines.suffix(60).joined(separator: "\n"))
                            .font(.caption2.monospaced())
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxHeight: 180)
                    ShareLink(item: model.shareLogs()) {
                        Label("Sdílet log", systemImage: "square.and.arrow.up")
                    }
                    Button("Vymazat log") { model.logLines.removeAll() }
                        .tint(.red)
                }
                .tint(GLStyle.blue)
            }
        }
    }

    private func status(_ key: String, _ value: String) -> some View {
        HStack {
            Text(key).foregroundStyle(GLStyle.muted)
            Spacer()
            Text(value).lineLimit(1).minimumScaleFactor(0.8)
        }
        .font(.subheadline)
    }
}

/// Named, local-only snapshot. No TV pairing tokens or Xbox authentication.
struct GameSubtitleProfile: Codable, Identifiable {
    var id: UUID
    var name: String
    var region: String
    var manualOCR: Bool
    var manualOCRLocked: Bool
    var manualOCRBox: CGRect
    var filterMenuText: Bool
    var ocrMode: String
    var expandOnEmpty: Bool
    var outputX: Double
    var outputBottom: Double
    var outputWidth: Double
    var outputFont: Double
    var outputLocked: Bool
    var choicesEnabled: Bool
    var choicesTextMode: Bool
    var choicesArea: CGRect
    var choicesLocked: Bool
    /// Optional to preserve previously saved 0.2.0 game profiles.
    var choicesOutputArea: CGRect?
    var choicesOutputLocked: Bool?
    /// Optional for backward-compatible 0.3.2 saved game profiles.
    var choicesOutputFont: Double?
    var subtitleDuration: Double
    var skipOldTranslations: Bool
    var maxTranslationAge: Double
}
