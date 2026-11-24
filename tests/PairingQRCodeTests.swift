import Foundation
@main
struct PairingQRCodeTests {
    static func main() {
        precondition(PairingQRCode.parse("GLPAIR1|192.168.1.12|123456") ==
            PairingQRCode(ip: "192.168.1.12", pin: "123456"))
        for value in ["GLPAIR1|10.1.2.3|100000", "GLPAIR1|172.31.1.9|999999",
                      "GLPAIR1|100.64.1.2|123456"] {
            precondition(PairingQRCode.parse(value) != nil)
        }
        for value in ["https://evil.example", "GLPAIR2|192.168.1.2|123456",
                      "GLPAIR1|8.8.8.8|123456", "GLPAIR1|127.0.0.1|123456",
                      "GLPAIR1|192.168.01.2|123456", "GLPAIR1|192.168.1.2|１２３４５６",
                      "GLPAIR1|192.168.1.2|123456|secret", "GLPAIR1|192.168.1.255|123456",
                      "GLPAIR1|192.168.1.2:41989|123456", "GLPAIR1|192.168.1.2|12345"] {
            precondition(PairingQRCode.parse(value) == nil)
        }
        print("PairingQRCode: LAN validation passed")
    }
}
