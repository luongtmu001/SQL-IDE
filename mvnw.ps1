param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)
$mvn = "$env:LOCALAPPDATA\Programs\apache-maven-3.9.16\bin\mvn.cmd"
if (Test-Path $mvn) {
    & $mvn @MavenArgs
} else {
    & mvn @MavenArgs
}

