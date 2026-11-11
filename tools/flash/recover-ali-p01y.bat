@echo off
setlocal
title ALI Car System - P01Y System Recovery
where fastboot >nul 2>nul || (
  echo Fastboot was not found in PATH.
  echo Install or expose Android/Intel Fastboot tools, then reopen this window.
  pause
  exit /b 1
)
if not exist "%~dp0system.img" (
  echo system.img was not found beside this file.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0recover-ali-p01y.ps1" -ImagePath "%~dp0system.img"
set "RECOVERY_EXIT=%ERRORLEVEL%"
echo.
if not "%RECOVERY_EXIT%"=="0" (
  echo Recovery stopped. Keep the tablet in Fastboot and do not reboot.
) else (
  echo System write reported OKAY. Keep the tablet in Fastboot until reviewed.
)
pause
exit /b %RECOVERY_EXIT%
