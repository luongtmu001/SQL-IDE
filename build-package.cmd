@echo off
setlocal enabledelayedexpansion

echo ======================================================================
echo                 SQL IDE - WINDOWS PACKAGING SCRIPT
echo ======================================================================

cd /d "%~dp0"

echo [1/6] Compiling and packaging main jar with Maven...
call "%~dp0mvnw.cmd" clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Maven build failed.
    exit /b 1
)

echo [2/6] Collecting runtime dependencies...
call "%~dp0mvnw.cmd" dependency:copy-dependencies -DoutputDirectory=target\libs -DincludeScope=runtime
if errorlevel 1 (
    echo [ERROR] Failed to copy dependencies.
    exit /b 1
)

echo [3/6] Staging jars and assets...
if exist "target\package-staging" rmdir /s /q "target\package-staging"
mkdir "target\package-staging"
copy /y "target\sql-ide-1.0.0-SNAPSHOT.jar" "target\package-staging\" >nul
copy /y "target\libs\*.jar" "target\package-staging\" >nul

if exist "target\dist" rmdir /s /q "target\dist"
mkdir "target\dist"

echo [4/6] Creating standalone Windows application with jpackage...
jpackage --type app-image ^
    --name "SQL-IDE" ^
    --app-version "1.0.0" ^
    --input "target\package-staging" ^
    --main-jar "sql-ide-1.0.0-SNAPSHOT.jar" ^
    --main-class "app.Main" ^
    --dest "target\dist" ^
    --icon "src\main\resources\static\icons\app-logo.ico" ^
    --java-options "--enable-native-access=ALL-UNNAMED" ^
    --java-options "-Dprism.lcdtext=true"

if errorlevel 1 (
    echo [ERROR] jpackage failed to create app-image.
    exit /b 1
)

echo [5/6] Copying native DLLs...
if exist "native\mssql-jdbc_auth-12.6.1.x64.dll" (
    copy /y "native\mssql-jdbc_auth-12.6.1.x64.dll" "target\dist\SQL-IDE\" >nul
    copy /y "native\mssql-jdbc_auth-12.6.1.x64.dll" "target\dist\SQL-IDE\app\" >nul
)

echo [6/6] Creating distributable ZIP package...
if not exist "dist" mkdir "dist"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Compress-Archive -Path 'target\dist\SQL-IDE' -DestinationPath 'dist\SQL-IDE-1.0.0-windows-x64.zip' -Force"

echo ======================================================================
echo PACKAGING COMPLETE!
echo.
echo Executable directory: target\dist\SQL-IDE\SQL-IDE.exe
echo Distributable ZIP:   dist\SQL-IDE-1.0.0-windows-x64.zip
echo ======================================================================

