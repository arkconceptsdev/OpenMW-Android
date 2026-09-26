# Credits and provenance

Ark Concepts assembled this personal Android build to play Morrowind on their
tablet. The changes address issues encountered in that setup; they do not
represent a claim that all upstream problems have been fixed or a commitment
to maintain a general-purpose replacement for the upstream project.

Ark Concepts' contributions include original launcher artwork, a proper app
icon and the OpenMW app name, a simplified launcher, changing the shared
storage root from `Alpha3` to `OpenMW_Android`, and a game-file selection flow
that leads directly to PLAY once valid files are configured. Project and
build fixes were made in support of that personal use.

Additional contributions reported by Ark Concepts include Force SDL Mouse
Emulation (working in their tablet testing), experimental Tap to Activate
(partially working), controller configuration export/import in Settings, and
support for adding any virtual key to the touch layout multiple times.

## Upstream lineage

The maintainer identifies
[Cavebros / Modding-OpenMW's Android port](https://gitlab.com/modding-openmw/openmw-android-docker)
as the likely source they started from. File comparisons strongly support
that identification. The [upstream README](https://gitlab.com/modding-openmw/openmw-android-docker/-/raw/main/README.md)
refers to Cavebros and points to [Duron27's Alpha3 project](https://gitlab.com/duron27/alpha3)
as its successor.

The following inherited files match release **2.7.4**, commit
`5b02e847dc646c9f10cd66001e4d65c5274dde49`, byte-for-byte by SHA-256:

- `payload/app/build.gradle.kts`
- `payload/app/ui/backgroundbouncebw.jpg`
- All four `angle/*.so` libraries
- `payload/app/src/main/cpp/Alpha3/deps/memory/lib/arm64-v8a/libGlossHook.a`
- `LICENSE.txt` and `3rdparty-licenses.txt`

`LauncherScreen.kt`, `LauncherViewModel.kt`, and `openmw_launcher_art.png`
are absent at their current paths in that release. This supports documenting
the new launcher separately from the inherited port. The comparison establishes
the source of the checked files; it is not a complete diff or proof of the
exact original checkout used for all files.

The project builds on OpenMW, SDL, OpenSceneGraph, and the other projects
listed in `Dockerfile` and the bundled third-party notices. Contributor
credits and copyright notices in imported source and patches are retained.

## Artwork

- `payload/app/src/main/res/drawable/openmw_launcher_art.png`: original
  launcher artwork by Ark Concepts, as confirmed by the maintainer.
- `backgroundbouncebw.jpg`, in both `payload/app/ui/` and Android drawable
  resources: inherited scrolling OpenMW background. The UI copy exactly matches
  upstream release 2.7.4. Its original artist and asset-specific terms have not
  yet been identified.
- `starmap.jpg`, `dreadnought_big_004.png`, and inherited icons: retain the
  existing assets while checking their origin against applicable notices.
  Do not infer ownership from the new launcher artwork.

## Prebuilt inputs

| Input | Verified origin and remaining record |
| --- | --- |
| `angle/*.so` | Exact match to Android port release 2.7.4; original ANGLE build revision and matching component notices remain undocumented |
| `payload/app/src/main/cpp/Alpha3/deps/memory/lib/arm64-v8a/libGlossHook.a` | Exact match to Android port release 2.7.4; original GlossHook build version and matching component notice remain undocumented |

These libraries are required build inputs. Their immediate distribution source
is now established. The comparison does not supply missing component notices
or establish original authorship of inherited artwork. Those component notices
and artwork attribution remain open documentation items.

[XMDS/GlossHook](https://github.com/XMDS/GlossHook) is a possible original source
for the static library and identifies its current repository license as MIT.
The binary matches the Android port's 2.7.4 distribution, but has not been
matched to a specific XMDS/GlossHook build.

## Existing notices

- `LICENSE.txt`
- `3rdparty-licenses.txt`
- `payload/3rdparty-licenses.txt`
- `payload/app/src/main/cpp/Alpha3/deps/spdlog/LICENSE`
- Dependency-specific notices embedded in source files
