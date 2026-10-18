@echo off
set PHILOSOPHERS=5
if not "%~1"=="" set PHILOSOPHERS=%~1

echo ===================================================
echo   Running Dining Philosophers MAS (%PHILOSOPHERS% Philosophers)
echo ===================================================

java -cp "bin;lib/jade.jar" Main %PHILOSOPHERS%
