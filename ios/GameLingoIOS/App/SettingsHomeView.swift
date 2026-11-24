import SwiftUI

// Compact GameLingo pages: titles and actions first, details in (?) Help.
/// The same settings overview is displayed as an iPhone tab and as a sheet.
/// All destinations are real screens, not placeholders.
struct GameLingoSettingsView: View {
    @ObservedObject var model: CaptionAppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                GameLingoSettingsOverview(model: model)
                    .padding(.horizontal, 18)
                    .padding(.top, 20)
                    .padding(.bottom, 36)
            }
            .background(GLNeonBackground())
            .navigationTitle("")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Hotovo") { dismiss() }
                }
            }
        }
        .tint(GLNeon.cyan)
        .preferredColorScheme(.dark)
    }
}

struct GameLingoSettingsOverview: View {
    @ObservedObject var model: CaptionAppModel
    var onDevices: (() -> Void)? = nil
    @State private var showRegions = false
    @State private var showDevices = false
    @State private var showXbox = false
    @State private var showPlayStation = false
    @State private var showTV = false

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            GLNeonTitle(title: "Nastavení",
                        subtitle: "Přizpůsobení překladu a titulků",
                        icon: "gearshape.fill")
                .padding(.bottom, 5)

            sectionLabel(GameLingoUIContract.settingsSections[0])
            NavigationLink {
                GameLingoLanguageSettingsView(model: model)
            } label: {
                GLDetailRow(icon: "globe",
                            title: "Jazyky",
                            detail: model.appLanguage.title + " · " +
                                model.gameLanguage.localizedTitle + " → " +
                                model.targetLanguage.localizedTitle)
            }
            .buttonStyle(.plain)

            sectionLabel(GameLingoUIContract.settingsSections[1])
            NavigationLink {
                GameLingoOCRSettingsView(model: model)
            } label: {
                GLDetailRow(icon: "viewfinder",
                            title: "OCR a rozpoznávání",
                            detail: "Citlivost, režim a filtrování textu")
            }
            .buttonStyle(.plain)
            NavigationLink {
                GameLingoTranslationSettingsView(model: model)
            } label: {
                GLDetailRow(icon: "character.book.closed.fill",
                            title: "Překlad",
                            detail: "Časování a aktuálnost titulků")
            }
            .buttonStyle(.plain)
            Button { showRegions = true } label: {
                GLDetailRow(icon: "rectangle.dashed",
                            title: "Oblasti obrazovky",
                            detail: "Čtyři nezávislé oblasti")
            }
            .buttonStyle(.plain)

            sectionLabel(GameLingoUIContract.settingsSections[2])
            NavigationLink {
                GameLingoAppearanceSettingsView(model: model)
            } label: {
                GLDetailRow(icon: "text.bubble",
                            title: "Vzhled titulků",
                            detail: "Velikost, šířka a poloha titulků")
            }
            .buttonStyle(.plain)
            NavigationLink {
                GameLingoProfilesScreen(model: model)
            } label: {
                GLDetailRow(icon: "gamecontroller",
                            title: "Profily her",
                            detail: "Nastavení pro jednotlivé hry")
            }
            .buttonStyle(.plain)

