#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$(realpath -m -- "${OPENMW_ANDROID_OUTPUT_DIR:-$SCRIPT_DIR/build-2-output}")"
KNOWN_GOOD_OUTPUT_DIR="$(realpath -m -- "$SCRIPT_DIR/full-build-output")"
APP_VERSION="${APP_VERSION:-Alpha}"

if [[ "$OUTPUT_DIR" == "$KNOWN_GOOD_OUTPUT_DIR" ]]; then
  echo "Refusing to overwrite the preserved Build #1 output directory." >&2
  exit 1
fi

if [[ ! "$APP_VERSION" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "APP_VERSION may contain only letters, numbers, dots, underscores, and hyphens." >&2
  exit 2
fi

command -v docker >/dev/null || {
  echo "Docker is required. Install and start Docker Engine with BuildKit enabled." >&2
  exit 1
}
for tool in sha256sum unzip awk; do
  command -v "$tool" >/dev/null || {
    echo "Required host tool not found: $tool" >&2
    exit 1
  }
done
docker info >/dev/null 2>&1 || {
  echo "Docker Engine is not reachable. Start Docker Engine and try again." >&2
  exit 1
}
docker buildx version >/dev/null 2>&1 || {
  echo "Docker Buildx is required for the local APK export." >&2
  exit 1
}

SOURCE_TREE_SHA256="$(
  cd "$SCRIPT_DIR"
  {
    sha256sum Dockerfile 3rdparty-licenses.txt README.md BUILD_HANDOFF.md
    find angle patches payload -type f \
      ! -path 'payload/.gradle/*' \
      ! -path 'payload/build/*' \
      ! -path 'payload/app/build/*' \
      ! -path 'payload/local.properties' \
      ! -path 'payload/release-keystore.properties' \
      ! -name '*.jks' ! -name '*.keystore' \
      ! -name '*.p12' ! -name '*.pfx' ! -name '*.pem' ! -name '*.key' \
      ! -name '*keystore.properties' ! -name 'key.properties' \
      ! -name '.env' ! -name '.env.*' \
      ! -path '*/.secure_files/*' \
      -print0 |
      LC_ALL=C sort -z |
      xargs -0 sha256sum
  } | sha256sum | cut -d ' ' -f 1
)"

mkdir -p "$OUTPUT_DIR"
STAGING_ROOT="$(mktemp -d "$OUTPUT_DIR/.staging.XXXXXX")"
STAGING_DIR="$STAGING_ROOT/export"
trap 'rm -rf -- "$STAGING_ROOT"' EXIT

docker buildx build \
  --progress=plain \
  --build-arg "APP_VERSION=$APP_VERSION" \
  --build-arg "SOURCE_TREE_SHA256=$SOURCE_TREE_SHA256" \
  --output "type=local,dest=$STAGING_DIR" \
  --tag openmw-android-complete \
  "$SCRIPT_DIR"

APK="$STAGING_DIR/openmw-android.apk"
MANIFEST="$STAGING_DIR/build-manifest.txt"

if [[ ! -s "$APK" || ! -s "$MANIFEST" ]]; then
  echo "Build finished without the APK and build manifest; refusing to report success." >&2
  exit 1
fi

unzip -t "$APK" >/dev/null
if ! unzip -Z1 "$APK" |
  awk '$0 == "lib/arm64-v8a/libopenmw.so" { found = 1 } END { exit !found }'; then
  echo "APK is missing the ARM64 OpenMW engine library." >&2
  exit 1
fi
if ! unzip -Z1 "$APK" |
  awk '$0 == "assets/libopenmw/resources/version" { found = 1 } END { exit !found }'; then
  echo "APK is missing the OpenMW game resource payload." >&2
  exit 1
fi

NATIVE_LIBRARY_COUNT="$(
  unzip -Z1 "$APK" |
    awk '$0 ~ /^lib\/arm64-v8a\/[^/]+\.so$/ { count++ } END { print count + 0 }'
)"
RESOURCE_FILE_COUNT="$(
  unzip -Z1 "$APK" |
    awk '$0 ~ /^assets\/libopenmw\/resources\// && $0 !~ /\/$/ { count++ } END { print count + 0 }'
)"
APK_SHA256="$(sha256sum "$APK" | cut -d ' ' -f 1)"

{
  printf 'APK: %s\n' "$(basename "$APK")"
  printf 'SHA-256: %s\n' "$APK_SHA256"
  printf 'ABI: arm64-v8a\n'
  printf 'ARM64 shared libraries: %s\n' "$NATIVE_LIBRARY_COUNT"
  printf 'OpenMW resource files: %s\n' "$RESOURCE_FILE_COUNT"
  printf 'Build manifest: %s\n' "$(basename "$MANIFEST")"
} | tee "$STAGING_DIR/build-result.txt"

mv -f -- "$MANIFEST" "$OUTPUT_DIR/build-manifest.txt"
mv -f -- "$STAGING_DIR/build-result.txt" "$OUTPUT_DIR/build-result.txt"
mv -f -- "$APK" "$OUTPUT_DIR/openmw-android.apk"

echo
echo "Complete APK: $OUTPUT_DIR/openmw-android.apk"
