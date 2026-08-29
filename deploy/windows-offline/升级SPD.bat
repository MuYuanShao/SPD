@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer\invoke-utf8-script.ps1" -ScriptPath "%~dp0installer\install.ps1" -Upgrade
if errorlevel 1 pause
