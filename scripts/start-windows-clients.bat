@echo off
REM ############################################################################
REM Windows Script to Run Sensors and Clients
REM 
REM This script connects to the system running in WSL.
REM 
REM Usage:
REM   1. Start the system in WSL: ./system.sh
REM   2. Get WSL IP: hostname -I (or wsl hostname -I from Windows)
REM   3. Set the IP below or pass as argument
REM   4. Run this script from the project directory
REM ############################################################################

setlocal enabledelayedexpansion

REM Configuration - Set WSL_IP to your WSL instance IP
REM You can find it by running: wsl hostname -I
set WSL_IP=%1
if "%WSL_IP%"=="" (
    echo.
    echo ============================================================
    echo   SENSOR AND CLIENT LAUNCHER FOR WINDOWS
    echo ============================================================
    echo.
    echo Usage: start-windows-clients.bat ^<WSL_IP^>
    echo.
    echo Example: start-windows-clients.bat 172.25.123.45
    echo.
    echo To find WSL IP, run: wsl hostname -I
    echo.
    exit /b 1
)

set DISCOVERY_PORT=3040
set PROJECT_DIR=%~dp0..
set POM_FILE=%PROJECT_DIR%\core\pom.xml

echo.
echo ============================================================
echo   CONNECTING TO WSL AT %WSL_IP%:%DISCOVERY_PORT%
echo ============================================================
echo.

REM Menu
:menu
echo.
echo What would you like to run?
echo.
echo   1. Single Sensor (SENSOR_001)
echo   2. All 4 Sensors
echo   3. CLI Client
echo   4. Malicious Sensor (ANOMALY_DATA mode)
echo   5. Malicious Sensor (REPLAY mode)
echo   6. Malicious Sensor (FLOOD mode)
echo   7. Exit
echo.
set /p choice="Enter choice (1-7): "

if "%choice%"=="1" goto single_sensor
if "%choice%"=="2" goto all_sensors
if "%choice%"=="3" goto cli_client
if "%choice%"=="4" goto malicious_anomaly
if "%choice%"=="5" goto malicious_replay
if "%choice%"=="6" goto malicious_flood
if "%choice%"=="7" goto end
echo Invalid choice. Try again.
goto menu

:single_sensor
echo.
echo Starting SENSOR_001...
mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" -Dexec.args="SENSOR_001 senha123 %WSL_IP% %DISCOVERY_PORT%"
goto menu

:all_sensors
echo.
echo Starting all 4 sensors in separate windows...
start "SENSOR_001" cmd /c "mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" -Dexec.args="SENSOR_001 senha123 %WSL_IP% %DISCOVERY_PORT%" & pause"
timeout /t 2 >nul
start "SENSOR_002" cmd /c "mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" -Dexec.args="SENSOR_002 senha456 %WSL_IP% %DISCOVERY_PORT%" & pause"
timeout /t 2 >nul
start "SENSOR_003" cmd /c "mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" -Dexec.args="SENSOR_003 senha789 %WSL_IP% %DISCOVERY_PORT%" & pause"
timeout /t 2 >nul
start "SENSOR_004" cmd /c "mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.Sensor" -Dexec.args="SENSOR_004 senha321 %WSL_IP% %DISCOVERY_PORT%" & pause"
echo Sensors started in separate windows.
goto menu

:cli_client
echo.
echo Starting CLI Client...
mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.ClientApp" -Dexec.args="CLI_CLIENT %WSL_IP% %DISCOVERY_PORT%"
goto menu

:malicious_anomaly
echo.
echo Starting Malicious Sensor (ANOMALY_DATA mode)...
mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.MaliciousSensor" -Dexec.args="--mode ANOMALY_DATA --password sensor123 --host %WSL_IP% --port %DISCOVERY_PORT%"
goto menu

:malicious_replay
echo.
echo Starting Malicious Sensor (REPLAY mode)...
mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.MaliciousSensor" -Dexec.args="--mode REPLAY --password sensor123 --host %WSL_IP% --port %DISCOVERY_PORT%"
goto menu

:malicious_flood
echo.
echo Starting Malicious Sensor (FLOOD mode)...
mvn -f "%POM_FILE%" exec:java -Dexec.mainClass="com.project.client.sensor.MaliciousSensor" -Dexec.args="--mode FLOOD --password sensor123 --host %WSL_IP% --port %DISCOVERY_PORT%"
goto menu

:end
echo.
echo Goodbye!
exit /b 0
