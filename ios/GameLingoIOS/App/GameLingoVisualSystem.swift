import SwiftUI

// GameLingo's real, reusable visual language. These are native SwiftUI views,
// not screenshots painted over controls. OCR/Remote Play continue underneath.
enum GLNeon {
    static let cyan = GameLingoUIContract.Palette.cyan
    static let blue = GameLingoUIContract.Palette.blue
    static let purple = GameLingoUIContract.Palette.purple
    static let ink = GameLingoUIContract.Palette.background
    static let panel = GameLingoUIContract.Palette.panel
    static let mist = GameLingoUIContract.Palette.muted

    static let accent = LinearGradient(
        colors: [cyan, blue, purple],
        startPoint: .leading, endPoint: .trailing
    )
    static let edge = LinearGradient(
        colors: [cyan.opacity(0.8), blue.opacity(0.45), purple.opacity(0.85)],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
}

struct GLNeonBackground: View {
    var body: some View {
        GeometryReader { proxy in
            ZStack {
                GLNeon.ink
                Ellipse()
                    .fill(GLNeon.blue.opacity(0.35))
                    .frame(width: 390, height: 270)
                    .blur(radius: 105)
                    .offset(x: proxy.size.width * 0.40, y: -170)
                Ellipse()
                    .fill(GLNeon.purple.opacity(0.31))
                    .frame(width: 335, height: 245)
                    .blur(radius: 98)
                    .offset(x: proxy.size.width * 0.40, y: proxy.size.height * 0.36)
                Ellipse()
                    .fill(GLNeon.cyan.opacity(0.12))
                    .frame(width: 280, height: 260)
                    .blur(radius: 105)
                    .offset(x: -150, y: -proxy.size.height * 0.17)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct GLNeonPanel<Content: View>: View {
    let emphasized: Bool
    let radius: CGFloat
    let content: Content

    init(emphasized: Bool = false, radius: CGFloat = 21,
         @ViewBuilder content: () -> Content) {
        self.emphasized = emphasized
        self.radius = radius
        self.content = content()
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .fill(LinearGradient(
                        colors: [GLNeon.panel.opacity(0.98),
                                 GLNeon.ink.opacity(0.93),
                                 GLNeon.purple.opacity(emphasized ? 0.23 : 0.08)],
                        startPoint: .topLeading, endPoint: .bottomTrailing))
            }
            .overlay {
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(GLNeon.edge,
                                  lineWidth: emphasized ? 1.6 : 0.8)
                    .opacity(emphasized ? 1 : 0.48)
            }
            .shadow(color: emphasized ? GLNeon.purple.opacity(0.18) : .clear,
                    radius: emphasized ? 16 : 0, y: 5)
    }
}

/// Brand mark drawn natively; a chat bubble plus controller, not the
/// generic system gamepad used in the original PlayCaption UI.
struct GLBrandMark: View {
    var size: CGFloat = 43

    var body: some View {
        ZStack {
            Image(systemName: "bubble.left.fill")
                .resizable()
                .scaledToFit()
                .foregroundStyle(GLNeon.accent)
                .shadow(color: GLNeon.purple.opacity(0.36), radius: 9)
            Image(systemName: "gamecontroller.fill")
                .resizable()
                .scaledToFit()
                .frame(width: size * 0.60, height: size * 0.42)
                .foregroundStyle(GLNeon.ink)
                .offset(x: -size * 0.025, y: -size * 0.10)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct GLBrandWordmark: View {
    var compact = false

    var body: some View {
        HStack(spacing: compact ? 8 : 12) {
            GLBrandMark(size: compact ? 44 : 55)
            (Text("Game").foregroundColor(.white)
             + Text("Lingo").foregroundColor(GLNeon.purple))
                .font(.system(size: compact ? 27 : 34,
                              weight: .heavy, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("GameLingo")
    }
}

struct GLPlatformBadge: View {
    let family: GLConsoleFamily?
    var isTelevision = false
    var size: CGFloat = 49

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [isTelevision ? GLNeon.cyan.opacity(0.23) :
                                    family == .xbox ? Color.green.opacity(0.22) :
                                    GLNeon.purple.opacity(0.25),
                                 GLNeon.panel],
                        startPoint: .topLeading, endPoint: .bottomTrailing))
            if isTelevision {
                Image(systemName: "tv.fill")
                    .font(.system(size: size * 0.42))
                    .foregroundStyle(GLNeon.cyan)
            } else if family == .xbox {
                Image(systemName: "xbox.logo")
                    .font(.system(size: size * 0.57, weight: .regular))
                    .foregroundStyle(.white)
                    .accessibilityLabel("Xbox")
            } else if family == .playStation {
                Image(systemName: "playstation.logo")
                    .font(.system(size: size * 0.56, weight: .regular))
                    .foregroundStyle(.white)
                    .accessibilityLabel("PlayStation")
            } else {
                Image(systemName: "gamecontroller.fill")
                    .font(.system(size: size * 0.40))
                    .foregroundStyle(GLNeon.accent)
            }
        }
        .frame(width: size, height: size)
        .overlay(RoundedRectangle(cornerRadius: 15)
            .strokeBorder(.white.opacity(0.08), lineWidth: 1))
        .accessibilityHidden(true)
    }
}

struct GLNeonTitle: View {
    let title: String
    let subtitle: String
    var icon: String = "sparkles"

    var body: some View {
        HStack(alignment: .center, spacing: 13) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(GLNeon.accent)
                .frame(width: 47, height: 47)
                .background(GLNeon.panel, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(GLNeon.cyan.opacity(0.30)))
            VStack(alignment: .leading, spacing: 3) {
                Text(LocalizedStringKey(title)).font(.title2.bold()).foregroundStyle(.white)
                Text(LocalizedStringKey(subtitle)).font(.caption).foregroundStyle(GLNeon.mist)
            }
            Spacer(minLength: 0)
        }
    }
}

struct GLDetailRow: View {
    let icon: String
    let title: String
    let detail: String

    var body: some View {
        GLNeonPanel(radius: 19) {
            HStack(spacing: 13) {
                Image(systemName: icon)
                    .font(.system(size: 21, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 46, height: 46)
                    .background(LinearGradient(
                        colors: [GLNeon.cyan.opacity(0.72),
                                 GLNeon.purple.opacity(0.78)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey(title))
                        .font(.system(size: 16, weight: .semibold,
                                      design: .rounded))
                        .foregroundStyle(.white)
                    Text(LocalizedStringKey(detail))
                        .font(.caption)
                        .foregroundStyle(GLNeon.mist)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.subheadline.bold())
                    .foregroundStyle(GLNeon.mist)
            }
            .padding(14)
        }
    }
}