            sectionLabel(GameLingoUIContract.settingsSections[3])
            Button {
                if let onDevices { onDevices() }
                else { showDevices = true }
            } label: {
                GLDetailRow(icon: "display.2",
                            title: "Zařízení",
                            detail: "Xbox, PlayStation a Google TV")
            }
            .buttonStyle(.plain)
            NavigationLink {
                GameLingoHistoryView(model: model)
            } label: {
                GLDetailRow(icon: "clock.arrow.circlepath", title: "Historie překladů",
                            detail: model.translationHistory.enabled ? "Poslední dialogy a herní texty" : "Vypnuto")
            }
            .buttonStyle(.plain)
            NavigationLink {
                GameLingoDiagnosticsView(model: model)
            } label: {
                GLDetailRow(icon: "waveform.path.ecg",
                            title: "Diagnostika",
                            detail: "Stav připojení a technický log")
            }
            .buttonStyle(.plain)
            NavigationLink {
                GameLingoLicenseView()
            } label: {
                GLDetailRow(icon: "info.circle",
                            title: "O aplikaci / Licence",
                            detail: "Autoři, licence a zdrojový kód")
            }
            .buttonStyle(.plain)
        }
        .sheet(isPresented: $showRegions) {
            SubtitleSettingsView(model: model)
        }
        .sheet(isPresented: $showDevices) {
            NavigationStack {
                ScrollView {
                    GameLingoDevicesPanel(
                        devices: model.devices,
                        addXbox: { showXbox = true },
                        addPlayStation: { showPlayStation = true },
                        addTV: { showTV = true }
                    )
                    .padding(18)
                }
                .background(GLNeonBackground())
                .navigationTitle("Zařízení")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Hotovo") { showDevices = false }
                    }
                }
            }
            .tint(GLNeon.cyan)
            .preferredColorScheme(.dark)
            // Pairing sheets must be presented from the visible Devices sheet,
            // not from the already-covered settings window.
            .sheet(isPresented: $showXbox) {
                GameLingoXboxScreen(devices: model.devices, auth: model.xbox)
                    .environmentObject(model)
            }
            .sheet(isPresented: $showPlayStation) {
                GameLingoPlayStationSetup(devices: model.devices)
                    .environmentObject(model)
            }
            .sheet(isPresented: $showTV) {
                GameLingoTVSetupScreen(devices: model.devices, tv: model.tv) {
                    showTV = false
                }
            }
        }
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(LocalizedStringKey(title))
            .font(.system(size: 11, weight: .bold, design: .rounded))
            .tracking(1.2)
            .foregroundStyle(GLNeon.cyan)
            .padding(.top, 8)
    }
}

struct GameLingoLanguageSettingsView: View {
    @ObservedObject var model: CaptionAppModel

    var body: some View {
        GLScreen(title: "Jazyky", help:
            "Jazyk aplikace určuje rozhraní. Jazyk hry vybírá OCR rozpoznávání. Jazyk překladu určuje výstup pro dialogy i herní texty. Zdroj a cíl přepínej před spuštěním hry.") {
            GLCard {
                GLHeader(title: "Jazyk aplikace", help:
                    "Jazyk tlačítek a nabídky GameLingo. Nemění jazyk hry ani překladu.")
                Picker("Jazyk aplikace", selection: $model.appLanguage) {
                    ForEach(GLAppLanguage.allCases) { language in
                        Text(LocalizedStringKey(language.title)).tag(language)
                    }
                }
                .pickerStyle(.menu)
            }
            GLCard {
                GLHeader(title: "Jazyk hry", help:
                    "Vyber jazyk, ve kterém hra zobrazuje původní dialogy.")
                Picker("Rozpoznávat jazyk", selection: $model.gameLanguage) {
                    ForEach(GLGameLanguage.allCases) { language in
                        Text(LocalizedStringKey(language.title)).tag(language)
                    }
                }
                .pickerStyle(.menu)
                .disabled(model.isRunning)
            }
            GLCard {
                GLHeader(title: "Překládat do", help:
                    "Vyber cílový jazyk pro dialogy i herní texty.")
                Picker("Cílový jazyk", selection: $model.targetLanguage) {
                    ForEach(GLTargetLanguage.allCases) { language in
                        Text(LocalizedStringKey(language.title)).tag(language)
                    }
                }
                .pickerStyle(.menu)
                .disabled(model.isRunning)
            }

        }
    }
}

