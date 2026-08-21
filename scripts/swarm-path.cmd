@echo off
rem Prepend usual Windows tool dirs. Fresh cmd / Grok-spawned shells often
rem lack Git, pwsh, py, grok. call this before where.exe / git / pwsh.
if defined SWARM_PATH_SEEDED exit /b 0
set "SWARM_PATH_SEEDED=1"
set "PATH=%SystemRoot%\System32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SystemRoot%\System32\WindowsPowerShell\v1.0;%PATH%"
if exist "%ProgramFiles%\Git\cmd\git.exe" set "PATH=%ProgramFiles%\Git\cmd;%ProgramFiles%\Git\bin;%PATH%"
if exist "%ProgramFiles(x86)%\Git\cmd\git.exe" set "PATH=%ProgramFiles(x86)%\Git\cmd;%PATH%"
if exist "%LocalAppData%\Programs\Git\cmd\git.exe" set "PATH=%LocalAppData%\Programs\Git\cmd;%PATH%"
if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "PATH=%ProgramFiles%\PowerShell\7;%PATH%"
if exist "%ProgramFiles%\PowerShell\7-preview\pwsh.exe" set "PATH=%ProgramFiles%\PowerShell\7-preview;%PATH%"
if exist "%LocalAppData%\Microsoft\WindowsApps" set "PATH=%LocalAppData%\Microsoft\WindowsApps;%PATH%"
if exist "%LocalAppData%\Programs\Python\Launcher\py.exe" set "PATH=%LocalAppData%\Programs\Python\Launcher;%PATH%"
if exist "%SystemRoot%\py.exe" set "PATH=%SystemRoot%;%PATH%"
if exist "%ProgramData%\chocolatey\bin" set "PATH=%ProgramData%\chocolatey\bin;%PATH%"
if exist "%LocalAppData%\Microsoft\WinGet\Links" set "PATH=%LocalAppData%\Microsoft\WinGet\Links;%PATH%"
if exist "%USERPROFILE%\scoop\shims" set "PATH=%USERPROFILE%\scoop\shims;%PATH%"
if exist "%USERPROFILE%\.local\bin" set "PATH=%USERPROFILE%\.local\bin;%PATH%"
if exist "%USERPROFILE%\AppData\Local\Programs\Grok" set "PATH=%USERPROFILE%\AppData\Local\Programs\Grok;%PATH%"
if exist "%LocalAppData%\grok" set "PATH=%LocalAppData%\grok;%PATH%"
exit /b 0
