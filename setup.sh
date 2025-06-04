#!/usr/bin/env bash

# Simple setup script for the Discord bot project
set -euo pipefail

# Ensure the Gradle wrapper is executable
chmod +x gradlew

# Verify Java version
if command -v java >/dev/null; then
  JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
  JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d. -f1)
  if [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "Java 17 or newer is required. Current version: $JAVA_VERSION"
    exit 1
  fi
else
  echo "Java is not installed. Please install JDK 17 or higher."
  exit 1
fi

# Build the project
./gradlew --no-daemon build

echo "Build finished. The resulting jar can be found in build/libs."

echo "To run the bot after building, execute:"
echo "  ./gradlew run --args \"<DISCORD_TOKEN> <YOUTUBE_API_KEY>\""

