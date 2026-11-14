@echo off
chcp 65001 >nul
set "PWSH=pwsh.exe"
where pwsh.exe >nul 2>nul
if errorlevel 1 set "PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not exist "%PWSH%" if not "%PWSH%"=="pwsh.exe" (
  echo Please install PowerShell 7, or run scripts/start.ps1 from the Codex terminal.
  pause
  exit /b 1
)
"%PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" %*
pause
