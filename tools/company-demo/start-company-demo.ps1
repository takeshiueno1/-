[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $projectRoot '.env'

function New-RandomSecret {
    param(
        [Parameter(Mandatory)]
        [int]$ByteCount
    )

    $bytes = New-Object byte[] $ByteCount
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }

    return [Convert]::ToBase64String($bytes).Replace('+', 'A').Replace('/', 'B').TrimEnd('=')
}

function Invoke-DockerCompose {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    & docker compose --env-file $envFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') に失敗しました。"
    }
}

function Get-LocalSetting {
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )

    $prefix = "$Name="
    $line = Get-Content -LiteralPath $envFile |
        Where-Object { $_.StartsWith($prefix, [System.StringComparison]::Ordinal) } |
        Select-Object -First 1
    if (-not $line) {
        return ''
    }

    return $line.Substring($prefix.Length)
}

Set-Location $projectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Dockerが見つかりません。会社PCへDocker Desktopまたは会社指定のDocker互換環境を導入してください。'
}

& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Dockerが起動していません。Docker Desktopを起動してから、もう一度実行してください。'
}

if (-not (Test-Path -LiteralPath $envFile)) {
    $dbPassword = New-RandomSecret -ByteCount 32
    $adminPassword = New-RandomSecret -ByteCount 18
    $demoPassword = New-RandomSecret -ByteCount 16

    @(
        "DB_PASSWORD=$dbPassword"
        'SESSION_COOKIE_SECURE=false'
        'APP_INITIAL_ADMIN_USERNAME=admin'
        "APP_INITIAL_ADMIN_PASSWORD=$adminPassword"
        'APP_DEMO_SEED_ENABLED=true'
        "APP_DEMO_SEED_PASSWORD=$demoPassword"
        'APP_DEMO_SEED_COUNT=50'
        'APP_DEMO_SEED_MONTHS=6'
        'APP_TEST_USER_ENABLED=true'
        'APP_TEST_USER_PASSWORD=test'
        'APP_TEST_MANAGER_PASSWORD=test02'
        'APP_PUBLIC_BASE_URL='
        'OUTLOOK_INTEGRATION_ENABLED=false'
        'OUTLOOK_TENANT_ID='
        'OUTLOOK_CLIENT_ID='
        'OUTLOOK_CLIENT_SECRET='
        'OUTLOOK_SENDER='
        'SLACK_INTEGRATION_ENABLED=false'
        'SLACK_WEBHOOK_URL='
    ) | Set-Content -LiteralPath $envFile -Encoding utf8

    Write-Host '会社PC専用の .env を作成しました。Gitの管理対象には含まれません。' -ForegroundColor Green
}
else {
    Write-Host '既存の .env を使用します。' -ForegroundColor Yellow
}

Write-Host '勤怠管理システムをビルドして起動しています。初回は数分かかる場合があります。' -ForegroundColor Cyan
Invoke-DockerCompose -Arguments @('up', '-d', '--build')

$applicationUrl = 'http://localhost:4173'
$deadline = (Get-Date).AddMinutes(3)
$isReady = $false
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri $applicationUrl -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            $isReady = $true
            break
        }
    }
    catch {
        Start-Sleep -Seconds 3
    }
}

if (-not $isReady) {
    Invoke-DockerCompose -Arguments @('ps')
    throw '3分以内に画面を確認できませんでした。Dockerのログを確認してください。'
}

Write-Host ''
Write-Host '起動しました。' -ForegroundColor Green
Write-Host "URL: $applicationUrl"
Write-Host '一般ユーザー: test / test'
Write-Host '役職者ユーザー: test02 / test02'
Write-Host "管理者ユーザー: admin / $(Get-LocalSetting -Name 'APP_INITIAL_ADMIN_PASSWORD')"
Write-Host ''

Start-Process $applicationUrl
