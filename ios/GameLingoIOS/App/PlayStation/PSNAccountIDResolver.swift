import Foundation
import SwiftUI

// Pinned PSN account-ID resolver from TP Play / Chiaki, AGPL-3.0.
// Use only the Sony browser to enter credentials; never save Sony passwords.
@MainActor
final class PSNAccountIDResolver: ObservableObject {
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private static let clientID = "ba495a24-818c-472b-b12d-ff231c1b5745"
    private static let clientSecret = "mvaiZkRsAsI1IBkY"
    private static let redirect = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
    private static let tokenURL = URL(string: "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token")!

    static let loginURL: URL = {
        var components = URLComponents(string: "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize")!
        components.queryItems = [
            URLQueryItem(name: "service_entity", value: "urn:service-entity:psn"),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirect),
            URLQueryItem(name: "scope", value: "psn:clientapp"),
            URLQueryItem(name: "request_locale", value: "en_US"),
            URLQueryItem(name: "ui", value: "pr"),
            URLQueryItem(name: "service_logo", value: "ps"),
            URLQueryItem(name: "layout_type", value: "popup"),
            URLQueryItem(name: "smcid", value: "remoteplay"),
            URLQueryItem(name: "prompt", value: "always"),
            URLQueryItem(name: "PlatformPrivacyWs1", value: "minimal"),
        ]
        return components.url!
    }()

    func resolve(from redirectText: String) async -> String? {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            guard let code = GLPSNRequestPolicy.code(from: redirectText) else {
                throw ResolverError.message("The pasted URL does not contain a sign-in code.")
            }

            var tokenRequest = URLRequest(url: Self.tokenURL)
            tokenRequest.httpMethod = "POST"
            tokenRequest.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            tokenRequest.setValue(Self.authorizationHeader, forHTTPHeaderField: "Authorization")
            var body = URLComponents()
            body.queryItems = [
                URLQueryItem(name: "grant_type", value: "authorization_code"),
                URLQueryItem(name: "code", value: code),
                URLQueryItem(name: "redirect_uri", value: Self.redirect),
            ]
            tokenRequest.httpBody = body.percentEncodedQuery?.data(using: .utf8)

            let (tokenData, tokenResponse) = try await GLPSNTransport.session.data(for: tokenRequest)
            try Self.requireSuccess(tokenResponse)
            let tokenObject = try JSONSerialization.jsonObject(with: tokenData) as? [String: Any]
            guard let token = tokenObject?["access_token"] as? String, !token.isEmpty else {
                throw ResolverError.message("Sony did not return an access token. Sign in again.")
            }

            guard let accountURL = GLPSNRequestPolicy.accountURL(token: token) else {
                throw ResolverError.message("The returned access token is invalid.")
            }
            var accountRequest = URLRequest(url: accountURL)
            accountRequest.setValue(Self.authorizationHeader, forHTTPHeaderField: "Authorization")
            let (accountData, accountResponse) = try await GLPSNTransport.session.data(for: accountRequest)
            try Self.requireSuccess(accountResponse)
            let accountObject = try JSONSerialization.jsonObject(with: accountData) as? [String: Any]
            let userIDText = accountObject?["user_id"] as? String ?? (accountObject?["user_id"] as? NSNumber)?.stringValue
            guard let userIDText, let userID = UInt64(userIDText) else {
                throw ResolverError.message("Sony did not return a valid account ID.")
            }
            var littleEndian = userID.littleEndian
            return withUnsafeBytes(of: &littleEndian) { Data($0).base64EncodedString() }
        } catch {
            errorMessage = (error as? ResolverError)?.errorDescription ?? "Sony sign-in request failed."
            return nil
        }
    }

    private static var authorizationHeader: String {
        let value = Data("\(clientID):\(clientSecret)".utf8).base64EncodedString()
        return "Basic \(value)"
    }

    private static func requireSuccess(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw ResolverError.message("Sony sign-in request failed (HTTP \(status)).")
        }
    }

    private enum ResolverError: LocalizedError {
        case message(String)
        var errorDescription: String? {
            if case .message(let value) = self { return value }
            return nil
        }
    }
}
