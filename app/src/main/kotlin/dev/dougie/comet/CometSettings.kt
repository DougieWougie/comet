package dev.dougie.comet

import android.content.Context

class CometSettings(context: Context) {
    private val prefs = context.getSharedPreferences("comet", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, null) ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER, value.trim()).apply()

    private companion object {
        const val KEY_SERVER = "server_url"
    }
}
