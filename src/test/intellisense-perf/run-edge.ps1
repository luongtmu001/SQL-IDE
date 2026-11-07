# Runs the benchmark pages in headless Edge (Chromium: what the Python app runs on), with a private profile.
# The server must be running: node src/test/intellisense-perf/server.js
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
$profile = Join-Path $env:TEMP 'sqlide-perf-edge-profile'
$base = 'http://127.0.0.1:8765'
$pages = @(
    @{ url = "$base/bench/engine.html?src=py&browser=edge"; name = 'engine-py-edge'; max = 300 },
    @{ url = "$base/bench/engine.html?src=java&browser=edge"; name = 'engine-java-edge'; max = 300 },
    @{ url = "$base/bench/editor?browser=edge"; name = 'editor-edge'; max = 600 },
    @{ url = "$base/bench/editor?browser=edge&mode=breakdown"; name = 'breakdown-edge'; max = 400 },
    @{ url = "$base/bench/editor?browser=edge&mode=focus"; name = 'focus-edge'; max = 200 }
)
$only = $args[0]   # optional: the name of one page
if ($only) { $pages = @($pages | Where-Object { $_.name -eq $only }) }
foreach ($p in $pages) {
    $edgeArgs = @('--headless=new', '--disable-gpu', '--no-first-run', '--disable-extensions',
        '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows',
        '--window-size=1100,620', "--user-data-dir=$profile", $p.url)
    $proc = Start-Process -FilePath $edge -ArgumentList $edgeArgs -PassThru
    $start = Get-Date
    $done = $false
    while (((Get-Date) - $start).TotalSeconds -lt $p.max) {
        Start-Sleep -Seconds 2
        $status = Invoke-RestMethod "$base/status"
        if ($status.($p.name)) { $done = $true; break }
    }
    Write-Output ("{0}: done={1} in {2:N0}s" -f $p.name, $done, ((Get-Date) - $start).TotalSeconds)
    taskkill /PID $proc.Id /T /F | Out-Null
    Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -like "*$profile*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}
