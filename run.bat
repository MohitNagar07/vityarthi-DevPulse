@echo off
setlocal enabledelayedexpansion

echo ===================================================================
echo   Compiling DevPulse: Server ^& API Health Monitoring CLI...
echo ===================================================================

if not exist "bin" mkdir "bin"
if not exist "data" mkdir "data"
if not exist "logs" mkdir "logs"

javac -d bin src/com/devpulse/model/*.java src/com/devpulse/repository/*.java src/com/devpulse/service/*.java src/com/devpulse/utils/*.java src/com/devpulse/main/*.java

if errorlevel 1 (
    echo.
    echo [ERROR] Compilation failed. Please check your JDK installation.
    pause
    exit /b 1
)

echo [SUCCESS] Compilation successful! Launching DevPulse CLI...
echo.

java -cp bin com.devpulse.main.DevPulseApp

pause
