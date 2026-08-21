@echo off
rem Always start System32 Windows PowerShell, hunt installs, import into this cmd.
rem No setlocal — CALL must leave PATH / SWARM_* in the caller.
if defined SWARM_PATH_SEEDED exit /b 0
set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "HERE=%~dp0"
set "ENVFILE=%TEMP%\swarm-path.env"
"%PS%" -NoProfile -ExecutionPolicy Bypass -File "%HERE%swarm-path.ps1" -Export "%ENVFILE%"
if not exist "%ENVFILE%" (
  echo swarm-path.ps1 did not write %ENVFILE% 1>&2
  exit /b 1
)
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENVFILE%") do (
  if /I "%%A"=="PATH" set "PATH=%%B"
  if /I "%%A"=="SWARM_GIT" set "SWARM_GIT=%%B"
  if /I "%%A"=="SWARM_GH" set "SWARM_GH=%%B"
  if /I "%%A"=="SWARM_PWSH" set "SWARM_PWSH=%%B"
  if /I "%%A"=="SWARM_POWERSHELL" set "SWARM_POWERSHELL=%%B"
  if /I "%%A"=="SWARM_PY" set "SWARM_PY=%%B"
  if /I "%%A"=="SWARM_PYTHON" set "SWARM_PYTHON=%%B"
  if /I "%%A"=="SWARM_GROK" set "SWARM_GROK=%%B"
  if /I "%%A"=="SWARM_ADB" set "SWARM_ADB=%%B"
  if /I "%%A"=="JAVA_HOME" set "JAVA_HOME=%%B"
  if /I "%%A"=="ANDROID_HOME" set "ANDROID_HOME=%%B"
)
set "SWARM_PATH_SEEDED=1"
exit /b 0
