package app.wiyu

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.wiyu.data.DocFamily
import app.wiyu.data.FileEntry
import app.wiyu.data.FileTypes
import app.wiyu.data.WiyuRepo
import org.libreoffice.LibreOfficeMainActivity
import org.libreoffice.ui.LibreOfficeUIActivity

/**
 * The only place that knows how to start the document editor.
 *
 * The editor is a separate activity with its own (View-based) UI and the GL
 * rendering surface; the shell never renders inside it.
 */
object EditorBridge {

    fun open(context: Context, entry: FileEntry) {
        openUri(context, Uri.parse(entry.uri), entry.mime, entry.name)
    }

    fun openUri(context: Context, uri: Uri, mime: String?, name: String? = null) {
        WiyuRepo.get(context).recordOpen(uri, name, mime)
        context.startActivity(viewIntent(context, uri, mime))
    }

    fun viewIntent(context: Context, uri: Uri, mime: String?): Intent =
        Intent(context, LibreOfficeMainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, mime ?: FileTypes.mimeFor(uri.lastPathSegment) ?: "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uri.scheme == "content") addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    /** Chooser for "open with another app"; the engine is deliberately excluded. */
    fun openWithChooser(context: Context, uri: Uri, mime: String?): Intent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: FileTypes.mimeFor(uri.lastPathSegment) ?: "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Open with")
    }

    fun newDocument(context: Context, family: DocFamily) {
        context.startActivity(Intent(context, LibreOfficeMainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(LibreOfficeUIActivity.NEW_DOC_TYPE_KEY, newDocumentType(family))
        })
    }

    private fun newDocumentType(family: DocFamily): String = when (family) {
        DocFamily.CALC -> LibreOfficeUIActivity.NEW_CALC_STRING_KEY
        DocFamily.IMPRESS -> LibreOfficeUIActivity.NEW_IMPRESS_STRING_KEY
        DocFamily.DRAW -> LibreOfficeUIActivity.NEW_DRAW_STRING_KEY
        else -> LibreOfficeUIActivity.NEW_WRITER_STRING_KEY
    }
}
