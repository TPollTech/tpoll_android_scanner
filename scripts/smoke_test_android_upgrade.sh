#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE_NAME="com.tpoll.scanner"
readonly PREVIOUS_APK="previous-release.apk"
readonly DATA_DIR="/data/user/0/$PACKAGE_NAME"
readonly PRESERVATION_MARKER="tpoll-upgrade-preserved-2026"
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

previous_launch=$(adb shell am start -W -n "$PACKAGE_NAME/.MainActivity")
printf '%s\n' "$previous_launch"
grep -q 'Status: ok' <<<"$previous_launch"
adb shell am force-stop "$PACKAGE_NAME"

adb root
adb wait-for-device
app_uid=$(adb shell stat -c %u "$DATA_DIR" | tr -d '\r')
[[ "$app_uid" =~ ^[0-9]+$ ]] || {
  echo "Could not determine the installed app UID."
  exit 1
}
adb shell mkdir -p "$DATA_DIR/shared_prefs" "$DATA_DIR/files"
adb push scripts/fixtures/update_prefs.xml /data/local/tmp/tpoll-update-prefs.xml
adb push scripts/fixtures/upgrade-preservation-marker.txt /data/local/tmp/tpoll-upgrade-marker.txt
adb shell cp /data/local/tmp/tpoll-update-prefs.xml "$DATA_DIR/shared_prefs/update_prefs.xml"
adb shell cp /data/local/tmp/tpoll-upgrade-marker.txt "$DATA_DIR/files/upgrade-preservation-marker.txt"
adb shell chown "$app_uid:$app_uid" \
  "$DATA_DIR/shared_prefs/update_prefs.xml" \
  "$DATA_DIR/files/upgrade-preservation-marker.txt"
adb shell chmod 600 \
  "$DATA_DIR/shared_prefs/update_prefs.xml" \
  "$DATA_DIR/files/upgrade-preservation-marker.txt"

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

adb shell grep -q "$PRESERVATION_MARKER" \
  "$DATA_DIR/files/upgrade-preservation-marker.txt"
adb shell grep -q 'name="automatic_updates_enabled" value="false"' \
  "$DATA_DIR/shared_prefs/update_prefs.xml"
adb shell grep -q "$PRESERVATION_MARKER" \
  "$DATA_DIR/shared_prefs/update_prefs.xml"

launch_output=$(adb shell am start -W -n "$PACKAGE_NAME/.MainActivity")
printf '%s\n' "$launch_output"
grep -q 'Status: ok' <<<"$launch_output"
adb shell am force-stop "$PACKAGE_NAME"
adb shell grep -q 'name="automatic_updates_enabled" value="false"' \
  "$DATA_DIR/shared_prefs/update_prefs.xml"
echo "Signed in-place upgrade from $previous_version to $installed_version preserved app data and settings."
