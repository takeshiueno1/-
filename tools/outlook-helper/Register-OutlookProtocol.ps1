[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$protocolRoot = 'HKCU:\Software\Classes\query-attendance-outlook'
$commandKey = Join-Path $protocolRoot 'shell\open\command'
$handler = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'Open-OutlookDraft.ps1'
$powershell = Join-Path $PSHOME 'powershell.exe'

New-Item -Path $commandKey -Force | Out-Null
Set-Item -Path $protocolRoot -Value 'URL:Query Attendance Outlook'
New-ItemProperty -Path $protocolRoot -Name 'URL Protocol' -Value '' -PropertyType String -Force | Out-Null
$command = '"' + $powershell + '" -NoProfile -ExecutionPolicy Bypass -File "' + $handler + '" "%1"'
Set-Item -Path $commandKey -Value $command

Write-Output 'Outlook integration is ready.'
