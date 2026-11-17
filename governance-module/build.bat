@echo off
setlocal
cd /d "%~dp0"

set "MVN="
for /f "delims=" %%i in ('where mvn 2^>nul') do if not defined MVN set "MVN=%%i"
if not defined MVN set "MVN=C:\Coding\apache-maven-3.10.0\bin\mvn.cmd"

set "REPO=%~dp0..\.m2repo"
if not exist "%REPO%" set "REPO=%USERPROFILE%\.m2\repository"

echo [governance-module] Building package...
call "%MVN%" -B "-Dmaven.repo.local=%REPO%" package
if errorlevel 1 exit /b %ERRORLEVEL%

echo [governance-module] Jar: %~dp0target\governance-module-1.0.0.jar
exit /b 0
