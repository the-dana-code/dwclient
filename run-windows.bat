@echo off
setlocal
cd /d "%~dp0"

REM Check if Java is installed
java -version >nul 2>&1
if errorlevel 1 (
    echo Java is not installed or not in your PATH.
    echo Please install Java 25 or later to run dwclient.
    pause
    exit /b 1
)

REM Let's find the JAR
set JAR_FILE=dwclient.jar
set FALLBACK_JAR=target\mud-client-1.0.0-shaded.jar

if not exist "%JAR_FILE%" if not exist "%FALLBACK_JAR%" (
    echo Shaded JAR not found.
    echo Attempting to build with Maven...
    call mvn clean package
    if errorlevel 1 (
        echo Failed to build the project. Please ensure Maven is installed and in your PATH.
        pause
        exit /b 1
    )
)

if exist "%JAR_FILE%" (
    echo Launching dwclient...
    java -jar "%JAR_FILE%" "./config.json"
) else if exist "%FALLBACK_JAR%" (
    echo Launching dwclient from target...
    java -jar "%FALLBACK_JAR%" "./config.json"
) else (
    echo Error: Could not find or build JAR file.
)

pause
