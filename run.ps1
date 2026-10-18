# run.ps1 - PowerShell launcher script
param (
    [int]$Philosophers = 5
)

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  Running Dining Philosophers MAS ($Philosophers Philosophers)" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

java -cp "bin;lib/jade.jar" Main $Philosophers
