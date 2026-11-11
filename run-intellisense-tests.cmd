@echo off
rem IntelliSense engine tests (Node.js, no database, no display). One file at a time: some tests measure time.
cd /d "%~dp0src\test\intellisense-js\js"
node --test --test-concurrency=1 "*.test.js"
