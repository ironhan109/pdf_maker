#!/bin/sh

# Setup standard gradlew script
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
exec "${APP_HOME}/gradle/wrapper/gradlew" "$@" 2>/dev/null || {
    which gradle >/dev/null 2>&1 && exec gradle "$@"
    echo "Gradle wrapper or system gradle required to build from command line."
    echo "Please open this project in Android Studio or install Gradle."
    exit 1
}
