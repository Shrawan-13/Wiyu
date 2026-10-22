package app.wiyu.data

/**
 * Document families. A family drives the icon, the accent colour and the engine
 * mode; the concrete format is a separate concern.
 */
enum class DocFamily(val id: String, val label: String) {
    WRITER("writer", "Documents"),
    CALC("calc", "Sheets"),
    IMPRESS("impress", "Slides"),
    DRAW("draw", "Drawings"),
    PDF("pdf", "PDFs"),
    OTHER("other", "Other");

    companion object {
        fun fromId(id: String?): DocFamily = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** A single supported format. */
data class DocType(
    val mimes: List<String>,
    val extensions: List<String>,
    val family: DocFamily,
    val label: String,
    val template: Boolean = false,
    val macroEnabled: Boolean = false,
    /** PDF is import-only in the editor, so it is view-only in the UI. */
    val editable: Boolean = true,
    /** LOKit filter name used by `saveAs`; defaults to the primary extension. */
    val saveFormat: String = extensions.firstOrNull() ?: "odt",
)

/**
 * The single source of truth for "what can Wiyu open".
 *
 * Everything else derives from this: the manifest intent filters (checked by
 * `FileTypesManifestTest`), the browser filters, the icons and the Save As
 * format choices. Do not add a MIME type anywhere else.
 */
object FileTypes {

    val types: List<DocType> = listOf(
        // ---- OpenDocument ------------------------------------------------
        DocType(listOf("application/vnd.oasis.opendocument.text"), listOf(".odt", ".fodt"), DocFamily.WRITER, "ODF text"),
        DocType(listOf("application/vnd.oasis.opendocument.text-template"), listOf(".ott"), DocFamily.WRITER, "ODF text template", template = true),
        DocType(listOf("application/vnd.oasis.opendocument.text-flat-xml"), listOf(".fodt"), DocFamily.WRITER, "Flat ODF text"),
        DocType(listOf("application/vnd.oasis.opendocument.spreadsheet"), listOf(".ods"), DocFamily.CALC, "ODF spreadsheet"),
        DocType(listOf("application/vnd.oasis.opendocument.spreadsheet-template"), listOf(".ots"), DocFamily.CALC, "ODF spreadsheet template", template = true),
        DocType(listOf("application/vnd.oasis.opendocument.spreadsheet-flat-xml"), listOf(".fods"), DocFamily.CALC, "Flat ODF spreadsheet"),
        DocType(listOf("application/vnd.oasis.opendocument.presentation"), listOf(".odp"), DocFamily.IMPRESS, "ODF presentation"),
        DocType(listOf("application/vnd.oasis.opendocument.presentation-template"), listOf(".otp"), DocFamily.IMPRESS, "ODF presentation template", template = true),
        DocType(listOf("application/vnd.oasis.opendocument.presentation-flat-xml"), listOf(".fodp"), DocFamily.IMPRESS, "Flat ODF presentation"),
        DocType(listOf("application/vnd.oasis.opendocument.graphics"), listOf(".odg"), DocFamily.DRAW, "ODF drawing"),
        DocType(listOf("application/vnd.oasis.opendocument.graphics-template"), listOf(".otg"), DocFamily.DRAW, "ODF drawing template", template = true),
        DocType(listOf("application/vnd.oasis.opendocument.graphics-flat-xml"), listOf(".fodg"), DocFamily.DRAW, "Flat ODF drawing"),

        // ---- Microsoft OOXML ---------------------------------------------
        DocType(listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), listOf(".docx"), DocFamily.WRITER, "Word document"),
        DocType(listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.template"), listOf(".dotx"), DocFamily.WRITER, "Word template", template = true),
        DocType(listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), listOf(".xlsx"), DocFamily.CALC, "Excel workbook"),
        DocType(listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.template"), listOf(".xltx"), DocFamily.CALC, "Excel template", template = true),
        DocType(listOf("application/vnd.openxmlformats-officedocument.presentationml.presentation"), listOf(".pptx"), DocFamily.IMPRESS, "PowerPoint presentation"),
        DocType(listOf("application/vnd.openxmlformats-officedocument.presentationml.slideshow"), listOf(".ppsx"), DocFamily.IMPRESS, "PowerPoint slideshow"),
        DocType(listOf("application/vnd.openxmlformats-officedocument.presentationml.template"), listOf(".potx"), DocFamily.IMPRESS, "PowerPoint template", template = true),

        // ---- Legacy Microsoft --------------------------------------------
        DocType(listOf("application/msword"), listOf(".doc", ".dot"), DocFamily.WRITER, "Word document"),
        DocType(listOf("application/vnd.ms-excel"), listOf(".xls", ".xlt"), DocFamily.CALC, "Excel workbook"),
        DocType(listOf("application/vnd.ms-powerpoint"), listOf(".ppt", ".pot", ".pps"), DocFamily.IMPRESS, "PowerPoint presentation"),
        DocType(listOf("application/vnd.ms-works"), listOf(".wps"), DocFamily.WRITER, "Microsoft Works"),
        DocType(listOf("application/vnd.visio"), listOf(".vsd", ".vst"), DocFamily.DRAW, "Visio drawing"),
        DocType(listOf("application/vnd.visio2013"), listOf(".vsdx", ".vstx"), DocFamily.DRAW, "Visio drawing"),
        DocType(listOf("application/vnd.visio.xml"), listOf(".vdx"), DocFamily.DRAW, "Visio drawing"),
        DocType(listOf("application/x-mspublisher"), listOf(".pub"), DocFamily.DRAW, "Publisher document"),

        // ---- Plain text and markup ----------------------------------------
        DocType(listOf("text/plain"), listOf(".txt"), DocFamily.WRITER, "Text"),
        DocType(listOf("text/markdown"), listOf(".md"), DocFamily.WRITER, "Markdown"),
        DocType(listOf("application/rtf", "text/rtf"), listOf(".rtf"), DocFamily.WRITER, "Rich text"),
        DocType(listOf("text/csv", "text/comma-separated-values"), listOf(".csv"), DocFamily.CALC, "CSV", saveFormat = "csv"),
        DocType(listOf("text/tab-separated-values"), listOf(".tsv"), DocFamily.CALC, "TSV", saveFormat = "csv"),

        // ---- Other formats the engine imports ------------------------------
        DocType(listOf("application/vnd.apple.keynote"), listOf(".key"), DocFamily.IMPRESS, "Keynote presentation"),
        DocType(listOf("application/x-abiword"), listOf(".abw"), DocFamily.WRITER, "AbiWord document"),
        DocType(listOf("application/x-pagemaker"), listOf(".pmd"), DocFamily.DRAW, "PageMaker document"),

        // ---- Vector graphics ------------------------------------------------
        DocType(listOf("image/svg+xml"), listOf(".svg"), DocFamily.DRAW, "SVG image"),
        DocType(listOf("image/x-emf"), listOf(".emf"), DocFamily.DRAW, "EMF image"),
        DocType(listOf("image/x-wmf"), listOf(".wmf"), DocFamily.DRAW, "WMF image"),
        DocType(listOf("image/x-svm"), listOf(".svm"), DocFamily.DRAW, "StarView metafile"),

        // ---- PDF (view-only) -------------------------------------------------
        DocType(listOf("application/pdf"), listOf(".pdf"), DocFamily.PDF, "PDF", editable = false, saveFormat = "pdf"),
    )

    /** Every MIME type Wiyu claims in its manifest intent filters. */
    val allMimes: List<String> = types.flatMap { it.mimes }.distinct().sorted()

    /** Extensions used by the crawlers to pre-filter files cheaply. */
    val supportedExtensions: Set<String> = types.flatMap { it.extensions }.map { it.lowercase() }.toSet()

    /** Families the user can create from the Home FAB. */
    val creatableFamilies: List<DocFamily> = listOf(DocFamily.WRITER, DocFamily.CALC, DocFamily.IMPRESS, DocFamily.DRAW)

    fun normalizedExtension(name: String?): String {
        if (name == null) return ""
        val idx = name.lastIndexOf('.')
        if (idx < 0 || idx == name.length - 1) return ""
        return name.substring(idx).lowercase()
    }

    /** MIME first, display-name extension second — providers lie about both. */
    fun typeFor(name: String?, mime: String?): DocType? {
        if (!mime.isNullOrBlank()) {
            types.firstOrNull { it.mimes.any { m -> m.equals(mime, ignoreCase = true) } }?.let { return it }
        }
        val ext = normalizedExtension(name)
        if (ext.isEmpty()) return null
        return types.firstOrNull { it.extensions.any { e -> e.equals(ext, ignoreCase = true) } }
    }

    fun familyFor(name: String?, mime: String?): DocFamily? = typeFor(name, mime)?.family

    fun isSupported(name: String?, mime: String?): Boolean = typeFor(name, mime) != null

    /** Best-effort MIME for a file we only know by name. */
    fun mimeFor(name: String?): String? = typeFor(name, null)?.mimes?.firstOrNull()

    /** The MIME to hand to ACTION_CREATE_DOCUMENT for a family. */
    fun defaultMimeFor(family: DocFamily): String = when (family) {
        DocFamily.WRITER -> "application/vnd.oasis.opendocument.text"
        DocFamily.CALC -> "application/vnd.oasis.opendocument.spreadsheet"
        DocFamily.IMPRESS -> "application/vnd.oasis.opendocument.presentation"
        DocFamily.DRAW -> "application/vnd.oasis.opendocument.graphics"
        DocFamily.PDF -> "application/pdf"
        DocFamily.OTHER -> "application/octet-stream"
    }

    /**
     * LOKit filter name for saving a file under [name].
     *
     * Falling back to the document's own family keeps "Save As" format
     * preserving instead of silently rewriting everything as ODF.
     */
    fun saveFormatFor(name: String?, family: DocFamily): String {
        val ext = normalizedExtension(name).removePrefix(".")
        if (ext.isNotEmpty() && SAVE_FILTERS.containsKey(ext)) return SAVE_FILTERS.getValue(ext)
        return fallbackSaveFormat(family)
    }

    fun fallbackSaveFormat(family: DocFamily): String = when (family) {
        DocFamily.WRITER -> "odt"
        DocFamily.CALC -> "ods"
        DocFamily.IMPRESS -> "odp"
        DocFamily.DRAW -> "odg"
        DocFamily.PDF -> "pdf"
        DocFamily.OTHER -> "odt"
    }

    /** Extensions the engine can write, mapped to LOKit filter names. */
    private val SAVE_FILTERS: Map<String, String> = mapOf(
        "odt" to "odt", "ott" to "ott", "fodt" to "fodt",
        "ods" to "ods", "ots" to "ots", "fods" to "fods",
        "odp" to "odp", "otp" to "otp", "fodp" to "fodp",
        "odg" to "odg", "otg" to "otg", "fodg" to "fodg",
        "doc" to "doc", "dot" to "dot",
        "docx" to "docx", "dotx" to "dotx",
        "xls" to "xls", "xlsx" to "xlsx", "xltx" to "xltx",
        "ppt" to "ppt", "pptx" to "pptx", "potx" to "potx",
        "rtf" to "rtf", "txt" to "txt", "csv" to "csv", "tsv" to "csv",
        "md" to "md", "svg" to "svg", "pdf" to "pdf",
    )
}
