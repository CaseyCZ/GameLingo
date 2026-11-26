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
    static func localized(_ key: String) -> String {
        let current = UserDefaults.standard.string(forKey: "gamelingo.appLanguage")
            ?? GLAppLanguage.systemDefault.rawValue
        for language in [current, "en"] {
            guard let path = Bundle.main.path(forResource: language, ofType: "lproj"),
                  let bundle = Bundle(path: path) else { continue }
            let value = bundle.localizedString(forKey: key, value: key, table: "Localizable")
            if value != key || language == "en" { return value }
        }
        return key
    }
}
