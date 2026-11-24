# GameLingo 0.5 — aktuální stav
Aktualizace 2026-11-24. Jediný aktuální rozcestník pro auditní větev `feature/audit-0.5`. Vývojový základ `feature/gamelingo-v05-platform-rebuild`; Master a veřejná vydání neměnit. Historické checkpointy 23. 11. nejsou současný stav. Poslední aplikační revize `f34348b1cd721c83074bae2590e6047b0a076303`, [CI 45384321662](https://github.com/CaseyCZ/GameLingo/actions/runs/45384321662): šest jobů PASS včetně iOS, Android mobil, TV, kontraktů, balíčků/SHA-256 a TV/mobil DEX guard. Po tomto buildu vznikly nové aplikační opravy v bodě 8; starý build zůstává historickým QA kandidátem, nikoli aktuálním kandidátem po opravách. Nový build a ověření čekají. HW všechny kombinace čekají; žádný nový release.

| Funkce | iOS | Android mobil | Google TV |
|---|---|---|---|
| Xbox / PS4 / PS5 | Implementované cesty Remote Play, HW čeká | Implementované cesty Remote Play, HW čeká | Přímé režimy implementované, HW čeká |
| OCR | Vision | ML Kit OCR | ML Kit OCR v přímém režimu; telefonní receiver OCR nedělá |
| Online překlad | Google GTX / MyMemory | Google GTX / Microsoft Edge / MyMemory | Google GTX / Microsoft Edge / MyMemory |
| Offline překlad nové věty | Ne | ML Kit fallback; dostupný model a HW test nutné | Ne; SDK odstraněno, CI ověřeno |
| Cache | SQLite | SQLite | SQLite pro lokální překlad |
| Čtyři oblasti | Implementace, HW čeká | Implementace, orange→TV a profilová parita nedokončena | Receiver podporuje orange geometrii; přímý režim má omezení modré/story |
| Párování TV | 6 číslic/QR, HMAC | 6 číslic/QR, HMAC | QR zobrazení/PIN, až 32 identit, jeden aktivní zdroj |
| Historie | Opt-in RAM | Opt-in RAM; story výjimka | Sdílený základ, runtime ověřit |
| Slovník | Ruční, herní profil | Ruční, herní profil; story výjimka | Podle přímé cesty; neprohlašovat plnou paritu |
| UI jazyk | cs/en/sk | cs/sk | Zachovat stávající katalog; ověřit skutečné volby |
| HDMI overlay | Odesílá do TV | Odesílá do TV | Vyžaduje oprávnění a kompatibilní firmware, HW čeká |

Offline cache hit není offline překlad nové věty. QR neslouží k instalaci APK. HMAC chrání integritu, nikoli důvěrnost; PIN/secret bootstrap a starší account transfer jsou otevřená rizika. Neoficiální online API nemají doloženou autorizaci. Neoznačovat Netflix/protected playback, Samsung/LG, univerzální HDMI, AI, dabing, automatickou instalaci či PC za ověřenou podporu. Zděděné LG/Samsung assets nejsou schválená nová podpora.

## Dokumentace
- [Návod pro testery](USER-GUIDE-0.5.md), [informace o soukromí (pracovní)](PRIVACY-0.5.md).
- [Licenční audit](LICENSE-AND-PROVIDER-AUDIT.md), [atribuce](../ATTRIBUTION.md), [licence](../LICENSE).
- [Audit kódu](CODE-AUDIT-0.5.md), [bezpečnost](SECURITY-PRIVACY-AUDIT-0.5.md), [úklid](CLEANUP-AUDIT-0.5.md).
- [Výsledky sestavení](BUILD_EVIDENCE_0.5.md), [fyzické QA](PHYSICAL_QA_0.5.md), [podmínky vydání](RELEASE_GATES_0.5.md), [vydávání](RELEASES.md).
- [REPORT.md](../REPORT.md) chronologie a obnovitelný checkpoint. [Mobilní kontrakt](MOBILE_PARITY.md) popisuje záměr, ne důkaz pixelové shody.

## Post-auditní plán
1–4: statické audity dokončeny, zjištěné problémy otevřené. 5: sjednocení dokumentace. 6: testovací balíčky ze stejné revize (nikoli veřejná distribuce), jejich inventář/podpis/checksum a dependency/notice evidence. 7: fyzická QA — [protokol připraven](QA-RESULTS-0.5.md), uživatelem odložena až po opravách a novém buildu. 8: opravy rozpracovány (CODE-01/06/07 a část CODE-02), další nálezy otevřené; viz REPORT. Zvláštní schválení bezpečnostní architektury/providerů/vyřazení funkcí, potom nová QA. 9: release pouze po splnění gates a výslovném souhlasu. Nepoužívat staré publish workflow s hardcoded build ID.
