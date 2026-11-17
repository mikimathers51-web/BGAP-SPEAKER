#!/bin/sh
# Minimal bootstrap – CI will use gradle/actions which can generate full wrapper
set -e
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "Downloading gradle wrapper jar..."
  curl -sL -o gradle/wrapper/gradle-wrapper.jar \
    https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar || true
fi
exec java -jar gradle/wrapper/gradle-wrapper.jar "$@"
