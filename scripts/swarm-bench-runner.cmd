@echo off
setlocal EnableExtensions
set "HERE=%~dp0"
call "%HERE%swarm-path.cmd"
if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" (
  "%ProgramFiles%\PowerShell\7\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-runner.ps1" %*
  exit /b %ERRORLEVEL%
)
where pwsh >nul 2>&1
if %ERRORLEVEL%==0 (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-runner.ps1" %*
  exit /b %ERRORLEVEL%
)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-bench-runner.ps1" %*
exit /b %ERRORLEVEL%
