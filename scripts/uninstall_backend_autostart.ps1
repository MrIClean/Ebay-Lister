Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$taskName = "PixelProfitLocalBackend"

try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
    Write-Host "Removed autostart task '$taskName'."
} catch {
    Write-Host "Autostart task '$taskName' was not found."
}
