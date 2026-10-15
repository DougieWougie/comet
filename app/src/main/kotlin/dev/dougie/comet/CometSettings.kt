package dev.dougie.comet

import android.content.Context

class CometSettings(context: Context) {
    private val prefs = context.getSharedPreferences("comet", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, null) ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER, value.trim()).apply()

    /** True/false forces dark/light; null follows the system. */
    var darkMode: Boolean?
        get() = if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, true) else null
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_DARK) else putBoolean(KEY_DARK, value) }.apply()

    private companion object {
        const val KEY_DARK = "dark_mode"
        const val KEY_SERVER = "server_url"
    }
}
