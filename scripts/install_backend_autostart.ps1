Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$taskName = "PixelProfitLocalBackend"
$launcherScript = (Resolve-Path "$PSScriptRoot\start_backend_background.ps1").Path

if (-not (Test-Path $launcherScript)) {
    throw "Missing launcher script at $launcherScript"
}

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$launcherScript`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited

try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
} catch {
    # Ignore if task does not exist.
}

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal | Out-Null
Start-ScheduledTask -TaskName $taskName

Write-Host "Installed autostart task '$taskName' and started backend launcher."
Write-Host "Backend will auto-start when you sign in to Windows."
