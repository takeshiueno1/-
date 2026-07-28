[CmdletBinding()]
param(
    [int]$DemoUserCount = 50,
    [int]$DemoMonths = 2
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontendDir = Join-Path $repoRoot 'frontend'
$backendDir = Join-Path $repoRoot 'backend'
$distRoot = Join-Path $repoRoot 'dist'
$packageName = 'attendance-demo-windows'
$packageDir = Join-Path $distRoot $packageName
$zipPath = Join-Path $distRoot "$packageName.zip"
$jarSource = Join-Path $backendDir 'target\attendance-0.0.1-SNAPSHOT.jar'

function Invoke-Checked {
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory)]
        [string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$FilePath の実行に失敗しました。終了コード: $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function New-DemoPassword {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $chars = [System.Collections.Generic.List[char]]::new()
        foreach ($required in @('A', 'a', '7')) {
            $chars.Add([char]$required)
        }
        $buffer = New-Object byte[] 1
        while ($chars.Count -lt 15) {
            $random.GetBytes($buffer)
            $chars.Add($alphabet[$buffer[0] % $alphabet.Length])
        }
        for ($index = $chars.Count - 1; $index -gt 0; $index--) {
            $random.GetBytes($buffer)
            $swapIndex = $buffer[0] % ($index + 1)
            $temporary = $chars[$index]
            $chars[$index] = $chars[$swapIndex]
            $chars[$swapIndex] = $temporary
        }
        return -join $chars
    }
    finally {
        $random.Dispose()
    }
}

function Reset-PackageDirectory {
    New-Item -ItemType Directory -Force -Path $distRoot | Out-Null
    $resolvedDist = (Resolve-Path $distRoot).Path.TrimEnd('\')
    $expectedPackage = [System.IO.Path]::GetFullPath($packageDir)
    if (-not $expectedPackage.StartsWith("$resolvedDist\", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "配布先がdist配下ではないため処理を中止しました: $expectedPackage"
    }
    if (Test-Path -LiteralPath $expectedPackage) {
        Remove-Item -LiteralPath $expectedPackage -Recurse -Force
    }
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    New-Item -ItemType Directory -Force -Path $expectedPackage | Out-Null
}

if ($DemoUserCount -lt 1 -or $DemoUserCount -gt 200) {
    throw 'DemoUserCountは1～200で指定してください。'
}
if ($DemoMonths -lt 1 -or $DemoMonths -gt 12) {
    throw 'DemoMonthsは1～12で指定してください。'
}

Write-Host '1/6 フロントエンドをビルドします。'
Invoke-Checked -FilePath 'npm.cmd' -Arguments @('run', 'build') -WorkingDirectory $frontendDir

Write-Host '2/6 Dockerなしデモ用JARをビルドします。'
Invoke-Checked -FilePath (Join-Path $backendDir 'mvnw.cmd') `
    -Arguments @('-Pdemo', '-DskipTests', 'package') `
    -WorkingDirectory $backendDir
if (-not (Test-Path -LiteralPath $jarSource)) {
    throw "JARが見つかりません: $jarSource"
}

Write-Host '3/6 配布フォルダーを作成します。'
Reset-PackageDirectory
Copy-Item -LiteralPath $jarSource -Destination (Join-Path $packageDir 'attendance-demo.jar')
New-Item -ItemType Directory -Force -Path (Join-Path $packageDir 'data') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $packageDir 'logs') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $packageDir 'config') | Out-Null

$javaCommand = Get-Command 'java.exe' -ErrorAction Stop
$javaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
$jlink = Join-Path $javaHome 'bin\jlink.exe'
if (-not (Test-Path -LiteralPath $jlink)) {
    throw "jlink.exeが見つかりません。JDK 21で実行してください: $jlink"
}

Write-Host '4/6 インストール不要のJava実行環境を同梱します。'
$modules = @(
    'java.base',
    'java.compiler',
    'java.desktop',
    'java.instrument',
    'java.logging',
    'java.management',
    'java.management.rmi',
    'java.naming',
    'java.net.http',
    'java.prefs',
    'java.security.jgss',
    'java.security.sasl',
    'java.sql',
    'java.transaction.xa',
    'java.xml',
    'jdk.crypto.ec',
    'jdk.unsupported',
    'jdk.zipfs'
) -join ','
Invoke-Checked -FilePath $jlink `
    -Arguments @(
        '--add-modules', $modules,
        '--strip-debug',
        '--no-header-files',
        '--no-man-pages',
        '--compress=zip-6',
        '--output', (Join-Path $packageDir 'runtime')
    ) `
    -WorkingDirectory $repoRoot

