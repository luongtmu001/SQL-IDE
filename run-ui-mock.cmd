@echo off
rem Launches SQL-IDE with the UI prototype mock data only (no real database connection).
rem Phase 8: run-ui.cmd now connects to real servers (all sessions read-only).
cd /d "%~dp0"
if not exist target mkdir target
set "SQLIDE_BACKEND=mock"
set "LOGFILE=%~dp0target\run-ui-mock-%RANDOM%.log"
call "%~dp0mvnw.cmd" -q compile javafx:run > "%LOGFILE%" 2>&1
