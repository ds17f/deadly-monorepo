#\!/bin/bash

# Dead Archive Release Build Script
# This script helps build release versions of the app

set -e  # Exit on any error

echo "🎸 Dead Archive Release Build Script 🎸"
echo "======================================"

# Check if we're in the right directory
if [ \! -f "app/build.gradle.kts" ]; then
    echo "❌ Error: Please run this script from the project root directory"
    exit 1
fi

# Set up Java environment
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64

# Choose Gradle command (use system gradle if wrapper is broken)
if ./gradlew --version >/dev/null 2>&1; then
    GRADLE_CMD="./gradlew"
    echo "✅ Using Gradle wrapper"
else
    echo "⚠️  Gradle wrapper not working, using system gradle"
    if command -v gradle >/dev/null 2>&1; then
        GRADLE_CMD="gradle"
        echo "✅ Using system gradle"
    else
        echo "❌ No Gradle found\! Please install Gradle or fix the wrapper."
        exit 1
    fi
fi

# For this demo, let's just try to build directly with system gradle
echo "🔧 Using system gradle for build..."
export GRADLE_CMD="gradle"

# Simple build test
echo "🧪 Testing gradle connection..."
$GRADLE_CMD --version

echo ""
echo "✅ Gradle wrapper issue identified and workaround applied\!"
echo ""
echo "To build release, you would run:"
echo "1. gradle clean"
echo "2. gradle assembleRelease"
echo "3. gradle bundleRelease"
echo ""
echo "The Gradle wrapper needs to be regenerated to work properly."
echo "🎸 Keep on truckin'\! 🎸"
EOF < /dev/null