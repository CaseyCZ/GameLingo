import Foundation

@main
struct InterfaceStatusTests {
    static func main() {
        let en: [String: String] = [
            "Odesláno do %@. Zobrazení potvrdí až televize.": "Sent to %@. Only the TV can confirm display.",
            "Hledání není dostupné: %@. Zadej IP ručně.": "Search unavailable: %@. Enter the IP manually.",
            "Hledání selhalo: %@. IP lze zadat ručně.": "Search failed: %@. Enter the IP manually.",
            "%@ · automatické obnovení vyčerpáno": "%@ · automatic reconnection attempts exhausted",
            "Microsoft přihlášení odmítl: %@": "Microsoft declined sign-in: %@",
            "Nelze získat kód Microsoftu: %@": "Could not get Microsoft code: %@",
            "Párování nebylo odesláno: %@": "Pairing was not sent: %@",
            "Připojuji se k TV na UDP %@…": "Connecting to TV on UDP %@…",
            "Ověřuji kód na TV (UDP %@)…": "Verifying the TV code (UDP %@)…",
            "Obnovuji Xbox · pokus %@/2": "Reconnecting Xbox · attempt %@/2",
            "Spojení s TV selhalo: %@": "TV connection failed: %@",
            "Nalezeno konzolí: %@": "Consoles found: %@",
            "Přijímač TV: UDP %@": "TV receiver: UDP %@",
            "Chyba odeslání: %@": "Send error: %@",
            "TV neodpovídá: %@": "TV not responding: %@",
            "Nelze odeslat: %@": "Could not send: %@",
            "PlayStation: %@": "PlayStation: %@",
            "Připojuji %@…": "Connecting to %@…",
        ]
        let sk: [String: String] = [
            "Odesláno do %@. Zobrazení potvrdí až televize.": "Odoslané do %@. Zobrazenie potvrdí až televízor.",
            "Hledání není dostupné: %@. Zadej IP ručně.": "Vyhľadávanie nie je dostupné: %@. Zadaj IP ručne.",
            "Hledání selhalo: %@. IP lze zadat ručně.": "Vyhľadávanie zlyhalo: %@. IP možno zadať ručne.",
            "%@ · automatické obnovení vyčerpáno": "%@ · pokusy o automatické obnovenie vyčerpané",
            "Microsoft přihlášení odmítl: %@": "Microsoft odmietol prihlásenie: %@",
            "Nelze získat kód Microsoftu: %@": "Nedá sa získať kód Microsoftu: %@",
            "Párování nebylo odesláno: %@": "Párovanie nebolo odoslané: %@",
            "Připojuji se k TV na UDP %@…": "Pripájam sa k TV na UDP %@…",
            "Ověřuji kód na TV (UDP %@)…": "Overujem kód na TV (UDP %@)…",
            "Obnovuji Xbox · pokus %@/2": "Obnovujem Xbox · pokus %@/2",
            "Spojení s TV selhalo: %@": "Spojenie s TV zlyhalo: %@",
            "Nalezeno konzolí: %@": "Nájdené konzoly: %@",
            "Přijímač TV: UDP %@": "Prijímač TV: UDP %@",
            "Chyba odeslání: %@": "Chyba odoslania: %@",
            "TV neodpovídá: %@": "TV neodpovedá: %@",
            "Nelze odeslat: %@": "Nedá sa odoslať: %@",
            "PlayStation: %@": "PlayStation: %@",
            "Připojuji %@…": "Pripájam %@…",
        ]
        // Use the same input while changing the translator, as when the UI
        // language changes without receiving a new network event.
        for (key, english) in en {
            let payload = "Xbox 2 / 192.0.2.10 · 100% %@"
            let source = key.replacingOccurrences(of: "%@", with: payload)
            precondition(GLInterfaceText.localizedStatus(source, translate: { en[$0] ?? $0 }) ==
                         english.replacingOccurrences(of: "%@", with: payload), key)
            precondition(GLInterfaceText.localizedStatus(source, translate: { sk[$0] ?? $0 }) ==
                         sk[key]!.replacingOccurrences(of: "%@", with: payload), key)
            precondition(GLInterfaceText.localizedStatus(source, translate: { $0 }) == source)
        }
        precondition(GLInterfaceText.localizedStatus("Připojuji se k TV na UDP 9876…", translate: { en[$0] ?? $0 }) ==
                     "Connecting to TV on UDP 9876…")
        precondition(GLInterfaceText.localizedStatus("custom status", translate: { en[$0] ?? $0 }) == "custom status")
        precondition(GLInterfaceText.localizedStatus("Nalezeno konzolí: 2", translate: { $0 == "Nalezeno konzolí: %@" ? "invalid" : $0 }) == "Nalezeno konzolí: 2")
        let metrics = "Průměr / P95 (posledních 128 vzorků)\nDialog OCR: 15 / 20 ms (n=3)\nXbox síť (video): 120 kb/s · ztráta 1 % (2 paketů) · jitter 3 ms\nPaměť: 100 MiB RSS · teplotní stav: nominal"
        let translations: [String: String] = [
            "Průměr / P95 (posledních 128 vzorků)": "Average / P95 (last 128 samples)",
            "Dialog čekání snímku": "Dialogue frame wait",
            "Dialog OCR": "Dialogue OCR",
            "Dialog čekání po OCR": "Dialogue wait after OCR",
            "Dialog překlad": "Dialogue translation",
            "Dialog celkem k výstupu": "Dialogue total to output",
            "Herní text čekání snímku": "Game text frame wait",
            "Herní text OCR": "Game text OCR",
            "Herní text překlad": "Game text translation",
            "Herní text celkem k výstupu": "Game text total to output",
            "dlouhé čekání ≥ 5 s": "long wait ≥ 5 s",
            "Chyby překladu": "Translation errors",
            "Xbox dekódovaný obraz": "Xbox decoded video",
            "bez nového snímku ≥ 5 s": "no new frame ≥ 5 s",
            "čekám na první snímek": "waiting for first frame",
            "Xbox síť": "Xbox network",
            "nedostupná": "unavailable",
            "Xbox síť (video)": "Xbox network (video)",
            "ztráta": "loss",
            "paketů": "packets",
            "Paměť": "Memory",
            "RSS nedostupná": "RSS unavailable",
            "teplotní stav": "thermal state",
            "nominal": "nominal",
            "fair": "fair",
            "serious": "serious",
            "critical": "critical",
            "unknown": "unknown",
            "Příklad pravidla (angličtina → čeština)": "Example rule (English → Czech)",
        ]
        let englishMetrics = GLInterfaceText.localizedDiagnostics(metrics, translate: { translations[$0] ?? $0 })
        precondition(englishMetrics == "Average / P95 (last 128 samples)\nDialogue OCR: 15 / 20 ms (n=3)\nXbox network (video): 120 kb/s · loss 1 % (2 packets) · jitter 3 ms\nMemory: 100 MiB RSS · thermal state: nominal")
        precondition(GLInterfaceText.localizedDiagnostics(metrics, translate: { $0 }) == metrics)
        print("Interface status tests passed")
    }
}
