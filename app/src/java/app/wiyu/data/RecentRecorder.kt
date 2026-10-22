package app.wiyu.data

import android.content.Context
import android.net.Uri

/**
 * Java-facing bridge used by the editor activity.
 *
 * Documents can reach the editor without passing through the Wiyu shell (the
 * system "Open with" flow, or a second intent while the app is running), so the
 * editor records them itself. Work happens on a scratch thread because this is
 * called from `onCreate`.
 */
object RecentRecorder {

    @JvmStatic
    fun record(context: Context, uri: Uri?, mime: String?) {
        if (uri == null) return
        val app = context.applicationContext
        Thread({
            try {
                WiyuRepo.get(app).recordOpen(uri, null, mime)
            } catch (t: Throwable) {
                // Never let bookkeeping take down document opening.
            }
        }, "wiyu-recent").start()
    }
}
