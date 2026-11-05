@echo off
rem Compiles and launches the SQL-IDE JavaFX prototype for UI review (mock data only).
rem Output is written to target\run-ui-<random>.log for inspection.
cd /d "%~dp0"
if not exist target mkdir target
set "LOGFILE=%~dp0target\run-ui-%RANDOM%.log"
call "%~dp0mvnw.cmd" -q compile javafx:run > "%LOGFILE%" 2>&1
