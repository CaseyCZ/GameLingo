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
    @State private var selectedID: UUID?
    @State private var confirmDelete = false

    private var selected: AutomaticOCRZone? {
        model.automaticZones.first { $0.id == selectedID } ?? model.automaticZones.first
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 17) {
                GLNeonTitle(title: "Oblasti překladu", subtitle: "Vyber jen oblasti, které chceš překládat.", icon: "viewfinder")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 78))], spacing: 8) {
                    ForEach(model.automaticZones) { zone in
                        Button { selectedID = zone.id } label: {
                            VStack(spacing: 7) {
                                Image(systemName: zone.enabled ? "viewfinder" : "eye.slash")
                                    .font(.system(size: 18, weight: .medium))
                                    .foregroundStyle(Color.yellow)
                                Text(zone.name).font(.system(size: 11, weight: .semibold))
                                    .lineLimit(1).minimumScaleFactor(0.75)
                            }
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity).frame(height: 65)
                            .background(selected?.id == zone.id ? Color.yellow.opacity(0.19) : GLNeon.panel,
                                        in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.yellow.opacity(selected?.id == zone.id ? 0.95 : 0.35)))
                        }.buttonStyle(.plain)
                        .accessibilityAddTraits(selected?.id == zone.id ? .isSelected : [])
                    }
                    Button {
                        var zones = model.automaticZones
                        let zone = AutomaticOCRZone(name: "Zóna " + String(zones.count + 1),
                            rect: CGRect(x: 0.2, y: 0.2, width: 0.6, height: 0.25))
                        zones.append(zone)
                        model.updateAutomaticZones(zones)
                        selectedID = zone.id
                    } label: {
                        VStack(spacing: 7) {
                            Image(systemName: "plus")
                            Text("Přidat zónu").font(.caption2)
                        }.frame(maxWidth: .infinity).frame(height: 65)
                    }.buttonStyle(.bordered).disabled(model.automaticZones.count >= 10)
                }
                if let zone = selected {
                    GLNeonPanel(emphasized: true) {
                        VStack(alignment: .leading, spacing: 11) {
                            Label("Náhled obrazovky", systemImage: "tv").font(.subheadline.bold())
                            preview(zone)
                            Text("Přetáhni vybraný rámeček. Ostatní oblasti zůstanou na svém místě.")
                                .font(.caption).foregroundStyle(GLNeon.mist)
                        }.padding(15)
                    }
                    GLCard {
                        VStack(alignment: .leading, spacing: 12) {
                            GLHeader(title: "Upravit zónu")
                            TextField("Název", text: Binding(get: { selected?.name ?? zone.name }, set: { name in edit(zone.id) { $0.name = name } }))
                            Toggle("Aktivní zóna", isOn: Binding(get: { selected?.enabled ?? zone.enabled }, set: { enabled in edit(zone.id) { $0.enabled = enabled } }))
                            slider("Šířka", value: Double(zone.rect.width), range: 0.08...1) { value in
                                edit(zone.id) { $0.rect.size.width = CGFloat(value); $0.rect.origin.x = min($0.rect.minX, 1 - $0.rect.width); $0.centered = false }
                            }
                            slider("Výška", value: Double(zone.rect.height), range: 0.04...1) { value in
                                edit(zone.id) { $0.rect.size.height = CGFloat(value); $0.rect.origin.y = min($0.rect.minY, 1 - $0.rect.height); $0.centered = false }
                            }
                            Button("Smazat zónu", role: .destructive) { confirmDelete = true }
                        }
                    }
                }
            }.padding(16)
            .disabled(model.isRunning)
        }
        .background(GLNeonBackground())
        .tint(GLStyle.blue)
        .navigationBarBackButtonHidden(true)
        .navigationTitle("Oblasti překladu")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { GLBackButton { dismiss() } }
            ToolbarItem(placement: .topBarTrailing) {
                GLHelpButton(title: "Oblasti překladu", explanation: "Vyber zónu a přetáhni její rámeček. Změny se ukládají automaticky.")
            }
        }
        .confirmationDialog("Smazat zónu", isPresented: $confirmDelete) {
            Button("Smazat zónu", role: .destructive) {
                guard let id = selected?.id, !model.isRunning else { return }
                model.updateAutomaticZones(model.automaticZones.filter { $0.id != id })
                selectedID = model.automaticZones.first?.id
            }
        }
    }

    private func preview(_ zone: AutomaticOCRZone) -> some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 13).fill(LinearGradient(colors: [GLNeon.panel, GLNeon.ink], startPoint: .topLeading, endPoint: .bottomTrailing))
                Image(systemName: "mountain.2.fill").resizable().scaledToFit()
                    .foregroundStyle(GLNeon.cyan.opacity(0.18))
                    .frame(width: geo.size.width * 0.85, height: geo.size.height * 0.68)
                    .position(x: geo.size.width * 0.57, y: geo.size.height * 0.46)
                ForEach(model.automaticZones) { item in
                    Text(item.name).font(.caption2.bold()).lineLimit(1)
                        .foregroundStyle(item.id == zone.id ? Color.black : Color.yellow)
                        .frame(width: max(1, item.rect.width * geo.size.width), height: max(1, item.rect.height * geo.size.height))
                        .background(Color.yellow.opacity(item.id == zone.id ? 0.85 : 0.15))
                        .overlay(Rectangle().strokeBorder(Color.yellow, lineWidth: item.id == zone.id ? 3 : 1))
                        .position(x: item.rect.midX * geo.size.width, y: item.rect.midY * geo.size.height)
                        .opacity(item.enabled ? 1 : 0.45)
                        .allowsHitTesting(false)
                }
                Color.clear.contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                        let x = value.location.x / max(1, geo.size.width)
                        let y = value.location.y / max(1, geo.size.height)
                        edit(zone.id) {
                            $0.rect.origin.x = max(0, min(1 - $0.rect.width, x - $0.rect.width / 2))
                            $0.rect.origin.y = max(0, min(1 - $0.rect.height, y - $0.rect.height / 2))
                            $0.centered = false
                        }
                    })
            }.clipShape(RoundedRectangle(cornerRadius: 13))
        }.aspectRatio(16.0 / 9.0, contentMode: .fit)
    }

    private func slider(_ label: String, value: Double, range: ClosedRange<Double>, change: @escaping (Double) -> Void) -> some View {
        VStack(alignment: .leading) {
            Text(verbatim: GLInterfaceText.localized(label) + ": " + String(Int(value * 100)) + " %")
            Slider(value: Binding(get: { min(range.upperBound, max(range.lowerBound, value)) }, set: change), in: range)
        }
    }

    private func edit(_ id: UUID, _ change: (inout AutomaticOCRZone) -> Void) {
        guard !model.isRunning else { return }
        var zones = model.automaticZones
        guard let i = zones.firstIndex(where: { $0.id == id }) else { return }
        change(&zones[i])
        model.updateAutomaticZones(zones)
    }
}
