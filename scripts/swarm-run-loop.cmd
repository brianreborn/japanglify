@echo off
REM Keep Runner.Listener alive for this Windows logon session — unless disarmed.
REM Hidden. USB adb still needs this logon.
title swarm-bench-listener
cd /d C:\actions-runner
:loop
if exist ".swarm-disarmed" (
  echo %DATE% %TIME% disarmed — listener stays down
  exit /b 0
)
tasklist /FI "IMAGENAME eq Runner.Listener.exe" | find /I "Runner.Listener.exe" >nul
if %ERRORLEVEL%==0 (
  timeout /t 20 /nobreak >nul
  goto loop
)
if not exist run.cmd (
  echo %DATE% %TIME% missing C:\actions-runner\run.cmd
  timeout /t 30 /nobreak >nul
  goto loop
)
echo %DATE% %TIME% starting run.cmd
call run.cmd
echo %DATE% %TIME% run.cmd exited %ERRORLEVEL% — restart in 5s
timeout /t 5 /nobreak >nul
goto loop
