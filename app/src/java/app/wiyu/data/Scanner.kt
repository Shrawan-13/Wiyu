package app.wiyu.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.yield
import java.io.File

/**
 * Walks the places the user granted and upserts everything Wiyu can open.
 *
 * Three sources, in decreasing order of trust:
 *  - SAF trees (always available, user-granted, persisted);
 *  - the raw filesystem, when "All files access" (or legacy READ_EXTERNAL_STORAGE)
 *    is granted — this is the only way to see documents outside Downloads on
 *    Android 13+;
 *  - MediaStore, to pick up Downloads without that permission.
 */
class Scanner(private val context: Context, private val db: WiyuDb) {

    data class Outcome(val indexed: Int, val failures: Int)

    suspend fun scanRoots(roots: List<RootEntry>, onProgress: (Int) -> Unit): Outcome {
        var indexed = 0
        var failures = 0
        val scanId = System.currentTimeMillis()
        for (root in roots) {
            if (!root.isTree) continue
            db.beginRootScan(root.uri)
            val before = indexed
            try {
                indexed += scanTree(root, scanId, onProgress)
                db.finishRootScan(root.uri)
            } catch (t: Throwable) {
                // A revoked grant must not wipe the entries we already have.
                failures++
                Log.w(TAG, "cannot scan ${root.uri}: ${t.message}")
            }
            Log.d(TAG, "root ${root.label}: ${indexed - before} files")
        }
        onProgress(indexed)
        return Outcome(indexed, failures)
    }

    private suspend fun scanTree(root: RootEntry, scanId: Long, onProgress: (Int) -> Unit): Int {
        val resolver = context.contentResolver
        val treeUri = Uri.parse(root.uri)
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (t: Throwable) {
            Log.w(TAG, "not a tree uri: ${root.uri}")
            return 0
        }
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocId to root.label)
        val batch = ArrayList<ScannedFile>(BATCH)
        var count = 0

        while (queue.isNotEmpty()) {
            val (docId, dir) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            resolver.query(childrenUri, TREE_PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    val mime = cursor.getString(2)
                    val childId = cursor.getString(0)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (dir.count { it == '/' } < MAX_DEPTH) queue.add(childId to "$dir/$name")
                        continue
                    }
                    val type = FileTypes.typeFor(name, mime) ?: continue
                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val modified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    batch.add(
                        ScannedFile(
                            uri = uri.toString(),
                            name = name,
                            dir = dir,
                            mime = mime ?: type.mimes.first(),
                            family = type.family,
                            size = size,
                            modified = modified,
                            root = root.uri,
                            scanId = scanId,
                            isTemplate = type.template,
                        )
                    )
                    count++
                    if (batch.size >= BATCH) {
                        db.upsertScanned(batch.toList())
                        batch.clear()
                        onProgress(count)
                        yield()
                    }
                }
            }
            yield()
        }
        if (batch.isNotEmpty()) db.upsertScanned(batch.toList())
        return count
    }

    /** Raw filesystem walk; only called when Wiyu can actually read the volume. */
    suspend fun scanDevice(onProgress: (Int) -> Unit): Outcome {
        val root = DEVICE_ROOT
        val base = Environment.getExternalStorageDirectory() ?: return Outcome(0, 1)
        if (!base.canRead()) return Outcome(0, 1)

        db.beginRootScan(root.uri)
        val scanId = System.currentTimeMillis()
        val batch = ArrayList<ScannedFile>(BATCH)
        val stack = ArrayDeque<Pair<File, String>>()
        stack.add(base to root.label)
        var count = 0

        while (stack.isNotEmpty()) {
            val (dir, logical) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (file in children) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                if (file.isDirectory) {
                    if (name in SKIP_DIRS) continue
                    if (logical.count { it == '/' } < MAX_DEPTH) stack.add(file to "$logical/$name")
                    continue
                }
                val type = FileTypes.typeFor(name, null) ?: continue
                batch.add(
                    ScannedFile(
                        uri = Uri.fromFile(file).toString(),
                        name = name,
                        dir = logical,
                        mime = type.mimes.first(),
                        family = type.family,
                        size = file.length(),
                        modified = file.lastModified(),
                        root = root.uri,
                        scanId = scanId,
                        isTemplate = type.template,
                    )
                )
                count++
                if (batch.size >= BATCH) {
                    db.upsertScanned(batch.toList())
                    batch.clear()
                    onProgress(count)
                    yield()
                }
            }
            yield()
        }
        if (batch.isNotEmpty()) db.upsertScanned(batch.toList())
        db.finishRootScan(root.uri)
        return Outcome(count, 0)
    }

    /** Downloads/Documents via MediaStore — no special permission on API 29+. */
    suspend fun scanMediaStore(onProgress: (Int) -> Unit): Outcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Outcome(0, 0)
        val root = RootEntry(MEDIA_ROOT_URI, "Downloads", false)
        db.beginRootScan(root.uri)
        val scanId = System.currentTimeMillis()
        val batch = ArrayList<ScannedFile>(BATCH)
        var count = 0

        val collections = listOf(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL) to "Downloads",
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL) to "Shared storage",
        )
        for ((collection, label) in collections) {
            try {
                context.contentResolver.query(collection, MEDIA_PROJECTION, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: continue
                        if (name.startsWith(".")) continue
                        val mime = cursor.getString(mimeIdx)
                        val type = FileTypes.typeFor(name, mime) ?: continue
                        val relative = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                        val dir = buildString {
                            append(label)
                            val trimmed = relative.trim('/')
                            if (trimmed.isNotEmpty()) append('/').append(trimmed)
                        }
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIdx))
                        batch.add(
                            ScannedFile(
                                uri = uri.toString(),
                                name = name,
                                dir = dir,
                                mime = mime ?: type.mimes.first(),
                                family = type.family,
                                size = cursor.getLong(sizeIdx),
                                modified = cursor.getLong(dateIdx) * 1000L,
                                root = root.uri,
                                scanId = scanId,
                                isTemplate = type.template,
                            )
                        )
                        count++
                        if (batch.size >= BATCH) {
                            db.upsertScanned(batch.toList())
                            batch.clear()
                            onProgress(count)
                            yield()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "MediaStore query failed for $collection: ${t.message}")
            }
        }
        if (batch.isNotEmpty()) db.upsertScanned(batch.toList())
        db.finishRootScan(root.uri)
        return Outcome(count, 0)
    }

    companion object {
        private const val TAG = "WiyuScanner"
        private const val BATCH = 200
        private const val MAX_DEPTH = 12

        val DEVICE_ROOT = RootEntry("device", "Internal storage", false)
        const val MEDIA_ROOT_URI = "mediastore"

        private val SKIP_DIRS = setOf("Android", "LOST.DIR", ".thumbnails")

        private val TREE_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        private val MEDIA_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )

        /** True when Wiyu may read the whole shared volume. */
        fun hasFullStorageAccess(context: Context): Boolean = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

        /** Permission the user still has to be asked for, or null. */
        fun missingLegacyPermission(context: Context): String? {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return null
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            return if (granted) null else Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}
