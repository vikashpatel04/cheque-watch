@echo off
rem Runs setup.sh with Git Bash (in PowerShell, plain "bash" may start WSL instead).
set "GITBASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%GITBASH%" (
  echo Git for Windows is required: https://git-scm.com/download/win
  exit /b 1
)
"%GITBASH%" "%~dp0setup.sh" %*
