# Building Wiyu

Wiyu is not a self-contained app: the document engine is a **prebuilt
LibreOffice core**. Gradle compiles the Java/UI layer, then packages the core's
shared library, UNO bindings and runtime assets into the APK. Two stages:

1. build the LibreOffice core for Android (once, or when the engine changes)
2. build the Wiyu APK against that core

## Prerequisites

| Tool | Version |
| --- | --- |
| JDK | 17 or newer |
| Android SDK | platform 36, build-tools matching |
| Android NDK | r27+ (or whatever the core build used) |
| LibreOffice core build tree | see stage 1 |

## Stage 1 — LibreOffice core

A full Wiyu build needs the whole LibreOffice source tree, not just `android/`.
The core is cross-compiled for each target ABI:

```bash
git clone https://github.com/LibreOffice/core.git libreoffice-core-src
cd libreoffice-core-src

# arm64 device build
./autogen.sh --with-distro=LibreOfficeAndroid
make            # hours; produces workdir/installation/LibreOfficeAndroid/instdir

# or, for an x86_64 emulator
./autogen.sh --with-distro=LibreOfficeAndroidX86_64
make
```

Point the environment at the SDK/NDK first (`ANDROID_SDK_HOME`,
`ANDROID_NDK_HOME`), as documented in `README.cross` in the LibreOffice tree.

What Wiyu consumes out of that tree:

| Artefact | Source | Destination |
| --- | --- | --- |
| `libo-native-code.so` (+ NSS, sqlite .so files) | core `instdir` / `workdir` | `app/jniLibs/<abi>/` |
| `java_uno.jar`, `libreoffice.jar`, `unoloader.jar` | `instdir/ure-link/share/java/` | compile classpath (read in place) |
| registry DBs, filters, fonts, `.ui` files, `LICENSE`/`NOTICE` | `instdir` | staged into `app/assets*` by Gradle |
| `types.rdb`, `offapi.rdb`, `oovbaapi.rdb` | `instdir` | `app/assets/unpack/program/` |

Gradle stages the assets itself (`copyAssets`, `copyUnpackAssets`,
`createRCfiles`, `createStrippedConfig*`); you only provide the core tree.

## Stage 2 — the APK

```bash
cp lo-build.properties.example lo-build.properties   # edit the paths
./gradlew :app:assembleDebug
```

Without `lo-build.properties`, the build looks for `libreoffice-build/instdir`
and `app/jniLibs` in this repository.

Every path can also be passed on the command line:

```bash
./gradlew :app:assembleDebug \
  -PliboInstdir=/path/to/core/workdir/installation/LibreOfficeAndroid/instdir \
  -PliboJniLibsdir=/path/to/core/workdir/.../lib \
  -PandroidNdkVersion=27.2.12479018
```

Precedence: `-P` property → `lo-build.properties` → default.

The `wiyuCoreReady` task (wired into `preBuild`) fails with a readable message if
the core artefacts are missing. To run Gradle for inspection or UI work without
the engine:

```bash
./gradlew :app:tasks -PskipCoreCheck=true
```

Compiling the app still needs the UNO jars on the classpath, so
`assembleDebug` cannot succeed without them.

## Notes

- `liboSrcRoot` is the repository root; it is used to locate `mobile-config.py`,
  which strips LibreOffice UI configuration that the mobile app does not need.
- `liboVersionMajor`/`liboVersionMinor` must match the core you built: the
  values are written into `assets/program/bootstraprc` as the product key.
- The output APK was called `LibreOfficeViewer`; it is now `Wiyu`.
