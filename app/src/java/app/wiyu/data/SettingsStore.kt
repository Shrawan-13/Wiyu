package app.wiyu.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WiyuSettings(
    val themeMode: Int = THEME_SYSTEM,
    val dynamicColor: Boolean = true,
    val onboardingDone: Boolean = false,
    val lastScanAt: Long = 0L,
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}

/** Tiny preference store; the shell has five settings, so SharedPreferences is plenty. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("wiyu_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val flow: StateFlow<WiyuSettings> = state

    val current: WiyuSettings get() = state.value

    private fun read() = WiyuSettings(
        themeMode = prefs.getInt(KEY_THEME, WiyuSettings.THEME_SYSTEM),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDED, false),
        lastScanAt = prefs.getLong(KEY_LAST_SCAN, 0L),
    )

    fun setThemeMode(mode: Int) = put { it.putInt(KEY_THEME, mode) }

    fun setDynamicColor(enabled: Boolean) = put { it.putBoolean(KEY_DYNAMIC, enabled) }

    fun setOnboardingDone(done: Boolean) = put { it.putBoolean(KEY_ONBOARDED, done) }

    fun setLastScanAt(millis: Long) = put { it.putLong(KEY_LAST_SCAN, millis) }

    private inline fun put(block: (android.content.SharedPreferences.Editor) -> Unit) {
        val editor = prefs.edit()
        block(editor)
        editor.apply()
        state.value = read()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_ONBOARDED = "onboarding_done"
        const val KEY_LAST_SCAN = "last_scan_at"
    }
}
