@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
call "%~dp0swarm-path.cmd"
where py >nul 2>&1
if %ERRORLEVEL%==0 (
  py -3 scripts\swarm-grok.py %*
  exit /b %ERRORLEVEL%
)
where python3 >nul 2>&1
if %ERRORLEVEL%==0 (
  python3 scripts\swarm-grok.py %*
  exit /b %ERRORLEVEL%
)
where python >nul 2>&1
if %ERRORLEVEL%==0 (
  python scripts\swarm-grok.py %*
  exit /b %ERRORLEVEL%
)
echo missing Python (py / python3 / python) after swarm-path.cmd 1>&2
where git 2>nul
where pwsh 2>nul
where grok 2>nul
exit /b 1
