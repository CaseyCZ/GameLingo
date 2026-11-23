import SwiftUI

/// Shared five-color / three-background palette for native iPhone and TV.
/// Each device has independent selected values. TV styles are applied only
/// by the authenticated GameLingo receiver, never by drawing on the iPhone.
enum GLPhoneSubtitleColor: String, CaseIterable, Identifiable, Codable {
    case white, yellow, cyan, green, purple

    var id: String { rawValue }
    var title: String {
        switch self {
        case .white: return "Bílá"
        case .yellow: return "Žlutá"
        case .cyan: return "Tyrkysová"
        case .green: return "Zelená"
        case .purple: return "Fialová"
        }
    }
    var color: Color {
        switch self {
        case .white: return .white
        case .yellow: return .yellow
        case .cyan: return GLNeon.cyan
        case .green: return .green
        case .purple: return GLNeon.purple
        }
    }
}

enum GLPhoneSubtitleBackground: String, CaseIterable, Identifiable, Codable {
    case none, dark, translucent

    var id: String { rawValue }
    var title: String {
        switch self {
        case .none: return "Žádné"
        case .dark: return "Tmavé"
        case .translucent: return "Průhledné"
        }
    }
    var fill: Color {
        switch self {
        case .none: return Color.clear
        case .dark: return Color.black.opacity(0.88)
        case .translucent: return Color.black.opacity(0.46)
        }
    }
}
