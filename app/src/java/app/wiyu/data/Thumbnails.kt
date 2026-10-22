package app.wiyu.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thumbnails come from the platform (SAF/MediaStore providers render them for
 * us) and are cached in memory with a hard byte budget so a long list scroll
 * cannot blow up the heap. Files without a thumbnail fall back to the family
 * glyph in the UI.
 */
object Thumbnails {

    private const val CACHE_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun load(context: Context, uri: String, sizePx: Int): Bitmap? {
        val key = "$uri@$sizePx"
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { loadUncached(context, uri, sizePx) } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun loadUncached(context: Context, uri: String, sizePx: Int): Bitmap? {
        // ContentResolver.loadThumbnail is API 29+; below that the provider API is
        // a mess, so we just show the glyph.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.loadThumbnail(Uri.parse(uri), Size(sizePx, sizePx), null)
        } catch (t: Throwable) {
            null
        }
    }
}
