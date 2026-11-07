@echo off
rem D3: unit tests, then the tests against the real SQL Server / PostgreSQL on this computer (read-only statements only).
rem Settings: %APPDATA%\LLTool-Desktop\it-db.properties. Output: target\db-test.log and target\db-test-report.txt
cd /d "%~dp0"
if not exist target mkdir target
set "LOG=%~dp0target\db-test.log"
echo === START %DATE% %TIME% > "%LOG%"
echo Dang build + chay unit test...
call "%~dp0mvnw.cmd" -B test >> "%LOG%" 2>&1
echo === UNIT TEST EXIT %ERRORLEVEL% >> "%LOG%"
echo Dang chay test voi SQL Server / PostgreSQL...
call "%~dp0mvnw.cmd" -B surefire:test -Dtest=QueryDbIT,GridPerfIT -Dsurefire.failIfNoSpecifiedTests=false "-DargLine=-Djava.library.path=%~dp0native" >> "%LOG%" 2>&1
echo === DB TEST EXIT %ERRORLEVEL% >> "%LOG%"
echo === END %DATE% %TIME% >> "%LOG%"
echo.
echo Xong. Ket qua: target\db-test-report.txt  (Claude se doc file nay) va target\grid-perf.txt
pause
