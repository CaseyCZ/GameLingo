import Foundation

struct PairingQRCode: Equatable {
    let ip: String
    let pin: String
    static func parse(_ raw: String) -> PairingQRCode? {
        guard raw.utf8.count <= 64 else { return nil }
        let fields = raw.split(separator: "|", omittingEmptySubsequences: false)
        guard fields.count == 3, fields[0] == "GLPAIR1",
              fields[2].utf8.count == 6,
              fields[2].utf8.allSatisfy({ (48...57).contains($0) }) else { return nil }
        let parts = fields[1].split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return nil }
        let octets = parts.compactMap { part -> UInt8? in
            guard !part.isEmpty, part.utf8.count <= 3,
                  part.utf8.allSatisfy({ (48...57).contains($0) }),
                  let n = UInt8(part), String(n) == String(part) else { return nil }
            return n
        }
        guard octets.count == 4 else { return nil }
        let a = octets[0], b = octets[1]
        let local = a == 10 || (a == 172 && (16...31).contains(b)) ||
            (a == 192 && b == 168) || (a == 169 && b == 254) ||
            (a == 100 && (64...127).contains(b))
        guard local, octets[3] != 0, octets[3] != 255 else { return nil }
        return PairingQRCode(ip: String(fields[1]), pin: String(fields[2]))
    }
}
