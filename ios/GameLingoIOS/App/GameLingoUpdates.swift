import SwiftUI
import Foundation
import CryptoKit

private let updateAPI = URL(string: "https://api.github.com/repos/CaseyCZ/GameLingo/releases?per_page=10")!
private let releaseRoot = "https://github.com/CaseyCZ/GameLingo/releases/download/"
private let maximumIPA: Int64 = 150 * 1024 * 1024

private struct UpdateFailure: LocalizedError {
    let key: String
    var errorDescription: String? { GLInterfaceText.localized(key) }
    init(_ message: String) { key = message }
}

private struct ReleaseAsset: Decodable {
    let name: String
    let browser_download_url: String
    let state: String
    let size: Int64
}
private struct ReleaseInfo: Decodable {
    let tag_name: String
    let draft: Bool
    let body: String?
    let assets: [ReleaseAsset]
}
private struct IPAManifest: Decodable {
    let schema: Int
    let package: String
    let tag: String
    let file: String
    let versionName: String
    let versionCode: Int
    let size: Int64
    let sha256: String
}
private struct IPAUpdate {
    let metadata: IPAManifest
    let url: URL
    let notes: String
}

private final class UpdateRedirects: NSObject, URLSessionTaskDelegate, URLSessionDownloadDelegate {
    let limit: Int64
    let progress: @Sendable (Int64) -> Void
    init(limit: Int64, progress: @escaping @Sendable (Int64) -> Void = { _ in }) {
        self.limit = limit
        self.progress = progress
    }
    static func trusted(_ url: URL) -> Bool {
        guard url.scheme == "https", url.user == nil, url.password == nil,
              url.port == nil || url.port == 443 else { return false }
        switch url.host?.lowercased() {
        case "api.github.com": return url == updateAPI
        case "github.com": return url.absoluteString.hasPrefix(releaseRoot)
        case "release-assets.githubusercontent.com", "objects.githubusercontent.com": return true
        default: return false
        }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(request.url.map(Self.trusted) == true ? request : nil)
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                    totalBytesExpectedToWrite: Int64) {
        if totalBytesWritten > limit || totalBytesExpectedToWrite > limit {
            downloadTask.cancel()
        } else { progress(totalBytesWritten) }
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didFinishDownloadingTo location: URL) {}
}

