@echo off
setlocal
title ALI Car System - P01Y Safe Flash
where adb >nul 2>nul || (
  echo ADB was not found in PATH.
  echo Install Android SDK Platform-Tools, then reopen this window.
  pause
  exit /b 1
)
where fastboot >nul 2>nul || (
  echo Fastboot was not found in PATH.
  echo Install Android SDK Platform-Tools, then reopen this window.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0flash-ali-p01y.ps1" -ImagePath "%~dp0system.img"
set "FLASH_EXIT=%ERRORLEVEL%"
echo.
if not "%FLASH_EXIT%"=="0" (
  echo The flash utility stopped. Read the error above; do not try random commands.
) else (
  echo The system partition write completed.
)
pause
exit /b %FLASH_EXIT%
