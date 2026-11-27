import Foundation

/// Independent of game OCR source and translation destination languages.
/// The supported UI languages and their native names are generated from
/// shared/locales/manifest.json; no new Swift case is needed to add a locale.
struct GLAppLanguage: RawRepresentable, Hashable, Identifiable, Codable, CaseIterable {
    let rawValue: String

    init?(rawValue: String) {
        guard Self.supportedCodes.contains(rawValue) else { return nil }
        self.rawValue = rawValue
    }

    // BEGIN GENERATED UI LANGUAGES
    private static let supportedCodes = [
        "cs",
        "en",
        "sk",
    ]
    private static let nativeNames: [String: String] = [
        "cs": "Čeština",
        "en": "English",
        "sk": "Slovenčina",
    ]
    // END GENERATED UI LANGUAGES

    static var allCases: [Self] {
        supportedCodes.compactMap { Self(rawValue: $0) }
    }
    static let czech = Self(rawValue: "cs")!
    static let english = Self(rawValue: "en")!
    static let slovak = Self(rawValue: "sk")!

    static var systemDefault: Self {
        let first = Locale.preferredLanguages.first ?? "en"
        let exact = Self(rawValue: first)
        let base = Self(rawValue: String(first.split(separator: "-").first ?? "en"))
        return exact ?? base ?? .english
    }

    var id: String { rawValue }
    var title: String { Self.nativeNames[rawValue] ?? rawValue }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let language = Self(rawValue: raw) else {
            throw DecodingError.dataCorruptedError(in: try decoder.singleValueContainer(),
                debugDescription: "Unsupported GameLingo interface language")
        }
        self = language
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

/// Dynamic UI strings use the same generated .lproj catalog as SwiftUI Text.
/// If the requested locale lacks a string, fall back to English, then Czech.
enum GLInterfaceText {
    // Only for app-defined status fields, never OCR output, profile names or logs.
    // Keep the original status in the model so changing the UI language also
    // translates a previously received status. Translate the template, not
    // the port, address, console name or external error supplied as its value.
    private static let statusTemplates = [
        "Odesláno do %@. Zobrazení potvrdí až televize.",
        "Hledání není dostupné: %@. Zadej IP ručně.",
        "Hledání selhalo: %@. IP lze zadat ručně.",
        "%@ · automatické obnovení vyčerpáno",
        "Microsoft přihlášení odmítl: %@",
        "Nelze získat kód Microsoftu: %@",
        "Párování nebylo odesláno: %@",
        "Připojuji se k TV na UDP %@…",
        "Ověřuji kód na TV (UDP %@)…",
        "Obnovuji Xbox · pokus %@/2",
        "Spojení s TV selhalo: %@",
        "Nalezeno konzolí: %@",
        "Přijímač TV: UDP %@",
        "Chyba odeslání: %@",
        "TV neodpovídá: %@",
        "Nelze odeslat: %@",
        "PlayStation: %@",
        "Připojuji %@…",
    ]

    static func localizedStatus(_ status: String,
                                translate: (String) -> String = { GLInterfaceText.localized($0) }) -> String {
        let exact = translate(status)
        if exact != status { return exact }
        for template in statusTemplates {
            let parts = template.components(separatedBy: "%@")
            guard parts.count == 2 else { continue }
            let prefix = parts[0], suffix = parts[1]
            guard status.hasPrefix(prefix), status.hasSuffix(suffix),
                  status.count > prefix.count + suffix.count else { continue }
            let value = String(status.dropFirst(prefix.count).dropLast(suffix.count))
            let localizedTemplate = translate(template)
            guard localizedTemplate.components(separatedBy: "%@").count == 2 else { return status }
            return localizedTemplate.replacingOccurrences(of: "%@", with: value)
        }
        return status
    }

    // Only for internally generated, non-content diagnostic metrics. Leave raw
    // logs canonical and translate at display time so language switching works.
    private static let diagnosticFields = [
        "Dialog čekání snímku",
        "Dialog OCR",
        "Dialog čekání po OCR",
        "Dialog překlad",
        "Dialog celkem k výstupu",
        "Herní text čekání snímku",
        "Herní text OCR",
        "Herní text překlad",
        "Herní text celkem k výstupu",
        "Chyby překladu",
        "Xbox dekódovaný obraz",
        "Xbox síť (video)",
        "Xbox síť",
        "Paměť",
    ]

    static func localizedDiagnostics(_ summary: String,
                                     translate: (String) -> String = { GLInterfaceText.localized($0) }) -> String {
        summary.components(separatedBy: "\n").map { line in
            guard let field = diagnosticFields.first(where: { line.hasPrefix($0 + ": ") }) else {
                return translate(line)
            }
            var value = String(line.dropFirst(field.count + 2))
            let tokens: [String]
            switch field {
            case "Xbox dekódovaný obraz":
                tokens = ["bez nového snímku ≥ 5 s", "čekám na první snímek"]
            case "Xbox síť (video)":
                tokens = ["ztráta", "paketů"]
            case "Xbox síť":
                tokens = ["nedostupná"]
            case "Paměť":
                tokens = ["RSS nedostupná", "teplotní stav", "nominal", "fair", "serious", "critical", "unknown"]
            case "Dialog OCR", "Herní text OCR":
                tokens = ["dlouhé čekání ≥ 5 s"]
            default:
                tokens = []
            }
            for token in tokens {
                value = value.replacingOccurrences(of: token, with: translate(token))
            }
            return translate(field) + ": " + value
        }.joined(separator: "\n")
    }

    static func localized(_ key: String) -> String {
        let current = UserDefaults.standard.string(forKey: "gamelingo.appLanguage")
            ?? GLAppLanguage.systemDefault.rawValue
        // Czech source strings are the keys; no cs.lproj file is needed.
        if current == "cs" { return key }
        for language in [current, "en"] {
            guard let path = Bundle.main.path(forResource: language, ofType: "lproj"),
                  let bundle = Bundle(path: path) else { continue }
            let missing = "__GAMELINGO_MISSING_TRANSLATION__"
            let value = bundle.localizedString(forKey: key, value: missing, table: "Localizable")
            // A legitimate translation may equal the Czech source text.
            if value != missing { return value }
        }
        return key
    }
}
