# Build notes

## Source boundary

This source snapshot contains the Android project, Docker build recipe,
patches, ANGLE shared libraries, and a prebuilt GlossHook static library.
The engine and other dependencies are downloaded during the Docker build.
No APK, signing key, local Android SDK configuration, or build cache belongs
in the source repository.

## Building and outputs

Run `bash build.sh` from the repository root. The default output directory is
`build-2-output/`. `OPENMW_ANDROID_OUTPUT_DIR` overrides that location; the
script refuses `full-build-output/` to protect a preserved reference build.

The Dockerfile builds native code with release optimizations, runs the
configured unit tests, and packages the Gradle `assembleDebug` variant. This
is not a release-signing workflow. Use a separate private signing setup for
future APK releases.

The host requires an x86_64 Linux environment, Docker Engine with Buildx,
network access, Bash, and the utilities checked by `build-apk.sh`. The image
provisions Java 21 and the Android/native toolchain. Launcher-only development
uses `setup-dev.sh` and the Gradle wrapper under `payload/`.

## Reproducibility and verification

The base image and principal Git inputs are pinned. Rust versions and the NDK
revision are specified in the Dockerfile. Each full build records resolved
system packages and dependency versions in `build-manifest.txt`, and APK
checksums and payload counts in `build-result.txt`.

External package repositories, Maven dependencies, and some downloaded
archives are not fully checksum-locked. The recipe is not a guarantee of
byte-identical output. Prebuilt ANGLE and GlossHook inputs need documented
origins and matching notices; see `CREDITS.md`.

The inherited Dockerfile records a historical snapshot commit. That record
is historical context and is not proof that a new APK corresponds to that
commit. The build script supplies a separate content fingerprint. Keep the
actual source revision and both output reports with each new APK release.

The pre-publication cleanup has not been rebuilt into an APK. Existing APKs
and checksums in the private release directories refer to earlier source,
which still contains the removed sensitive logging. Validate a fresh build
before publishing a corresponding APK.
