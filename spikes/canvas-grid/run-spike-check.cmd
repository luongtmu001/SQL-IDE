@echo off
rem Canvas grid spike: builds and runs the S1 check. Output: target\spike-check.log, target\spike-check.txt, target\spike-*.png
cd /d "%~dp0"
if not exist target mkdir target
set "LOG=%~dp0target\spike-check.log"
echo === START %DATE% %TIME% > "%LOG%"
call "%~dp0..\..\mvnw.cmd" -B test >> "%LOG%" 2>&1
echo === EXIT %ERRORLEVEL% >> "%LOG%"
echo === END %DATE% %TIME% >> "%LOG%"
echo Xong. Claude se doc target\spike-check.txt va anh target\spike-*.png
pause
