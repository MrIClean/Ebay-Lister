Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path "$PSScriptRoot\.."
$backendScript = Join-Path $repoRoot "scripts\start_backend.ps1"
$tunnelScript = Join-Path $repoRoot "scripts\start_tunnel.ps1"

Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$backendScript`""
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$tunnelScript`""

Write-Host "Started backend and tunnel in separate windows."
Write-Host "Copy the https://*.trycloudflare.com URL from the tunnel window into the app Backend URL field."