struct GameLingoLicenseView: View {
    var body: some View {
        GLScreen(title: "O aplikaci", help:
            "GameLingo je nezávislá aplikace pro překlad her. Funkční základy a jejich licence jsou zachovány.") {
            GLNeonPanel(emphasized: true) {
                VStack(spacing: 9) {
                    GLBrandWordmark()
                    Text("Překlad herních dialogů a textů")
                        .font(.subheadline)
                        .foregroundStyle(GLNeon.mist)
                    Text("Verze 0.5 Beta")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(GLNeon.cyan)
                }
                .frame(maxWidth: .infinity)
                .padding(25)
            }
            GLCard {
                GLHeader(title: "Licence")
                Text("GameLingo je otevřený projekt využívající části PlayCaptionu a Chiaki. Zdrojové kódy a změny jsou dostupné podle podmínek GNU AGPLv3.")
                    .font(.subheadline)
                Text("Nejde o oficiální aplikaci Sony, Microsoftu ani Google. Názvy konzolí jsou ochranné známky jejich vlastníků.")
                    .font(.caption)
                    .foregroundStyle(GLNeon.mist)
            }
            GLCard {
                GLHeader(title: "Zdrojové kódy")
                Link("GameLingo na GitHubu",
                     destination: URL(string: "https://github.com/CaseyCZ/GameLingo")!)
                Link("Původní PlayCaption",
                     destination: URL(string: "https://github.com/AdamRuzick4/PlayCaption-app")!)
                Link("Chiaki / TP Play",
                     destination: URL(string: "https://github.com/mB14ck22/tp-play")!)
            }
        }
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
            "Doba zobrazení určuje, jak dlouho titulek zůstane na televizi. Starší překlady může GameLingo zahodit po zobrazení nového dialogu. Jazyk hry a překladu lze změnit v sekci Jazyky.") {
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

    private var tvPreviewAllowed: Bool {
        guard let selected = model.devices.chosenTelevision else { return false }
        return model.tv.receiverPaired && model.tv.receiverConnected &&
            model.tv.ip.trimmingCharacters(in: .whitespacesAndNewlines) == selected.ipAddress
    }

    var body: some View {
        GLScreen(title: "Titulky – vzhled", help:
            "iPhone a TV mají nezávislé barvy, pozadí i velikost titulků. TV přijímá změny barev přes ověřené spárování; ukázku zobrazíš tlačítkem Náhled na TV.") {
            GLNeonTitle(title: "Vzhled titulků",
                        subtitle: "Vlastní nastavení pro iPhone a TV",
                        icon: "captions.bubble.fill")
            GLNeonPanel(emphasized: true) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(LinearGradient(
                            colors: [Color(red: 0.08, green: 0.21, blue: 0.40),
                                     Color(red: 0.16, green: 0.11, blue: 0.35),
                                     GLNeon.ink],
                            startPoint: .topLeading, endPoint: .bottomTrailing))
                    Image(systemName: "mountain.2.fill")
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(GLNeon.cyan.opacity(0.22))
                        .frame(maxWidth: 205)
                    Text(model.targetLanguage.sampleSubtitle)
                        .font(.system(size: 19 * model.phoneFontScale, weight: .bold))
                        .foregroundStyle(model.phoneTextColor.color)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(model.phoneBackground.fill,
                                    in: RoundedRectangle(cornerRadius: 9))
                        .frame(maxWidth: .infinity, maxHeight: .infinity,
                               alignment: .bottom)
                        .padding(.bottom, 13)
                }
                .frame(height: 176)
                .padding(14)
            }

            GLCard {
                GLHeader(title: "Titulky na iPhonu")
                GLValueSlider(title: "Velikost písma",
                    value: "\(Int(model.phoneFontScale * 100)) %",
                    selection: $model.phoneFontScale,
                    range: 0.70...1.80)
                Text("Barva textu")
                    .font(.subheadline.bold())
                HStack(spacing: 9) {
                    ForEach(GLPhoneSubtitleColor.allCases) { choice in
                        Button {
                            model.phoneTextColor = choice
                        } label: {
                            Circle().fill(choice.color)
                                .frame(width: 36, height: 36)
                                .overlay(Circle().strokeBorder(
                                    model.phoneTextColor == choice ? GLNeon.cyan : .white.opacity(0.4),
                                    lineWidth: model.phoneTextColor == choice ? 3 : 1))
                                .overlay {
                                    if model.phoneTextColor == choice {
                                        Image(systemName: "checkmark")
                                            .font(.caption.bold())
                                            .foregroundStyle(.black)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(choice.title)
                        .accessibilityAddTraits(model.phoneTextColor == choice
                                                ? [.isSelected] : [])
                    }
                }
                Picker("Pozadí", selection: $model.phoneBackground) {
                    ForEach(GLPhoneSubtitleBackground.allCases) { choice in
                        Text(LocalizedStringKey(choice.title)).tag(choice)
                    }
                }
                .pickerStyle(.segmented)
            }
            GLCard {
                GLHeader(title: "Titulky na TV",
                         help: "Nastavení barev a pozadí platí jen pro připojený GameLingo TV přijímač. Barvu lze měnit nezávisle na titulkách na iPhonu.")
                Text("Barva textu na TV")
                    .font(.subheadline.bold())
                HStack(spacing: 9) {
                    ForEach(GLPhoneSubtitleColor.allCases) { choice in
                        Button {
                            model.tvTextColor = choice
                        } label: {
                            Circle().fill(choice.color)
                                .frame(width: 36, height: 36)
                                .overlay(Circle().strokeBorder(
                                    model.tvTextColor == choice ? GLNeon.cyan : .white.opacity(0.4),
                                    lineWidth: model.tvTextColor == choice ? 3 : 1))
                                .overlay {
                                    if model.tvTextColor == choice {
                                        Image(systemName: "checkmark")
                                            .font(.caption.bold())
                                            .foregroundStyle(.black)
                                    }
                                }
                        }
                        .frame(minWidth: 44, minHeight: 44)
                        .buttonStyle(.plain)
                        .accessibilityLabel("TV: " + choice.title)
                        .accessibilityAddTraits(model.tvTextColor == choice
                                                ? [.isSelected] : [])
                    }
                }
                Picker("Pozadí na TV", selection: $model.tvBackground) {
                    ForEach(GLPhoneSubtitleBackground.allCases) { choice in
                        Text(LocalizedStringKey(choice.title)).tag(choice)
                    }
                }
                .pickerStyle(.segmented)
                Toggle("Zamknout polohu", isOn: $model.outputLocked)
                GLValueSlider(title: "Velikost písma TV",
                    value: "\(Int(model.outputFont * 100)) %",
                    selection: $model.outputFont,
                    range: 0.70...1.80,
                    disabled: model.outputLocked)
                GLValueSlider(title: "Šířka titulků",
                    value: "\(Int(model.outputWidth * 100)) %",
                    selection: $model.outputWidth,
                    range: 0.30...0.95,
                    disabled: model.outputLocked)
                GLPrimaryButton(title: "Náhled na TV",
                                systemImage: "tv",
                                disabled: !tvPreviewAllowed) {
                    model.tv.updateCaptionStyle(
                        color: model.tvTextColor.rawValue,
                        background: model.tvBackground.rawValue)
                    model.tv.sendSubtitle(model.targetLanguage.sampleSubtitle,
                                          duration: 6_000, preview: true)
                }
            }
        }
    }
}

struct GameLingoProfilesScreen: View {
    @ObservedObject var model: CaptionAppModel
    @State private var profileName = ""
    @State private var savedProfileMessage = ""

    var body: some View {
        GLScreen(title: "Profily her", help:
            "Ulož si pro každou hru vlastní OCR režim, všechny čtyři oblasti, filtry a nastavení titulků. Profil jde uložit i během hraní; načtení a mazání jsou dostupné po zastavení překladu. Neobsahuje hesla ani přihlašovací údaje.") {
            GLCard {
                TextField("Název hry", text: $profileName)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .padding(10)
                    .background(GLStyle.surfaceAlt, in: RoundedRectangle(cornerRadius: 11))
                GLPrimaryButton(title: "Uložit profil", systemImage: "square.and.arrow.down",
                    disabled: profileName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    let name = profileName.trimmingCharacters(in: .whitespacesAndNewlines)
                    model.saveGameProfile(named: name)
                    savedProfileMessage = "✓ Profil „\(name)“ uložen"
                    profileName = ""
                }
                if !savedProfileMessage.isEmpty {
                    Text(savedProfileMessage)
                        .font(.subheadline)
                        .foregroundStyle(GLStyle.green)
                }
            }
            if model.gameProfiles.isEmpty {
                GLCard {
                    Text("Zatím žádné profily").foregroundStyle(GLStyle.muted)
                }
            } else {
                Button("Nepoužívat herní slovník") { model.disableGameGlossary() }
                    .disabled(model.isRunning || model.activeGlossaryProfileID == nil)
                ForEach(model.gameProfiles) { profile in
                    GLCard {
                        HStack(spacing: 10) {
                            Image(systemName: "gamecontroller")
                                .foregroundStyle(GLStyle.blue)
                            Text(profile.name).font(.headline)
                            if model.activeGlossaryProfileID == profile.id {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(GLStyle.green)
                            }
                            Spacer()
                        }
                        NavigationLink {
                            GameLingoGlossaryEditor(model: model, profileID: profile.id)
                        } label: {
                            Label("Herní slovník", systemImage: "character.book.closed")
                        }.disabled(model.isRunning)
                        HStack(spacing: 10) {
                            Button("Použít") { model.applyGameProfile(profile) }
                                .buttonStyle(.borderedProminent)
                                .tint(GLStyle.blue)
                                .disabled(model.isRunning)
                            Spacer()
                            Button(role: .destructive) {
                                model.deleteGameProfile(profile.id)
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

private struct GameLingoGlossaryEditor: View {
    @ObservedObject var model: CaptionAppModel
    let profileID: UUID
    @State private var draft = ""
    @State private var message = ""
    @State private var source = ""
    @State private var target = ""
    @State private var loaded = false
    var body: some View {
        GLScreen(title: "Herní slovník", help:
            "Použij uložený profil před spuštěním hry. Jeden jazykový pár na profil, až 50 pravidel. Každý řádek: originál | chybný překlad | správný překlad. Prázdný prostřední sloupec opraví jen celý přesně shodný originál. Rozlišuje velká a malá písmena.") {
            GLCard {
                Text("Jazyky slovníku: " + source + " → " + target)
                    .foregroundStyle(GLStyle.muted)
                Text("Pro jiné jazyky změň jazyk hry a překladu, poté použij nový slovník. Změna páru nahradí předchozí slovník tohoto profilu.")
                    .font(.caption)
                Button("Nový slovník pro aktuální jazyky") {
                    source = model.gameLanguage.rawValue
                    target = model.targetLanguage.rawValue
                    draft = ""
                    message = "Změny se uloží až tlačítkem Uložit slovník."
                }.disabled(model.isRunning)
                TextEditor(text: $draft)
                    .font(.system(.body, design: .monospaced))
                    .frame(minHeight: 220)
                    .disabled(model.isRunning)
                Text("Příklad: Arthur Morgan | Artur Morgan | Arthur Morgan")
                    .font(.caption).foregroundStyle(GLStyle.muted)
                Button("Uložit slovník") {
                    guard let glossary = GameGlossary.parse(draft, source: source, target: target) else {
                        message = "Neplatný formát, duplicita originálu nebo překročený limit."
                        return
                    }
                    model.setGameGlossary(profileID: profileID, glossary: glossary)
                    message = "Slovník uložen. Použij tento profil před hraním."
                }.disabled(model.isRunning || !loaded)
                Text(message).font(.caption)
            }
        }
        .onAppear {
            guard !loaded else { return }
            let glossary = model.gameProfiles.first(where: { $0.id == profileID })?.glossary
            draft = glossary?.editorText ?? ""
            source = glossary?.sourceLanguage ?? model.gameLanguage.rawValue
            target = glossary?.targetLanguage ?? model.targetLanguage.rawValue
            loaded = true
        }
    }
}

private struct GameLingoHistoryView: View {
    @ObservedObject var model: CaptionAppModel
    @State private var confirmClear = false
    var body: some View {
        GLScreen(title: "Historie překladů", help:
            "Volitelná historie posledních 200 úspěšných překladů na tomto zařízení. Ukládá se pouze v paměti aplikace do jejího ukončení. Vypnutí historii vymaže; překladová cache a technický log jsou oddělené.") {
            GLCard {
                Toggle("Uchovávat historii", isOn: Binding(
                    get: { model.translationHistory.enabled },
                    set: { model.setHistoryEnabled($0) }))
                Text("Pouze v paměti do ukončení aplikace · max. 200 položek · bez synchronizace")
                    .font(.caption).foregroundStyle(GLStyle.muted)
                Button("Vymazat historii", role: .destructive) { confirmClear = true }
                    .disabled(model.translationHistory.entries.isEmpty)
                    .confirmationDialog("Vymazat celou historii překladů?", isPresented: $confirmClear) {
                        Button("Vymazat", role: .destructive) { model.clearHistory() }
                    }
            }
            if model.translationHistory.entries.isEmpty {
                GLCard {
                    Text(model.translationHistory.enabled ? "Zatím žádné překlady." : "Historie je vypnutá.")
                        .foregroundStyle(GLStyle.muted)
                }
            }
            ForEach(Array(model.translationHistory.entries.reversed())) { entry in
                GLCard {
                    Text(entry.kind + " · " + entry.source + " → " + entry.target + " · " +
                         entry.date.formatted(date: .omitted, time: .standard))
                        .font(.caption).foregroundStyle(GLStyle.muted)
                    Text(entry.translated).font(.headline).textSelection(.enabled)
                    Text(entry.original).font(.subheadline).foregroundStyle(GLStyle.muted)
                        .textSelection(.enabled)
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
                Text(model.performanceSummary)
                    .font(.caption2.monospaced()).foregroundStyle(GLStyle.muted)
                    .textSelection(.enabled)
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
    /// Backwards compatible: older saved profiles did not contain languages.
    var gameLanguage: String?
    var targetLanguage: String?
    var phoneFontScale: Double?
    var phoneTextColor: String?
    var phoneBackground: String?
    /// New in Beta 4 development; optional to decode older saved profiles.
    var tvTextColor: String?
    var tvBackground: String?
    var glossary: GameGlossary? = nil
}
