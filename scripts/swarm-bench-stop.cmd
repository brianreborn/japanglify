@echo off
rem Idle SHALOM: Grok down => listener down.
setlocal EnableExtensions
set "HERE=%~dp0"
call "%HERE%swarm-path.cmd"
if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" (
  "%ProgramFiles%\PowerShell\7\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-stop.ps1" %*
  exit /b %ERRORLEVEL%
)
where pwsh >nul 2>&1
if %ERRORLEVEL%==0 (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-stop.ps1" %*
  exit /b %ERRORLEVEL%
)
if exist "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" (
  "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-stop.ps1" %*
  exit /b %ERRORLEVEL%
)
echo missing pwsh and powershell.exe 1>&2
exit /b 1
