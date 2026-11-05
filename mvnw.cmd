@echo off
setlocal
if exist "%LOCALAPPDATA%\Programs\apache-maven-3.9.16\bin\mvn.cmd" (
    call "%LOCALAPPDATA%\Programs\apache-maven-3.9.16\bin\mvn.cmd" %*
) else (
    call mvn %*
)
