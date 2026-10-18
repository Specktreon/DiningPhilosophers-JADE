@echo off
echo ===================================================
echo   Compiling Dining Philosophers MAS in JADE
echo ===================================================

if not exist bin mkdir bin

javac -encoding UTF-8 -cp "lib/jade.jar" -d bin src\*.java

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Compilation completed successfully into bin/ directory.
) else (
    echo [ERROR] Compilation failed. Please check javac and lib/jade.jar.
)
