# OpenMW for Android

A personal ARM64 Android build by Ark Concepts, based on
[Cavebros / Modding-OpenMW's Android port](https://gitlab.com/modding-openmw/openmw-android-docker).

I wanted to play Morrowind on my tablet. The APK and source available to me
didn't work for my setup, so I worked through the issues that were getting in
the way and made this version playable for me. I'm sharing the source in case
those changes are useful to someone else.

The scope is the problems I encountered on my own tablet. Other bugs may
remain, and compatibility with other devices will vary. This is a personal
build, with no commitment to ongoing maintenance or support, and is independent
of the official OpenMW project.

## What I changed

- Added a proper app icon and named the app **OpenMW**.
- Created new launcher artwork and simplified the launcher screen.
- Changed the app's shared storage root from `Alpha3` to `OpenMW_Android`
  so its files are easier to identify.
- Added a setup flow that asks you to select your game files when none are
  configured. Once valid files are selected, the launcher presents **PLAY**.
- Added **Force SDL Mouse Emulation**, which works in my tablet testing.
- Added **Tap to Activate** for touch interaction. This is experimental and
  only partially working in my testing.
- Added **Export Controller Config** and **Import Controller Config** in
  Settings. Restart OpenMW after importing to apply the configuration.
- Added support for any virtual key in the touch layout, including multiple
  instances of the same key.
- Worked through the build and usability issues that prevented me from
  playing on my tablet.

The engine, Android port, and existing integrations come from upstream
contributors. Selected inherited files match upstream release 2.7.4 exactly.
See [Credits](CREDITS.md) for the source lineage and attribution.

This repository contains the Android launcher, integration code, patches,
and some prebuilt dependency inputs. The Docker build fetches the OpenMW
engine and other native dependencies.

## Source release status

This is a source-only publication. APKs are managed separately and are not
included here. The bundled dependency and
inherited artwork provenance still needs completion; see [Credits](CREDITS.md).
Morrowind game data is not included. Supply your own game installation data.

## Build the complete APK

Use an x86_64 Linux environment with Docker Engine, Docker Buildx/BuildKit,
Bash, `realpath`, `sha256sum`, `unzip`, and standard Unix utilities. The build
requires network access and substantial disk, memory, and CPU resources.

From the repository root:

```bash
bash build.sh
```

Outputs are written to `build-2-output/`:

- `openmw-android.apk`: debug APK for `arm64-v8a`.
- `build-manifest.txt`: dependency revisions and resolved system packages.
- `build-result.txt`: APK SHA-256 and native/resource payload counts.

The script verifies that the APK contains both the OpenMW native library and
engine resources. It preserves `full-build-output/` if that directory exists.
To choose a different output location and resource version prefix:

```bash
APP_VERSION=1.0 OPENMW_ANDROID_OUTPUT_DIR="$PWD/build-local-output" bash build.sh
```

The default container build packages a **debug APK**, not a release-signed APK.
Signing keys and signing properties must remain private. The app declares
minSdk 24, while native engine code targets API 26; compatibility with API
24/25 has not been established by this publication review.

## Launcher development

With Java 21, Android command-line tools, and `sdkmanager` available:

```bash
bash setup-dev.sh
cd payload
bash gradlew assembleDebug
```

This builds the launcher only unless native libraries and engine resources
have already been generated. Use the full Docker build for a complete APK.
The recipe specifies NDK `29.0.14206865`, CMake `3.22.1`, and compile/target
SDK 36. See [Build notes](BUILD_HANDOFF.md) for limitations.

## Project layout

| Path | Contents |
| --- | --- |
| `payload/` | Android Gradle project, launcher, and native integration |
| `patches/` | Engine and dependency changes applied during the build |
| `angle/` | Prebuilt ANGLE graphics libraries |
| `Dockerfile` | Native toolchain, dependency build, and APK packaging |
| `build.sh`, `build-apk.sh` | Full-build entry point and output validation |

## Documentation and contributions

- [UI architecture](UI_ARCHITECTURE.md)
- [Controller configuration transfer](CONTROLLER_CONFIG_NOTES.md)
- [Contributing](CONTRIBUTING.md)
- [Credits and dependency provenance](CREDITS.md)

When reporting problems, include the device, Android version, build version,
steps to reproduce, and sanitized logs. Remove API keys, cookies, account
information, and personal file paths before sharing diagnostics.

## License and attribution

The inherited source snapshot includes [GNU GPL version 3](LICENSE.txt),
[third-party notices](3rdparty-licenses.txt), and dependency-specific notices.
Preserve these notices when redistributing the source. Bundled assets and
third-party components have their own provenance and licensing requirements;
the included GPL text does not establish rights to every inherited asset.
