@echo off
rem Seed tools from the SHALOM instance that already ran jobs.
rem 1) C:\actions-runner\.env (written by swarm-bench-runner)
rem 2) absolute fallbacks below. Never use where.exe to find them.
if defined SWARM_PATH_SEEDED exit /b 0
set "SWARM_PATH_SEEDED=1"

if exist "C:\actions-runner\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("C:\actions-runner\.env") do (
    if /I "%%A"=="PATH" set "PATH=%%B"
    if /I "%%A"=="JAVA_HOME" set "JAVA_HOME=%%B"
    if /I "%%A"=="ANDROID_HOME" set "ANDROID_HOME=%%B"
    if /I "%%A"=="ANDROID_SDK_ROOT" set "ANDROID_SDK_ROOT=%%B"
  )
)

if exist "C:\Program Files\Git\cmd\git.exe" set "SWARM_GIT=C:\Program Files\Git\cmd\git.exe"
if exist "C:\Program Files\Git\bin\git.exe" if not defined SWARM_GIT set "SWARM_GIT=C:\Program Files\Git\bin\git.exe"
if exist "%LocalAppData%\Programs\Git\cmd\git.exe" if not defined SWARM_GIT set "SWARM_GIT=%LocalAppData%\Programs\Git\cmd\git.exe"
if defined SWARM_GIT set "PATH=%SWARM_GIT:~0,-8%;%PATH%"

if exist "C:\Program Files\PowerShell\7\pwsh.exe" set "SWARM_PWSH=C:\Program Files\PowerShell\7\pwsh.exe"
if exist "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" set "SWARM_POWERSHELL=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not defined SWARM_PWSH if defined SWARM_POWERSHELL set "SWARM_PWSH=%SWARM_POWERSHELL%"
if exist "C:\Program Files\PowerShell\7\" set "PATH=C:\Program Files\PowerShell\7;%PATH%"

if exist "%SystemRoot%\py.exe" set "SWARM_PY=%SystemRoot%\py.exe"
if exist "%LocalAppData%\Programs\Python\Launcher\py.exe" set "SWARM_PY=%LocalAppData%\Programs\Python\Launcher\py.exe"
for /d %%D in ("%LocalAppData%\Programs\Python\Python3*") do if exist "%%D\python.exe" set "SWARM_PYTHON=%%D\python.exe"

if exist "%USERPROFILE%\.local\bin\grok.exe" set "SWARM_GROK=%USERPROFILE%\.local\bin\grok.exe"
if exist "%LocalAppData%\Programs\Grok\grok.exe" set "SWARM_GROK=%LocalAppData%\Programs\Grok\grok.exe"

if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools" set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
if exist "%LocalAppData%\Android\Sdk\platform-tools\adb.exe" set "PATH=%LocalAppData%\Android\Sdk\platform-tools;%PATH%"
if exist "%SystemRoot%\System32" set "PATH=%SystemRoot%\System32;%SystemRoot%\System32\WindowsPowerShell\v1.0;%PATH%"
exit /b 0
