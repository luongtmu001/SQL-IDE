# SQL IDE Windows Packaging Script (PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "                SQL IDE - WINDOWS PACKAGING SCRIPT                    " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

$rootDir = $PSScriptRoot
Set-Location $rootDir

Write-Host "`n[1/6] Compiling and packaging main jar with Maven..." -ForegroundColor Yellow
& "$rootDir\mvnw.cmd" clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

Write-Host "`n[2/6] Collecting runtime dependencies..." -ForegroundColor Yellow
& "$rootDir\mvnw.cmd" dependency:copy-dependencies -DoutputDirectory=target\libs -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) { throw "Failed to copy dependencies." }

Write-Host "`n[3/6] Staging jars and assets..." -ForegroundColor Yellow
$stagingDir = "$rootDir\target\package-staging"
if (Test-Path $stagingDir) { Remove-Item -Recurse -Force $stagingDir }
New-Item -ItemType Directory -Path $stagingDir | Out-Null

Copy-Item "$rootDir\target\sql-ide-1.0.0-SNAPSHOT.jar" $stagingDir
Copy-Item "$rootDir\target\libs\*.jar" $stagingDir

$distDir = "$rootDir\target\dist"
if (Test-Path $distDir) { Remove-Item -Recurse -Force $distDir }
New-Item -ItemType Directory -Path $distDir | Out-Null

Write-Host "`n[4/6] Creating standalone Windows application with jpackage..." -ForegroundColor Yellow
$jpackageArgs = @(
    "--type", "app-image",
    "--name", "SQL-IDE",
    "--app-version", "1.0.0",
    "--input", $stagingDir,
    "--main-jar", "sql-ide-1.0.0-SNAPSHOT.jar",
    "--main-class", "app.Main",
    "--dest", $distDir,
    "--icon", "$rootDir\src\main\resources\static\icons\app-logo.ico",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--java-options", "-Dprism.lcdtext=true"
)
& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

Write-Host "`n[5/6] Copying native DLLs..." -ForegroundColor Yellow
$nativeDll = "$rootDir\native\mssql-jdbc_auth-12.6.1.x64.dll"
if (Test-Path $nativeDll) {
    Copy-Item $nativeDll "$distDir\SQL-IDE\"
    Copy-Item $nativeDll "$distDir\SQL-IDE\app\"
}

Write-Host "`n[6/6] Creating distributable ZIP package..." -ForegroundColor Yellow
$finalDistDir = "$rootDir\dist"
if (-not (Test-Path $finalDistDir)) { New-Item -ItemType Directory -Path $finalDistDir | Out-Null }
$zipOutput = "$finalDistDir\SQL-IDE-1.0.0-windows-x64.zip"
if (Test-Path $zipOutput) { Remove-Item -Force $zipOutput }
Compress-Archive -Path "$distDir\SQL-IDE" -DestinationPath $zipOutput -CompressionLevel Optimal

Write-Host "`n======================================================================" -ForegroundColor Green
Write-Host "PACKAGING COMPLETE!" -ForegroundColor Green
Write-Host "Executable folder : $distDir\SQL-IDE\SQL-IDE.exe" -ForegroundColor White
Write-Host "Distributable ZIP : $zipOutput" -ForegroundColor White
Write-Host "======================================================================" -ForegroundColor Green