private enum IPAUpdates {
    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }
    static var build: Int? {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String).flatMap(Int.init)
    }
    static func isNewer(_ manifest: IPAManifest) -> Bool {
        guard let build, manifest.versionCode > build else { return false }
        return manifest.versionName.compare(version, options: .numeric) == .orderedDescending
    }
    static func request(_ url: URL) throws -> URLRequest {
        guard UpdateRedirects.trusted(url) else { throw UpdateFailure("Nepovolený zdroj aktualizace.") }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        request.setValue("GameLingo-iOS-Updater", forHTTPHeaderField: "User-Agent")
        return request
    }
    static func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 300
        return URLSession(configuration: config)
    }
    static func checkResponse(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              let url = http.url, UpdateRedirects.trusted(url) else {
            throw UpdateFailure("GitHub neposkytl platný soubor.")
        }
    }
    static func read(_ url: URL, limit: Int) async throws -> Data {
        let session = session()
        defer { session.finishTasksAndInvalidate() }
        let (bytes, response) = try await session.bytes(for: request(url), delegate: UpdateRedirects(limit: Int64(limit)))
        try checkResponse(response)
        guard response.expectedContentLength <= Int64(limit) else { throw UpdateFailure("Příliš velká metadata.") }
        var data = Data()
        for try await byte in bytes {
            guard data.count < limit else { throw UpdateFailure("Příliš velká metadata.") }
            data.append(byte)
        }
        return data
    }
    static func find() async throws -> IPAUpdate? {
        let releases = try JSONDecoder().decode([ReleaseInfo].self, from: await read(updateAPI, limit: 2*1024*1024))
        var candidates = [IPAUpdate]()
        for release in releases where !release.draft {
            let tag = release.tag_name
            guard tag.range(of: #"^v[0-9]+\.[0-9]+\.[0-9]+-test-[a-f0-9]{8}-r[0-9]+$"#, options: .regularExpression) != nil,
                  let semver = String(tag.dropFirst()).components(separatedBy: "-test-").first else { continue }
            func asset(_ name: String) -> ReleaseAsset? {
                let items = release.assets.filter { $0.name == name && $0.state == "uploaded" &&
                    $0.browser_download_url == releaseRoot + tag + "/" + name }
                return items.count == 1 ? items.first : nil
            }
            guard let metadata = asset("GameLingo-iOS-update.json") else { continue }
            guard metadata.size > 0, metadata.size <= 32768, let url = URL(string: metadata.browser_download_url) else {
                throw UpdateFailure("Neplatná metadata aktualizace.")
            }
            let m = try JSONDecoder().decode(IPAManifest.self, from: await read(url, limit: 32768))
            guard m.schema == 1, m.package == "cz.caseycz.gamelingo.ios", m.tag == tag,
                  m.versionName == semver, m.file == "GameLingo-iOS-v\(semver)-dev-unsigned.ipa",
                  (1...2000).contains(m.versionCode), m.size > 0, m.size <= maximumIPA,
                  m.sha256.range(of: #"^[a-f0-9]{64}$"#, options: .regularExpression) != nil,
                  let ipa = asset(m.file), ipa.size == m.size, let ipaURL = URL(string: ipa.browser_download_url) else {
                throw UpdateFailure("Neplatné údaje IPA.")
            }
            if isNewer(m) { candidates.append(IPAUpdate(metadata: m, url: ipaURL, notes: String((release.body ?? "").prefix(2000)))) }
        }
        return candidates.max { $0.metadata.versionCode < $1.metadata.versionCode }
    }
    static func download(_ update: IPAUpdate, progress: @escaping @Sendable (Int64) -> Void) async throws -> URL {
        let session = session()
        defer { session.finishTasksAndInvalidate() }
        let (temp, response) = try await session.download(for: request(update.url),
            delegate: UpdateRedirects(limit: update.metadata.size, progress: progress))
        defer { try? FileManager.default.removeItem(at: temp) }
        try checkResponse(response)
        let size = try temp.resourceValues(forKeys: [.fileSizeKey]).fileSize
        guard Int64(size ?? -1) == update.metadata.size else { throw UpdateFailure("Velikost IPA nesouhlasí.") }
        var hash = SHA256()
        let handle = try FileHandle(forReadingFrom: temp)
        defer { try? handle.close() }
        while let chunk = try handle.read(upToCount: 65536), !chunk.isEmpty {
            try Task.checkCancellation()
            hash.update(data: chunk)
        }
        let actual = hash.finalize().map { String(format: "%02x", $0) }.joined()
        guard actual == update.metadata.sha256 else { throw UpdateFailure("Kontrolní součet IPA nesouhlasí.") }
        try Task.checkCancellation()
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("GameLingo-updates", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent(update.metadata.file)
        if FileManager.default.fileExists(atPath: destination.path) { try FileManager.default.removeItem(at: destination) }
        try FileManager.default.moveItem(at: temp, to: destination)
        return destination
    }
}

@MainActor
final class GameLingoUpdateModel: ObservableObject {
    @Published private(set) var message = ""
    @Published private(set) var messageDetail: String?
    @Published private(set) var available: String?
    @Published private(set) var notes = ""
    @Published private(set) var downloaded: URL?
    @Published private(set) var busy = false
    @Published private(set) var progress = 0.0
    private var candidate: IPAUpdate?
    private var task: Task<Void, Never>?
    private var checked = false
    static var currentVersion: String { IPAUpdates.version }

    func checkAtStartup() {
        guard !checked else { return }
        checked = true
        check(passive: true)
    }
    func check(passive: Bool = false) {
        guard !busy else { return }
        busy = true
        if !passive { messageDetail = nil; message = "Kontroluji vydání…" }
        task = Task {
            defer { busy = false; task = nil }
            do {
                let result = try await IPAUpdates.find()
                try Task.checkCancellation()
                candidate = result
                available = result?.metadata.versionName
                notes = result?.notes ?? ""
                if !passive || result != nil {
                    messageDetail = nil
                    message = result == nil ? "Novější verze není dostupná." : "Je dostupná nová verze."
                }
            } catch {
                if !passive && !Task.isCancelled {
                    message = "Kontrola se nezdařila:"
                    messageDetail = (error as? UpdateFailure)?.key ?? "Neznámá chyba."
                }
            }
        }
    }
    func download() {
        guard !busy, let candidate else { return }
        busy = true
        downloaded = nil
        progress = 0
        messageDetail = nil
        message = "Stahuji IPA…"
        task = Task {
            defer { busy = false; task = nil }
            do {
                let url = try await IPAUpdates.download(candidate) { [weak self] bytes in
                    Task { @MainActor in self?.progress = min(1, Double(bytes) / Double(candidate.metadata.size)) }
                }
                try Task.checkCancellation()
                downloaded = url
                progress = 1
                message = "IPA ověřena. Ulož ji do Souborů nebo předej do podepisovací aplikace."
            } catch {
                message = Task.isCancelled ? "Stahování zrušeno." : "Stažení se nezdařilo:"
                messageDetail = Task.isCancelled ? nil : ((error as? UpdateFailure)?.key ?? "Neznámá chyba.")
            }
        }
    }
    func cancel() { task?.cancel() }
}

struct GameLingoUpdatePanel: View {
    @ObservedObject var updates: GameLingoUpdateModel
    var running: Bool = false
    var body: some View {
        GLCard {
            GLHeader(title: "Aktualizace")
            Button("Zkontrolovat aktualizace") { updates.check() }
                .disabled(updates.busy || running)
            if let version = updates.available {
                Text(verbatim: GLInterfaceText.localized("Nová verze:") + " " + version).font(.headline)
                if !updates.notes.isEmpty { Text(updates.notes).font(.caption) }
                Button("Stáhnout IPA") { updates.download() }
                    .disabled(updates.busy || running)
            }
            if updates.busy {
                ProgressView(value: updates.progress)
                Button("Zrušit") { updates.cancel() }
            }
            if let file = updates.downloaded {
                ShareLink(item: file) { Label("Uložit nebo sdílet IPA", systemImage: "square.and.arrow.up") }
            }
            if !updates.message.isEmpty {
                Text(verbatim: GLInterfaceText.localized(updates.message) +
                    (updates.messageDetail.map { " " + GLInterfaceText.localized($0) } ?? ""))
                    .font(.caption).multilineTextAlignment(.center)
            }
            Text("Nepodepsaná IPA: instalaci a podpis provádíš vlastním nástrojem.").font(.caption)
        }
    }
}
