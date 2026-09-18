# DevPulse PowerShell Compile and Launch Script

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  Compiling DevPulse: Server & API Health Monitoring CLI..." -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan

# Ensure necessary directories exist
@("bin", "data", "logs") | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ | Out-Null
    }
}

# Find and compile all Java source files
$javaFiles = Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName

if ($javaFiles.Count -eq 0) {
    Write-Host "[ERROR] No Java source files found in src/" -ForegroundColor Red
    exit
}

javac -d bin $javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[ERROR] Compilation failed." -ForegroundColor Red
    exit 1
}

Write-Host "[SUCCESS] Compilation successful! Launching DevPulse CLI...`n" -ForegroundColor Green

# Pass any extra command line arguments directly to DevPulseApp
java -cp bin com.devpulse.main.DevPulseApp $args
