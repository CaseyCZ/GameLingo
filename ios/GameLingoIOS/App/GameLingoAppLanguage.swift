import Foundation

/// A separate setting from the language spoken in the game and its subtitles.
/// The language only controls GameLingo navigation and interface copy.
enum GLAppLanguage: String, CaseIterable, Identifiable, Codable {
    case czech = "cs"
    case english = "en"
    case slovak = "sk"

    var id: String { rawValue }
    var title: String {
        switch self {
        case .czech: return "Čeština"
        case .english: return "English"
        case .slovak: return "Slovenčina"
        }
    }
}

/// Resolve dynamic labels against the language selected inside GameLingo.
/// SwiftUI Text(LocalizedStringKey) uses the same .lproj catalog automatically.
enum GLInterfaceText {
    static func localized(_ key: String) -> String {
        let language = UserDefaults.standard.string(forKey: "gamelingo.appLanguage") ?? "cs"
        guard let path = Bundle.main.path(forResource: language, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return key }
        return bundle.localizedString(forKey: key, value: key, table: "Localizable")
    }
}
