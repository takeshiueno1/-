@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item -LiteralPath 'HKCU:\Software\Classes\query-attendance-outlook' -Recurse -Force -ErrorAction SilentlyContinue"
