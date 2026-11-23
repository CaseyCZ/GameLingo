import SwiftUI

/// The phone overlay can be styled independently from Google TV, whose colour
/// is drawn by the receiver. No false "TV color" option is presented.
enum GLPhoneCaptionColor: String, CaseIterable, Identifiable, Codable {
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
    var tint: Color {
        switch self {
        case .white: return .white
        case .yellow: return .yellow
        case .cyan: return GLNeon.cyan
        case .green: return GLStyle.green
        case .purple: return GLNeon.purple
        }
    }
}

enum GLPhoneCaptionBackground: String, CaseIterable, Identifiable, Codable {
    case dark, transparent, none

    var id: String { rawValue }
    var title: String {
        switch self {
        case .dark: return "Tmavé"
        case .transparent: return "Průhledné"
        case .none: return "Žádné"
        }
    }

    var opacity: Double {
        switch self {
        case .dark: return 0.91
        case .transparent: return 0.48
        case .none: return 0
        }
    }
}

struct GLPhoneCaptionPreview: View {
    let color: GLPhoneCaptionColor
    let background: GLPhoneCaptionBackground
    let fontScale: Double

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 15)
                .fill(LinearGradient(
                    colors: [Color(red: 0.10, green: 0.23, blue: 0.35),
                             Color(red: 0.12, green: 0.14, blue: 0.27),
                             GLNeon.ink],
                    startPoint: .topLeading, endPoint: .bottomTrailing))
            Image(systemName: "mountain.2.fill")
                .resizable()
                .scaledToFit()
                .foregroundStyle(GLNeon.cyan.opacity(0.17))
                .padding(23)
            Text("To je skvělý plán!")
                .font(.system(size: CGFloat(18 * fontScale),
                              weight: .bold, design: .rounded))
                .foregroundStyle(color.tint)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 13)
                .padding(.vertical, 10)
                .background(.black.opacity(background.opacity),
                            in: RoundedRectangle(cornerRadius: 9))
                .frame(maxWidth: .infinity, maxHeight: .infinity,
                       alignment: .bottom)
                .padding(.bottom, 22)
        }
        .frame(height: 185)
        .overlay(RoundedRectangle(cornerRadius: 15)
            .strokeBorder(GLNeon.edge, lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Náhled titulků na telefonu")
    }
}