$adminPassword = New-DemoPassword
$demoPassword = New-DemoPassword
$seedLog = Join-Path $packageDir 'seed-output.log'
$seedErrorLog = Join-Path $packageDir 'seed-error.log'
$seedProcess = $null

Write-Host "5/6 $DemoUserCount 人分のデモDBを作成します。初回だけ数分かかる場合があります。"
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:SERVER_PORT = '4191'
$env:APP_INITIAL_ADMIN_USERNAME = 'admin'
$env:APP_INITIAL_ADMIN_PASSWORD = $adminPassword
$env:APP_DEMO_SEED_ENABLED = 'true'
$env:APP_DEMO_SEED_PASSWORD = $demoPassword
$env:APP_DEMO_SEED_COUNT = $DemoUserCount.ToString()
$env:APP_DEMO_SEED_MONTHS = $DemoMonths.ToString()

try {
    $seedProcess = Start-Process `
        -FilePath (Join-Path $packageDir 'runtime\bin\java.exe') `
        -ArgumentList @('-jar', 'attendance-demo.jar') `
        -WorkingDirectory $packageDir `
        -RedirectStandardOutput $seedLog `
        -RedirectStandardError $seedErrorLog `
        -PassThru `
        -WindowStyle Hidden

    $deadline = [DateTime]::UtcNow.AddMinutes(10)
    $seedCompleted = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($seedProcess.HasExited) {
            break
        }
        if (Test-Path -LiteralPath $seedLog) {
            $logText = Get-Content -LiteralPath $seedLog -Raw -ErrorAction SilentlyContinue
            if ($logText -match 'DEMO_SEED_COMPLETED' -and
                $logText -match 'INITIAL_ADMIN_CREATED') {
                $seedCompleted = $true
                break
            }
        }
        Start-Sleep -Seconds 2
    }
    if (-not $seedCompleted) {
        $errorDetail = if (Test-Path -LiteralPath $seedErrorLog) {
            Get-Content -LiteralPath $seedErrorLog -Raw
        }
        else {
            'エラーログはありません。'
        }
        throw "デモDBの作成完了を確認できませんでした。`n$errorDetail"
    }
}
finally {
    if ($null -ne $seedProcess -and -not $seedProcess.HasExited) {
        Stop-Process -Id $seedProcess.Id
        $seedProcess.WaitForExit(10000) | Out-Null
    }
}

$environmentFile = @"
@echo off
set "SPRING_PROFILES_ACTIVE=demo"
set "SERVER_ADDRESS=127.0.0.1"
set "SERVER_PORT=4280"
set "APP_INITIAL_ADMIN_USERNAME=admin"
set "APP_INITIAL_ADMIN_PASSWORD=$adminPassword"
set "APP_DEMO_SEED_ENABLED=false"
set "APP_DEMO_SEED_PASSWORD=$demoPassword"
"@
Set-Content -LiteralPath (Join-Path $packageDir 'config\demo.env.cmd') `
    -Value $environmentFile `
    -Encoding utf8

