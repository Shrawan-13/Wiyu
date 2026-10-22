package app.wiyu

import app.wiyu.data.FileTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The manifest and [FileTypes] used to be duplicated by hand with "keep in
 * sync" comments. This test replaces those comments: adding a format in one
 * place without the other now fails the build.
 */
class FileTypesManifestTest {

    private fun manifestText(): String {
        val candidates = listOf(File("AndroidManifest.xml"), File("app/AndroidManifest.xml"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun declaredMimes(text: String): Set<String> =
        Regex("android:mimeType=\"([^\"]+)\"")
            .findAll(text)
            .map { it.groupValues[1] }
            .filter { it != "*/*" }
            .toSet()

    @Test
    fun manifestClaimsEverySupportedMime() {
        val text = manifestText()
        val missing = FileTypes.allMimes.filterNot { text.contains("\"$it\"") }
        assertTrue("Manifest is missing intent filters for: $missing", missing.isEmpty())
    }

    @Test
    fun manifestClaimsOnlySupportedMimes() {
        val declared = declaredMimes(manifestText())
        val unsupported = declared - FileTypes.allMimes.toSet()
        assertTrue("Manifest claims types the engine cannot open: $unsupported", unsupported.isEmpty())
    }

    @Test
    fun manifestAndRegistryAgreeExactly() {
        val declared = declaredMimes(manifestText())
        assertEquals(
            "Manifest intent filters and FileTypes.kt disagree",
            FileTypes.allMimes.toSet(),
            declared,
        )
    }

    @Test
    fun pdfAndPlainTextAreOpenable() {
        // Both were missing before the UI rework: PDFs and .txt files could not
        // be opened with Wiyu at all.
        assertTrue(FileTypes.allMimes.contains("application/pdf"))
        assertTrue(FileTypes.allMimes.contains("text/plain"))
    }
}
