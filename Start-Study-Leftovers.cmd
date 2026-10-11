@echo off
setlocal
title Study Leftovers Launcher
cd /d "%~dp0"

where docker >nul 2>nul
if errorlevel 1 (
  echo Docker Desktop was not found.
  echo Install and start Docker Desktop, then double-click this file again.
  echo https://www.docker.com/products/docker-desktop/
  pause
  exit /b 1
)

docker info >nul 2>nul
if errorlevel 1 (
  echo Docker Desktop is installed but is not running yet.
  echo Start Docker Desktop, wait until it is ready, then try again.
  pause
  exit /b 1
)

echo Starting Study Leftovers. The first launch can take a few minutes...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-all.ps1"
if errorlevel 1 (
  echo Startup did not complete. Review the error above.
  pause
  exit /b 1
)
pause
