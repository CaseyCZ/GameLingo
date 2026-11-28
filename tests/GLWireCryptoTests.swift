import Foundation
import CryptoKit

@main struct GLWireCryptoTests {
    static func main() throws {
        let key = String(repeating: "ab", count: 32)
        let id = "audit-test-phone"
        let text = Data("PCT1\t1\t1800\tHello".utf8)
        let nonce = try AES.GCM.Nonce(data: Data((0..<12).map { UInt8($0) }))
        let expected = "AAECAwQFBgcICQoLXJHyEjqnv5PGmoS/LeO3QqMcaCN1b3y5Z6GKzDSS9wA1"
        precondition(GLWireCrypto.seal(secret: key, id: id, payload: text, nonce: nonce) == expected)
        precondition(GLWireCrypto.open(secret: key, id: id, encoded: expected) == text)
        precondition(GLWireCrypto.open(secret: key, id: "other", encoded: expected) == nil)
        precondition(GLWireCrypto.open(secret: String(repeating: "cd", count: 32), id: id, encoded: expected) == nil)
        var altered = Data(base64Encoded: expected)!
        altered[altered.count - 1] ^= 1
        precondition(GLWireCrypto.open(secret: key, id: id, encoded: altered.base64EncodedString()) == nil)
        let first = GLWireCrypto.seal(secret: key, id: id, payload: text)!
        let second = GLWireCrypto.seal(secret: key, id: id, payload: text)!
        precondition(first != second)
        print("GLWireCryptoTests passed")
    }
}