$startBatch = @'
@echo off
chcp 65001 > nul
cd /d "%~dp0"
call "%~dp0config\demo.env.cmd"
for /f %%S in ('powershell.exe -NoProfile -Command "try { (Invoke-RestMethod 'http://127.0.0.1:4280/actuator/health' -TimeoutSec 3).status } catch { 'DOWN' }"') do set "DEMO_STATUS=%%S"
if "%DEMO_STATUS%"=="UP" (
  echo The attendance demo is already running.
  start "" "http://127.0.0.1:4280/"
  exit /b 0
)
echo Starting the attendance demo without Docker.
echo Please keep this window open.
echo.
start "" powershell.exe -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 12; Start-Process 'http://127.0.0.1:4280/'"
"%~dp0runtime\bin\java.exe" -jar "%~dp0attendance-demo.jar"
echo.
echo The attendance demo has stopped.
pause
'@
Set-Content -LiteralPath (Join-Path $packageDir 'デモを起動.cmd') `
    -Value $startBatch `
    -Encoding utf8

$mobileStartBatch = @'
@echo off
chcp 65001 > nul
cd /d "%~dp0"
call "%~dp0config\demo.env.cmd"
for /f %%S in ('powershell.exe -NoProfile -Command "try { (Invoke-RestMethod 'http://127.0.0.1:4280/actuator/health' -TimeoutSec 3).status } catch { 'DOWN' }"') do set "DEMO_STATUS=%%S"
if "%DEMO_STATUS%"=="UP" (
  echo The demo is already running.
  echo Close the existing demo window before starting smartphone access mode.
  pause
  exit /b 1
)
for /f %%I in ('powershell.exe -NoProfile -Command "$ip = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -ne $null -and $_.IPv4Address -ne $null } | ForEach-Object { $_.IPv4Address.IPAddress } | Select-Object -First 1; if ($ip) { $ip }"') do set "LAN_IP=%%I"
if not defined LAN_IP (
  echo A LAN IPv4 address could not be detected.
  echo Connect this PC and the smartphone to the same Wi-Fi and try again.
  pause
  exit /b 1
)
set "SERVER_ADDRESS=0.0.0.0"
echo Starting smartphone access mode.
echo Keep this window open.
echo.
echo Smartphone URL: http://%LAN_IP%:4280/
echo Allow access only for Private networks if Windows Firewall asks.
echo Do not use this mode on public Wi-Fi.
echo.
start "" powershell.exe -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 12; Start-Process 'http://127.0.0.1:4280/'"
"%~dp0runtime\bin\java.exe" -jar "%~dp0attendance-demo.jar"
echo.
echo The attendance demo has stopped.
pause
'@
Set-Content -LiteralPath (Join-Path $packageDir 'スマホで確認.cmd') `
    -Value $mobileStartBatch `
    -Encoding utf8

$loginGuide = @"
勤怠管理システム Dockerなしデモ版
=================================

起動方法
  1. 「デモを起動.cmd」をダブルクリックします。
  2. 黒い画面は閉じず、ブラウザーが開くまで10～30秒待ちます。
  3. 終了するときは黒い画面を選び、Ctrl+Cを押して閉じます。

スマートフォンからの確認
  1. PCとスマートフォンを同じ信頼できるWi-Fiへ接続します。
  2. 通常版を起動している場合は、先に黒い起動画面を閉じます。
  3. 「スマホで確認.cmd」をダブルクリックします。
  4. 黒い画面に表示された http://PCのIPv4アドレス:4280/ をスマートフォンで開きます。
  5. Windowsファイアウォールの確認では「プライベート ネットワーク」だけを許可します。

管理者
  ユーザーID: admin
  パスワード: $adminPassword

一般社員（$DemoUserCount 人）
  ユーザーID: demo001 ～ demo$($DemoUserCount.ToString('000'))
  共通パスワード: $demoPassword

データ
  各一般社員について、直近 $DemoMonths か月分の勤務表が登録されています。
  入力・保存した内容は、このフォルダー内のdataフォルダーへ保存されます。

注意
  ・上司への説明用デモです。本番運用には使用しないでください。
  ・Docker、Java、Node.jsのインストールは不要です。
  ・通常起動はこのPCだけ、スマホ確認起動は同じLAN内だけからアクセスできます。
  ・スマホ確認起動を公衆Wi-Fiやインターネット公開には使用しないでください。
  ・Outlook、Slackの実送信には別途社内連携設定が必要です。
  ・このファイルにはログイン情報があるため、説明後はZIPと展開先を適切に削除してください。
"@
Set-Content -LiteralPath (Join-Path $packageDir 'はじめに・ログイン情報.txt') `
    -Value $loginGuide `
    -Encoding utf8

Remove-Item -LiteralPath $seedLog, $seedErrorLog -Force -ErrorAction SilentlyContinue

Write-Host '6/6 ZIPを作成します。'
Compress-Archive -LiteralPath $packageDir -DestinationPath $zipPath -CompressionLevel Optimal

Write-Host ''
Write-Host 'Dockerなしデモ版を作成しました。'
Write-Host "フォルダー: $packageDir"
Write-Host "ZIP:        $zipPath"
Write-Host "ログイン情報: $(Join-Path $packageDir 'はじめに・ログイン情報.txt')"
