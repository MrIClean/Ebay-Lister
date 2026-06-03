param(
    [Parameter(Mandatory = $true)]
    [string]$Code,

    [ValidateSet("production", "sandbox")]
    [string]$Environment = "production"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$envPath = Join-Path $repoRoot "backend\.env"

if (-not (Test-Path $envPath)) {
    throw "Missing backend .env at $envPath"
}

function Read-DotEnv {
    param([string]$Path)

    $map = @{}
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $map[$Matches[1].Trim()] = $Matches[2]
        }
    }
    return $map
}

function Set-DotEnvValue {
    param(
        [string]$Path,
        [string]$Key,
        [string]$Value
    )

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

$envMap = Read-DotEnv -Path $envPath
$upperEnvironment = $Environment.ToUpperInvariant()

$clientIdKey = "EBAY_${upperEnvironment}_CLIENT_ID"
$clientSecretKey = "EBAY_${upperEnvironment}_CLIENT_SECRET"
$redirectUriKey = "EBAY_${upperEnvironment}_REDIRECT_URI"
$refreshTokenKey = "EBAY_${upperEnvironment}_REFRESH_TOKEN"

$clientId = if ($envMap.ContainsKey($clientIdKey) -and -not [string]::IsNullOrWhiteSpace([string]$envMap[$clientIdKey])) {
    [string]$envMap[$clientIdKey]
} else {
    [string]$envMap["EBAY_CLIENT_ID"]
}
$clientSecret = if ($envMap.ContainsKey($clientSecretKey) -and -not [string]::IsNullOrWhiteSpace([string]$envMap[$clientSecretKey])) {
    [string]$envMap[$clientSecretKey]
} else {
    [string]$envMap["EBAY_CLIENT_SECRET"]
}
$redirectUri = if ($envMap.ContainsKey($redirectUriKey) -and -not [string]::IsNullOrWhiteSpace([string]$envMap[$redirectUriKey])) {
    [string]$envMap[$redirectUriKey]
} else {
    [string]$envMap["EBAY_REDIRECT_URI"]
}

if ([string]::IsNullOrWhiteSpace($clientId)) {
    throw "Missing $clientIdKey or EBAY_CLIENT_ID"
}
if ([string]::IsNullOrWhiteSpace($clientSecret)) {
    throw "Missing $clientSecretKey or EBAY_CLIENT_SECRET"
}
if ([string]::IsNullOrWhiteSpace($redirectUri)) {
    throw "Missing $redirectUriKey or EBAY_REDIRECT_URI"
}

$tokenUrl = if ($Environment -eq "sandbox") {
    "https://api.sandbox.ebay.com/identity/v1/oauth2/token"
} else {
    "https://api.ebay.com/identity/v1/oauth2/token"
}

$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${clientId}:${clientSecret}"))

Write-Host "Exchanging eBay authorization code..."
Write-Host "Environment: $Environment"
Write-Host "Token URL: $tokenUrl"
Write-Host "Client ID length: $($clientId.Length)"
Write-Host "Redirect URI: $redirectUri"
Write-Host "Refresh target: $refreshTokenKey"

$response = Invoke-RestMethod `
    -Method Post `
    -Uri $tokenUrl `
    -Headers @{
        Authorization = "Basic $basic"
        "Content-Type" = "application/x-www-form-urlencoded"
    } `
    -Body @{
        grant_type = "authorization_code"
        code = $Code
        redirect_uri = $redirectUri
    }

if ([string]::IsNullOrWhiteSpace($response.refresh_token)) {
    throw "eBay token response did not include refresh_token"
}

$backupPath = "$envPath.bak.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -Path $envPath -Destination $backupPath

Set-DotEnvValue -Path $envPath -Key "EBAY_ENVIRONMENT" -Value $Environment
Set-DotEnvValue -Path $envPath -Key "USE_REAL_EBAY" -Value "true"
Set-DotEnvValue -Path $envPath -Key $refreshTokenKey -Value $response.refresh_token

Write-Host "Updated backend .env."
Write-Host "Backup: $backupPath"
Write-Host "Access token expires in seconds: $($response.expires_in)"
Write-Host "Refresh token length: $($response.refresh_token.Length)"
if ($response.refresh_token_expires_in) {
    Write-Host "Refresh token expires in seconds: $($response.refresh_token_expires_in)"
}
