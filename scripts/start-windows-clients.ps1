#!/usr/bin/env pwsh
# ############################################################################
# Windows PowerShell Script to Run Sensors and Clients
# 
# This script connects to the system running in WSL.
# 
# Usage:
#   1. Start the system in WSL: ./system.sh -s
#   2. Get WSL IP: wsl hostname -I
#   3. Run this script with the IP as argument
#
# Examples:
#   .\start-windows-clients.ps1 172.25.123.45
#   .\scripts\start-windows-clients.ps1 172.25.123.45
# ############################################################################

param(
    [Parameter(Position=0)]
    [string]$WSL_IP
)

# Configuration
$DISCOVERY_PORT = 3040
$SCRIPT_DIR = $PSScriptRoot
$PROJECT_DIR = Split-Path -Parent $SCRIPT_DIR
$POM_FILE = Join-Path $PROJECT_DIR "core\pom.xml"

# Colors for output
function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host ""
}

function Write-Success {
    param([string]$Text)
    Write-Host $Text -ForegroundColor Green
}

function Write-Info {
    param([string]$Text)
    Write-Host $Text -ForegroundColor Yellow
}

# Validate WSL_IP argument
if ([string]::IsNullOrWhiteSpace($WSL_IP)) {
    Write-Header "SENSOR AND CLIENT LAUNCHER FOR WINDOWS"
    Write-Host "Usage: .\start-windows-clients.ps1 <WSL_IP>" -ForegroundColor White
    Write-Host ""
    Write-Host "Example: .\start-windows-clients.ps1 172.25.123.45" -ForegroundColor Gray
    Write-Host ""
    Write-Host "To find WSL IP, run: " -NoNewline
    Write-Host "wsl hostname -I" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# Verify POM file exists
if (-not (Test-Path $POM_FILE)) {
    Write-Host "Error: POM file not found at $POM_FILE" -ForegroundColor Red
    Write-Host "Make sure you're running this from the project directory." -ForegroundColor Red
    exit 1
}

Write-Header "CONNECTING TO WSL AT ${WSL_IP}:${DISCOVERY_PORT}"

# Menu functions
function Start-SingleSensor {
    Write-Info "Starting SENSOR_001..."
    mvn -f $POM_FILE exec:java "-Dexec.mainClass=com.project.client.sensor.Sensor" "-Dexec.args=SENSOR_001 senha123 $WSL_IP $DISCOVERY_PORT"
}

function Start-AllSensors {
    Write-Info "Starting all 4 sensors in separate windows..."
    
    $sensors = @(
        @{ Id = "SENSOR_001"; Password = "senha123" },
        @{ Id = "SENSOR_002"; Password = "senha456" },
        @{ Id = "SENSOR_003"; Password = "senha789" },
        @{ Id = "SENSOR_004"; Password = "senha321" }
    )
    
    foreach ($sensor in $sensors) {
        $sensorId = $sensor.Id
        $password = $sensor.Password
        $command = "mvn -f '$POM_FILE' exec:java '-Dexec.mainClass=com.project.client.sensor.Sensor' '-Dexec.args=$sensorId $password $WSL_IP $DISCOVERY_PORT'; Read-Host 'Press Enter to close'"
        
        Start-Process pwsh -ArgumentList "-NoExit", "-Command", $command
        Write-Success "  Started $sensorId"
        Start-Sleep -Seconds 2
    }
    
    Write-Success "All sensors started in separate windows."
}

function Start-CliClient {
    Write-Info "Starting CLI Client..."
    mvn -f $POM_FILE exec:java "-Dexec.mainClass=com.project.client.ClientApp" "-Dexec.args=CLI_CLIENT $WSL_IP $DISCOVERY_PORT"
}

function Start-MaliciousSensor {
    param([string]$Mode)
    Write-Info "Starting Malicious Sensor ($Mode mode)..."
    mvn -f $POM_FILE exec:java "-Dexec.mainClass=com.project.client.sensor.MaliciousSensor" "-Dexec.args=--mode $Mode --password sensor123 --host $WSL_IP --port $DISCOVERY_PORT"
}

# Main menu loop
function Show-Menu {
    Write-Host ""
    Write-Host "What would you like to run?" -ForegroundColor White
    Write-Host ""
    Write-Host "  1. Single Sensor (SENSOR_001)"
    Write-Host "  2. All 4 Sensors"
    Write-Host "  3. CLI Client"
    Write-Host "  4. Malicious Sensor (ANOMALY_DATA mode)"
    Write-Host "  5. Malicious Sensor (REPLAY mode)"
    Write-Host "  6. Malicious Sensor (FLOOD mode)"
    Write-Host "  7. Exit"
    Write-Host ""
}

# Main loop
while ($true) {
    Show-Menu
    $choice = Read-Host "Enter choice (1-7)"
    
    switch ($choice) {
        "1" { Start-SingleSensor }
        "2" { Start-AllSensors }
        "3" { Start-CliClient }
        "4" { Start-MaliciousSensor -Mode "ANOMALY_DATA" }
        "5" { Start-MaliciousSensor -Mode "REPLAY" }
        "6" { Start-MaliciousSensor -Mode "FLOOD" }
        "7" { 
            Write-Host ""
            Write-Host "Goodbye!" -ForegroundColor Cyan
            exit 0
        }
        default {
            Write-Host "Invalid choice. Try again." -ForegroundColor Red
        }
    }
}
