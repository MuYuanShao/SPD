@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer\invoke-utf8-script.ps1" -ScriptPath "%~dp0installer\manage.ps1" -Action stop
if errorlevel 1 pause
