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
