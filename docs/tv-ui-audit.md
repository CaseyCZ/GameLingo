# GameLingo TV — UI audit scope

The audit follows the generated TV variant after all upstream branding and patch steps. It does not claim screenshots or hardware validation.

| Destination | Treatment |
|---|---|
| First run, introduction, beta, home, navigation | Existing branded screens; shared TV theme |
| Languages | Preserve approved content and picker |
| OCR and translation | Shared settings panels, full-screen TV presentation |
| Four-region editor | D-pad editor, temporary TV preview, profile save |
| Caption style | Full-screen preview and controls |
| Profiles | Branded picker and edit/save workflow |
| Receiver | Branded PIN, QR, IP and phone list |
| Expanded QR | Full-screen QR, live expiry, cleanup |
| Device management and source selection | Branded dashboard, themed confirmation/input dialogs, console pickers |
| PS registration, Xbox linking, login, takeover | Inherited functional paths; common app/dialog theme; external OAuth remains external |
| Glossary, history, translation cache | Branded pages and selection; themed confirmations |
| Diagnostics, OCR tests, overlay test | Branded pages and status; consent is system-owned |
| Help, About, license notices | Branded pages / themed dialogs |
| LG / Samsung wizard, export and developer screens | Inherited functionality; common explicit dialog theme where applicable |
| Android permissions, IME, external web/login | System/external UI; not redesigned |

The build runs `tools/audit_tv_ui.py` on the final generated TV source and publishes a separate **GameLingo-TV-UI-inventory** artifact. It inventories manifest activities, Kotlin UI entry points and unthemed alert/dialog constructors. It is not a guarantee of focus order, geometry, typography or runtime behavior. Those still require physical TV testing.

Do not change approved languages, phone UI, translation defaults, permissions, account or signing behavior as part of visual-only changes.

## Help and remote focus

TV help buttons share fixed geometry, the standard focused panel and a full-screen help destination. The generated UI audit inventories help calls and rejects independent literal question-mark buttons outside the shared component. Existing language picker layout is preserved. Physical focus navigation and safe-area checks remain necessary.

## iOS parity follow-up

Added opt-in iOS-style fixed caption duration (3–10 s), age limit (2–8 s), narrow/center/wide OCR presets, profile persistence and named glossary selection, direct subtitle width/font controls and actual timed overlay preview. Existing game timing and legacy automatic crop are kept by default. Story queue retains its own reading timing. Local console aliases do not change console identities or credentials. Hardware validation remains required; the UI contract does not establish identical runtime behavior across Swift/Vision and Android/ML Kit.

## SwiftUI visual source of truth

Android and Google TV use native equivalents of the current iOS `GameLingoVisualSystem.swift`, `GameLingoStyle.swift`, `GameLingoHome.swift`, `SettingsHomeView.swift` and `SubtitleSettingsView.swift`. The iOS files are unchanged. `GameLingoVisualKit` supplies common detail rows, vector symbol equivalents, wordmark, primary action and subtitle preview; Android's fixed five-tab navigation uses matching symbol semantics. TV uses the same rows with D-pad focus and a wide layout. Apple SF Symbols are not redistributed. The approved language picker remains unchanged. Runtime and visual verification on devices is still required.

Registration and pairing use the existing SwiftUI platform panel hierarchy, with native Android cards and PIN entry; no changes to Sony/Microsoft authorization or receiver pairing protocol. Android game profiles now expose the save form and individual profile cards, TV keeps remote-friendly actions. Diagnostics uses existing opt-in history only, not additional text storage.

TV home has two aligned source actions: mobile download under the phone receiver, device management under direct TV. Download QR codes resolve the newest publicly published complete mobile release (including prereleases) via the GitHub releases API. The QR encodes the exact mobile IPA/APK URL; on API failure the UI explicitly falls back to the releases page. No pairing token is included. iOS app sources remain untouched.
