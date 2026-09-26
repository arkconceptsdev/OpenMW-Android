# Controller configuration transfer notes

The Settings transfer actions are shown only for the OpenMW game group. They use the Android document picker and do not add shared-storage permissions.

## Existing files and code paths

- Engine bindings: `$USER_CONFIG/input_v3.xml`. `Constants.USER_CONFIG` points to `/storage/emulated/0/OpenMW_Android/config`. `ManageAssets` seeds the file from the bundled `libopenmw/ui/input_v3.xml`; OpenMW reads it when the engine starts.
- On-screen layout: `/storage/emulated/0/OpenMW_Android/OpenMW/ui/UI.cfg`. `UIStateManager.saveButtonState` writes it and `UIStateManager.loadButtonState` reads it.
- Button presets: `/storage/emulated/0/OpenMW_Android/OpenMW/ui/button_configs.json`. `ButtonConfigManager` reads and writes this file.
- Custom button art: PNG/GIF files stored beside those UI files and referenced by the layout or button presets.

## Transfer archive

The version 1 ZIP keeps the original file formats and contains `config/input_v3.xml`, optional `ui/UI.cfg` and `ui/button_configs.json`, plus referenced `ui/images/*` assets. `manifest.txt` identifies the archive format. Import rejects unknown paths, duplicates, malformed XML/JSON/layout data, missing referenced images, and archives over the configured size limits. Imported image URIs in button presets are rebound to the destination UI folder so bundles remain usable across different storage paths.

The archive is extracted and validated in the app cache before any live file changes. Import stages replacements beside their destinations and rolls back earlier replacements if a file operation fails. A successful import requires restarting OpenMW because the engine and control UI load these files at startup.

## Settings and APK build

The Settings `ControlsMenu` expands `ControlsInsert`, which launches `ControllerConfigTransfer` through Android's `CreateDocument` and `OpenDocument` contracts. The transfer helper targets the OpenMW paths above; other game groups retain their existing Configure/Reset Controls actions without showing these OpenMW-only transfer buttons.

`build-apk.sh` defaults to `openmw-android/build-2-output` and refuses the preserved `full-build-output` directory. The Docker build runs the archive unit tests before assembling the debug APK.