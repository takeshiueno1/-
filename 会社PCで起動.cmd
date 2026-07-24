@echo off
chcp 65001 > nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\company-demo\start-company-demo.ps1"
if errorlevel 1 (
  echo.
  echo 起動に失敗しました。上に表示された内容を確認してください。
)
echo.
pause
