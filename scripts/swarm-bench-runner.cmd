@echo off
setlocal EnableExtensions
set "HERE=%~dp0"
call "%HERE%swarm-path.cmd"
if defined SWARM_PWSH (
  "%SWARM_PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-runner.ps1" %*
  exit /b %ERRORLEVEL%
)
echo missing PowerShell 1>&2
exit /b 1
