@echo off
setlocal
title Stop Study Leftovers
cd /d "%~dp0"
docker compose down
if errorlevel 1 (
  echo Study Leftovers could not be stopped. Check that Docker Desktop is running.
) else (
  echo Study Leftovers has stopped. Your account and study data are preserved.
)
pause

