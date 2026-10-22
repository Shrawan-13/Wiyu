# Wiyu

[![Build debug APK](https://github.com/Shrawan-13/Wiyu/actions/workflows/android-debug.yml/badge.svg)](https://github.com/Shrawan-13/Wiyu/actions/workflows/android-debug.yml)

Wiyu is an Android document viewer **and editor**, built on the LibreOffice
engine. It opens ODF, OOXML, RTF, CSV, SVG and the other formats LibreOffice
handles, renders documents through LibreOfficeKit, and lets the user edit them.

This repository is a fork of the LibreOffice Android app
(`core/android` in [LibreOffice/core](https://github.com/LibreOffice/core)),
rebuilt as a standalone project and rebranded. The engine, rendering pipeline,
touch handling and editing stack are kept as they are; the user interface is
being replaced with a Material 3 design.

## Status

| Area | State |
| --- | --- |
| Standalone project structure | done |
| Rebrand to Wiyu (label, applicationId, APK name) | done |
| Material 3 shell (Compose) | done — Home, Browse, Search, Settings |
| Document index + auto-discovery | done — SAF folders, all-files access, MediaStore |
| Fast search | done — FTS4 index, debounced, filterable by type |
| "Open with" / share-target coverage | done — VIEW/EDIT/PICK/SEND for every supported format |
| Saving | done — Save As keeps the picked format, `file://` save-back, "Save a copy" |
| Engine (LibreOfficeKit, tiles, editing) | untouched, upstream code |

The app has two layers:

- **`app.wiyu`** — a Compose/Material 3 shell that indexes documents, browses
  them and launches the editor. It never renders a document.
- **`org.libreoffice.LibreOfficeMainActivity`** — the untouched engine surface
  (Views + OpenGL tiles). The shell starts it with an `ACTION_VIEW` intent, so
  it keeps its own window, input handling and rendering pipeline.

Documents are discovered from, in order of preference:

1. folders granted through the Storage Access Framework (always available);
2. the shared volume directly, when "Full device access" is granted in Settings
   (`MANAGE_EXTERNAL_STORAGE`, opt-in);
3. `MediaStore` (Downloads and shared storage), when neither applies.

The index is a local SQLite database with an FTS4 table for search. Nothing is
uploaded anywhere and no account is needed.

CI compiles the app on every push; see [docs/BUILDING.md](docs/BUILDING.md)
for the local requirements (Android SDK and the prebuilt LibreOffice core).

## Repository layout

```
app/                      the Android application module
  AndroidManifest.xml     activities, intent filters, FileProvider
  build.gradle.kts        app config, asset staging from the core build
  liboSettings.gradle.kts checked-in replacement for the generated config
  res/                    resources (themes, layouts, ~50 locales)
  src/java/org/libreoffice/
    LibreOfficeMainActivity.java    document viewer/editor entry point
    LOKit*.java                     Java<->native bridge, tile provider
    canvas/, overlay/, ui/          selection handles, headers, legacy browser
  src/java/app/wiyu/
    MainActivity.kt                 Compose host: navigation, FAB, launchers
    designsystem/                   M3 theme, family glyphs, list rows
    data/                           FileTypes, WiyuDb, Scanner, WiyuRepo
    feature/{home,browse,search,settings,details}
    EditorBridge.kt, FileOps.kt     intent glue, copy/share/delete
  src/test/java/app/wiyu/           manifest/registry parity test
Bootstrap/src/            LibreOfficeKit JNI bindings (shared engine glue)
gradle/                   version catalog + wrapper
docs/                     architecture, build instructions
mobile-config.py          strips unneeded LibreOffice UI config from the assets
LICENSES/                 MPL 2.0 / LGPL / ALv2 texts
```

## Two rules that keep the engine working

1. **The Java namespace stays `org.libreoffice`.** It is baked into the JNI
   symbols of the prebuilt native library (`Java_org_libreoffice_kit_...`) and
   into the generated `R` class. Branding lives in `applicationId`, the app
   label and the resources — not in the package name.
2. **The canvas, overlay, `LOKit*` and `Tile*` classes are engine code.**
   UI work happens in `app/wiyu`; the viewer/editor surface is treated as a
   black box and is only ever started through `EditorBridge`.
3. **`app/wiyu/data/FileTypes.kt` is the single list of openable formats.**
   The manifest intent filters are checked against it by
   `FileTypesManifestTest`, so adding a format means editing one place.

## Building

CI builds a debug APK on every push to `main`, on pull requests and on demand
(`.github/workflows/android-debug.yml`); the APK is uploaded as a run artifact.
Locally: `./gradlew :app:assembleDebug`.

The LibreOffice engine is not rebuilt in CI. It is downloaded from the
[`lo-core-v2`](https://github.com/Shrawan-13/Wiyu/releases/tag/lo-core-v2)
release and consumed through the "prebuilt bundle" path — see
[docs/BUILDING.md](docs/BUILDING.md).

The bundle ships all four Android ABIs. Debug builds filter to `arm64-v8a`
(~92 MB APK); release builds package `arm64-v8a`, `armeabi-v7a`, `x86_64` and
`x86`.

Caching layers, in order of impact: the engine bundle (293 MB compressed,
restored from the Actions cache), Gradle user home (dependencies, wrapper,
build cache), and Gradle's own task output cache. A warm debug build takes
roughly 1.5 minutes; a cold one is dominated by toolchain downloads.

## Known limitations

- **PDF is view-only.** The engine imports PDFs, but writing them back is out
  of scope for this fork.
- **Full device access is a Play policy liability.** `MANAGE_EXTERNAL_STORAGE`
  is declared but strictly opt-in (Settings → Full device access); the default
  path is Storage Access Framework folders plus MediaStore.
- The legacy document browser (`org.libreoffice.ui.LibreOfficeUIActivity`) is
  still present but unreachable; it survives because `LibreOfficeMainActivity`
  shares helpers with it.
- Debug builds are `arm64-v8a` only: the engine is roughly 200 MB per ABI.

## Roadmap

1. ~~Material 3 shell~~ — done.
2. ~~Home, Browse, Search, Settings~~ — done.
3. ~~Auto-discovery and indexing~~ — done.
4. ~~"Open with" coverage for every supported format~~ — done.
5. Verify on device against the real engine build, then delete the legacy
   browser activity and its layouts.

## License

Wiyu is a derivative work of LibreOffice and is distributed under the Mozilla
Public License 2.0. See [LICENSES/](LICENSES/) and [NOTICE.md](NOTICE.md).
