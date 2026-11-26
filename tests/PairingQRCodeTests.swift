import Foundation
@main
struct PairingQRCodeTests {
    static func main() {
        let pin = "0123456789abcdef0123456789abcdef"
        precondition(PairingQRCode.parse("GLPAIR2|192.168.1.12|\(pin)") ==
            PairingQRCode(ip: "192.168.1.12", pin: pin))
        for ip in ["10.1.2.3", "172.31.1.9", "100.64.1.2"] {
            precondition(PairingQRCode.parse("GLPAIR2|\(ip)|\(pin)") != nil)
        }
        for value in ["https://evil.example", "GLPAIR1|192.168.1.2|123456",
                      "GLPAIR2|8.8.8.8|\(pin)", "GLPAIR2|127.0.0.1|\(pin)",
                      "GLPAIR2|192.168.01.2|\(pin)", "GLPAIR2|192.168.1.2|１２３４５６",
                      "GLPAIR2|192.168.1.2|\(pin)|secret", "GLPAIR2|192.168.1.255|\(pin)",
                      "GLPAIR2|192.168.1.2:41989|\(pin)", "GLPAIR2|192.168.1.2|123456"] {
            precondition(PairingQRCode.parse(value) == nil)
        }
        print("PairingQRCode: authenticated code and LAN validation passed")
    }
}
