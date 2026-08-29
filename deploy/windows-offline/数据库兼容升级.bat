@echo off
set /p BACKUP_EVIDENCE=请输入数据库备份凭据文件完整路径:
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer\invoke-utf8-script.ps1" -ScriptPath "%~dp0installer\legacy-repair.ps1" -BackupEvidencePath "%BACKUP_EVIDENCE%"
if errorlevel 1 pause
