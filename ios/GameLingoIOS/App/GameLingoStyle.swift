import SwiftUI

// Shared, minimal GameLingo design system used on every iPhone screen.
// Functional region colours (blue/green/yellow/orange) are deliberately separate.
enum GLStyle {
    static let background = Color(red: 7/255, green: 11/255, blue: 20/255)
    static let surface = Color(red: 17/255, green: 24/255, blue: 39/255)
    static let surfaceAlt = Color(red: 23/255, green: 32/255, blue: 51/255)
    static let border = Color(red: 43/255, green: 54/255, blue: 83/255)
    static let blue = Color(red: 17/255, green: 190/255, blue: 244/255)
    static let violet = Color(red: 136/255, green: 75/255, blue: 248/255)
    static let muted = Color(red: 148/255, green: 163/255, blue: 184/255)
    static let green = Color(red: 53/255, green: 229/255, blue: 125/255)
    static let gradient = LinearGradient(colors: [violet,
        Color(red: 60/255, green: 105/255, blue: 255/255), blue],
        startPoint: .leading, endPoint: .trailing)
}

struct GLCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(GLStyle.surface, in: RoundedRectangle(cornerRadius: 19))
        .overlay(RoundedRectangle(cornerRadius: 19)
            .strokeBorder(GLStyle.border, lineWidth: 1))
    }
}

struct GLHelpButton: View {
    let title: String
    let explanation: String
    @State private var showing = false

    var body: some View {
        Button { showing = true } label: {
            Image(systemName: "questionmark.circle")
                .font(.title3)
                .foregroundStyle(GLStyle.blue)
        }
        .accessibilityLabel("Nápověda: " + title)
        .sheet(isPresented: $showing) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 15) {
                        Label(title, systemImage: "questionmark.circle.fill")
                            .font(.title2.bold())
                            .foregroundStyle(GLStyle.blue)
                        Text(explanation)
                            .font(.body)
                            .foregroundStyle(.white)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(22)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .background(GLStyle.background.ignoresSafeArea())
                .navigationTitle("Nápověda")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Hotovo") { showing = false }
                    }
                }
            }
            .tint(GLStyle.blue)
            .preferredColorScheme(.dark)
            .presentationDetents([.medium, .large])
        }
    }
}

struct GLHeader: View {
    let title: String
    var help: String? = nil

    var body: some View {
        HStack(spacing: 10) {
            Text(title).font(.headline).foregroundStyle(.white)
            Spacer(minLength: 0)
            if let help { GLHelpButton(title: title, explanation: help) }
        }
    }
}

struct GLPrimaryButton: View {
    let title: String
    let systemImage: String
    var disabled: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                Text(title)
                Spacer(minLength: 0)
                Image(systemName: "arrow.right")
            }
            .font(.headline)
            .foregroundStyle(.white)
            .padding(.horizontal, 17)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(GLStyle.gradient, in: RoundedRectangle(cornerRadius: 14))
            .opacity(disabled ? 0.38 : 1)
        }
        .buttonStyle(.plain)
        .disabled(disabled)
    }
}

struct GLSettingsLink: View {
    let icon: String
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .frame(width: 27)
                .foregroundStyle(GLStyle.blue)
            Text(title)
                .font(.body.weight(.medium))
                .foregroundStyle(.white)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(GLStyle.muted)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}

struct GLValueSlider: View {
    let title: String
    let value: String
    @Binding var selection: Double
    let range: ClosedRange<Double>
    var step: Double? = nil
    var disabled = false
    var color: Color = GLStyle.blue

    var body: some View {
        VStack(spacing: 7) {
            HStack {
                Text(title).font(.subheadline)
                Spacer()
                Text(value).font(.subheadline.monospacedDigit())
                    .foregroundStyle(GLStyle.muted)
            }
            if let step {
                Slider(value: $selection, in: range, step: step)
            } else {
                Slider(value: $selection, in: range)
            }
        }
        .tint(color)
        .disabled(disabled)
    }
}

struct GLScreen<Content: View>: View {
    let title: String
    let help: String
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 13) {
                content
            }
            .padding(.horizontal, 18)
            .padding(.top, 20)
            .padding(.bottom, 35)
        }
        .background(GLStyle.background.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                GLHelpButton(title: title, explanation: help)
            }
        }
        .tint(GLStyle.blue)
        .preferredColorScheme(.dark)
    }
}
