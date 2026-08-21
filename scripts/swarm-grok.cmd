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
echo missing Python. Hunter did not find py.exe or python.exe. 1>&2
echo SWARM_PY=%SWARM_PY% SWARM_PYTHON=%SWARM_PYTHON% 1>&2
echo Install Python 3 (python.org or: winget install Python.Python.3.12) with the py launcher. 1>&2
exit /b 1
