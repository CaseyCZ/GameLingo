import Foundation

enum GLPSNRequestPolicy {
    static func code(from input: String) -> String? {
        guard let url = URLComponents(string: input.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme?.lowercased() == "https",
              url.host?.lowercased() == "remoteplay.dl.playstation.net",
              url.percentEncodedPath == "/remoteplay/redirect",
              url.user == nil, url.password == nil, url.port == nil || url.port == 443,
              url.fragment == nil,
              let items = url.queryItems, !items.contains(where: { $0.name == "error" }) else { return nil }
        let codes = items.filter { $0.name == "code" }
        guard codes.count == 1, let code = codes[0].value,
              !code.isEmpty, code.utf8.count <= 4096 else { return nil }
        return code
    }

    static func accountURL(token: String) -> URL? {
        guard !token.isEmpty, token.utf8.count <= 8192,
              token != ".", token != "..",
              let encoded = token.addingPercentEncoding(withAllowedCharacters:
                  CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")) else { return nil }
        return URL(string: "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token/" + encoded)
    }
}

private final class GLPSNNoRedirect: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

enum GLPSNTransport {
    static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.timeoutIntervalForRequest = 15
        return URLSession(configuration: config, delegate: GLPSNNoRedirect(), delegateQueue: nil)
    }()
}
