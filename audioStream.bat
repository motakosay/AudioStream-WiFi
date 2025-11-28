@echo off
REM 🎶 Ritual batch file to start your audio server

REM Ensure Python is available in PATH
where python nul 2nul
if %errorlevel% neq 0 (
    echo ❌ Python not found in PATH. Please install or add it.
    pause
    exit b
)

REM Navigate to the script directory
cd d Ewifi_phone_computer

REM Run the server script
echo 🌀 Launching server_sounddy.py — awaiting sonic pilgrims...
python server_sounddy.py

REM Keep window open after script ends
echo 🔚 Ritual complete. Press any key to close.
pause
