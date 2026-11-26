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
        ScrollView {
            VStack(spacing: 12) {
                GLNeonTitle(title: "Oblasti překladu", subtitle: "Vyber jen oblasti, které chceš překládat.", icon: "viewfinder")
                ForEach(model.automaticZones) { zone in
                    GLNeonPanel(emphasized: zone.enabled) {
                    HStack(spacing: 12) {
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
                    }.padding(12)
                    }
                }
                GLCard {
                Button {
                    var all = model.automaticZones
                    all.append(.init(name: "Zóna " + String(all.count+1),
                                     rect: CGRect(x: 0.2, y: 0.2, width: 0.6, height: 0.25)))
                    model.updateAutomaticZones(all)
                } label: { Label("Přidat zónu", systemImage: "plus") }
                .disabled(model.automaticZones.count >= 10)
                }
            }.padding(16)
        }
        .background(GLNeonBackground())
        .tint(GLStyle.blue)
        .navigationBarBackButtonHidden(true)
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
        ScrollView {
            VStack(spacing: 12) {
            GLNeonTitle(title: "Upravit zónu", subtitle: "Náhled obrazovky", icon: "viewfinder")
            GLCard {
            TextField("Název", text: $zone.name).multilineTextAlignment(.center)
            }
            GLNeonPanel(emphasized: true) {
            VStack(spacing: 10) {
            GeometryReader { proxy in
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 13).fill(LinearGradient(colors: [GLNeon.panel, GLNeon.ink], startPoint: .topLeading, endPoint: .bottomTrailing))
                    Image(systemName: "mountain.2.fill").resizable().scaledToFit().foregroundStyle(GLNeon.cyan.opacity(0.18)).frame(width: proxy.size.width * 0.85, height: proxy.size.height * 0.68).position(x: proxy.size.width * 0.57, y: proxy.size.height * 0.46)
                    Rectangle().strokeBorder(Color.yellow, lineWidth: 3)
                        .frame(width: proxy.size.width * zone.rect.width, height: proxy.size.height * zone.rect.height)
                        .offset(x: proxy.size.width * zone.rect.minX, y: proxy.size.height * zone.rect.minY)
                }
            }.aspectRatio(16.0/9.0, contentMode: .fit)
            }
            .padding(15)
            }
            GLCard {
            slider("Vodorovná pozice", value: Binding(get: { Double(zone.rect.minX) }, set: { zone.rect.origin.x = CGFloat($0) }),
                   range: 0...max(0.001, 1-Double(zone.rect.width)))
            slider("Svislá pozice", value: Binding(get: { Double(zone.rect.minY) }, set: { zone.rect.origin.y = CGFloat($0) }),
                   range: 0...max(0.001, 1-Double(zone.rect.height)))
            slider("Šířka", value: Binding(get: { Double(zone.rect.width) }, set: { zone.rect.size.width = CGFloat($0) }),
                   range: 0.08...max(0.08, 1-Double(zone.rect.minX)))
            slider("Výška", value: Binding(get: { Double(zone.rect.height) }, set: { zone.rect.size.height = CGFloat($0) }),
                   range: 0.04...max(0.04, 1-Double(zone.rect.minY)))
            }
            Button("Uložit") {
                zone.centered = false
                save(zone)
                dismiss()
            }.buttonStyle(.borderedProminent).disabled(!zone.valid)
            }.padding(16)
        }
        .background(GLNeonBackground())
        .tint(GLStyle.blue)
        .navigationBarBackButtonHidden(true)
        .navigationTitle("Upravit zónu")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { GLBackButton { dismiss() } }
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
