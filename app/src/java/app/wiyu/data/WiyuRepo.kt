package app.wiyu.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class ScanState(
    val running: Boolean = false,
    val reason: String = "",
    val indexed: Int = 0,
    val failures: Int = 0,
    val finishedAt: Long = 0L,
    val error: String? = null,
)

/**
 * Single entry point to Wiyu's data: index, discovery and settings.
 *
 * UI code never touches [WiyuDb] on the main thread — use [query] and observe
 * [version] to know when to re-read.
 */
class WiyuRepo private constructor(private val app: Context) {

    val db = WiyuDb(app)
    val settings = SettingsStore(app)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val revision = MutableStateFlow(0)

    /** Incremented whenever the index changed; screens key their queries on it. */
    val version: StateFlow<Int> = revision

    private val scanState = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = scanState

    private val scanning = AtomicBoolean(false)
    private var lastBump = 0L

    suspend fun <T> query(block: (WiyuDb) -> T): T = withContext(Dispatchers.IO) { block(db) }

    fun refresh() {
        revision.value = revision.value + 1
    }

    /** Crawls granted folders and (when permitted) the whole device. */
    fun startScan(reason: String) {
        if (!scanning.compareAndSet(false, true)) return
        scanState.value = ScanState(running = true, reason = reason)
        scope.launch {
            val scanner = Scanner(app, db)
            var failures = 0
            // Each source counts from zero, so the reported progress carries the
            // offset of the sources already finished.
            var base = 0
            var indexed = 0
            val progress: (Int) -> Unit = { n -> publishProgress(base + n) }
            try {
                val roots = db.roots()
                val rootOutcome = scanner.scanRoots(roots, progress)
                failures += rootOutcome.failures
                base = rootOutcome.indexed
                val deviceOutcome = if (Scanner.hasFullStorageAccess(app)) {
                    scanner.scanDevice(progress)
                } else {
                    scanner.scanMediaStore(progress)
                }
                indexed = base + deviceOutcome.indexed
                failures += deviceOutcome.failures
                settings.setLastScanAt(System.currentTimeMillis())
                scanState.value = ScanState(
                    running = false,
                    reason = reason,
                    indexed = indexed,
                    failures = failures,
                    finishedAt = System.currentTimeMillis(),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "scan failed", t)
                scanState.value = ScanState(
                    running = false,
                    reason = reason,
                    indexed = indexed,
                    failures = failures,
                    error = t.message ?: t.javaClass.simpleName,
                    finishedAt = System.currentTimeMillis(),
                )
            } finally {
                scanning.set(false)
                refresh()
            }
        }
    }

    private fun publishProgress(indexed: Int) {
        if (indexed > scanState.value.indexed) {
            scanState.value = scanState.value.copy(indexed = indexed)
        }
        val now = System.currentTimeMillis()
        if (now - lastBump > 600) {
            lastBump = now
            refresh()
        }
    }

    // -- roots ----------------------------------------------------------------

    fun roots(): List<RootEntry> = db.roots()

    fun addRoot(uri: Uri, label: String) {
        db.addRoot(uri.toString(), label, isTree = true)
        refresh()
        startScan("folder added")
    }

    fun removeRoot(uri: String) {
        db.removeRoot(uri)
        refresh()
    }

    /** Human readable folder name for a granted tree. */
    suspend fun displayNameForTree(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            app.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (t: Throwable) {
            null
        } ?: "Folder"
    }

    // -- single files ---------------------------------------------------------

    /**
     * Remembers an open. Called by the shell before launching the editor and by
     * the editor itself for intents that bypass the shell.
     */
    fun recordOpen(uri: Uri?, name: String?, mime: String?) {
        if (uri == null) return
        val scheme = uri.scheme ?: return
        if (scheme != "content" && scheme != "file") return
        // Called straight from a click handler and from the editor's onCreate,
        // so the metadata query and the insert happen off the caller's thread.
        scope.launch {
            try {
                var displayName = name
                var size = 0L
                if (scheme == "content") {
                    app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            displayName = displayName ?: c.stringOrNull(OpenableColumns.DISPLAY_NAME)
                            size = c.longOrNull(OpenableColumns.SIZE) ?: 0L
                        }
                    }
                }
                val resolvedMime = mime ?: app.contentResolver.getType(uri)
                db.recordOpen(uri.toString(), displayName, resolvedMime, size, 0L)
                refresh()
            } catch (t: Throwable) {
                // Bookkeeping must never prevent a document from opening.
                Log.d(TAG, "cannot record open for $uri: ${t.message}")
            }
        }
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else null
    }

    /** Imports the old SharedPreferences recents list once, so nothing is lost. */
    fun migrateLegacyRecents() {
        if (settings.current.onboardingDone) return
        scope.launch {
            try {
                val prefs = app.getSharedPreferences("EXPLORER_PREFS", Context.MODE_PRIVATE)
                val raw = prefs.getString("RECENT_DOCUMENT_URIS", "").orEmpty()
                val uris = raw.split(' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                for (value in uris) {
                    val uri = Uri.parse(value)
                    recordOpen(uri, null, null)
                }
                settings.setOnboardingDone(true)
                Log.d(TAG, "imported ${uris.size} legacy recents")
            } catch (t: Throwable) {
                Log.d(TAG, "legacy recents import failed: ${t.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WiyuRepo"

        @Volatile
        private var instance: WiyuRepo? = null

        fun get(context: Context): WiyuRepo =
            instance ?: synchronized(this) {
                instance ?: WiyuRepo(context.applicationContext).also { instance = it }
            }
    }
}
