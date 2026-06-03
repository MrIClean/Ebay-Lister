Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$envPath = Join-Path $repoRoot "backend\.env"
$lastCloudUrlPath = Join-Path $repoRoot "backend\.last-cloud-url"
$lastCloudLinkPath = Join-Path $repoRoot "backend\.last-cloud-link"
$backendScript = Join-Path $repoRoot "scripts\start_backend_background.ps1"
$appId = "com.example.ebaylister"
$prefsName = "ebay_lister_prefs"
$prefsDevicePath = "shared_prefs/$prefsName.xml"

function Read-DotEnv {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path $Path)) {
        return $map
    }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $map[$Matches[1].Trim()] = $Matches[2]
        }
    }
    return $map
}

function Set-DotEnvValue {
    param([string]$Path, [string]$Key, [string]$Value)
    $lines = [System.Collections.Generic.List[string]]::new()
    $found = $false
    foreach ($line in Get-Content $Path) {
        if ($line -match "^\s*$([regex]::Escape($Key))=") {
            $lines.Add("$Key=$Value")
            $found = $true
        } else {
            $lines.Add($line)
        }
    }
    if (-not $found) {
        $lines.Add("$Key=$Value")
    }
    Set-Content -Path $Path -Value $lines
}

function Ensure-BackendApiToken {
    param([string]$Path)
    $envMap = Read-DotEnv -Path $Path
    $existing = [string]$envMap["BACKEND_API_TOKEN"]
    if (-not [string]::IsNullOrWhiteSpace($existing)) {
        return $existing
    }

    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $token = -join ($bytes | ForEach-Object { $_.ToString("x2") })
    $backupPath = "$Path.bak.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Copy-Item -Path $Path -Destination $backupPath
    Set-DotEnvValue -Path $Path -Key "BACKEND_API_TOKEN" -Value $token
    Write-Host "Created BACKEND_API_TOKEN in backend/.env."
    Write-Host "Backup: $backupPath"
    return $token
}

function Stop-BackendIfRunning {
    try {
        $listeners = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction Stop
        foreach ($listener in $listeners) {
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    } catch {
        # No listener or no permission; backend launcher will handle startup.
    }
}

function Get-CloudflaredPath {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    $fallbackPath = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
    if (Test-Path $fallbackPath) {
        return $fallbackPath
    }
    throw "cloudflared executable not found. Reinstall Cloudflare.cloudflared or update PATH."
}

function Start-QuickTunnel {
    param([string]$CloudflaredPath)
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $outLog = Join-Path $repoRoot "backend\tunnel_$stamp.out.log"
    $errLog = Join-Path $repoRoot "backend\tunnel_$stamp.err.log"
    $process = Start-Process -FilePath $CloudflaredPath -ArgumentList @(
        "tunnel",
        "--url",
        "http://127.0.0.1:8000"
    ) -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru

    Write-Host "Started Cloudflare quick tunnel process $($process.Id)."
    Write-Host "Tunnel logs: $errLog"

    $deadline = (Get-Date).AddSeconds(45)
    while ((Get-Date) -lt $deadline) {
        foreach ($path in @($errLog, $outLog)) {
            if (Test-Path $path) {
                $content = Get-Content $path -Raw -ErrorAction SilentlyContinue
                $match = [regex]::Match($content, "https://[a-zA-Z0-9-]+\.trycloudflare\.com")
                if ($match.Success) {
                    return $match.Value
                }
            }
        }
        Start-Sleep -Seconds 1
    }

    throw "Cloudflare tunnel started, but no trycloudflare URL appeared within 45 seconds."
}

function Get-ConnectedAdbSerial {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        return ""
    }
    & $adb.Source start-server | Out-Null
    $devices = & $adb.Source devices
    foreach ($line in $devices) {
        if ($line -match '^(?<serial>[^\s]+)\s+device$') {
            return $Matches["serial"]
        }
    }
    return ""
}

function New-CloudSetupLink {
    param([string]$CloudUrl)
    $encodedUrl = [System.Uri]::EscapeDataString($CloudUrl)
    return "pixelprofit://connection?mode=cloud&backend_url=$encodedUrl"
}

