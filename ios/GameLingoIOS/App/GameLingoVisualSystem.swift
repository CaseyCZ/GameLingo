import SwiftUI

// GameLingo's real, reusable visual language. These are native SwiftUI views,
// not screenshots painted over controls. OCR/Remote Play continue underneath.
enum GLNeon {
    static let cyan = Color(red: 0.02, green: 0.84, blue: 1.00)
    static let blue = Color(red: 0.18, green: 0.34, blue: 0.99)
    static let purple = Color(red: 0.69, green: 0.16, blue: 0.99)
    static let ink = Color(red: 0.025, green: 0.038, blue: 0.09)
    static let panel = Color(red: 0.065, green: 0.10, blue: 0.20)
    static let mist = Color(red: 0.57, green: 0.64, blue: 0.78)

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

struct GLBrandWordmark: View {
    var compact = false

    var body: some View {
        HStack(spacing: compact ? 9 : 12) {
            Image(systemName: "gamecontroller.fill")
                .font(.system(size: compact ? 27 : 35, weight: .semibold))
                .foregroundStyle(GLNeon.accent)
                .shadow(color: GLNeon.cyan.opacity(0.45), radius: 11)
                .accessibilityHidden(true)
            (Text("Game").foregroundColor(.white)
             + Text("Lingo").foregroundColor(GLNeon.purple))
                .font(.system(size: compact ? 28 : 34,
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
                ZStack {
                    Circle().strokeBorder(Color.white, lineWidth: 2.2)
                        .frame(width: size * 0.51, height: size * 0.51)
                    Text("X")
                        .font(.system(size: size * 0.46,
                                      weight: .black, design: .rounded))
                        .foregroundStyle(.white)
                }
            } else if family == .playStation {
                Text("PS")
                    .font(.system(size: size * 0.34,
                                  weight: .black, design: .rounded))
                    .italic()
                    .foregroundStyle(.white)
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
                Text(title).font(.title2.bold()).foregroundStyle(.white)
                Text(subtitle).font(.caption).foregroundStyle(GLNeon.mist)
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
                    Text(title)
                        .font(.system(size: 16, weight: .semibold,
                                      design: .rounded))
                        .foregroundStyle(.white)
                    Text(detail)
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
