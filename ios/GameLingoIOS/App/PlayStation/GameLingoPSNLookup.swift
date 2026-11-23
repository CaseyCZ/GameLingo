import Foundation

/// PSN account ID resolution needed to register PS4/PS5 Remote Play.
/// Uses Sony's public Remote Play OAuth client flow. Never stores credentials,
/// login passwords, authorization codes, or returned access tokens.
@MainActor
final class GameLingoPSNLookup: ObservableObject {
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?

    // Protocol constants from the AGPL TP Play/chiaki-ng registration adapter.
    private static let clientID = "ba495a24-818c-472b-b12d-ff231c1b5745"
    private static let clientSecret = "mvaiZkRsAsI1IBkY"
    private static let redirect = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
    private static let tokenURL = URL(string:
        "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token")!

    static var loginURL: URL {
        var components = URLComponents(string:
            "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize")!
        components.queryItems = [
            URLQueryItem(name: "service_entity", value: "urn:service-entity:psn"),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirect),
            URLQueryItem(name: "scope", value: "psn:clientapp"),
            URLQueryItem(name: "request_locale", value: "cs_CZ"),
            URLQueryItem(name: "ui", value: "pr"),
            URLQueryItem(name: "service_logo", value: "ps"),
            URLQueryItem(name: "layout_type", value: "popup"),
            URLQueryItem(name: "smcid", value: "remoteplay"),
            URLQueryItem(name: "prompt", value: "always")
        ]
        return components.url!
    }

    func resolve(redirectLink: String) async -> Data? {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            let input = redirectLink.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let components = URLComponents(string: input),
                  components.scheme?.lowercased() == "https",
                  components.host?.lowercased() == "remoteplay.dl.playstation.net",
                  let code = components.queryItems?
                    .first(where: { $0.name == "code" })?.value,
                  !code.isEmpty else {
                throw PSNError.message("Vlož odkaz po přihlášení k PlayStation.")
            }
            var tokenRequest = URLRequest(url: Self.tokenURL)
            tokenRequest.httpMethod = "POST"
            tokenRequest.setValue("application/x-www-form-urlencoded",
                                  forHTTPHeaderField: "Content-Type")
            tokenRequest.setValue(Self.authHeader,
                                  forHTTPHeaderField: "Authorization")
            var body = URLComponents()
            body.queryItems = [
                URLQueryItem(name: "grant_type", value: "authorization_code"),
                URLQueryItem(name: "code", value: code),
                URLQueryItem(name: "redirect_uri", value: Self.redirect)
            ]
            tokenRequest.httpBody = body.percentEncodedQuery?.data(using: .utf8)
            let (tokenData, response) = try await URLSession.shared.data(for: tokenRequest)
            try Self.requireSuccess(response)
            let tokenJSON = try JSONSerialization.jsonObject(with: tokenData) as? [String: Any]
            guard let token = tokenJSON?["access_token"] as? String,
                  !token.isEmpty,
                  let pathToken = token.addingPercentEncoding(
                    withAllowedCharacters: .urlPathAllowed),
                  let accountURL = URL(string: Self.tokenURL.absoluteString + "/" + pathToken)
            else {
                throw PSNError.message("Účet PlayStation nebylo možné ověřit.")
            }
            var accountRequest = URLRequest(url: accountURL)
            accountRequest.setValue(Self.authHeader, forHTTPHeaderField: "Authorization")
            let (data, accountResponse) = try await URLSession.shared.data(for: accountRequest)
            try Self.requireSuccess(accountResponse)
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            let value = json?["user_id"] as? String ??
                        (json?["user_id"] as? NSNumber)?.stringValue
            guard let value, let id = UInt64(value) else {
                throw PSNError.message("PlayStation nevrátil platný účet.")
            }
            var littleEndianID = id.littleEndian
            return withUnsafeBytes(of: &littleEndianID) { Data($0) }
        } catch {
            self.error = error.localizedDescription
            return nil
        }
    }

    private static var authHeader: String {
        "Basic " + Data("\(clientID):\(clientSecret)".utf8).base64EncodedString()
    }

    private static func requireSuccess(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode)
        else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw PSNError.message("Přihlášení PlayStation selhalo (HTTP \(status)).")
        }
    }

    private enum PSNError: LocalizedError {
        case message(String)
        var errorDescription: String? {
            if case .message(let message) = self { return message }
            return nil
        }
    }
}
