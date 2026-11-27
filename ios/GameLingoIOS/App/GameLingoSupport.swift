import SwiftUI
import UIKit

struct GLRocketIcon: View {
    var body: some View {
        Path { p in
            p.move(to: CGPoint(x: 5, y: 17))
            p.addQuadCurve(to: CGPoint(x: 21, y: 3), control: CGPoint(x: 7, y: 3))
            p.addQuadCurve(to: CGPoint(x: 9, y: 20), control: CGPoint(x: 21, y: 16))
            p.closeSubpath()
            p.move(to: CGPoint(x: 7, y: 10)); p.addLine(to: CGPoint(x: 2, y: 13)); p.addLine(to: CGPoint(x: 5, y: 17))
            p.move(to: CGPoint(x: 14, y: 17)); p.addLine(to: CGPoint(x: 11, y: 23)); p.addLine(to: CGPoint(x: 9, y: 20))
            p.move(to: CGPoint(x: 5, y: 20)); p.addLine(to: CGPoint(x: 2, y: 23))
            p.addEllipse(in: CGRect(x: 12, y: 8, width: 4, height: 4))
        }.stroke(style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
            .frame(width: 24, height: 24)
    }
}
struct GLNewsPage: View {
    @Environment(\.dismiss) private var dismiss
    private var entries: [String] {
        GameLingoUpdateModel.currentVersion == "0.5.9" ?
            ["Obrázková a interaktivní nápověda", "Hlášení problémů v diagnostice", "Raketa v horní liště otevírá novinky."] : ["Přehled této verze je na GitHubu."]
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        GLRocketIcon().foregroundStyle(GLStyle.blue)
                        Text("GameLingo " + GameLingoUpdateModel.currentVersion).font(.headline)
                    }
                    ForEach(entries, id: \.self) { item in
                        Label(LocalizedStringKey(item), systemImage: "checkmark.circle").font(.subheadline)
                    }
                    if let url = URL(string: "https://github.com/CaseyCZ/GameLingo/releases") {
                        Link("Historie vydání", destination: url).font(.subheadline)
                    }
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
            }.background(GLNeonBackground())
                .navigationTitle("Co je nového").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) { GLBackButton { dismiss() } }
                    ToolbarItem(placement: .topBarTrailing) {
                        GLHelpButton(title: "Co je nového", explanation: "Přehled této verze je na GitHubu.")
                    }
                }
        }.tint(GLStyle.blue).preferredColorScheme(.dark)
    }
}

// Fixed metadata and explicit boolean/numeric state only, never raw logs or credentials.
struct GLProblemReportPage: View {
    @Environment(\.dismiss) private var dismiss
    let diagnostic: String
    @State private var category = "Překlad a titulky"
    @State private var detail = ""
    @State private var include = true
    @State private var copied = false
    private let categories = ["Překlad a titulky","Připojení konzole","Párování TV","Kamera a OCR","Pád aplikace","Vzhled a ovládání","Ostatní"]
    private var metadata: String { "GameLingo " + GameLingoUpdateModel.currentVersion + " · iOS " + UIDevice.current.systemVersion }
    private var report: String {
        metadata + "\n" + GLInterfaceText.localized(category) + "\n\n" + String(detail.prefix(2000)) + (include ? "\n\n"+diagnostic : "")
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 9) {
                    Text(verbatim: metadata).font(.caption).foregroundStyle(GLStyle.muted)
                    Picker("Typ problému", selection: $category) {
                        ForEach(categories, id: \.self) { Text(LocalizedStringKey($0)).tag($0) }
                    }.pickerStyle(.menu)
                    Text("Popis problému").font(.subheadline)
                    TextEditor(text: $detail).font(.subheadline).frame(minHeight: 90, maxHeight: 120)
                        .scrollContentBackground(.hidden).padding(6).background(GLNeon.panel)
                    Toggle("Přiložit základní diagnostiku", isOn: $include).font(.subheadline)
                    Text("GitHub je veřejný. Před odesláním zkontroluj hlášení; potřebuješ účet.").font(.caption)
                    Text(verbatim: report).font(.caption.monospaced()).textSelection(.enabled)
                    Button("Zkopírovat hlášení") { UIPasteboard.general.string = report; copied = true }
                        .disabled(detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if copied { Text("Zkopírováno").font(.caption).foregroundStyle(GLStyle.green) }
                    if let url = URL(string: "https://github.com/CaseyCZ/GameLingo/issues/new") { Link("Otevřít GitHub", destination: url) }
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
            }.background(GLNeonBackground()).navigationTitle("Nahlásit problém").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) { GLBackButton { dismiss() } }
                    ToolbarItem(placement: .topBarTrailing) {
                        GLHelpButton(title: "Nahlásit problém", explanation: "GitHub je veřejný. Před odesláním zkontroluj hlášení; potřebuješ účet.")
                    }
                }
        }.preferredColorScheme(.dark).tint(GLStyle.blue)
    }
}

