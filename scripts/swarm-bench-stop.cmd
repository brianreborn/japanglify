@echo off
rem Idle SHALOM: Grok down => listener down.
setlocal EnableExtensions
set "HERE=%~dp0"
call "%HERE%swarm-path.cmd"
if defined SWARM_PWSH (
  "%SWARM_PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-stop.ps1" %*
  exit /b %ERRORLEVEL%
)
echo missing PowerShell 1>&2
exit /b 1
