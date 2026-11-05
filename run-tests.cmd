@echo off
rem Runs the SQL-IDE unit test suite. Output: target\run-tests.log (+ target\surefire-reports).
cd /d "%~dp0"
if not exist target mkdir target
call "%~dp0mvnw.cmd" test > "%~dp0target\run-tests.log" 2>&1
echo Done. See target\run-tests.log >> "%~dp0target\run-tests.log"
