Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Set-Location "$PSScriptRoot\..\backend"

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    throw "Missing backend virtualenv at backend/.venv. Run setup first."
}

Write-Host "Starting backend on http://127.0.0.1:8000 ..."
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
