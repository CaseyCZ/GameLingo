param([switch]$Rapid,[switch]$Translate,[switch]$PrepareModel)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path '.venv\Scripts\python.exe')) {
    if (Get-Command py -ErrorAction SilentlyContinue) {
        & py -3.11 -m venv .venv
    } else {
        & python -m venv .venv
    }
    if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11 (winget install --exact --id Python.Python.3.11) and retry.' }
}
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
& $python -m pip install -r requirements.txt
if ($LASTEXITCODE -ne 0) { throw 'Base dependencies failed.' }
if ($Rapid) {
    & $python -m pip install -r requirements-rapid.txt
    if ($LASTEXITCODE -ne 0) { throw 'RapidOCR installation failed.' }
}
if ($Translate -or $PrepareModel) {
    & $python -m pip install -r requirements-translate.txt
    if ($LASTEXITCODE -ne 0) { throw 'Argos installation failed.' }
}
if ($PrepareModel) {
    & $python prepare_model.py
    if ($LASTEXITCODE -ne 0) { throw 'Model setup failed.' }
}
& $python -m unittest -v test_core test_translation_timing
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed.' }
& $python lab.py
