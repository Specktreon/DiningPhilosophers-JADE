# compile.ps1 - PowerShell compilation script
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  Compiling Dining Philosophers MAS in JADE" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

javac -encoding UTF-8 -cp "lib/jade.jar" -d bin src/*.java

if ($LASTEXITCODE -eq 0) {
    Write-Host "[SUCCESS] Compilation completed successfully into bin/ directory." -ForegroundColor Green
} else {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
}
