package app.wiyu

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/**
 * File operations shared by the shell: naming, copying, deleting and staging a
 * file for the share sheet. Everything returns false/null instead of throwing,
 * because a revoked SAF grant is a normal, expected condition.
 */
object FileOps {

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            } else {
                null
            }
        }
    } catch (t: Throwable) {
        null
    }

    /** Copies [source] into [target] (used by "Save a copy"). */
    fun copy(context: Context, source: String, target: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(Uri.parse(source)) ?: return false
            val output = context.contentResolver.openOutputStream(target, "wt") ?: return false
            input.use { ins -> output.use { outs -> ins.copyTo(outs) } }
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun delete(context: Context, uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                "content" -> DocumentsContract.deleteDocument(context.contentResolver, uri)
                "file" -> uri.path?.let { File(it).delete() } ?: false
                else -> false
            }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * `file://` URIs cannot leave the process, so a cached copy is handed to the
     * share sheet through the FileProvider instead.
     */
    fun stageForShare(context: Context, uri: Uri, name: String): Uri? = try {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val target = File(dir, name)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { ins ->
            target.outputStream().use { outs -> ins.copyTo(outs) }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    } catch (t: Throwable) {
        null
    }
}
