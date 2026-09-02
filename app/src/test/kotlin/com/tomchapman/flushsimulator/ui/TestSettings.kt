package com.tomchapman.flushsimulator.ui

import com.tomchapman.flushsimulator.core.Settings

/**
 * An in-memory [Settings], so a test can seed a well-played game by writing one.
 *
 * `core` has its own for its own tests; test doubles do not cross a module boundary
 * without dragging in test fixtures, and this is eight lines.
 */
internal class MapSettings(initial: Map<String, Any> = emptyMap()) : Settings {
    private val values = LinkedHashMap(initial)

    override fun getInt(key: String, default: Int) = (values[key] as? Int) ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getDouble(key: String, default: Double) = (values[key] as? Double) ?: default
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun getString(key: String) = values[key] as? String
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
