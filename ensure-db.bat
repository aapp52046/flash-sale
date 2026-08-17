@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call "%~dp0load-env.cmd"

if not defined DB_USERNAME set "DB_USERNAME=postgres"
if not defined DB_PASSWORD set "DB_PASSWORD=postgres"
if not defined DB_NAME set "DB_NAME=flash_sale"
set "PGPASSWORD=%DB_PASSWORD%"

set "PSQL="
if exist "%DASH_PSQL%" set "PSQL=%DASH_PSQL%"
if not defined PSQL if exist "C:\Program Files\PostgreSQL\18\bin\psql.exe" set "PSQL=C:\Program Files\PostgreSQL\18\bin\psql.exe"
if not defined PSQL if exist "C:\Program Files\PostgreSQL\16\bin\psql.exe" set "PSQL=C:\Program Files\PostgreSQL\16\bin\psql.exe"
if not defined PSQL if exist "C:\Program Files\PostgreSQL\15\bin\psql.exe" set "PSQL=C:\Program Files\PostgreSQL\15\bin\psql.exe"
if not defined PSQL (
  where psql >nul 2>&1 && for /f "delims=" %%I in ('where psql') do if not defined PSQL set "PSQL=%%I"
)
if not defined PSQL (
  echo [ERROR] psql.exe not found. Install PostgreSQL or set DASH_PSQL in .env
  exit /b 1
)
for %%I in ("%PSQL%") do set "PGBIN=%%~dpI"

echo [db] checking %DB_NAME% ...
"%PSQL%" -U %DB_USERNAME% -h localhost -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='%DB_NAME%'" 2>nul | findstr /r "1" >nul
if errorlevel 1 (
  echo [db] creating %DB_NAME% ...
  "%PGBIN%createdb.exe" -U %DB_USERNAME% -h localhost %DB_NAME%
  if errorlevel 1 (
    echo [ERROR] createdb failed
    exit /b 1
  )
)

"%PSQL%" -U %DB_USERNAME% -h localhost -d %DB_NAME% -tAc "SELECT to_regclass('public.product')" 2>nul | findstr /i "product" >nul
if errorlevel 1 (
  echo [db] applying schema.sql ...
  "%PSQL%" -U %DB_USERNAME% -h localhost -d %DB_NAME% -f "%~dp0schema.sql"
  if errorlevel 1 exit /b 1
)

"%PSQL%" -U %DB_USERNAME% -h localhost -d %DB_NAME% -tAc "SELECT 1 FROM product LIMIT 1" 2>nul | findstr /r "1" >nul
if errorlevel 1 (
  echo [db] applying docker/seed.sql ...
  "%PSQL%" -U %DB_USERNAME% -h localhost -d %DB_NAME% -f "%~dp0docker\seed.sql"
  if errorlevel 1 exit /b 1
)

echo [db] ready: %DB_NAME%
exit /b 0
