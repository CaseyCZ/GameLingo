import SwiftUI

/// Independent GameLingo screen-region editor follows the Android phone miniature 16:9 television:
/// blue = OCR input area, green = Czech subtitle output, yellow = game responses.
struct SubtitleSettingsView: View {
    @ObservedObject var model: CaptionAppModel
    @Environment(\.dismiss) private var dismiss
    @State private var editing: Area = .reading
    @State private var orangePreviewTask: Task<Void, Never>?

    private enum Area: String, CaseIterable {
        case reading = "Čtení"
        case output = "Zobrazení"
        case choices = "Volby odpovědí"
        case outputChoices = "Překlad herních textů"

        var label: String {
            switch self {
            case .reading: return "Modrá"
            case .output: return "Zelená"
            case .choices: return "Žlutá"
            case .outputChoices: return "Oranžová"
            }
        }

        var color: Color {
            switch self {
            case .reading: return .blue
            case .output: return .green
            case .choices: return .yellow
            case .outputChoices: return .orange
            }
        }
        var icon: String {
            switch self {
            case .reading: return "viewfinder"
            case .output: return "captions.bubble.fill"
            case .choices: return "text.viewfinder"
            case .outputChoices: return "character.bubble.fill"
            }
        }
    }

    /// Green rectangle represents where the TV places Czech captions.
    private var outputRect: CGRect {
        let h = CGFloat(0.12 * model.outputFont)
        let w = CGFloat(model.outputWidth)
        let x = CGFloat(model.outputX)
        let y = CGFloat(model.outputBottom)
        return CGRect(x: x - w / 2, y: y - h, width: w, height: h)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 17) {
                    GLNeonTitle(
                        title: "OCR / Oblasti",
                        subtitle: "Čtyři nezávislé oblasti pro každou hru",
                        icon: "viewfinder"
                    )
                    Text("Vyber oblast, potom uprav její polohu a velikost v náhledu.")
                        .font(.subheadline)
                        .foregroundStyle(GLNeon.mist)
                    HStack(spacing: 8) {
                        ForEach(Area.allCases, id: \.self) { area in
                            Button {
                                editing = area
                            } label: {
                                VStack(spacing: 7) {
                                    Image(systemName: area.icon)
                                        .font(.system(size: 18, weight: .medium))
                                        .foregroundStyle(area.color)
                                    Text(area.label)
                                        .font(.system(size: 11, weight: .semibold))
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.75)
                                        .foregroundStyle(.white)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 65)
                                .background(editing == area
                                            ? area.color.opacity(0.19)
                                            : GLNeon.panel,
                                            in: RoundedRectangle(cornerRadius: 14))
                                .overlay(RoundedRectangle(cornerRadius: 14)
                                    .strokeBorder(area.color.opacity(
                                        editing == area ? 0.95 : 0.35),
                                        lineWidth: editing == area ? 1.8 : 0.8))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(area.label + " – " + area.rawValue)
                        }
                    }
                    GLNeonPanel(emphasized: true) {
                        VStack(alignment: .leading, spacing: 11) {
                            HStack {
                                Label("Náhled obrazovky", systemImage: "tv")
                                    .font(.subheadline.bold())
                                Spacer()
                                Text(editing.label)
                                    .font(.caption.bold())
                                    .foregroundStyle(editing.color)
                            }
                            television
                            Text("Přetáhni vybraný rámeček. Ostatní oblasti zůstanou na svém místě.")
                                .font(.caption)
                                .foregroundStyle(GLNeon.mist)
                        }
                        .padding(15)
                    }
                    GLCard {
                        if editing == .reading {
                            readControls
                        } else if editing == .output {
                            outputControls
                        } else if editing == .choices {
                            choicesControls
                        } else {
                            choicesOutputControls
                        }
                    }
                }
                .padding(16)
            }
            .background(GLNeonBackground())
            .onDisappear { orangePreviewTask?.cancel() }
            .navigationTitle("Oblasti")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    GLHelpButton(title: "Oblasti", explanation:
                        "Modrá: odkud číst dialogy. Zelená: kam zobrazit české titulky. Žlutá: odkud číst herní texty a volby. Oranžová: kam zobrazit jejich český překlad. Vyber barvu a přetáhni její oblast v náhledu. Každá oblast má vlastní velikost a polohu. Změny se ukládají automaticky.")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Hotovo") { dismiss() }
                }
            }
        }
        .tint(GLStyle.blue)
        .preferredColorScheme(.dark)
    }

    private var television: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 13)
                    .fill(LinearGradient(
                        colors: [Color(red: 0.07, green: 0.17, blue: 0.29),
                                 Color(red: 0.17, green: 0.11, blue: 0.30),
                                 GLNeon.ink],
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                Image(systemName: "mountain.2.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(GLNeon.cyan.opacity(0.18))
                    .frame(width: geo.size.width * 0.85,
                           height: geo.size.height * 0.68)
                    .position(x: geo.size.width * 0.57,
                              y: geo.size.height * 0.46)
                RoundedRectangle(cornerRadius: 13)
                    .strokeBorder(GLNeon.edge, lineWidth: 1.6)

                // All four regions stay visible like the approved preview.
                // Disabled OCR/text lanes are dimmed, never falsely activated.
                rectangle(outputRect, color: .green,
                          name: "Titulky", selected: editing == .output,
                          size: geo.size)
                rectangle(model.manualOCRBox, color: .blue,
                          name: "Čtení dialogů", selected: editing == .reading,
                          size: geo.size, active: model.manualOCR)
                rectangle(model.choicesArea, color: .yellow,
                          name: model.choicesTextMode ? "Herní texty" : "Volby",
                          selected: editing == .choices,
                          size: geo.size, active: model.choicesEnabled)
                rectangle(model.choicesOutputArea, color: .orange,
                          name: "Překlad textů",
                          selected: editing == .outputChoices,
                          size: geo.size, active: model.choicesEnabled)
                if !model.manualOCR {
                    Text("OCR: automaticky")
                        .font(.caption2)
                        .foregroundStyle(GLNeon.mist)
                        .padding(6)
                        .background(GLNeon.ink.opacity(0.86),
                                    in: RoundedRectangle(cornerRadius: 6))
                }
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let x = min(1, max(0, value.location.x / max(1, geo.size.width)))
                            let y = min(1, max(0, value.location.y / max(1, geo.size.height)))
                            moveSelected(toX: x, y: y)
                        }
                        .onEnded { _ in
                            showOnTV()
                        })
            }
            .clipShape(RoundedRectangle(cornerRadius: 13))
        }
        .aspectRatio(16.0 / 9.0, contentMode: .fit)
    }

    private func rectangle(_ rect: CGRect, color: Color, name: String,
                           selected: Bool, size: CGSize,
                           active: Bool = true) -> some View {
        Text(name)
            .font(.caption2.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .foregroundStyle(selected ? .black : color)
            .frame(width: max(28, rect.width * size.width),
                   height: max(18, rect.height * size.height))
            .background(color.opacity(selected ? 0.85 : 0.15))
            .overlay(
                RoundedRectangle(cornerRadius: 3)
                    .strokeBorder(color, style: StrokeStyle(
                        lineWidth: selected ? 3 : 2,
                        dash: selected ? [] : [7, 4]))
            )
            .position(x: rect.midX * size.width, y: rect.midY * size.height)
            .opacity(active ? 1 : 0.46)
            .allowsHitTesting(false)
    }

    private func moveSelected(toX x: CGFloat, y: CGFloat) {
        if editing == .reading {
            guard model.manualOCR, !model.manualOCRLocked else { return }
            var rect = model.manualOCRBox
            rect.origin.x = max(0, min(1 - rect.width, x - rect.width / 2))
            rect.origin.y = max(0, min(1 - rect.height, y - rect.height / 2))
            model.manualOCRBox = rect
        } else if editing == .output {
            guard !model.outputLocked else { return }
            let width = CGFloat(model.outputWidth)
            let h = CGFloat(0.12 * model.outputFont)
            model.outputX = Double(max(width / 2, min(1 - width / 2, x)))
            model.outputBottom = Double(max(h, min(1, y + h / 2)))
        } else if editing == .choices {
            guard model.choicesEnabled, !model.choicesLocked else { return }
            var rect = model.choicesArea
            rect.origin.x = max(0, min(1 - rect.width, x - rect.width / 2))
            rect.origin.y = max(0, min(1 - rect.height, y - rect.height / 2))
            model.choicesArea = rect
        } else {
            guard model.choicesEnabled, !model.choicesOutputLocked else { return }
            var rect = model.choicesOutputArea
            rect.origin.x = max(0, min(1 - rect.width, x - rect.width / 2))
            rect.origin.y = max(0, min(1 - rect.height, y - rect.height / 2))
            model.choicesOutputArea = rect
            scheduleOrangeRelocation()
        }
    }

    private var readControls: some View {
        VStack(alignment: .leading, spacing: 12) {
            GLHeader(title: "Čtení dialogů", help: "Modrá je vstup OCR. Automatika sleduje vybranou část obrazu, ručně nastavíš přesný rámeček. Funkce filtrování může zachytit i některé nápisy ve hře.")
            Picker("Způsob čtení", selection: $model.manualOCR) {
                Text("Automaticky").tag(false)
                Text("Ručně").tag(true)
            }
            .pickerStyle(.segmented)

            if model.manualOCR {
                Toggle("Zamknout oblast čtení", isOn: $model.manualOCRLocked)
                if !model.manualOCRLocked {
                    Text(verbatim: GLInterfaceText.localized("Šířka:") + " " + String(Int(model.manualOCRBox.width * 100)) + " %")
                    Slider(value: Binding(
                        get: { Double(model.manualOCRBox.width) },
                        set: { updateOCRSize(width: CGFloat($0), height: nil) }
                    ), in: 0.20...1.0)
                    Text(verbatim: GLInterfaceText.localized("Výška:") + " " + String(Int(model.manualOCRBox.height * 100)) + " %")
                    Slider(value: Binding(
                        get: { Double(model.manualOCRBox.height) },
                        set: { updateOCRSize(width: nil, height: CGFloat($0)) }
                    ), in: 0.05...0.60)
                }
                Button("Náhled na TV") {
                    model.tv.showOCRRegion(model.manualOCRBox, visible: true)
                }.buttonStyle(.bordered)
            } else {
                Picker("Výchozí oblast", selection: $model.subtitleRegion) {
                    ForEach(SubtitleRegion.allCases) { preset in
                        Text(preset.title).tag(preset)
                    }
                }.pickerStyle(.menu)
            }
            Toggle("Filtrovat položky herního menu", isOn: $model.filterMenuText)
            Button("Obnovit oblast") {
                model.manualOCRBox = CGRect(x: 0.10, y: 0.72,
                                            width: 0.80, height: 0.25)
                showOnTV()
            }.font(.subheadline)
        }
    }

    private var outputControls: some View {
        VStack(alignment: .leading, spacing: 12) {
            GLHeader(title: "Přeložené titulky", help: "Zelená určuje místo zobrazení českých dialogů na TV. Velikost písma je nezávislá na oranžovém překladu herních textů.")
            Toggle("Zamknout polohu českých titulků", isOn: $model.outputLocked)
            if !model.outputLocked {
                Text(verbatim: GLInterfaceText.localized("Šířka titulků:") + " " + String(Int(model.outputWidth * 100)) + " %")
                Slider(value: $model.outputWidth, in: 0.30...0.95)
                Text(verbatim: GLInterfaceText.localized("Velikost písma:") + " " + String(Int(model.outputFont * 100)) + " %")
                Slider(value: $model.outputFont, in: 0.70...1.80)
            }
            Button("Náhled na TV") {
                showOnTV()
            }
            .buttonStyle(.borderedProminent)
            .tint(.green)
            Button("Obnovit výchozí polohu") {
                model.outputX = 0.5
                model.outputBottom = 0.93
                model.outputWidth = 0.62
                model.outputFont = 1
                showOnTV()
            }.font(.subheadline)
        }
    }

    private var choicesControls: some View {
        VStack(alignment: .leading, spacing: 12) {
            GLHeader(title: "Herní texty", help: "Žlutá oblast čte originální anglické herní texty, nabídky nebo možnosti odpovědí. Jejich překlad se zobrazuje samostatně v oranžové oblasti.")
            Toggle("Překládat volby / texty", isOn: $model.choicesEnabled)
            if model.choicesEnabled {
                Toggle("Filtrovat pokyny ovladače", isOn: $model.filterChoiceControls)
                Picker("Typ obsahu", selection: $model.choicesTextMode) {
                    Text("Volby odpovědí").tag(false)
                    Text("Text (dopisy, SMS)").tag(true)
                }
                .pickerStyle(.segmented)
                Toggle("Zamknout žlutý rámeček", isOn: $model.choicesLocked)
                if !model.choicesLocked {
                    Text(verbatim: GLInterfaceText.localized("Šířka:") + " " + String(Int(model.choicesArea.width * 100)) + " %")
                    Slider(value: Binding(
                        get: { Double(model.choicesArea.width) },
                        set: { updateChoicesSize(width: CGFloat($0), height: nil) }
                    ), in: 0.10...1.0)
                    Text(verbatim: GLInterfaceText.localized("Výška:") + " " + String(Int(model.choicesArea.height * 100)) + " %")
                    Slider(value: Binding(
                        get: { Double(model.choicesArea.height) },
                        set: { updateChoicesSize(width: nil, height: CGFloat($0)) }
                    ), in: 0.10...1.0)
                }
                Button("Náhled na TV") {
                    model.tv.sendChoices([], area: model.choicesArea,
                                         asText: model.choicesTextMode, showFrame: true,
                                         outputArea: model.choicesOutputArea,
                                 outputFont: model.choicesOutputFont)
                }
                .buttonStyle(.bordered)
                .tint(.yellow)
                if !model.lastChoices.isEmpty {
                    Text(verbatim: GLInterfaceText.localized("Poslední rozpoznané:") + " " + model.lastChoices)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Button("Obnovit oblast") {
                    model.choicesArea = CGRect(x: 0.55, y: 0.45,
                                               width: 0.40, height: 0.35)
                    showOnTV()
                }.font(.subheadline)
            } else {
                Text("Volby jsou vypnuté, takže OCR dál čte pouze oblast dialogových titulků.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var choicesOutputControls: some View {
        VStack(alignment: .leading, spacing: 12) {
            GLHeader(title: "Překlad herních textů", help: "Oranžová je výstup překladu ze žluté oblasti. Při hraní se aktuální přeložený text při změně polohy ihned přesune. Písmo můžeš měnit nezávisle na běžných titulcích.")
            if model.choicesEnabled {
                Toggle("Zamknout oranžový rámeček", isOn: $model.choicesOutputLocked)
                if !model.choicesOutputLocked {
                    Text(verbatim: GLInterfaceText.localized("Šířka:") + " " + String(Int(model.choicesOutputArea.width * 100)) + " %")
                    Slider(value: Binding(
                        get: { Double(model.choicesOutputArea.width) },
                        set: { updateChoicesOutputSize(width: CGFloat($0), height: nil) }
                    ), in: 0.20...0.95)
                    Text(verbatim: GLInterfaceText.localized("Výška:") + " " + String(Int(model.choicesOutputArea.height * 100)) + " %")
                    Slider(value: Binding(
                        get: { Double(model.choicesOutputArea.height) },
                        set: { updateChoicesOutputSize(width: nil, height: CGFloat($0)) }
                    ), in: 0.08...0.40)
                }
                gameTextFontControl
                Button("Náhled na TV") {
                    showOnTV()
                }
                .buttonStyle(.borderedProminent)
                .tint(.orange)
                Button("Obnovit výchozí polohu") {
                    model.choicesOutputArea = CGRect(x: 0.07, y: 0.43,
                                                     width: 0.43, height: 0.17)
                    showOnTV()
                }
                .font(.subheadline)
            } else {
                Text("Nejdříve zapni překlad herních textů ve žluté oblasti.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    /// Only the orange output panel controls Czech text size.
    /// The yellow rectangle is OCR input only.
    private var gameTextFontControl: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(verbatim: GLInterfaceText.localized("Velikost písma:") + " " + String(Int(model.choicesOutputFont * 100)) + " %")
            Slider(value: Binding(
                get: { model.choicesOutputFont },
                set: { newValue in
                    model.choicesOutputFont = newValue
                    scheduleOrangeRelocation()
                }
            ), in: 0.60...2.00, step: 0.05)
                .tint(.orange)
        }
    }

    private func updateChoicesOutputSize(width: CGFloat?, height: CGFloat?) {
        var area = model.choicesOutputArea
        let center = CGPoint(x: area.midX, y: area.midY)
        if let width { area.size.width = width }
        if let height { area.size.height = height }
        area.origin.x = max(0, min(1 - area.width, center.x - area.width / 2))
        area.origin.y = max(0, min(1 - area.height, center.y - area.height / 2))
        model.choicesOutputArea = area
        scheduleOrangeRelocation()
    }

    private func updateChoicesSize(width: CGFloat?, height: CGFloat?) {
        var area = model.choicesArea
        let center = CGPoint(x: area.midX, y: area.midY)
        if let width { area.size.width = width }
        if let height { area.size.height = height }
        area.origin.x = max(0, min(1 - area.width, center.x - area.width / 2))
        area.origin.y = max(0, min(1 - area.height, center.y - area.height / 2))
        model.choicesArea = area
    }

    private func updateOCRSize(width: CGFloat?, height: CGFloat?) {
        var area = model.manualOCRBox
        let center = CGPoint(x: area.midX, y: area.midY)
        if let width { area.size.width = width }
        if let height { area.size.height = height }
        area.origin.x = max(0, min(1 - area.width, center.x - area.width / 2))
        area.origin.y = max(0, min(1 - area.height, center.y - area.height / 2))
        model.manualOCRBox = area
    }

    // Debounce drag/slider changes while a game is running. When a game text
    // is already visible, move that same translation instead of showing a demo.
    private func scheduleOrangeRelocation() {
        guard model.isRunning else { return }
        orangePreviewTask?.cancel()
        orangePreviewTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(180))
            guard !Task.isCancelled else { return }
            model.relocateActiveChoices()
        }
    }

    private func showOnTV() {
        if editing == .reading {
            guard model.manualOCR else { return }
            model.tv.showOCRRegion(model.manualOCRBox, visible: true)
        } else if editing == .output {
            model.tv.sendSubtitle("Ukázka českých titulků", duration: 6000, preview: true)
        } else if editing == .choices {
            guard model.choicesEnabled else { return }
            model.tv.sendChoices([], area: model.choicesArea,
                                 asText: model.choicesTextMode, showFrame: true,
                                 outputArea: model.choicesOutputArea,
                                 outputFont: model.choicesOutputFont)
        } else {
            guard model.choicesEnabled else { return }
            // Prefer the real live translation, but the preview button must
            // still work if OCR has not recognized any yellow-area text yet.
            if model.isRunning && model.relocateActiveChoices() {
                orangePreviewTask?.cancel()
                return
            }
            orangePreviewTask?.cancel()
            model.tv.sendChoices(["Ukázka českého překladu"],
                                 area: model.choicesArea, asText: true,
                                 showFrame: false, outputArea: model.choicesOutputArea,
                                 outputFont: model.choicesOutputFont)
            orangePreviewTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(6))
                guard !Task.isCancelled else { return }
                // Do not erase a newer real game translation after the demo.
                if model.isRunning && model.relocateActiveChoices() { return }
                model.tv.sendChoices([], area: model.choicesArea,
                                     asText: model.choicesTextMode,
                                     outputArea: model.choicesOutputArea,
                                 outputFont: model.choicesOutputFont)
            }
        }
    }
}
