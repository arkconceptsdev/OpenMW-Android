#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$ROOT/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${WORKSPACE_ROOT}/.cache/android-sdk}"

if ! command -v sdkmanager >/dev/null 2>&1; then
  echo "sdkmanager is required. Install the Android SDK command-line tools first." >&2
  exit 1
fi

mkdir -p "$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

sdkmanager --install \
  platform-tools \
  'platforms;android-36' \
  'build-tools;35.0.0' \
  'cmake;3.22.1' \
  'ndk;29.0.14206865'

printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT/payload/local.properties"
echo "Android SDK configured at $SDK_ROOT"