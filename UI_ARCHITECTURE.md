# OpenMW Android UI Architecture

This project has two separate UI systems. The Android project owns the
launcher and the touch/controller overlay. The OpenMW engine owns the actual
in-game HUD and menus. The engine source is fetched by `Dockerfile` during the
native build, so the upstream paths below are not checked-in workspace files.

## Build and source boundary

`openmw-android/Dockerfile` pins OpenMW to
`dbbd9456e8d3d643ec41bb1331ef4b607db20a04`, applies the Android patches, and
builds the engine for `arm64-v8a`. It copies the engine's resources and the
checked-in `payload/app/ui` files into `assets/libopenmw`, then packages the
full APK. A local Gradle build produces only the launcher/native project
outputs and is not a complete game package.

The Docker build applies these UI/input-relevant changes:

- `patches/openmw/androidmain.cpp` adds the SDL/JNI bridge for mouse position,
  cursor visibility, relative mouse motion, mouse buttons, the Android virtual
  controller mapping, and surface lifecycle callbacks.
- `patches/openmw/0009-windowmanagerimp-always-show-mouse-when-possible-pat.patch`
  changes `MWGui::WindowManager::getCursorVisible()` so the cursor remains
  available outside controller-menu mode.
- `patches/openmw/base-changes.patch` exposes the OpenSceneGraph viewer to the
  Android surface lifecycle and adjusts the postprocessor HUD layout.
- `Dockerfile` changes `files/data/mygui/openmw_postprocessor_hud.layout` at
  build time and copies the engine UI resources into the APK.

## In-game engine UI

The engine UI is implemented in the pinned OpenMW source under
`apps/openmw/mwgui`. `MWGui::WindowManager` in
`windowmanagerimp.hpp/.cpp` is the central owner and coordinator. It creates
the major windows, tracks the GUI mode stack, manages focus/cursor state,
updates the HUD, routes input to MyGUI, and resizes/repositions tracked
windows. It also owns the OpenMW GUI scaling path around
`MyGUI::RenderManager::getInstance().getViewSize()` and the engine window
manager scaling factor.

### Major screens and ownership

| User-facing area | Engine classes/files | Main MyGUI layout/resource |
| --- | --- | --- |
| HUD, health/magicka/fatigue, crosshair, weapon/spell/status indicators | `MWGui::HUD`, `hud.hpp/.cpp`; `MWGui::PostProcessorHud`, `postprocessorhud.hpp/.cpp` | `openmw_hud.layout`, `openmw_hud_box.skin.xml`, `openmw_hud_energybar.skin.xml`, `openmw_postprocessor_hud.layout` |
| Inventory and equipment | `MWGui::InventoryWindow`, `inventorywindow.hpp/.cpp`; `InventoryItemModel`; `ItemView`, `ItemWidget`; `InventoryTabsOverlay` | `openmw_inventory_window.layout`, `openmw_inventory_tabs.layout` |
| Containers and world item transfers | `MWGui::ContainerWindow`, `container.hpp/.cpp`; `ContainerItemModel`; `WorldItemModel`; `ItemTransfer` | `openmw_container_window.layout`, `openmw_count_window.layout`, item selection layouts |
| Dialogue and persuasion | `MWGui::DialogueWindow`, `dialogue.hpp/.cpp`; `PersuasionDialog` and related message/choice widgets | `openmw_dialogue_window.layout`, `openmw_persuasion_dialog.layout` |
| Character creation | `MWGui::CharacterCreation`, `BirthDialog`, `RaceDialog`, `ClassChoiceDialog`, `ReviewDialog`, and their source files | `openmw_chargen_*.layout`, `openmw_infobox.layout` |
| Character stats and level-up | `MWGui::StatsWindow`, `statswindow.hpp/.cpp`; `StatsWatcher`; `LevelupDialog` | `openmw_stats_window.layout`, `openmw_levelup_dialog.layout` |
| Journal and quest history | `MWGui::JournalWindow`, `journalwindow.hpp/.cpp`; `JournalViewModel` and journal books | `openmw_journal.layout`, `openmw_journal.skin.xml` |
| World/local map | `MWGui::MapWindow`, `mapwindow.hpp/.cpp` | `openmw_map_window.layout`, `openmw_map_window.skin.xml` |
| Main menu and save/load flows | `MWGui::MainMenu`, `mainmenu.hpp/.cpp`; save/load dialogs | `openmw_mainmenu.layout`, `openmw_savegame_dialog.layout` |
| Quick keys, spells, and item selection | `MWGui::QuickKeysMenu`, `quickkeysmenu.hpp/.cpp`; spell/item models | `openmw_quickkeys_menu.layout`, assignment and magic-selection layouts |
| Console/debug and text entry | `MWGui::Console`, `console.hpp/.cpp`; `TextInputDialog`, `DebugWindow` | `openmw_console.layout`, `openmw_text_input.layout`, `openmw_debug_window.layout` |
| Travel, waiting, trading, repair, alchemy, enchanting, spells, training | The corresponding `MWGui` window/dialog classes in `mwgui` | The matching `openmw_*.layout` files in `files/data/mygui` |

