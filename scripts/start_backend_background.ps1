Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$backendScript = Join-Path $PSScriptRoot "start_backend.ps1"
$healthUrl = "http://127.0.0.1:8000/health"

if (-not (Test-Path $backendScript)) {
    throw "Missing backend launcher at $backendScript"
}

try {
    $health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2
    if ($health.status -eq "ok") {
        Write-Host "Backend already running at $healthUrl"
        exit 0
    }
} catch {
    # Backend is not reachable yet; continue to start it.
}

Start-Process powershell -WindowStyle Hidden -ArgumentList @(
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    "`"$backendScript`""
)

Write-Host "Started backend in background."
