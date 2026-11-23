import Foundation

/// Languages offered for reading game dialogue with Apple Vision.
enum GLGameLanguage: String, CaseIterable, Identifiable, Codable {
    case english = "en"
    case german = "de"
    case french = "fr"
    case spanish = "es"
    case italian = "it"
    case portuguese = "pt"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .english: return "Angličtina"
        case .german: return "Němčina"
        case .french: return "Francouzština"
        case .spanish: return "Španělština"
        case .italian: return "Italština"
        case .portuguese: return "Portugalština"
        }
    }
    var localizedTitle: String { GLInterfaceText.localized(title) }

    var visionIdentifier: String {
        switch self {
        case .english: return "en-US"
        case .german: return "de-DE"
        case .french: return "fr-FR"
        case .spanish: return "es-ES"
        case .italian: return "it-IT"
        case .portuguese: return "pt-BR"
        }
    }
}

enum GLTargetLanguage: String, CaseIterable, Identifiable, Codable {
    case czech = "cs"
    case english = "en"
    case slovak = "sk"
    case german = "de"
    case polish = "pl"
    case french = "fr"
    case spanish = "es"
    case italian = "it"
    case portuguese = "pt"

    var id: String { rawValue }
    var localizedTitle: String { GLInterfaceText.localized(title) }

    var title: String {
        switch self {
        case .czech: return "Čeština"
        case .english: return "Angličtina"
        case .slovak: return "Slovenština"
        case .german: return "Němčina"
        case .polish: return "Polština"
        case .french: return "Francouzština"
        case .spanish: return "Španělština"
        case .italian: return "Italština"
        case .portuguese: return "Portugalština"
        }
    }
}
