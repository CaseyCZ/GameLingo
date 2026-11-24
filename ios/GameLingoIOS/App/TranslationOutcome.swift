import Foundation

/// Provider failures are never represented as translated text.
enum GLTranslationFailure: Error, Equatable {
    case emptyInput
    case invalidRequest
    case invalidResponse
    case httpStatus(Int)
    case timeout
    case cancelled
    case network
    case providerUnavailable

    var diagnostic: String {
        switch self {
        case .emptyInput: return "prázdný vstup"
        case .invalidRequest: return "neplatný požadavek"
        case .invalidResponse: return "neplatná odpověď překladače"
        case .httpStatus(let status): return "HTTP \(status)"
        case .timeout: return "časový limit"
        case .cancelled: return "zrušeno"
        case .network: return "nedostupná síť nebo služba"
        case .providerUnavailable: return "překladové služby mají dočasnou pauzu"
        }
    }

    static func classify(_ error: Error) -> GLTranslationFailure {
        if error is CancellationError { return .cancelled }
        if let error = error as? URLError {
            if error.code == .timedOut { return .timeout }
            if error.code == .cancelled { return .cancelled }
        }
        return .network
    }
}

enum GLTranslationOutcome {
    static func myMemoryResponse(_ data: Data, statusCode: Int?) -> Result<String, GLTranslationFailure> {
        guard let statusCode else { return .failure(.invalidResponse) }
        guard statusCode == 200 else { return .failure(.httpStatus(statusCode)) }
        guard let response = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let serviceStatus = response["responseStatus"] as? Int else { return .failure(.invalidResponse) }
        guard serviceStatus == 200 else { return .failure(.httpStatus(serviceStatus)) }
        guard let payload = response["responseData"] as? [String: Any],
              let translated = payload["translatedText"] as? String else { return .failure(.invalidResponse) }
        let text = translated.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !text.uppercased().hasPrefix("MYMEMORY WARNING") else {
            return .failure(.invalidResponse)
        }
        return .success(text)
    }

    static func googleResponse(_ data: Data, statusCode: Int?) -> Result<String, GLTranslationFailure> {
        guard let statusCode else { return .failure(.invalidResponse) }
        guard statusCode == 200 else { return .failure(.httpStatus(statusCode)) }
        guard let array = (try? JSONSerialization.jsonObject(with: data)) as? [Any],
              let segments = array.first as? [[Any]], !segments.isEmpty else {
            return .failure(.invalidResponse)
        }
        var parts = [String]()
        for segment in segments {
            guard let value = segment.first as? String else { return .failure(.invalidResponse) }
            parts.append(value)
        }
        let text = parts.joined().trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return .failure(.invalidResponse) }
        // Identical text can be a valid translation of a name, code or loanword.
        return .success(text)
    }
    static func edgeResponse(_ data: Data, statusCode: Int?, target: String) -> Result<String, GLTranslationFailure> {
        guard let statusCode else { return .failure(.invalidResponse) }
        guard statusCode == 200 else { return .failure(.httpStatus(statusCode)) }
        guard let rows = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]],
              rows.count == 1, let translations = rows[0]["translations"] as? [[String: Any]],
              let translation = translations.first(where: {
                  ($0["to"] as? String)?.caseInsensitiveCompare(target) == .orderedSame
              }), let raw = translation["text"] as? String else { return .failure(.invalidResponse) }
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? .failure(.invalidResponse) : .success(text)
    }

}
