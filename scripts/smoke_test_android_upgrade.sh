#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE_NAME="com.tpoll.scanner"
readonly PREVIOUS_APK="previous-release.apk"
: "${RELEASE_APK:?RELEASE_APK must point to the versioned release APK}"
readonly RELEASE_APK

read_installed_version() {
  local versions
  versions=$(adb shell dumpsys package "$PACKAGE_NAME" |
    sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p')
  printf '%s' "${versions%%$'\n'*}"
}

adb install "$PREVIOUS_APK"
previous_version=$(read_installed_version)
[[ "$previous_version" =~ ^[0-9]+$ ]] || {
  echo "Could not read previous versionCode."
  exit 1
}

adb install -r "$RELEASE_APK"
installed_version=$(read_installed_version)
[[ "$installed_version" =~ ^[0-9]+$ ]] || {
  echo "Could not read installed versionCode."
  exit 1
}

(( previous_version < installed_version )) || {
  echo "Upgrade did not increase versionCode."
  exit 1
}
[[ "$installed_version" == "$EXPECTED_VERSION_CODE" ]] || {
  echo "Installed versionCode is $installed_version; expected $EXPECTED_VERSION_CODE."
  exit 1
}

launch_output=$(adb shell am start -W -n "$PACKAGE_NAME/.MainActivity")
printf '%s\n' "$launch_output"
grep -q 'Status: ok' <<<"$launch_output"
echo "Signed in-place upgrade from $previous_version to $installed_version and app launch verified."
