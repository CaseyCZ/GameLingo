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

    /// Preview only; actual live subtitles always use the translation engine.
    var sampleSubtitle: String {
        switch self {
        case .czech: return "To je skvělý plán!"
        case .english: return "That's a great plan!"
        case .slovak: return "To je skvelý plán!"
        case .german: return "Das ist ein toller Plan!"
        case .polish: return "To świetny plan!"
        case .french: return "C'est un excellent plan !"
        case .spanish: return "¡Es un gran plan!"
        case .italian: return "È un ottimo piano!"
        case .portuguese: return "É um ótimo plano!"
        }
    }

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
