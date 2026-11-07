@echo off
rem Canvas grid spike: opens the demo window (choose K1-K4 and Canvas / TableView).
cd /d "%~dp0"
call "%~dp0..\..\mvnw.cmd" -B -q compile javafx:run
