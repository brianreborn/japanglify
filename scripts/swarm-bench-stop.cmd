@echo off
rem Idle SHALOM: Grok down => listener down.
set "HERE=%~dp0"
where pwsh >nul 2>&1
if %ERRORLEVEL%==0 (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-stop.ps1" %*
  exit /b %ERRORLEVEL%
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-stop.ps1" %*
exit /b %ERRORLEVEL%
