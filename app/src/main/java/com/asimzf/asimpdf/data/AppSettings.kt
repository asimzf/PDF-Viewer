package com.asimzf.asimpdf.data

import android.content.Context

/** Viewer preferences that should survive closing the app. */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("asimpdf_prefs", Context.MODE_PRIVATE)

    var nightMode: Boolean
        get() = prefs.getBoolean(KEY_NIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT, value).apply()

    var continuousScroll: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ON, value).apply()

    var signaturePath: String?
        get() = prefs.getString(KEY_SIGNATURE, null)
        set(value) = prefs.edit().putString(KEY_SIGNATURE, value).apply()

    fun lastPage(key: String): Int = prefs.getInt("page_$key", 0)

    fun rememberPage(key: String, page: Int) {
        prefs.edit().putInt("page_$key", page).apply()
    }

    private companion object {
        const val KEY_NIGHT = "night_mode"
        const val KEY_CONTINUOUS = "continuous_scroll"
        const val KEY_KEEP_ON = "keep_screen_on"
        const val KEY_SIGNATURE = "signature_path"
    }
}
