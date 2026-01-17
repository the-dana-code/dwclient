#!/bin/bash

# Change directory to the location of the script
cd "$(dirname "$0")"

JAR_FILE="DWClient.jar"
FALLBACK_JAR="target/mud-client-1.0.0-shaded.jar"

if [ ! -f "$JAR_FILE" ] && [ ! -f "$FALLBACK_JAR" ]; then
    echo "Shaded JAR not found."
    echo "Attempting to build with Maven..."
    if command -v mvn &> /dev/null; then
        mvn clean package
    else
        echo "Maven (mvn) not found. Please install Maven or ensure the JAR is built."
        read -p "Press enter to exit..."
        exit 1
    fi
fi

if [ -f "$JAR_FILE" ]; then
    echo "Launching DWClient..."
    java -jar "$JAR_FILE"
elif [ -f "$FALLBACK_JAR" ]; then
    echo "Launching DWClient from target..."
    java -jar "$FALLBACK_JAR"
else
    echo "Error: Could not find or build JAR file."
    read -p "Press enter to exit..."
fi