### Shared engine UI infrastructure

- `MWGui::Layout` (`layout.hpp/.cpp`) loads a MyGUI layout and provides
  coordinate, visibility, text, title, and widget lookup helpers.
- `MWGui::WindowBase` and `WindowPinnableBase` provide shared window behavior.
- `MWGui::KeyboardNavigation` owns controller/keyboard focus traversal and
  activation for menu widgets.
- `MWGui::ControllerButtonsOverlay` adds controller button hints to menus.
- `MWGui::Cursor` and `ResourceImageSetPointerFix` provide cursor assets and
  hotspot/position behavior.
- `MWGui::InventoryTabsOverlay`, `InventoryItemModel`, `ItemView`, and
  `ItemWidget` form the reusable inventory/item interaction layer.
- `files/data/mygui/skins.xml`, `openmw_windows.skin.xml`, `core.xml`,
  `openmw_resources.xml`, `openmw_pointer.xml`, and the individual `.skin.xml`
  files define the shared skins, fonts, pointers, resource aliases, and
  widget appearance. Layouts use pixel-like MyGUI coordinates relative to the
  current view size; they are not Android Compose layouts.

## Android launcher UI

The launcher starts at `MainActivity`. After permissions and asset/config
initialization, it sets `OpenMWTheme`, observes the selected game code group,
updates the configured resolution, and renders `RootNav`.

`RootNav` owns the root navigation host. Its primary route is now the minimal
launcher in `ui/launcher/LauncherScreen.kt`; the existing `SettingPage` is
available on the secondary Settings route. The older `MainScreen`,
`MainPageNav`, and `MainPage` remain in the project as advanced/legacy launcher
surfaces, but they are no longer the default landing screen.

### Launcher state and game-data handoff

1. `LauncherScreen` observes `GameFilesPreferences.getGameFilesUriState()`.
   The value is a filesystem path despite the historical `*_URI_KEY` name.
2. `GameFilesValidator.validatePath()` checks the saved root for
   `Morrowind.ini`, `Data Files`, and `Data Files/Morrowind.esm`.
3. If the saved path is absent or invalid, the launcher exposes only
   `SELECT GAME FILES`. That action uses Android's `OpenDocumentTree` contract.
   `GameFilesValidator.validateTree()` validates the selected
   `DocumentFile` tree, resolves shared-storage trees to a filesystem path,
   and persists the tree permission for future access.
4. `LauncherViewModel` stores the resolved path with
   `GameFilesPreferences.storeGameFilesPath()` and calls
   `processSelectedFolder()` from `SettingsFragment.kt`. That existing
   processing step updates the OpenMW data paths, content/fallback archive
   entries, converted INI settings, and user configuration.
5. Once the saved path is valid, the launcher exposes `PLAY` and `SETTINGS`.
   `PLAY` performs the same `UserManageAssets.resourcePrepare()` resource
   synchronization used by the previous launch action, sets the existing
   launch state, and calls `Context.startGame()`.
6. `startGame()` starts `EngineActivity`. The engine activity reads the
   prepared files under `Constants.USER_CONFIG`, `Constants.USER_OPENMW_CFG`,
   and the external OpenMW storage directories before loading the native
   library and SDL surface.

