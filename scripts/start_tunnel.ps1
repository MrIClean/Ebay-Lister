Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Write-Host "Starting Cloudflare quick tunnel for http://127.0.0.1:8000 ..."
Write-Host "Keep this window open."

$cloudflaredCmd = Get-Command cloudflared -ErrorAction SilentlyContinue
if ($cloudflaredCmd) {
	& $cloudflaredCmd.Source tunnel --url http://127.0.0.1:8000
	exit
}

$fallbackPath = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
if (Test-Path $fallbackPath) {
	& $fallbackPath tunnel --url http://127.0.0.1:8000
	exit
}

throw "cloudflared executable not found. Reinstall Cloudflare.cloudflared or update PATH."
