import SwiftUI

struct GLTranslationModePicker: View {
    @ObservedObject var model: CaptionAppModel
    var body: some View {
        VStack(alignment: .center, spacing: 5) {
            Text("Režim překladu").font(.caption)
            HStack(spacing: 6) {
                ForEach([false, true], id: \.self) { automatic in
                    Button {
                        model.setAutomaticGameDialogue(automatic)
                    } label: {
                        Text(automatic ? LocalizedStringKey("Automatický") : LocalizedStringKey("Standardní"))
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .foregroundStyle(.white)
                            .background {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(automatic == model.automaticGameDialogue ? AnyShapeStyle(GLStyle.gradient) : AnyShapeStyle(GLStyle.surface))
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(automatic == model.automaticGameDialogue ? .isSelected : [])
                }
            }
            .disabled(model.isRunning)
        }
    }
}

struct GLAutomaticZonesView: View {
    @ObservedObject var model: CaptionAppModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            Section {
                ForEach(model.automaticZones) { zone in
                    HStack {
                        Toggle(zone.name, isOn: Binding(get: { zone.enabled }, set: { enabled in
                            edit(zone.id) { $0.enabled = enabled }
                        }))
                        NavigationLink {
                            GLAutomaticZoneEditor(zone: zone) { edited in
                                edit(zone.id) { $0 = edited }
                            }
                        } label: { Image(systemName: "slider.horizontal.3") }
                        .buttonStyle(.borderless)
                        Button(role: .destructive) {
                            model.updateAutomaticZones(model.automaticZones.filter { $0.id != zone.id })
                        } label: { Image(systemName: "trash") }
                        .buttonStyle(.borderless)
                    }
                }
                Button {
                    var all = model.automaticZones
                    all.append(.init(name: "Zóna " + String(all.count+1),
                                     rect: CGRect(x: 0.2, y: 0.2, width: 0.6, height: 0.25)))
                    model.updateAutomaticZones(all)
                } label: { Label("Přidat zónu", systemImage: "plus") }
                .disabled(model.automaticZones.count >= 10)
            } footer: {
                Text("Vyber jen oblasti, které chceš překládat. Herní profil není povinný.")
            }
        }
        .disabled(model.isRunning)
        .navigationTitle("Oblasti překladu")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { GLBackButton { dismiss() } }
            ToolbarItem(placement: .topBarTrailing) {
                GLHelpButton(title: "Oblasti překladu", explanation: "Vyber jen oblasti, které chceš překládat. Herní profil není povinný.")
            }
        }
    }
    private func edit(_ id: UUID, _ change: (inout AutomaticOCRZone) -> Void) {
        var zones = model.automaticZones
        guard let i = zones.firstIndex(where: { $0.id == id }) else { return }
        change(&zones[i])
        model.updateAutomaticZones(zones)
    }
}

private struct GLAutomaticZoneEditor: View {
    @State var zone: AutomaticOCRZone
    let save: (AutomaticOCRZone) -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Form {
            TextField("Název", text: $zone.name)
            GeometryReader { proxy in
                ZStack(alignment: .topLeading) {
                    Rectangle().fill(Color.black.opacity(0.7))
                    Rectangle().strokeBorder(Color.yellow, lineWidth: 3)
                        .frame(width: proxy.size.width * zone.rect.width, height: proxy.size.height * zone.rect.height)
                        .offset(x: proxy.size.width * zone.rect.minX, y: proxy.size.height * zone.rect.minY)
                }
            }.aspectRatio(16/9, contentMode: .fit)
            slider("Vodorovná pozice", value: Binding(get: { Double(zone.rect.minX) }, set: { zone.rect.origin.x = CGFloat($0) }),
                   range: 0...max(0.001, 1-Double(zone.rect.width)))
            slider("Svislá pozice", value: Binding(get: { Double(zone.rect.minY) }, set: { zone.rect.origin.y = CGFloat($0) }),
                   range: 0...max(0.001, 1-Double(zone.rect.height)))
            slider("Šířka", value: Binding(get: { Double(zone.rect.width) }, set: { zone.rect.size.width = CGFloat($0) }),
                   range: 0.08...max(0.08, 1-Double(zone.rect.minX)))
            slider("Výška", value: Binding(get: { Double(zone.rect.height) }, set: { zone.rect.size.height = CGFloat($0) }),
                   range: 0.04...max(0.04, 1-Double(zone.rect.minY)))
            Button("Uložit") {
                zone.centered = false
                save(zone)
                dismiss()
            }.disabled(!zone.valid)
        }
        .navigationTitle("Upravit zónu")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                GLHelpButton(title: "Upravit zónu", explanation: "Vyber jen oblasti, které chceš překládat. Herní profil není povinný.")
            }
        }
    }
    private func slider(_ label: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View {
        VStack(alignment: .leading) {
            Text(LocalizedStringKey(label))
            Slider(value: value, in: range)
        }
    }
}
