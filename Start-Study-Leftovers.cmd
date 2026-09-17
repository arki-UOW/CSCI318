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

if not exist ".env" (
  copy /y ".env.example" ".env" >nul
  echo Created your private .env file. The app works without an AI key,
  echo but Gemini features require GEMINI_API_KEY in that file.
  echo.
)

echo Starting Study Leftovers. The first launch can take a few minutes...
docker compose up -d --build
if errorlevel 1 (
  echo.
  echo Study Leftovers could not start. Recent container details follow:
  docker compose ps
  pause
  exit /b 1
)

echo.
echo Study Leftovers is running at http://localhost:3000
start "" "http://localhost:3000"
pause
