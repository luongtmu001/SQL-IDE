@echo off
rem Phase 8 / D0.1 + D1 (Gate D approved 2026-11-05).
rem 1) Git baseline of the UI prototype (tag ui-prototype-baseline)
rem 2) copies the D0 + D1 code from .phase8-pending over the project
rem 3) commits it. Run once from the SQL-IDE folder.
cd /d "%~dp0"
if not exist ".phase8-pending\" goto :nothing
where git >nul 2>&1
if errorlevel 1 goto :nogit
if not exist ".git\" git init
git add -A -- . ":(exclude).phase8-pending" ":(exclude)apply-phase8-d1.cmd"
git commit -m "Baseline: JavaFX UI prototype (phases 0-7) before Gate D backend integration"
git tag -f ui-prototype-baseline
call :apply
git add -A
git commit -m "Phase 8 D0+D1: backend flags, read-only guard, per-tab sessions, credential resolver, pooled Explorer connections"
git log --oneline -n 3
goto :done
:nogit
echo Git not found on PATH: applying the code without commits.
call :apply
goto :done
:apply
xcopy ".phase8-pending\*" "." /E /Y /I /Q
rmdir /S /Q ".phase8-pending"
exit /b 0
:nothing
echo Nothing to apply: .phase8-pending not found.
exit /b 1
:done
echo Done.
