@echo off
REM Load KEY=VALUE from .env into the current cmd session.
if not exist "%~dp0.env" exit /b 0
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0.env") do (
  if not "%%A"=="" if not "%%B"=="" set "%%A=%%B"
)
exit /b 0
