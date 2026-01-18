#!/bin/bash

# Change directory to the location of the script
cd "$(dirname "$0")"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Java is not installed or not in your PATH."
    echo "Please install Java 25 or later to run dwclient."
    read -p "Press enter to exit..."
    exit 1
fi

JAR_FILE="dwclient.jar"
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
    echo "Launching dwclient..."
    java -jar "$JAR_FILE" "./config.json"
elif [ -f "$FALLBACK_JAR" ]; then
    echo "Launching dwclient from target..."
    java -jar "$FALLBACK_JAR" "./config.json"
else
    echo "Error: Could not find or build JAR file."
    read -p "Press enter to exit..."
fi
