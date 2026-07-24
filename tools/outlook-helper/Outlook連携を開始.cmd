@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Register-OutlookProtocol.ps1"
if errorlevel 1 (
  echo Outlook integration setup failed.
  pause
)
