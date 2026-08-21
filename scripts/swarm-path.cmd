@echo off
rem Seed tools from the SHALOM instance that already ran jobs.
rem 1) C:\actions-runner\.env
rem 2) HKLM Git for Windows + GitHub CLI (system install)
rem 3) absolute fallbacks. Never where.exe.
if defined SWARM_PATH_SEEDED exit /b 0
set "SWARM_PATH_SEEDED=1"
set "REG=%SystemRoot%\System32\reg.exe"

if exist "C:\actions-runner\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("C:\actions-runner\.env") do (
    if /I "%%A"=="PATH" set "PATH=%%B"
    if /I "%%A"=="JAVA_HOME" set "JAVA_HOME=%%B"
    if /I "%%A"=="ANDROID_HOME" set "ANDROID_HOME=%%B"
    if /I "%%A"=="ANDROID_SDK_ROOT" set "ANDROID_SDK_ROOT=%%B"
  )
)

rem System Git for Windows (HKLM, not user PATH)
for %%K in ("HKLM\SOFTWARE\GitForWindows" "HKLM\SOFTWARE\WOW6432Node\GitForWindows") do (
  for /f "skip=2 tokens=2,*" %%A in ('"%REG%" query %%K /v InstallPath 2^>nul') do (
    if exist "%%B\cmd\git.exe" set "SWARM_GIT=%%B\cmd\git.exe"
    if exist "%%B\bin\git.exe" if not defined SWARM_GIT set "SWARM_GIT=%%B\bin\git.exe"
    if exist "%%B\cmd" set "PATH=%%B\cmd;%%B\bin;%%B\mingw64\bin;%PATH%"
  )
)

if not defined SWARM_GIT if exist "C:\Program Files\Git\cmd\git.exe" set "SWARM_GIT=C:\Program Files\Git\cmd\git.exe"
if not defined SWARM_GIT if exist "C:\Program Files (x86)\Git\cmd\git.exe" set "SWARM_GIT=C:\Program Files (x86)\Git\cmd\git.exe"
if defined SWARM_GIT for %%I in ("%SWARM_GIT%") do set "PATH=%%~dpI;%PATH%"

rem System GitHub CLI (gh.exe). Does not provide git.exe.
if exist "C:\Program Files\GitHub CLI\gh.exe" set "SWARM_GH=C:\Program Files\GitHub CLI\gh.exe"
if exist "C:\Program Files (x86)\GitHub CLI\gh.exe" if not defined SWARM_GH set "SWARM_GH=C:\Program Files (x86)\GitHub CLI\gh.exe"
if defined SWARM_GH for %%I in ("%SWARM_GH%") do set "PATH=%%~dpI;%PATH%"

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
set "PATH=%SystemRoot%\System32;%SystemRoot%\System32\WindowsPowerShell\v1.0;%PATH%"
exit /b 0