The artwork in `res/drawable/openmw_launcher_art.png` is rendered with
Compose `ContentScale.Crop`, keeping its aspect ratio. The crop uses a small
top focal bias on wide displays so the logo is not sacrificed to the lower
action area. Launcher actions use transparent dark framing, a muted gold
border, and serif typography so the artwork remains the dominant surface.

## Android game host and overlay

`EngineActivity` extends SDL's `SDLActivity`. It selects and loads the native
libraries, sets OpenGL/SDL environment variables, forces landscape mode through
the manifest/SDL hints, and hosts the native SDL view in
`res/layout/engine_activity.xml`.

The layout is a `FrameLayout` named `sdl_container`. `EngineActivity` inserts
the SDL surface, then layers `compose_overlayUI` above it and attaches a
separate `compose_leftThumb` view for the left stick. The overlay is composed
after the container knows its actual width and height. It includes:

- `OverlayUI`: utility menu, mouse/cursor controls, keyboard, travel popup,
  quick actions, and diagnostics.
- `ResizableDraggableButton` from `DynamicButtons.kt`: configurable key/mouse
  action buttons.
- `ResizableDraggableThumbstick` and
  `ResizableDraggableRightThumbstick`: analog movement/look controls.
- `VirtualKeyboard`, `MouseIcon`, `HiddenMenu`, `RadialMenu`, and scroll-wheel
  indicators.
- `GridOverlay` and edit-mode controls for configuring the overlay itself.

`StateManager.kt` contains `UIStateManager` and `ButtonState`. It stores the
active button group, cursor/mouse state, overlay visibility, edit mode, grid,
thumbstick state, and normalized container dimensions. `ButtonsConfig.kt`
serializes button definitions and image references. The checked-in
`payload/app/ui/UI.cfg` is a default configuration; user configurations are
written under the app's external OpenMW UI directory.

## Input and coordinate flow

1. Android touch is received by Compose pointer handlers in the overlay
   controls. A button can dispatch an SDL/OpenMW key down/up event or a mouse
   button event. Dragging a button can also dispatch relative SDL mouse motion.
2. `EngineActivity.handleKeyEvent()` synthesizes Android key events for
   keyboard/utility actions. `Keyboard.kt` maps the virtual keyboard to
   Android key codes.
3. `DynamicButtons.kt` uses the current Android display size and
   `EngineActivity.resolutionX/Y` to translate the SDL cursor position back to
   Android coordinates. Relative touch movement is converted to SDL mouse
   motion using the configured sensitivity and screen width.
4. `patches/openmw/androidmain.cpp` forwards those actions to SDL's Android
   window and mouse/controller queues. OpenMW then processes them through its
   input/window manager and MyGUI input manager.
5. `input_v3.xml` supplies the SDL virtual controller/key/channel bindings
   used by the packaged Android input configuration.

There are therefore three coordinate concerns to keep separate:

- Android Compose coordinates in dp/pixels inside the `sdl_container`.
- SDL/OpenMW window coordinates based on the configured engine resolution.
- MyGUI view/layout coordinates based on the current render view size and
  engine GUI scaling factor.

`MainActivity` reads the real Android display size and
`updateResolutionInConfig()` writes the selected resolution into the OpenMW
configuration. `EngineActivity` records the actual SDL container dimensions.
`UIStateManager.saveButtonState()` stores button positions as normalized
fractions of that container and reconstructs absolute positions on load. The
engine's own window scaling remains in `MWGui::WindowManager`; changing the
Android overlay size does not automatically resize MyGUI windows.

## Build and phone handoff

Use the full Docker export, not the launcher-only Gradle APK:

```bash
cd openmw-android
docker build --output=. -t openmw-android .
```

The resulting `openmw-android/openmw-android.apk` is an ARM64 package with the
engine payload. Install it on an ARM64 Android phone, copy in legally obtained
Morrowind data, launch `com.alpha3.launcher`, and test the launcher in both
first-launch and configured states. Verify directory selection, invalid-folder
feedback, saved-path restoration, Play launch, Settings navigation, and the
logo/action placement on phone and tablet aspect ratios.

Do not use the workspace's `openmw-api36` x86_64 software AVD as a runtime
validation target.