struct GLGuideContent: View {
    let title: String
    let explanation: String
    @State private var selected = 0
    @State private var expanded = false
    @State private var demo = false
    @State private var position = 0.25
    @State private var rule = false
    @State private var full = false
    private var kind: String {
        let t = title.lowercased()
        if t.contains("kamer") { return "camera" }
        if t.contains("slovník") { return "glossary" }
        if t.contains("oblast") || t.contains("rozložení") { return "regions" }
        if t.contains("xbox") || t.contains("playstation") { return "console" }
        if t.contains("párov") || t.contains("google tv") || t == "gamelingo tv" { return "pairing" }
        if t == "gamelingo" { return "home" }
        return "other"
    }
    private var steps: [(String,String)] {
        switch kind {
        case "camera": return [("camera", "Zvol Kameru ve výběru zařízení."), ("tv", "Namiř telefon na text nebo obrazovku."), ("viewfinder", "Zvol celý obraz nebo nastav žlutý rámeček na původní titulky."), ("text.bubble", "Překlad se zobrazí podle vybraného režimu.")]
        case "glossary": return [("text.bubble", "Opiš originální výraz přesně ze hry."), ("character.book.closed.fill", "Doplň chybný překlad a požadovanou opravu oddělené svislítky."), ("checkmark.circle", "Ulož slovník a použij správný profil před hraním.")]
        case "regions": return [("viewfinder", "Modrá čte dialogy, zelená určuje jejich výstup."), ("rectangle.dashed", "Žlutá čte herní texty, oranžová určuje jejich výstup."), ("checkmark.circle", "Uprav oblasti podle obrazu a ulož profil.")]
        case "console": return [("gamecontroller", "Povol vzdálené funkce na konzoli a přihlas se ke svému účtu."), ("display.2", "Vyber uloženou konzoli nebo přidej novou."), ("play.fill", "Vyber zdroj obrazu a výstup titulků.")]
        case "pairing": return [("wifi", "Připoj telefon a televizi ke stejné domácí síti."), ("qrcode.viewfinder", "Na televizi otevři párování a v telefonu načti QR kód nebo zadej celý kód."), ("checkmark.circle", "Ověř spojení a spusť překlad.")]
        case "home": return [("gamecontroller", "Vyber zdroj obrazu podle zařízení."), ("globe", "Připoj zařízení a vyber jazyk hry a překladu."), ("play.fill", "Nastav výstup a spusť překlad.")]
        default: return GLInterfaceText.localized(explanation).components(separatedBy: ". ").filter { !$0.isEmpty }.map { ("info.circle", $0) }
        }
    }
    private func illustration(_ i: Int, large: Bool = false) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 7).fill(GLNeon.panel)
            if kind == "glossary" {
                VStack(spacing: 2) {
                    Text(verbatim: "Save game").foregroundStyle(i == 0 ? GLStyle.blue : .white)
                    if i > 0 { Text(verbatim: "Zachránit hru → Uložit hru").foregroundStyle(GLStyle.blue) }
                }.font(.system(size: large ? 14 : 9, design: .monospaced)).padding(4)
            } else if kind == "regions" || (kind == "camera" && i >= 2) {
                VStack(spacing: 3) {
                    Text(verbatim: "Where are you going?").padding(3).frame(maxWidth: .infinity)
                        .overlay(Rectangle().stroke(kind == "camera" ? Color.yellow : Color.blue, lineWidth: 1.5))
                    Text(verbatim: "Kam jdeš?").padding(3).frame(maxWidth: .infinity)
                        .overlay(Rectangle().stroke(kind == "camera" ? Color.clear : Color.green, lineWidth: 1.5))
                    if kind == "regions" && i == 1 {
                        HStack {
                            Text(verbatim: "Inventory").overlay(Rectangle().stroke(Color.yellow))
                            Text(verbatim: "Inventář").overlay(Rectangle().stroke(Color.orange))
                        }
                    }
                }.font(.system(size: large ? 14 : 8)).padding(4)
            } else {
                Image(systemName: steps[i].0).font(.system(size: large ? 44 : 25)).foregroundStyle(GLStyle.blue)
            }
        }.frame(width: large ? nil : 96, height: large ? 120 : 62).clipped()
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(LocalizedStringKey(title)).font(.headline)
                ForEach(steps.indices, id: \.self) { i in
                    Button { selected = i; expanded = true } label: {
                        HStack(spacing: 10) {
                            illustration(i)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(i+1)").font(.caption.bold()).foregroundStyle(GLStyle.blue)
                                Text(LocalizedStringKey(steps[i].1)).font(.subheadline).foregroundStyle(.white)
                            }.frame(maxWidth: .infinity, alignment: .leading)
                        }.padding(4).overlay(RoundedRectangle(cornerRadius: 8).stroke(expanded && selected == i ? GLStyle.blue : .clear))
                    }.buttonStyle(.plain)
                }
                if expanded && steps.indices.contains(selected) {
                    illustration(selected, large: true)
                    Text(LocalizedStringKey(steps[selected].1)).font(.caption)
                    HStack {
                        Button("Předchozí krok") { selected -= 1 }.disabled(selected == 0)
                        Spacer()
                        Button("Další krok") { selected += 1 }.disabled(selected == steps.count - 1)
                    }.font(.caption)
                    Button("Hotovo") { expanded = false }.font(.caption)
                }
                if kind != "other" { Text(LocalizedStringKey(explanation)).font(.caption).foregroundStyle(GLStyle.muted) }
                if ["camera","regions","glossary"].contains(kind) {
                    Button("Vyzkoušet") { demo.toggle() }.font(.subheadline)
                    if demo { practice }
                }
            }.padding(14).frame(maxWidth: .infinity, alignment: .leading)
        }
    }
    // Practice is local view state only, never real OCR settings or stored profiles.
    private var practice: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Ukázka bez změny nastavení").font(.caption).foregroundStyle(GLStyle.muted)
            if kind == "glossary" {
                Text("Příklad pravidla (angličtina → čeština)").font(.caption)
                Text(verbatim: "Save game | Zachránit hru | Uložit hru").font(.caption.monospaced())
                Toggle("Použít ukázkové pravidlo", isOn: $rule).font(.subheadline)
                Text(verbatim: rule ? "Uložit hru" : "Zachránit hru").font(.subheadline)
            } else {
                if kind == "camera" { Toggle("Celý obraz", isOn: $full).font(.subheadline) }
                GeometryReader { g in
                    ZStack(alignment: .top) {
                        RoundedRectangle(cornerRadius: 8).fill(GLNeon.ink)
                        Text(verbatim: "Where are you going?").font(.caption).position(x: g.size.width/2, y: 74)
                        if !full {
                            Rectangle().stroke(kind == "camera" ? Color.yellow : Color.blue, lineWidth: 2)
                                .frame(width: g.size.width*0.85, height: 24)
                                .position(x: g.size.width/2, y: CGFloat(12+90*position))
                        }
                        if full || abs(12+90*position-74) < 12 {
                            Text(verbatim: "Kam jdeš?").font(.caption.bold()).position(x: g.size.width/2, y: 107)
                        }
                    }
                }.frame(height: 120)
                if !full {
                    Text("Posuň oblast na původní titulek.").font(.caption)
                    Slider(value: $position, in: 0...1)
                }
            }
        }.padding(9).background(GLNeon.panel, in: RoundedRectangle(cornerRadius: 9))
    }
}
