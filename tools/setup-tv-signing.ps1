# Run on your own Windows PC; never upload the private key to the repository.
# Requires JDK keytool and GitHub CLI (gh auth login).
param([string]$Repository = "CaseyCZ/GameLingo")
$ErrorActionPreference = "Stop"
Get-Command gh,keytool -ErrorAction Stop | Out-Null
gh auth status
if ($LASTEXITCODE -ne 0) { throw "Run gh auth login first." }
$existing = gh secret list --repo $Repository --json name --jq '.[].name'
if ($LASTEXITCODE -ne 0) { throw "Cannot inspect repository secret names." }
if ($existing -contains "GAMELINGO_TV_KEYSTORE_B64" -or $existing -contains "GAMELINGO_TV_KEYSTORE_PASSWORD") {
    throw "A TV signing secret already exists. Do not replace it; inspect existing signing configuration first."
}
$dir = Join-Path $HOME ".gamelingo-signing"
New-Item -ItemType Directory -Force $dir | Out-Null
$key = Join-Path $dir "gamelingo-tv.p12"
$secure = Read-Host "Enter your signing password (at least 12 characters; save it securely)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:GAMELINGO_TV_KEYSTORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    if ($env:GAMELINGO_TV_KEYSTORE_PASSWORD.Length -lt 12) { throw "Password too short." }
    if (!(Test-Path $key)) {
        keytool -genkeypair -alias gamelingo-tv -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12 -keystore $key -storepass:env GAMELINGO_TV_KEYSTORE_PASSWORD -keypass:env GAMELINGO_TV_KEYSTORE_PASSWORD -dname "CN=GameLingo TV" 
        if ($LASTEXITCODE -ne 0) { throw "Key creation failed." }
    }
    keytool -list -alias gamelingo-tv -storetype PKCS12 -keystore $key -storepass:env GAMELINGO_TV_KEYSTORE_PASSWORD | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Existing key or password invalid." }
    [Convert]::ToBase64String([IO.File]::ReadAllBytes($key)) | gh secret set GAMELINGO_TV_KEYSTORE_B64 --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Keystore upload failed." }
    $env:GAMELINGO_TV_KEYSTORE_PASSWORD | gh secret set GAMELINGO_TV_KEYSTORE_PASSWORD --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Password secret upload failed; do not regenerate the key." }
    Write-Host "Signing secrets configured. Back up $key and its password privately. Do not replace this key."
} finally {
    Remove-Item Env:GAMELINGO_TV_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
