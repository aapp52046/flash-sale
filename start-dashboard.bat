@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call "%~dp0load-env.cmd"

if not defined DASH_DB_PASSWORD if defined DB_PASSWORD set "DASH_DB_PASSWORD=%DB_PASSWORD%"
if not defined DASH_DB_PASSWORD set "DASH_DB_PASSWORD=postgres"
if not defined DASH_DB_NAME set "DASH_DB_NAME=flash_sale"
if not defined DASH_APP_URL set "DASH_APP_URL=http://localhost:8080"
if not defined DASH_PORT set "DASH_PORT=9999"

where python >nul 2>&1
if errorlevel 1 (
  echo [ERROR] python not found
  pause
  exit /b 1
)

echo Starting dashboard ...
echo   Dashboard  http://127.0.0.1:%DASH_PORT%
echo   App        %DASH_APP_URL%/login
echo.

start "" "http://127.0.0.1:%DASH_PORT%"
python jmeter\dashboard.py
if errorlevel 1 (
  echo.
  echo [ERROR] dashboard.py exit %ERRORLEVEL%
  echo Check DB_PASSWORD in .env
  pause
)
endlocal
