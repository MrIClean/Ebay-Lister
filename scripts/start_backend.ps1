Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$backendRoot = Join-Path $repoRoot "backend"
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$debugApkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$usbStatePath = Join-Path $PSScriptRoot ".usb-install-state.json"
$appId = "com.example.ebaylister"
$mainActivity = "com.example.ebaylister/.MainActivity"

Set-Location $backendRoot

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    throw "Missing backend virtualenv at backend/.venv. Run setup first."
}

$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
function Get-ConnectedAdbSerials {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbPath
    )

    try {
        & $AdbPath start-server | Out-Null
        $devices = & $AdbPath devices
        $serials = @()
        foreach ($line in $devices) {
            if ($line -match "^(?<serial>[^\s]+)\s+device$") {
                $serials += $Matches['serial']
            }
        }
        return $serials
    } catch {
        return @()
    }
}

function Configure-AdbReverse {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbPath,
        [Parameter(Mandatory = $true)]
        [string[]]$Serials
    )

    $configured = $false
    foreach ($serial in $Serials) {
        try {
            & $AdbPath -s $serial reverse tcp:8000 tcp:8000 | Out-Null
            $configured = $true
        } catch {
            # Ignore single-device reverse failures and keep trying other devices.
        }
    }
    return $configured
}

function Get-InstallState {
    param([string]$StatePath)

    if (-not (Test-Path $StatePath)) {
        return @{}
    }

    try {
        $raw = Get-Content -Path $StatePath -Raw
        if ([string]::IsNullOrWhiteSpace($raw)) {
            return @{}
        }

        $parsed = ConvertFrom-Json -InputObject $raw -ErrorAction Stop
        $state = @{}
        foreach ($prop in $parsed.PSObject.Properties) {
            $state[$prop.Name] = [string]$prop.Value
        }
        return $state
    } catch {
        return @{}
    }
}

function Save-InstallState {
    param(
        [hashtable]$State,
        [string]$StatePath
    )

    $json = $State | ConvertTo-Json -Compress
    Set-Content -Path $StatePath -Value $json
}

function Test-AppInstalledOnDevice {
    param(
        [string]$AdbPath,
        [string]$Serial,
        [string]$ApplicationId
    )

    $pmOutput = & $AdbPath -s $Serial shell pm path $ApplicationId
    return ($pmOutput | Select-String -Pattern '^package:' -Quiet)
}

function Get-DebugApkHash {
    param(
        [string]$RepoRoot,
        [string]$GradleWrapperPath,
        [string]$ApkPath
    )

    Push-Location $RepoRoot
    try {
        & $GradleWrapperPath --console=plain :app:assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle assembleDebug failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path $ApkPath)) {
        throw "Debug APK not found at $ApkPath"
    }

    return (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash
}

function Install-DebugAppIfNeeded {
    param(
        [string]$AdbPath,
        [string]$Serial,
        [string]$RepoRoot,
        [string]$GradleWrapperPath,
        [string]$ApplicationId,
        [string]$MainActivityName,
        [string]$ApkPath,
        [string]$StatePath
    )

    $state = Get-InstallState -StatePath $StatePath
    $currentHash = Get-DebugApkHash -RepoRoot $RepoRoot -GradleWrapperPath $GradleWrapperPath -ApkPath $ApkPath
    $previousHash = ""
    if ($state.ContainsKey($Serial)) {
        $previousHash = [string]$state[$Serial]
    }

    $isInstalled = Test-AppInstalledOnDevice -AdbPath $AdbPath -Serial $Serial -ApplicationId $ApplicationId
    $hashChanged = $currentHash -ne $previousHash

    if (-not $isInstalled -or $hashChanged) {
        Write-Host "Installing debug app on $Serial (new install or changed APK)..."
        Push-Location $RepoRoot
        try {
            & $GradleWrapperPath --console=plain :app:installDebug
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle installDebug failed with exit code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }

        $state[$Serial] = $currentHash
        Save-InstallState -State $state -StatePath $StatePath

        & $AdbPath -s $Serial shell am start -n $MainActivityName | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Auto-install and launch finished on $Serial ($ApplicationId)."
        } else {
            Write-Host "Installed on $Serial but failed to auto-launch $ApplicationId."
        }
    } else {
        Write-Host "APK unchanged on $Serial. Skipping reinstall."
    }
}

