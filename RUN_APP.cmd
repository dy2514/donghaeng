@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Run-App.ps1" %*
set "DONGHAENG_EXIT=%ERRORLEVEL%"
if not "%DONGHAENG_EXIT%"=="0" pause
exit /b %DONGHAENG_EXIT%
