@echo off
setlocal
cd /d "%~dp0"

call "%~dp0build.bat"
if errorlevel 1 exit /b %ERRORLEVEL%

echo.
echo [team-module] Running demo...
chcp 65001 >nul
java -Dfile.encoding=UTF-8 -jar "%~dp0target\team-module-1.0.0.jar"
exit /b %ERRORLEVEL%