$adbWatcher = $null
if ($adbCmd) {
    if (-not (Test-Path $gradleWrapper)) {
        throw "Missing Gradle wrapper at $gradleWrapper"
    }

    $initialSerials = Get-ConnectedAdbSerials -AdbPath $adbCmd.Source
    $initialConnected = Configure-AdbReverse -AdbPath $adbCmd.Source -Serials $initialSerials
    if ($initialConnected) {
        Write-Host "adb reverse configured for connected USB device(s): 127.0.0.1:8000 -> host 127.0.0.1:8000"
        foreach ($serial in $initialSerials) {
            Install-DebugAppIfNeeded -AdbPath $adbCmd.Source -Serial $serial -RepoRoot $repoRoot -GradleWrapperPath $gradleWrapper -ApplicationId $appId -MainActivityName $mainActivity -ApkPath $debugApkPath -StatePath $usbStatePath
        }
    } else {
        Write-Host "No adb device detected yet. Auto-connect watcher enabled."
    }

    # Keep adb reverse active and auto-install debug app when a USB device connects.
    $adbWatcher = Start-Job -Name "pixelprofit-adb-watch" -ArgumentList $adbCmd.Source, $repoRoot, $gradleWrapper, $appId, $mainActivity, $debugApkPath, $usbStatePath, $initialSerials -ScriptBlock {
        param([string]$AdbPath, [string]$RepoRoot, [string]$GradleWrapperPath, [string]$ApplicationId, [string]$MainActivityName, [string]$ApkPath, [string]$StatePath, [string[]]$InitialSerials)

        function Get-ConnectedAdbSerials {
            param([string]$Path)
            try {
                & $Path start-server | Out-Null
                $devices = & $Path devices
                $serials = @()
                foreach ($line in $devices) {
                    if ($line -match "^(?<serial>[^\s]+)\s+device$") {
                        $serials += $Matches['serial']
                    }
                }
                return $serials
            } catch {
                return @()
            }
        }

        function Get-InstallState {
            param([string]$Path)
            if (-not (Test-Path $Path)) {
                return @{}
            }
            try {
                $raw = Get-Content -Path $Path -Raw
                if ([string]::IsNullOrWhiteSpace($raw)) {
                    return @{}
                }
                $parsed = ConvertFrom-Json -InputObject $raw -ErrorAction Stop
                $state = @{}
                foreach ($prop in $parsed.PSObject.Properties) {
                    $state[$prop.Name] = [string]$prop.Value
                }
                return $state
            } catch {
                return @{}
            }
        }

        function Save-InstallState {
            param([hashtable]$State, [string]$Path)
            $json = $State | ConvertTo-Json -Compress
            Set-Content -Path $Path -Value $json
        }

        function Test-AppInstalledOnDevice {
            param([string]$Path, [string]$Serial, [string]$Package)
            $pmOutput = & $Path -s $Serial shell pm path $Package
            return ($pmOutput | Select-String -Pattern '^package:' -Quiet)
        }

        function Get-DebugApkHash {
            param([string]$Root, [string]$WrapperPath, [string]$BuiltApkPath)
            Push-Location $Root
            try {
                & $WrapperPath --console=plain :app:assembleDebug
                if ($LASTEXITCODE -ne 0) {
                    throw "Gradle assembleDebug failed with exit code $LASTEXITCODE"
                }
            } finally {
                Pop-Location
            }

            if (-not (Test-Path $BuiltApkPath)) {
                throw "Debug APK not found at $BuiltApkPath"
            }
            return (Get-FileHash -Path $BuiltApkPath -Algorithm SHA256).Hash
        }

        function Install-DebugAppIfNeeded {
            param(
                [string]$Path,
                [string]$Serial,
                [string]$Root,
                [string]$WrapperPath,
                [string]$Package,
                [string]$Activity,
                [string]$BuiltApkPath,
                [string]$InstallStatePath
            )

            $state = Get-InstallState -Path $InstallStatePath
            $currentHash = Get-DebugApkHash -Root $Root -WrapperPath $WrapperPath -BuiltApkPath $BuiltApkPath
            $previousHash = ""
            if ($state.ContainsKey($Serial)) {
                $previousHash = [string]$state[$Serial]
            }

            $isInstalled = Test-AppInstalledOnDevice -Path $Path -Serial $Serial -Package $Package
            $hashChanged = $currentHash -ne $previousHash

            if (-not $isInstalled -or $hashChanged) {
                Write-Host "Installing debug app on $Serial (new install or changed APK)..."
                Push-Location $Root
                try {
                    & $WrapperPath --console=plain :app:installDebug
                    if ($LASTEXITCODE -ne 0) {
                        throw "Gradle installDebug failed with exit code $LASTEXITCODE"
                    }
                } finally {
                    Pop-Location
                }

                $state[$Serial] = $currentHash
                Save-InstallState -State $state -Path $InstallStatePath

                & $Path -s $Serial shell am start -n $Activity | Out-Null
                if ($LASTEXITCODE -eq 0) {
                    Write-Host "Auto-install and launch finished on $Serial ($Package)."
                } else {
                    Write-Host "Installed on $Serial but failed to auto-launch $Package."
                }
            } else {
                Write-Host "APK unchanged on $Serial. Skipping reinstall."
            }
        }

        $previousSerials = @($InitialSerials)
        while ($true) {
            try {
                $currentSerials = Get-ConnectedAdbSerials -Path $AdbPath

                foreach ($serial in $currentSerials) {
                    try {
                        & $AdbPath -s $serial reverse tcp:8000 tcp:8000 | Out-Null
                    } catch {
                        # Ignore transient reverse failures.
                    }
                }

                $newSerials = @($currentSerials | Where-Object { $_ -notin $previousSerials })
                foreach ($serial in $newSerials) {
                    Write-Host "USB device detected ($serial). Checking whether install is needed..."
                    Install-DebugAppIfNeeded -Path $AdbPath -Serial $serial -Root $RepoRoot -WrapperPath $GradleWrapperPath -Package $ApplicationId -Activity $MainActivityName -BuiltApkPath $ApkPath -InstallStatePath $StatePath
                }

                $previousSerials = @($currentSerials)
            } catch {
                # Ignore transient adb failures; next iteration will retry.
            }
            Start-Sleep -Seconds 5
        }
    }
} else {
    Write-Host "adb not found in PATH. Skipping adb reverse setup."
}

Write-Host "Starting backend on http://127.0.0.1:8000 ..."
try {
    .\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
} finally {
    if ($adbWatcher) {
        Stop-Job -Job $adbWatcher -ErrorAction SilentlyContinue | Out-Null
        Remove-Job -Job $adbWatcher -Force -ErrorAction SilentlyContinue | Out-Null
    }
}
