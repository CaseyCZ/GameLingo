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
            ["Hlášení problémů v diagnostice", "Raketa v horní liště otevírá novinky."] : ["Přehled této verze je na GitHubu."]
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
                .toolbar { ToolbarItem(placement: .topBarLeading) { GLBackButton { dismiss() } } }
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
                .toolbar { ToolbarItem(placement: .topBarLeading) { GLBackButton { dismiss() } } }
        }.preferredColorScheme(.dark).tint(GLStyle.blue)
    }
}
