package app.wiyu.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * Small, dependency-free formatters. They are used from list rows, so they must
 * be allocation-cheap: the expensive SimpleDateFormat instances are created
 * lazily per call only when actually needed.
 */

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
        bytes < gb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
    }
}

private fun startOfToday(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** "Today", "Yesterday", "Last 7 days" or "Earlier" — used for list grouping. */
fun dateGroup(millis: Long): String {
    if (millis <= 0L) return "Earlier"
    val today = startOfToday()
    return when {
        millis >= today -> "Today"
        millis >= today - TimeUnit.DAYS.toMillis(1) -> "Yesterday"
        millis >= today - TimeUnit.DAYS.toMillis(6) -> "Last 7 days"
        else -> "Earlier"
    }
}

fun formatRelativeTime(millis: Long): String {
    if (millis <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - millis
    if (diff < 0) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 && millis >= startOfToday() -> "$hours h ago"
        millis >= startOfToday() - TimeUnit.DAYS.toMillis(1) -> "yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}

fun formatDateTime(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
}
