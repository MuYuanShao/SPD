@echo off
rem SPD platform one-click startup (double-click friendly)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1" %*
echo.
pause
