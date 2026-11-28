import Foundation
import CryptoKit

/// Same nonce || ciphertext || tag format as Android and the TV.
enum GLWireCrypto {
    private static let purpose = "GameLingo/TEXT2/AES-256-GCM/v1"
    private static func key(_ secret: String) -> SymmetricKey? {
        let chars = Array(secret.utf8)
        guard chars.count == 64 || chars.count == 32 else { return nil }
        var bytes = [UInt8]()
        for i in stride(from: 0, to: chars.count, by: 2) {
            guard let byte = UInt8(String(decoding: chars[i..<(i + 2)], as: UTF8.self), radix: 16) else { return nil }
            bytes.append(byte)
        }
        let derived = HMAC<SHA256>.authenticationCode(for: Data(purpose.utf8),
            using: SymmetricKey(data: Data(bytes)))
        return SymmetricKey(data: Data(derived))
    }
    private static func aad(_ id: String) -> Data { Data("PCPAIR1\tTEXT2\t\(id)".utf8) }
    static func seal(secret: String, id: String, payload: Data, nonce: AES.GCM.Nonce? = nil) -> String? {
        guard let key = key(secret), (1...8192).contains(payload.count),
              let box = try? AES.GCM.seal(payload, using: key, nonce: nonce, authenticating: aad(id)),
              let combined = box.combined else { return nil }
        return combined.base64EncodedString()
    }
    static func open(secret: String, id: String, encoded: String) -> Data? {
        guard (40...11000).contains(encoded.utf8.count), let key = key(secret),
              let combined = Data(base64Encoded: encoded), (29...8220).contains(combined.count),
              let box = try? AES.GCM.SealedBox(combined: combined) else { return nil }
        return try? AES.GCM.open(box, using: key, authenticating: aad(id))
    }
}
