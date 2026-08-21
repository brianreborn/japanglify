@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
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
python scripts\swarm-grok.py %*
exit /b %ERRORLEVEL%
