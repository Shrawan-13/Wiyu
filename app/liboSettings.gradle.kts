/*
 * Wiyu build configuration.
 *
 * The Wiyu UI is plain Android code, but the document engine is a prebuilt
 * LibreOffice core: libo-native-code.so, the UNO Java bindings
 * (libreoffice.jar, java_uno.jar, unoloader.jar) and the runtime assets
 * (registry databases, filters, fonts, .ui files) all come from a separate
 * LibreOffice core build - see docs/BUILDING.md.
 *
 * This file replaces the liboSettings.gradle.kts that a LibreOffice tree
 * normally generates at configure time, so that the project can be opened and
 * understood (and even built, once the artefacts exist) as a standalone
 * repository.
 *
 * Values are resolved with this precedence:
 *   1. -P<key>=<value> on the Gradle command line
 *   2. <repo root>/lo-build.properties  (git-ignored; copy the .example)
 *   3. the defaults below
 */

import java.util.Properties
import org.gradle.api.GradleException

val wiyuLoProps = Properties().apply {
    val f = rootProject.file("lo-build.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

fun loSetting(key: String, default: String): String =
    (project.findProperty(key) as String?)
        ?: wiyuLoProps.getProperty(key)
        ?: default

// --- paths into the LibreOffice core build tree -----------------------------

// Repository root. The build uses this to find mobile-config.py, which strips
// LibreOffice UI configuration the mobile app does not need.
extra["liboSrcRoot"] = rootProject.projectDir.absolutePath

// Scratch build directory of the LibreOffice core build.
extra["liboWorkdir"] = rootProject.file(
    loSetting("liboWorkdir", "libreoffice-build/workdir")
).absolutePath

// Installation tree produced by `make install` in the core build.
extra["liboInstdir"] = rootProject.file(
    loSetting("liboInstdir", "libreoffice-build/instdir")
).absolutePath

// Sub-paths inside liboInstdir (these are stable across LibreOffice versions).
extra["liboUREJavaFolder"] = loSetting("liboUREJavaFolder", "ure-link/share/java")
extra["liboEtcFolder"] = loSetting("liboEtcFolder", "etc")
extra["liboUreMiscFolder"] = loSetting("liboUreMiscFolder", "ure-link/share/misc")
extra["liboSharedResFolder"] = loSetting("liboSharedResFolder", "share/registry/res")

// Where the bundled .so files live. App-relative by default, so the core build
// output can simply be dropped into app/jniLibs/<abi>/.
extra["liboJniLibsdir"] = loSetting("liboJniLibsdir", "jniLibs")

// --- prebuilt bundle --------------------------------------------------------

// The CI/release path: instead of a LibreOffice source tree, use the bundle
// published as the `lo-core-<version>` release asset. It contains the staged
// runtime assets, the native libraries and the UNO jars, i.e. exactly what the
// copy*/create* tasks below would otherwise produce from a core build:
//
//   libreoffice-build/assets/           -> APK assets (already staged)
//   libreoffice-build/jniLibs/<abi>/    -> .so files
//   libreoffice-build/jars/*.jar        -> compile classpath
//   libreoffice-build/res_generated/    -> res/raw license + notice
//
// Detection is by presence of assets/, so a real core tree keeps working.
val prebuiltBundleDir = rootProject.file(loSetting("wiyuBundle", "libreoffice-build"))
val usePrebuiltBundle = prebuiltBundleDir.resolve("assets").isDirectory

extra["wiyuBundleDir"] = prebuiltBundleDir.absolutePath
extra["wiyuPrebuilt"] = usePrebuiltBundle

// --- toolchain --------------------------------------------------------------

// Leave both empty to let the Android Gradle Plugin pick the NDK installed in
// the SDK; set them to pin a specific NDK (required for reproducible builds).
extra["androidNdkPath"] = loSetting("androidNdkPath", "")
extra["androidNdkVersion"] = loSetting("androidNdkVersion", "")

// --- identity, versions, branding -------------------------------------------

val versionMajor = loSetting("liboVersionMajor", "27")
val versionMinor = loSetting("liboVersionMinor", "2")
val gitCommit = loSetting("liboGitFullCommit", "unknown")

// Must match the LibreOffice core the native library was built from: it is
// written into bootstraprc and selects the user configuration layer.
extra["liboVersionMajor"] = versionMajor
extra["liboVersionMinor"] = versionMinor
extra["liboGitFullCommit"] = gitCommit
extra["buildIdShort"] = loSetting("buildIdShort", gitCommit.take(8))

extra["vendor"] = loSetting("vendor", "Wiyu")
extra["privacyPolicyUrl"] = loSetting("privacyPolicyUrl", "")
extra["androidApplicationId"] = loSetting("androidApplicationId", "app.wiyu")
extra["androidVersionName"] = loSetting("androidVersionName", "1.0.0")
extra["androidMinSdkVersion"] = loSetting("androidMinSdkVersion", "21").toInt()

// --- pre-flight check -------------------------------------------------------

// Without the engine artefacts the .so files and UNO jars are missing, and the
// failure would otherwise surface as a wall of "package does not exist" errors.
// Registered in app/build.gradle.kts on preBuild.
// Note: capture these here - inside a task action, `extra` means the *task's*
// extra properties, not the project's.
val coreInstdirDir = file(extra["liboInstdir"] as String)
val coreJavaJarsDir = file("${extra["liboInstdir"]}/${extra["liboUREJavaFolder"]}")
val coreJniLibsDir = file(extra["liboJniLibsdir"] as String)
val bundleAssetsDir = prebuiltBundleDir.resolve("assets")
val bundleJniLibsDir = prebuiltBundleDir.resolve("jniLibs")
val bundleJarsDir = prebuiltBundleDir.resolve("jars")

tasks.register("wiyuCoreReady") {
    group = "verification"
    description = "Checks that the LibreOffice engine artefacts are present."

    doLast {
        val missing = buildList {
            if (usePrebuiltBundle) {
                if (!bundleAssetsDir.isDirectory) add("bundle assets: $bundleAssetsDir")
                if (!bundleJniLibsDir.isDirectory) add("bundle native libraries: $bundleJniLibsDir")
                if (!bundleJarsDir.isDirectory) add("bundle UNO jars: $bundleJarsDir")
            } else {
                if (!coreInstdirDir.isDirectory) add("core install tree: $coreInstdirDir")
                if (!coreJavaJarsDir.isDirectory) add("UNO Java bindings: $coreJavaJarsDir")
                if (!coreJniLibsDir.isDirectory) add("native libraries: $coreJniLibsDir")
            }
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                """
                |Wiyu cannot build: the LibreOffice engine is missing.
                |
                |  missing: ${missing.joinToString("\n           ")}
                |
                |Fetch the prebuilt bundle from the lo-core-<version> release
                |(docs/BUILDING.md), or point the project at a LibreOffice core
                |build tree with lo-build.properties.
                |To run Gradle without the engine, use -PskipCoreCheck=true.
                """.trimMargin()
            )
        }
    }
}
