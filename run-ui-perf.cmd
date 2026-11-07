@echo off
rem Phase 0 (docs/feature_09): the app with the measuring timeline on. Connects to the real servers.
rem Timeline: target\perf\app-timeline-*.log (durations and counts only, no SQL text, no passwords).
cd /d "%~dp0"
if not exist target mkdir target
set "SQLIDE_PERF_IS=1"
set "LOGFILE=%~dp0target\run-ui-perf-%RANDOM%.log"
call "%~dp0mvnw.cmd" -q compile javafx:run > "%LOGFILE%" 2>&1