function Wait-BackendHealth {
    param([string]$ApiToken)
    $deadline = (Get-Date).AddSeconds(60)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:8000/health" -Headers @{ "X-Api-Key" = $ApiToken } -TimeoutSec 5
            if ($health.status -eq "ok") {
                return $health
            }
            $lastError = "health status was '$($health.status)'"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 2
    }
    throw "Backend health check failed after 60 seconds: $lastError"
}

function Set-AppStringPref {
    param(
        [xml]$Doc,
        [string]$Name,
        [string]$Value
    )
    $map = $Doc.SelectSingleNode("/map")
    $existing = $map.SelectSingleNode("string[@name='$Name']")
    if ($existing -eq $null) {
        $existing = $Doc.CreateElement("string")
        $attr = $Doc.CreateAttribute("name")
        $attr.Value = $Name
        [void]$existing.Attributes.Append($attr)
        [void]$map.AppendChild($existing)
    }
    $existing.InnerText = $Value
}

function Configure-AppOnPhone {
    param([string]$Serial, [string]$CloudUrl, [string]$ApiToken)
    $adb = (Get-Command adb -ErrorAction Stop).Source
    $workDir = Join-Path $env:TEMP "pixelprofit-cloud-mode"
    New-Item -ItemType Directory -Path $workDir -Force | Out-Null
    $prefsPath = Join-Path $workDir "$prefsName.xml"

    $raw = & $adb -s $Serial exec-out run-as $appId cat $prefsDevicePath 2>$null
    if ([string]::IsNullOrWhiteSpace($raw)) {
        $raw = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map />"
    }

    [xml]$doc = $raw
    Set-AppStringPref -Doc $doc -Name "backend_mode" -Value "cloud"
    Set-AppStringPref -Doc $doc -Name "cloud_backend_url" -Value $CloudUrl
    Set-AppStringPref -Doc $doc -Name "backend_api_token" -Value $ApiToken
    $doc.Save($prefsPath)

    & $adb -s $Serial push $prefsPath "/data/local/tmp/$prefsName.xml" | Out-Null
    & $adb -s $Serial shell run-as $appId mkdir -p shared_prefs | Out-Null
    & $adb -s $Serial shell run-as $appId cp "/data/local/tmp/$prefsName.xml" $prefsDevicePath | Out-Null
    & $adb -s $Serial shell run-as $appId chmod 600 $prefsDevicePath | Out-Null
    & $adb -s $Serial shell am force-stop $appId | Out-Null
    & $adb -s $Serial shell am start -n "$appId/.MainActivity" | Out-Null
}

if (-not (Test-Path $envPath)) {
    throw "Missing backend/.env. Create it from backend/.env.example first."
}

$token = Ensure-BackendApiToken -Path $envPath
Stop-BackendIfRunning
& $backendScript

$health = Wait-BackendHealth -ApiToken $token

$cloudflared = Get-CloudflaredPath
$cloudUrl = Start-QuickTunnel -CloudflaredPath $cloudflared
$setupLink = New-CloudSetupLink -CloudUrl $cloudUrl
Set-Content -Path $lastCloudUrlPath -Value $cloudUrl
Set-Content -Path $lastCloudLinkPath -Value $setupLink

Write-Host "Cloud backend URL: $cloudUrl"
Write-Host "Saved URL to backend/.last-cloud-url"
Write-Host "Phone setup link: $setupLink"
Write-Host "Saved setup link to backend/.last-cloud-link"
Write-Host "For security, the setup link does not include your backend API token."

$serial = Get-ConnectedAdbSerial
if ([string]::IsNullOrWhiteSpace($serial)) {
    Write-Host "No USB phone detected. Open the setup link on your phone to switch the app to Cloud mode."
    exit 0
}

Configure-AppOnPhone -Serial $serial -CloudUrl $cloudUrl -ApiToken $token
Write-Host "Configured PixelProfit on $serial for Cloud mode and relaunched it."
Write-Host "You can unplug the phone while this computer, backend, and tunnel process stay running."
