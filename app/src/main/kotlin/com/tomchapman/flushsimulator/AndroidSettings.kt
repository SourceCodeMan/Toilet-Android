package com.tomchapman.flushsimulator

import android.content.Context
import android.content.SharedPreferences
import com.tomchapman.flushsimulator.core.Settings

/**
 * `core`'s [Settings], over SharedPreferences.
 *
 * The Swift wrote straight to `UserDefaults`; this is the same store by another name.
 * Writes are `apply()` rather than `commit()` — nothing here is worth blocking the
 * frame for, and the worst case is losing the last flush to a kill -9.
 */
class AndroidSettings(context: Context) : Settings {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("flush", Context.MODE_PRIVATE)

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getDouble(key: String, default: Double): Double =
        java.lang.Double.longBitsToDouble(prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default)))

    override fun putDouble(key: String, value: Double) {
        prefs.edit().putLong(key, java.lang.Double.doubleToRawLongBits(value)).apply()
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
