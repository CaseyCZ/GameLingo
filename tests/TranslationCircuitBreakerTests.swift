import Foundation

@main
struct TranslationCircuitBreakerTests {
    static func main() {
        var primary = TranslationCircuitBreaker()
        var backup = TranslationCircuitBreaker()
        let old = primary.acquire(now: 0)!
        let first = primary.acquire(now: 0)!
        primary.complete(first, success: false, now: 1)
        let second = primary.acquire(now: 1)!
        primary.complete(second, success: false, now: 2)
        primary.complete(old, success: true, now: 3)
        precondition(primary.acquire(now: 31.99) == nil)
        let alternative = backup.acquire(now: 3)!
        backup.complete(alternative, success: true, now: 4)
        let probe = primary.acquire(now: 32)!
        precondition(primary.acquire(now: 32) == nil)
        primary.complete(probe, success: false, now: 33)
        precondition(primary.acquire(now: 62.99) == nil)
        let cancelled = primary.acquire(now: 63)!
        primary.complete(cancelled, success: nil, now: 64)
        precondition(primary.acquire(now: 93.99) == nil)
        let recovered = primary.acquire(now: 94)!
        primary.complete(recovered, success: true, now: 95)
        let fail = primary.acquire(now: 95)!
        primary.complete(fail, success: false, now: 96)
        let ok = primary.acquire(now: 96)!
        primary.complete(ok, success: true, now: 97)
        let failAgain = primary.acquire(now: 97)!
        primary.complete(failAgain, success: false, now: 98)
        precondition(primary.acquire(now: 98) != nil)
        let good = GLTranslationOutcome.myMemoryResponse(Data(#"{"responseStatus":200,"responseData":{"translatedText":"Ahoj"}}"#.utf8), statusCode: 200)
        if case .success("Ahoj") = good {} else { fatalError("fallback parsing") }
        for body in [
            #"{"responseStatus":429,"responseData":{"translatedText":"limit"}}"#,
            #"{"responseStatus":200,"responseData":{"translatedText":"MYMEMORY WARNING: quota"}}"#,
            #"{"responseStatus":200,"responseData":{"translatedText":123}}"#
        ] {
            let outcome = GLTranslationOutcome.myMemoryResponse(Data(body.utf8), statusCode: 200)
            if case .failure = outcome {} else { fatalError("invalid fallback accepted") }
        }
        print("TranslationCircuitBreaker: recovery, late completions, exclusive probe and fallback parsing passed")
    }
}
