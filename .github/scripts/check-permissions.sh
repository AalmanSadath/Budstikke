#!/usr/bin/env bash
# Fails when the APK asks for any permission that lets it reach the network.
# The app's promise is that messages never leave the phone through it, and
# Android enforces that only while the manifest has no INTERNET permission, so
# a dependency that merges one in must stop the build rather than ship.
set -euo pipefail

apk=${1:?usage: check-permissions.sh <apk>}
tools=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
permissions=$("$tools/aapt2" dump permissions "$apk")
echo "$permissions"

forbidden='android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE)'
if found=$(echo "$permissions" | grep -oE "$forbidden"); then
  echo "::error::$apk asks for network permissions: $(echo "$found" | sort -u | tr '\n' ' ')"
  exit 1
fi
echo "No network permissions."
