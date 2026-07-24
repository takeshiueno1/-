[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $projectRoot '.env'

Set-Location $projectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Dockerが見つかりません。'
}

if (-not (Test-Path -LiteralPath $envFile)) {
    throw '.envが見つかりません。先に「会社PCで起動.cmd」を実行してください。'
}

& docker compose --env-file $envFile down
if ($LASTEXITCODE -ne 0) {
    throw '勤怠管理システムの停止に失敗しました。'
}

Write-Host '勤怠管理システムを停止しました。DBデータは保持されています。' -ForegroundColor Green
