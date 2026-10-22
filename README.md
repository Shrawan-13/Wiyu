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
| Material 3 UI | **not started** — see roadmap |
| File auto-discovery ("Open document") | **not started** |
| "Open with" / share-target coverage | manifest filters inherited, to be verified |
| Engine (LibreOfficeKit, tiles, editing) | untouched, upstream code |

The app cannot be compiled here yet: it needs the Android SDK/NDK and a
prebuilt LibreOffice core. See [docs/BUILDING.md](docs/BUILDING.md).

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
    canvas/, overlay/, ui/          selection handles, headers, document browser
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
2. **`app/src/java/.../canvas`, `overlay`, `LOKit*`, `Tile*` are engine code.**
   UI work happens in `ui/` (the document browser) and in new screens; the
   viewer/editor surface is treated as a black box.

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

## Roadmap

1. **Material 3 shell** — `Theme.Material3.DayNight` theme, dynamic color
   (Android 12+) with a Wiyu fallback palette, `TopAppBar`, M3
   `FloatingActionButton`, `NavigationBar`, motion specs from the M3 guidance.
2. **Home screen** replacing `ui/LibreOfficeUIActivity`: recent documents as M3
   cards/list, empty state, "Open document" entry point via Storage Access
   Framework.
3. **Auto-discovery** — `MediaStore.Downloads`/`Documents` queries and SAF
   enumeration filtered to supported MIME types, with incremental authorization
   instead of a blanket storage permission.
4. **Deep integration** — keep the `VIEW`/`EDIT`/`PICK` intent filters in sync
   with the supported-format list, expose `ACTION_OPEN_DOCUMENT` targets, so
   Wiyu appears in "Open with" and share sheets for every supported document.
5. **Verify** on device with the real engine build.

## License

Wiyu is a derivative work of LibreOffice and is distributed under the Mozilla
Public License 2.0. See [LICENSES/](LICENSES/) and [NOTICE.md](NOTICE.md).
