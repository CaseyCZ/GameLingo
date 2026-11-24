import Foundation

@main
struct TranslationOutcomeTests {
    static func expect(_ result: Result<String, GLTranslationFailure>, _ expected: Result<String, GLTranslationFailure>) {
        switch (result, expected) {
        case (.success(let actual), .success(let wanted)): precondition(actual == wanted)
        case (.failure(let actual), .failure(let wanted)): precondition(actual == wanted)
        default: fatalError("Unexpected translation outcome: \(result)")
        }
    }
    static func response(_ body: String, _ status: Int? = 200) -> Result<String, GLTranslationFailure> {
        GLTranslationOutcome.googleResponse(Data(body.utf8), statusCode: status)
    }
    static func main() {
        expect(response("[[[\"Ahoj, \",\"Hello, \"],[\"světe!\",\"world!\"]]]"), .success("Ahoj, světe!"))
        expect(response("[[[\"Paris\",\"Paris\"]]]"), .success("Paris"))
        expect(response("[[[\" \",\"Hello\"]]]"), .failure(.invalidResponse))
        expect(response("[[[\"Ahoj\",\"Hello\"],[null,\"world\"]]]"), .failure(.invalidResponse))
        expect(response("[[[42,\"Hello\"]]]"), .failure(.invalidResponse))
        expect(response("[[]]"), .failure(.invalidResponse))
        expect(response("invalid JSON"), .failure(.invalidResponse))
        expect(response("{}", nil), .failure(.invalidResponse))
        expect(response("[[[\"Ahoj\"]]]", 429), .failure(.httpStatus(429)))
        expect(response("[[[\"Ahoj\"]]]", 503), .failure(.httpStatus(503)))
        precondition(GLTranslationFailure.classify(URLError(.timedOut)) == .timeout)
        precondition(GLTranslationFailure.classify(URLError(.notConnectedToInternet)) == .network)
        precondition(GLTranslationFailure.classify(URLError(.cancelled)) == .cancelled)
        precondition(GLTranslationFailure.classify(CancellationError()) == .cancelled)
        print("TranslationOutcome: success, identical names, invalid data, HTTP and network failures passed")
    }
}
