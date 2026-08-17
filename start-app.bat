@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call "%~dp0load-env.cmd"

if not defined DB_PASSWORD set "DB_PASSWORD=postgres"
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dev"

if not exist "Redis\redis-server.exe" (
  echo [ERROR] Redis\redis-server.exe not found
  exit /b 1
)

echo [1/4] PostgreSQL ...
call "%~dp0ensure-db.bat"
if errorlevel 1 (
  echo [ERROR] database not ready
  pause
  exit /b 1
)

echo [2/4] starting Redis on localhost:6379 ...
start "FlashSale Redis" "Redis\redis-server.exe" "Redis\redis.windows.conf"

echo [3/4] waiting for Redis ...
set /a tries=0
:waitredis
"Redis\redis-cli.exe" -p 6379 ping >nul 2>&1
if not errorlevel 1 goto ready
set /a tries+=1
if %tries% geq 15 (
  echo [ERROR] Redis did not start. Is port 6379 in use?
  pause
  exit /b 1
)
timeout /t 1 /nobreak >nul
goto waitredis
:ready

echo.
echo   Login      http://localhost:8080/login     admin / admin123
echo   Admin      http://localhost:8080/admin
echo   Dashboard  run start-dashboard.bat  -^>  http://127.0.0.1:9999
echo.
echo [4/4] starting Spring Boot ...
call mvnw.cmd spring-boot:run
if errorlevel 1 (
  echo.
  echo [ERROR] Spring Boot failed
  pause
)
endlocal
