package app.wiyu.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A file as the UI sees it. */
data class FileEntry(
    val id: Long,
    val uri: String,
    val name: String,
    val path: String,
    val dir: String,
    val mime: String?,
    val family: DocFamily,
    val size: Long,
    val modified: Long,
    val lastOpened: Long,
    val isTemplate: Boolean,
) {
    val extension: String get() = FileTypes.normalizedExtension(name).removePrefix(".")
}

data class DirEntry(val path: String, val name: String, val fileCount: Int)

data class RootEntry(val uri: String, val label: String, val isTree: Boolean)

/** A row produced by the crawlers. */
data class ScannedFile(
    val uri: String,
    val name: String,
    val dir: String,
    val mime: String?,
    val family: DocFamily,
    val size: Long,
    val modified: Long,
    val root: String,
    val scanId: Long,
    val isTemplate: Boolean = false,
)

/**
 * Local index of discoverable documents.
 *
 * Plain SQLite (no annotation processor) with an FTS4 shadow table for search.
 * Every method here is blocking and must be called off the main thread — see
 * [WiyuRepo.query].
 */
class WiyuDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE files (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                uri TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                path TEXT NOT NULL,
                dir TEXT NOT NULL,
                mime TEXT,
                family TEXT NOT NULL,
                size INTEGER NOT NULL DEFAULT 0,
                modified INTEGER NOT NULL DEFAULT 0,
                root TEXT NOT NULL,
                scan_id INTEGER NOT NULL DEFAULT 0,
                template INTEGER NOT NULL DEFAULT 0,
                last_opened INTEGER NOT NULL DEFAULT 0,
                open_count INTEGER NOT NULL DEFAULT 0,
                missing INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX files_dir ON files(dir)")
        db.execSQL("CREATE INDEX files_root ON files(root, scan_id)")
        db.execSQL("CREATE INDEX files_recent ON files(last_opened DESC)")
        db.execSQL("CREATE INDEX files_family ON files(family)")
        db.execSQL(
            """
            CREATE TABLE dirs (
                path TEXT PRIMARY KEY,
                parent TEXT NOT NULL,
                root TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX dirs_parent ON dirs(parent)")
        db.execSQL(
            """
            CREATE TABLE roots (
                uri TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                is_tree INTEGER NOT NULL DEFAULT 1,
                added_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE VIRTUAL TABLE files_fts USING fts4(name, path)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema v1 — nothing to migrate yet.
    }

    // -- writes ---------------------------------------------------------------

    fun upsertScanned(items: List<ScannedFile>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) upsertOne(db, item)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun upsertOne(db: SQLiteDatabase, item: ScannedFile) {
        val path = if (item.dir.isEmpty()) item.name else "${item.dir}/${item.name}"
        val now = System.currentTimeMillis()
        val existing = existingId(db, item.uri)
        if (existing == null) {
            val values = ContentValues().apply {
                put("uri", item.uri)
                put("name", item.name)
                put("path", path)
                put("dir", item.dir)
                put("mime", item.mime)
                put("family", item.family.id)
                put("size", item.size)
                put("modified", item.modified)
                put("root", item.root)
                put("scan_id", item.scanId)
                put("template", if (item.isTemplate) 1 else 0)
                put("missing", 0)
                put("added_at", now)
            }
            val id = db.insert("files", null, values)
            if (id > 0) {
                db.execSQL(
                    "INSERT INTO files_fts(rowid, name, path) VALUES(?, ?, ?)",
                    arrayOf(id, item.name, path),
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO dirs(path, parent, root) VALUES(?, ?, ?)",
                    arrayOf(item.dir, parentOf(item.dir), item.root),
                )
            }
        } else {
            val values = ContentValues().apply {
                put("name", item.name)
                put("path", path)
                put("dir", item.dir)
                put("mime", item.mime)
                put("family", item.family.id)
                put("size", item.size)
                put("modified", item.modified)
                put("root", item.root)
                put("scan_id", item.scanId)
                put("template", if (item.isTemplate) 1 else 0)
                put("missing", 0)
            }
            db.update("files", values, "_id = ?", arrayOf(existing.toString()))
            db.execSQL(
                "UPDATE files_fts SET name = ?, path = ? WHERE rowid = ?",
                arrayOf(item.name, path, existing),
            )
            db.execSQL(
                "INSERT OR IGNORE INTO dirs(path, parent, root) VALUES(?, ?, ?)",
                arrayOf(item.dir, parentOf(item.dir), item.root),
            )
        }
    }

    private fun existingId(db: SQLiteDatabase, uri: String): Long? =
        db.rawQuery("SELECT _id FROM files WHERE uri = ?", arrayOf(uri)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    /** Marks every row of [root] as "not seen yet"; crawlers then stamp [scanId]. */
    fun beginRootScan(root: String) {
        writableDatabase.execSQL(
            "UPDATE files SET scan_id = -1 WHERE root = ?",
            arrayOf(root),
        )
    }

    /** Drops rows of [root] that the last crawl did not see. */
    fun finishRootScan(root: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT _id FROM files WHERE root = ? AND scan_id < 0", arrayOf(root)).use { c ->
                while (c.moveToNext()) {
                    db.execSQL("DELETE FROM files_fts WHERE rowid = ?", arrayOf(c.getLong(0)))
                }
            }
            db.delete("files", "root = ? AND scan_id < 0", arrayOf(root))
            db.execSQL(
                "DELETE FROM dirs WHERE root = ? AND path NOT IN (SELECT dir FROM files)",
                arrayOf(root),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Records an open. Files that are not indexed yet (opened through the
     * system "Open with" flow) are added under [LOOSE_DIR] so they show up in
     * recents without pretending to know their folder.
     */
    fun recordOpen(uri: String, name: String?, mime: String?, size: Long, modified: Long) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val existing = existingId(db, uri)
        if (existing != null) {
            db.execSQL(
                "UPDATE files SET last_opened = ?, open_count = open_count + 1 WHERE _id = ?",
                arrayOf(now, existing),
            )
            return
        }
        val displayName = name ?: uri.substringAfterLast('/')
        val family = FileTypes.familyFor(displayName, mime) ?: DocFamily.OTHER
        val path = "$LOOSE_DIR/$displayName"
        val values = ContentValues().apply {
            put("uri", uri)
            put("name", displayName)
            put("path", path)
            put("dir", LOOSE_DIR)
            put("mime", mime ?: FileTypes.mimeFor(displayName))
            put("family", family.id)
            put("size", size)
            put("modified", modified)
            put("root", ROOT_LOOSE)
            put("scan_id", 0)
            put("template", 0)
            put("last_opened", now)
            put("open_count", 1)
            put("missing", 0)
            put("added_at", now)
        }
        val id = db.insert("files", null, values)
        if (id > 0) {
            db.execSQL("INSERT INTO files_fts(rowid, name, path) VALUES(?, ?, ?)", arrayOf(id, displayName, path))
            db.execSQL(
                "INSERT OR IGNORE INTO dirs(path, parent, root) VALUES(?, ?, ?)",
                arrayOf(LOOSE_DIR, "", ROOT_LOOSE),
            )
        }
    }

    fun removeFile(uri: String) {
        val db = writableDatabase
        val id = existingId(db, uri) ?: return
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM files_fts WHERE rowid = ?", arrayOf(id))
            db.delete("files", "_id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addRoot(uri: String, label: String, isTree: Boolean = true) {
        val values = ContentValues().apply {
            put("uri", uri)
            put("label", label)
            put("is_tree", if (isTree) 1 else 0)
            put("added_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("roots", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO dirs(path, parent, root) VALUES(?, ?, ?)",
            arrayOf(label, "", uri),
        )
    }

    fun removeRoot(uri: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT _id FROM files WHERE root = ?", arrayOf(uri)).use { c ->
                while (c.moveToNext()) {
                    db.execSQL("DELETE FROM files_fts WHERE rowid = ?", arrayOf(c.getLong(0)))
                }
            }
            db.delete("files", "root = ?", arrayOf(uri))
            db.delete("dirs", "root = ?", arrayOf(uri))
            db.delete("roots", "uri = ?", arrayOf(uri))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearIndex() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Keep granted roots; drop everything discovered.
            db.execSQL("DELETE FROM files")
            db.execSQL("DELETE FROM files_fts")
            db.execSQL("DELETE FROM dirs")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // -- reads ----------------------------------------------------------------

    fun roots(): List<RootEntry> =
        readableDatabase.rawQuery(
            "SELECT uri, label, is_tree FROM roots ORDER BY added_at",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(RootEntry(c.getString(0), c.getString(1), c.getInt(2) == 1)) } }

    /** Recently opened first, then recently discovered. */
    fun recent(limit: Int): List<FileEntry> =
        readableDatabase.rawQuery(
            """
            SELECT * FROM files
            WHERE missing = 0 AND family != ?
            ORDER BY (CASE WHEN last_opened > 0 THEN last_opened ELSE added_at END) DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(DocFamily.OTHER.id, limit.toString()),
        ).use { c -> c.readEntries() }

    fun countsByFamily(): Map<DocFamily, Int> {
        val result = linkedMapOf<DocFamily, Int>()
        readableDatabase.rawQuery(
            "SELECT family, COUNT(*) FROM files WHERE missing = 0 GROUP BY family",
            null,
        ).use { c ->
            while (c.moveToNext()) result[DocFamily.fromId(c.getString(0))] = c.getInt(1)
        }
        return result
    }

    fun totalFiles(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM files WHERE missing = 0", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun subdirs(parent: String): List<DirEntry> =
        readableDatabase.rawQuery(
            """
            SELECT d.path, (SELECT COUNT(*) FROM files f WHERE f.dir = d.path AND f.missing = 0)
            FROM dirs d
            WHERE d.parent = ?
            ORDER BY d.path COLLATE NOCASE
            """.trimIndent(),
            arrayOf(parent),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val path = c.getString(0)
                    add(DirEntry(path, path.substringAfterLast('/'), c.getInt(1)))
                }
            }
        }

    fun children(dir: String): List<FileEntry> =
        readableDatabase.rawQuery(
            "SELECT * FROM files WHERE dir = ? AND missing = 0 ORDER BY name COLLATE NOCASE",
            arrayOf(dir),
        ).use { c -> c.readEntries() }

    fun filesInFamily(family: DocFamily, limit: Int): List<FileEntry> =
        readableDatabase.rawQuery(
            """
            SELECT * FROM files WHERE family = ? AND missing = 0
            ORDER BY (CASE WHEN last_opened > 0 THEN last_opened ELSE modified END) DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(family.id, limit.toString()),
        ).use { c -> c.readEntries() }

    fun search(query: String, family: DocFamily?, limit: Int): List<FileEntry> {
        val match = toFtsQuery(query) ?: return emptyList()
        val sql = StringBuilder(
            """
            SELECT f.* FROM files_fts JOIN files f ON f._id = files_fts.rowid
            WHERE files_fts MATCH ? AND f.missing = 0
            """.trimIndent()
        )
        val args = mutableListOf(match)
        if (family != null) {
            sql.append(" AND f.family = ?")
            args.add(family.id)
        }
        sql.append(" ORDER BY f.last_opened DESC, f.modified DESC LIMIT ?")
        args.add(limit.toString())
        return readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { c -> c.readEntries() }
    }

    fun searchCount(query: String, family: DocFamily?): Int {
        val match = toFtsQuery(query) ?: return 0
        val sql = StringBuilder(
            "SELECT COUNT(*) FROM files_fts JOIN files f ON f._id = files_fts.rowid WHERE files_fts MATCH ? AND f.missing = 0"
        )
        val args = mutableListOf(match)
        if (family != null) {
            sql.append(" AND f.family = ?")
            args.add(family.id)
        }
        return readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun Cursor.readEntries(): List<FileEntry> {
        val idIdx = getColumnIndexOrThrow("_id")
        val uriIdx = getColumnIndexOrThrow("uri")
        val nameIdx = getColumnIndexOrThrow("name")
        val pathIdx = getColumnIndexOrThrow("path")
        val dirIdx = getColumnIndexOrThrow("dir")
        val mimeIdx = getColumnIndexOrThrow("mime")
        val familyIdx = getColumnIndexOrThrow("family")
        val sizeIdx = getColumnIndexOrThrow("size")
        val modifiedIdx = getColumnIndexOrThrow("modified")
        val openedIdx = getColumnIndexOrThrow("last_opened")
        val templateIdx = getColumnIndexOrThrow("template")
        return buildList {
            while (moveToNext()) {
                add(
                    FileEntry(
                        id = getLong(idIdx),
                        uri = getString(uriIdx),
                        name = getString(nameIdx),
                        path = getString(pathIdx),
                        dir = getString(dirIdx),
                        mime = getString(mimeIdx),
                        family = DocFamily.fromId(getString(familyIdx)),
                        size = getLong(sizeIdx),
                        modified = getLong(modifiedIdx),
                        lastOpened = getLong(openedIdx),
                        isTemplate = getInt(templateIdx) == 1,
                    )
                )
            }
        }
    }

    companion object {
        private const val NAME = "wiyu.db"
        private const val VERSION = 1

        /** Index root used for files that arrived through "Open with". */
        const val ROOT_LOOSE = "loose"
        const val LOOSE_DIR = "Opened from other apps"

        fun parentOf(path: String): String {
            val idx = path.lastIndexOf('/')
            return if (idx <= 0) "" else path.substring(0, idx)
        }

        /**
         * Turns free text into a safe FTS4 prefix query. Returns null when the
         * input has no usable token, which callers treat as "no results"
         * rather than running a malformed MATCH.
         */
        fun toFtsQuery(input: String): String? {
            val tokens = input.split(Regex("[^\\p{L}\\p{N}_]+"))
                .filter { it.isNotBlank() }
                .take(6)
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
