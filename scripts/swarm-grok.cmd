@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
call "%~dp0swarm-path.cmd"
if defined SWARM_PY (
  "%SWARM_PY%" -3 scripts\swarm-grok.py %*
  exit /b %ERRORLEVEL%
)
if defined SWARM_PYTHON (
  "%SWARM_PYTHON%" scripts\swarm-grok.py %*
  exit /b %ERRORLEVEL%
)
echo missing Python. Set SWARM_PY or install py.exe 1>&2
exit /b 1
