@echo off
rem Compiles and runs the unit tests; everything goes to target\claude-test.log (read by Claude).
cd /d "%~dp0"
if not exist target mkdir target
set "LOG=%~dp0target\claude-test.log"
echo === START %DATE% %TIME% > "%LOG%"
call "%~dp0mvnw.cmd" -B compile >> "%LOG%" 2>&1
echo === COMPILE EXIT %ERRORLEVEL% >> "%LOG%"
if not "%ERRORLEVEL%"=="0" goto done
call "%~dp0mvnw.cmd" -B test >> "%LOG%" 2>&1
echo === TEST EXIT %ERRORLEVEL% >> "%LOG%"
:done
echo === END %DATE% %TIME% >> "%LOG%"
